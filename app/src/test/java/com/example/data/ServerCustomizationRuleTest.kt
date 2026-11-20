package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.ChannelType
import com.example.model.RolePermission
import com.example.model.ServerModerationConfig
import com.example.model.ServerNotificationConfig
import com.example.model.ServerNotificationLevel
import com.example.model.ServerPrivacyConfig
import com.example.model.ServerThemeSurfaceStyle
import com.example.model.ServerVerificationLevel
import com.example.model.ServerWelcomeConfig
import com.example.model.UserRole
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ServerCustomizationRuleTest : FirestoreEmulatorTestBase() {

    private fun newRepos(scope: CoroutineScope): Triple<ChatRepository, CommunityRepository, ServerCustomizationRepository> {
        val chatRepo = ChatRepository(
            scope = scope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        val commRepo = CommunityRepository(chatRepo)
        val customRepo = ServerCustomizationRepository(commRepo)
        return Triple(chatRepo, commRepo, customRepo)
    }

    @Test
    fun customizationLifecycle_enforcesServerSidePermissionsAndSecurityIsolation() = runBlocking {
        val aliceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (aliceChat, aliceComm, aliceCustom) = newRepos(aliceScope)
        val (bobChat, bobComm, bobCustom) = newRepos(bobScope)

        try {
            val aliceUid = signInTestUser(ALICE_EMAIL)
            val suffix = UUID.randomUUID().toString().take(6)
            val handle = "custom-$suffix"
            val created = aliceComm.createCommunity(
                name = "Studio Alpha $suffix",
                description = "Serveur initial",
                uniqueHandleRaw = handle,
                imageUrl = null,
                bannerUrl = null,
                isPublic = true,
                categoryTag = "Tech",
                rulesGuidelines = "Respect",
                homeWelcomeMessage = "Bienvenue"
            ).getOrThrow()

            // 1. Identity, Icon preset, Banner & Theme
            val themed = aliceCustom.updateIdentityAndTheme(
                serverId = created.id,
                name = "Studio Nova $suffix",
                uniqueHandleRaw = handle,
                description = "Serveur ultra personnalisé",
                iconText = "SN",
                iconPresetKey = "rocket",
                imageUrl = "https://example.com/icon.png",
                bannerUrl = "https://example.com/banner.png",
                accentColorHex = 0xFF7C3AEDL,
                secondaryColorHex = 0xFF14B8A6L,
                surfaceStyle = ServerThemeSurfaceStyle.ROYAL_PURPLE,
                categoryTag = "Design"
            ).getOrThrow()
            assertEquals("Studio Nova $suffix", themed.name)
            assertEquals("rocket", themed.customization.theme.iconPresetKey)

            // 2. Categories & Channels with custom icons & positions
            val vipCat = aliceCustom.upsertCategory(created.id, null, "ESPACE VIP", isPrivate = false).getOrThrow()
            aliceCustom.moveCategoryOrder(created.id, vipCat.id, -1).getOrThrow()
            val ch1 = aliceCustom.upsertChannelCustomization(
                serverId = created.id,
                channelId = null,
                name = "annonces-pro",
                topic = "Mises à jour",
                type = ChannelType.ANNOUNCEMENT,
                categoryName = "ESPACE VIP",
                iconKey = "megaphone",
                slowmodeSeconds = 15,
                isReadOnly = true,
                isPrivate = false
            ).getOrThrow()
            val ch2 = aliceCustom.upsertChannelCustomization(
                serverId = created.id,
                channelId = null,
                name = "dev-shield",
                topic = "Sécurité",
                type = ChannelType.TEXT,
                categoryName = "ESPACE VIP",
                iconKey = "shield",
                slowmodeSeconds = 0,
                isReadOnly = false,
                isPrivate = false
            ).getOrThrow()
            aliceCustom.moveChannelOrder(created.id, ch2.id, -1).getOrThrow()
            assertEquals("shield", ch2.iconKey)

            // 3. Roles & permissions
            val customRole = aliceComm.saveCustomRole(
                serverId = created.id,
                roleName = "Architecte",
                colorHex = 0xFFF59E0BL,
                permissions = setOf(RolePermission.SEND_MESSAGES, RolePermission.MANAGE_CHANNELS)
            ).getOrThrow()
            aliceCustom.moveRoleOrder(created.id, customRole.id, -1).getOrThrow()

            // 4. Welcome, Goodbye & Notifications
            aliceCustom.updateWelcomeAndGoodbyeConfig(
                serverId = created.id,
                welcomeConfig = ServerWelcomeConfig(
                    welcomeEnabled = true,
                    welcomeChannelId = ch2.id,
                    welcomeMessageTemplate = "Salut {user}, welcome to {server}",
                    goodbyeEnabled = true,
                    goodbyeChannelId = ch2.id,
                    goodbyeMessageTemplate = "Bye {user}",
                    dmWelcomeEnabled = true
                ),
                homeWelcomeMessage = "Accueil personnalisé"
            ).getOrThrow()
            aliceCustom.updateNotificationConfig(
                serverId = created.id,
                config = ServerNotificationConfig(
                    defaultLevel = ServerNotificationLevel.MENTIONS_ONLY,
                    suppressEveryoneMentions = true,
                    suppressRoleMentions = false,
                    highlightAnnouncements = true
                )
            ).getOrThrow()

            // 5. Rules, Verification, Moderation (blockedWords isolated!) & Privacy
            aliceCustom.updateRulesAndVerification(
                serverId = created.id,
                rulesList = listOf("Respecter les membres", "Pas de spam"),
                rulesGuidelines = "Respecter les membres\nPas de spam",
                requireRulesAcceptance = true,
                verificationLevel = ServerVerificationLevel.HIGH
            ).getOrThrow()
            aliceCustom.updateModerationConfig(
                serverId = created.id,
                config = ServerModerationConfig(
                    autoModEnabled = true,
                    blockSpamAndLinks = true,
                    blockBadWords = true,
                    blockedWords = listOf("scam", "phishing"),
                    defaultSlowmodeSeconds = 10,
                    maxMentionsPerMessage = 3,
                    antiRaidModeEnabled = true
                )
            ).getOrThrow()

            // Verify Firestore isolation: blockedWords is in /admin_settings/security, NOT /servers/{id}
            val srvDoc = withTimeout(TIMEOUT_MS) { firestore.collection("servers").document(created.id).get().await() }
            @Suppress("UNCHECKED_CAST")
            val modMap = srvDoc.get("moderationConfig") as? Map<String, Any?>
            assertNull("blockedWords must never be stored in public /servers doc", modMap?.get("blockedWords"))

            val secDoc = withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(created.id)
                    .collection("admin_settings").document("security").get().await()
            }
            @Suppress("UNCHECKED_CAST")
            val storedWords = secDoc.get("blockedWords") as? List<String>
            assertEquals(listOf("scam", "phishing"), storedWords)

            // Generate invite while allowed, then disable invites in privacy
            val invite = aliceComm.createInvite(created.id, maxUses = 5, expiresInHours = 24).getOrThrow()
            aliceCustom.updatePrivacyConfig(
                serverId = created.id,
                isPublic = false,
                config = ServerPrivacyConfig(
                    discoverableInCatalog = false,
                    allowInviteLinks = false,
                    requireInviteApproval = true,
                    allowMemberDms = false,
                    hideMemberListForNonStaff = true
                )
            ).getOrThrow()

            // 6. Switch to Bob (normal user):
            val bobUid = signInTestUser(BOB_EMAIL)

            // Bob CANNOT join via invite once allowInviteLinks == false (enforced server-side!)
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id).update(
                        mapOf(
                            "memberIds" to FieldValue.arrayUnion(bobUid),
                            "lastInviteCodeUsed" to invite.code,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                fail("Server rule must reject invite join when allowInviteLinks=false")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Re-enable invites & make public briefly so Bob joins as regular member
            signInTestUser(ALICE_EMAIL)
            aliceCustom.updatePrivacyConfig(
                serverId = created.id,
                isPublic = true,
                config = ServerPrivacyConfig(
                    discoverableInCatalog = true,
                    allowInviteLinks = true,
                    requireInviteApproval = false,
                    allowMemberDms = true,
                    hideMemberListForNonStaff = true
                )
            ).getOrThrow()

            signInTestUser(BOB_EMAIL)
            val joinRes = bobComm.joinByInviteCode(invite.code)
            assertTrue("Bob should join once invites re-enabled: ${joinRes.exceptionOrNull()?.message}", joinRes.isSuccess)

            // Regular member Bob CANNOT read /admin_settings/security
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id)
                        .collection("admin_settings").document("security").get().await()
                }
                fail("Non-staff member Bob must not read admin_settings/security")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Regular member Bob CAN read /admin_settings/public_policy
            val policyDoc = withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(created.id)
                    .collection("admin_settings").document("public_policy").get().await()
            }
            assertTrue(policyDoc.exists())
            assertEquals("HIGH", policyDoc.getString("verificationLevel"))

            // Regular member Bob CANNOT tamper with verificationLevel or moderationConfig or customRoles
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id)
                        .update("verificationLevel", "NONE").await()
                }
                fail("Member Bob must not modify verificationLevel")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id)
                        .collection("admin_settings").document("security")
                        .set(
                            mapOf(
                                "settingType" to "security",
                                "serverId" to created.id,
                                "updatedByUid" to bobUid,
                                "autoModEnabled" to false
                            )
                        ).await()
                }
                fail("Member Bob must not write admin_settings/security")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Clean up
            signInTestUser(ALICE_EMAIL)
            assertFalse(aliceCustom.deleteCustomRole(created.id, "role_admin").isSuccess)
            assertTrue(aliceCustom.deleteCustomRole(created.id, customRole.id).isSuccess)
            assertTrue(aliceComm.deleteCommunity(created.id).isSuccess)
            val deletedSnap = withTimeout(TIMEOUT_MS) { firestore.collection("servers").document(created.id).get().await() }
            assertFalse(deletedSnap.exists())
            assertEquals(aliceUid, auth.currentUser?.uid)
        } finally {
            aliceChat.releaseResources()
            bobChat.releaseResources()
            aliceScope.cancel()
            bobScope.cancel()
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.custom@test.com"
        const val BOB_EMAIL = "bob.custom@test.com"
        const val TIMEOUT_MS = 5000L
    }
}
