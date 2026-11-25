package com.example

import com.example.data.ChatRepository
import com.example.model.ShopCatalog
import com.example.model.ShopCategory
import com.example.model.SuperBadgesCatalog
import com.example.ui.components.OrbsWheelEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrbsShopAndWheelUnitTest {

    @Test
    fun `wheel max reward is 500 orbs and over 200 odds are strictly rare`() {
        assertEquals(500, OrbsWheelEngine.maxPrizeOrbs())
        assertTrue(OrbsWheelEngine.prizes.all { it.orbs <= 500 })
        assertTrue(OrbsWheelEngine.prizes.none { it.orbs > 500 })

        val probOver200 = OrbsWheelEngine.probabilityForOver200()
        assertTrue("Expected >200 Orbs probability <= 0.08, was $probOver200", probOver200 <= 0.08)
        assertTrue("Expected >200 Orbs probability > 0, was $probOver200", probOver200 > 0.0)

        val totalRolls = 1000
        var over200Count = 0
        var jackpot500Count = 0
        for (i in 0 until totalRolls) {
            val roll = (i + 0.5f) / totalRolls.toFloat()
            val idx = OrbsWheelEngine.pickWeightedPrizeIndex(roll)
            val won = OrbsWheelEngine.prizes[idx]
            assertTrue(won.orbs <= 500)
            if (won.orbs > 200) over200Count++
            if (won.orbs == 500) jackpot500Count++
        }

        assertEquals(60, over200Count) // exact 6.0% across uniform grid
        assertEquals(18, jackpot500Count) // exact 1.8% jackpot 500
    }

    @Test
    fun `shop catalog contains upgraded items new models valid themes bundles and badges`() {
        val itemsById = ShopCatalog.initialShopItems.associateBy { it.id }
        val themeIds = ShopCatalog.initialChatThemes.map { it.id }.toSet()
        val badgeIds = SuperBadgesCatalog.allBadges.map { it.id }.toSet()

        // Verify new models exist across categories
        assertTrue(itemsById.containsKey("dec_eclipse_crimson"))
        assertTrue(itemsById.containsKey("dec_quantum_vortex"))
        assertTrue(itemsById.containsKey("dec_kitsune_spirit"))
        assertTrue(itemsById.containsKey("dec_katana_slash"))
        assertTrue(itemsById.containsKey("dec_prism_hologram"))
        assertTrue(itemsById.containsKey("dec_neural_cyber"))
        assertTrue(itemsById.containsKey("dec_starlight_crown"))
        assertTrue(itemsById.containsKey("dec_obsidian_seraph"))
        assertTrue(itemsById.containsKey("eff_aurora_curtain"))
        assertTrue(itemsById.containsKey("eff_cyber_rain"))
        assertTrue(itemsById.containsKey("eff_golden_feathers"))
        assertTrue(itemsById.containsKey("eff_crimson_eclipse"))
        assertTrue(itemsById.containsKey("bundle_kitsune_sakura"))
        assertTrue(itemsById.containsKey("bundle_cyber_overdrive"))
        assertTrue(itemsById.containsKey("bundle_celestial_emperor"))

        // Integrity checks for bundles, themes, and badges
        ShopCatalog.initialShopItems.forEach { item ->
            if (item.category == ShopCategory.CHAT_THEME) {
                assertTrue("Unknown chat theme ${item.decorationKey}", themeIds.contains(item.decorationKey))
            }
            item.badgeId?.let { bId ->
                assertTrue("Unknown badge $bId on ${item.id}", badgeIds.contains(bId))
            }
            item.bundleItemIds.forEach { childId ->
                assertTrue("Bundle ${item.id} references missing $childId", itemsById.containsKey(childId))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `repository persists bundle purchase individual items favorites and chat theme`() = runTest {
        val repo = ChatRepository(TestScope(testScheduler))
        repo.updateOrbsBalance(1200)

        val cyberPack = ShopCatalog.initialShopItems.first { it.id == "bundle_cyber_overdrive" }
        assertTrue(repo.purchaseShopItem(cyberPack, targetChatId = "c_general"))

        val updated = repo.currentUser.value
        assertEquals(1200 - cyberPack.priceOrbs, updated.orbsBalance)
        assertTrue(updated.purchasedShopItemIds.containsAll(cyberPack.bundleItemIds))
        assertTrue(updated.badges.contains("CYBER_LEGEND"))
        assertEquals("prism_hologram", updated.avatarDecoration)
        assertEquals("eff_cyber_rain", updated.profileEffect)
        assertEquals("cyberpunk", updated.equippedChatThemeId)
        assertEquals("cyberpunk", repo.chatBackgrounds.value["c_general"]?.id)

        // Insufficient Orbs check
        val emperorPack = ShopCatalog.initialShopItems.first { it.id == "bundle_celestial_emperor" }
        assertFalse(repo.purchaseShopItem(emperorPack, targetChatId = "c_general"))
        assertFalse(repo.currentUser.value.purchasedShopItemIds.contains(emperorPack.id))

        // Favorites toggle persistence
        repo.toggleShopFavorite("dec_kitsune_spirit")
        assertTrue(repo.currentUser.value.favoriteShopItemIds.contains("dec_kitsune_spirit"))
        repo.toggleShopFavorite("dec_kitsune_spirit")
        assertFalse(repo.currentUser.value.favoriteShopItemIds.contains("dec_kitsune_spirit"))
    }
}
