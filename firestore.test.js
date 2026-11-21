const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write users, public_profiles, or servers", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("public_profiles").get());
  await assertFails(unauthDb.collection("servers").where("isPublic", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set({ userId: ALICE_UID, name: "Hack" }));
});

test("User PII isolation: Bob cannot read or write Alice private profile or notifications", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice",
      email: "alice@secret.com",
      orbsBalance: 1000,
    });
    await adminDb.collection("users").doc(ALICE_UID).collection("notifications").doc("n1").set({
      userId: ALICE_UID,
      title: "Private",
      body: "Secret mention",
      isRead: false,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notifications").get());

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).set({ userId: ALICE_UID, name: "Pwned" }));
});

test("Public profiles can be listed by signed-in users without exposing PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice",
      clanTag: "RVN",
      xp: 220,
      level: 3,
      isPublic: true,
    })
  );
  await assertSucceeds(bobDb.collection("public_profiles").where("isPublic", "==", true).get());
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Spoof",
      isPublic: true,
    })
  );
});

test("Public servers can be discovered and joined, while private servers reject non-members", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("servers").doc("srv_pub").set({
      name: "Raven Public",
      ownerId: ALICE_UID,
      isPublic: true,
      memberIds: [ALICE_UID],
      inviteCode: "RAVEN-PUB",
      boostCount: 0,
      boostLevel: 0,
    })
  );

  await assertSucceeds(
    aliceDb.collection("servers").doc("srv_priv").set({
      name: "Raven Private",
      ownerId: ALICE_UID,
      isPublic: false,
      memberIds: [ALICE_UID],
      inviteCode: "RAVEN-PRIV",
      boostCount: 0,
      boostLevel: 0,
    })
  );

  // Bob can query public servers, cannot read private server
  await assertSucceeds(bobDb.collection("servers").where("isPublic", "==", true).get());
  await assertFails(bobDb.collection("servers").doc("srv_priv").get());

  // Bob can join public server by adding himself to memberIds
  await assertSucceeds(
    bobDb.collection("servers").doc("srv_pub").update({
      memberIds: [ALICE_UID, BOB_UID],
    })
  );

  // Bob cannot hijack ownerId or join private server directly
  await assertFails(
    bobDb.collection("servers").doc("srv_pub").update({
      ownerId: BOB_UID,
      memberIds: [ALICE_UID, BOB_UID],
    })
  );
  await assertFails(
    bobDb.collection("servers").doc("srv_priv").update({
      memberIds: [ALICE_UID, BOB_UID],
    })
  );
});

test("Channel messages, whiteboard, jukebox, voice_peers, and rtc_signals enforce membership and sender rules", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("srv_1").set({
      name: "Guild",
      ownerId: ALICE_UID,
      isPublic: false,
      memberIds: [ALICE_UID, BOB_UID],
    });
    await db.collection("servers").doc("srv_1").collection("channels").doc("ch_1").set({
      serverId: "srv_1",
      name: "general",
      type: "TEXT",
      isPublic: false,
      memberIds: [ALICE_UID, BOB_UID],
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Alice sends message
  await assertSucceeds(
    aliceDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("messages").doc("msg_1").set({
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Hello WebRTC!",
      isPublic: false,
      memberIds: [ALICE_UID, BOB_UID],
    })
  );

  // Charlie (non-member) cannot list unfiltered, read directly, or write messages
  await assertFails(
    charlieDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("messages").doc("msg_2").set({
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      text: "Intrusion",
      isPublic: false,
      memberIds: [CHARLIE_UID],
    })
  );

  // Bob can list messages he belongs to, and react to Alice's message, but cannot edit Alice's text
  await assertSucceeds(
    bobDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("messages").where("memberIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("messages").doc("msg_1").update({
      reactions: { "🔥": { count: 1, users: [BOB_UID] } },
    })
  );
  await assertFails(
    bobDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("messages").doc("msg_1").update({
      text: "Edited by Bob",
    })
  );

  // Whiteboard & Jukebox sync by member
  await assertSucceeds(
    bobDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("whiteboard").doc("canvas").set({
      serverId: "srv_1",
      updatedBy: BOB_UID,
      memberIds: [ALICE_UID, BOB_UID],
      isPublic: false,
      strokes: [{ colorHex: 123456, strokeWidth: 6, points: ["0,0", "10,10"] }],
    })
  );
  await assertSucceeds(
    aliceDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("jukebox").doc("state").set({
      serverId: "srv_1",
      updatedBy: ALICE_UID,
      isPlaying: true,
      elapsedSec: 12,
      memberIds: [ALICE_UID, BOB_UID],
      isPublic: false,
    })
  );

  // Voice peer presence & WebRTC signaling
  await assertSucceeds(
    aliceDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("voice_peers").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      isMuted: false,
      isDeafened: false,
      isCameraOn: true,
      isScreenSharing: false,
      memberIds: [ALICE_UID, BOB_UID],
      isPublic: false,
    })
  );
  await assertFails(
    bobDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("voice_peers").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Spoof",
      memberIds: [ALICE_UID, BOB_UID],
    })
  );

  await assertSucceeds(
    aliceDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("rtc_signals").doc("sig_1").set({
      fromUid: ALICE_UID,
      toUid: BOB_UID,
      type: "OFFER",
      payload: "v=0...",
    })
  );
  await assertSucceeds(
    bobDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("rtc_signals").where("toUid", "==", BOB_UID).get()
  );
  await assertFails(
    charlieDb.collection("servers").doc("srv_1").collection("channels").doc("ch_1").collection("rtc_signals").doc("sig_1").get()
  );
});

test("Advanced communities: server-side permissions block normal users from sensitive edits, role escalation, announcements, invites, and deletion", async () => {
  const DANA_UID = "dana_999";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("comm_1").set({
      name: "Raven Builders",
      uniqueHandle: "raven-builders",
      description: "Communauté officielle",
      ownerId: ALICE_UID,
      adminIds: [ALICE_UID, DANA_UID],
      moderatorIds: [CHARLIE_UID],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID, DANA_UID],
      isPublic: false,
      inviteCode: "BUILD-2026",
    });
    await db.collection("servers").doc("comm_1").collection("members").doc(BOB_UID).set({
      uid: BOB_UID,
      serverId: "comm_1",
      name: "Bob",
      role: "MEMBER",
      isTimedOut: false,
      isPublic: false,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const danaDb = testEnv.authenticatedContext(DANA_UID).firestore();

  // Normal member (Bob) CANNOT modify sensitive community data, channels, announcements, invites, or delete community
  await assertFails(bobDb.collection("servers").doc("comm_1").update({ name: "Hacked Name" }));
  await assertFails(bobDb.collection("servers").doc("comm_1").update({ isPublic: true }));
  await assertFails(bobDb.collection("servers").doc("comm_1").update({ adminIds: [ALICE_UID, BOB_UID] }));
  await assertFails(bobDb.collection("servers").doc("comm_1").delete());
  await assertFails(
    bobDb.collection("servers").doc("comm_1").collection("channels").doc("ch_illegal").set({
      serverId: "comm_1",
      name: "illegal",
      type: "TEXT",
      memberIds: [ALICE_UID, BOB_UID],
    })
  );
  await assertFails(
    bobDb.collection("servers").doc("comm_1").collection("announcements").doc("ann_bad").set({
      serverId: "comm_1",
      title: "Fake",
      content: "Not allowed",
      authorId: BOB_UID,
      memberIds: [ALICE_UID, BOB_UID],
    })
  );
  await assertFails(
    bobDb.collection("community_invites").doc("BOB-INV").set({
      code: "BOB-INV",
      serverId: "comm_1",
      createdByUid: BOB_UID,
      isRevoked: false,
    })
  );

  // Privilege escalation on /members blocked for normal member
  await assertFails(
    bobDb.collection("servers").doc("comm_1").collection("members").doc(BOB_UID).update({
      role: "ADMIN",
    })
  );
  // Bob CAN update his own online status only
  await assertSucceeds(
    bobDb.collection("servers").doc("comm_1").collection("members").doc(BOB_UID).update({
      status: "IDLE",
    })
  );

  // Moderator (Charlie) CAN publish announcements, create invites, and timeout Bob,
  // but CANNOT rename community, promote admins, or delete community
  await assertSucceeds(
    charlieDb.collection("servers").doc("comm_1").collection("announcements").doc("ann_mod").set({
      serverId: "comm_1",
      title: "Annonce Modération",
      content: "Nouveau tournoi ce week-end",
      authorId: CHARLIE_UID,
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID, DANA_UID],
    })
  );
  await assertSucceeds(
    charlieDb.collection("servers").doc("comm_1").collection("members").doc(BOB_UID).update({
      isTimedOut: true,
    })
  );
  await assertFails(charlieDb.collection("servers").doc("comm_1").update({ name: "Mod Rename Blocked" }));
  await assertFails(charlieDb.collection("servers").doc("comm_1").delete());

  // Admin (Dana) CAN update community name/description/banner/roles, CANNOT delete community (Owner-only)
  await assertSucceeds(
    danaDb.collection("servers").doc("comm_1").update({
      description: "Description mise à jour par Admin",
    })
  );
  await assertFails(danaDb.collection("servers").doc("comm_1").delete());

  // Member leaving community removes ONLY themselves
  await assertSucceeds(
    bobDb.collection("servers").doc("comm_1").update({
      memberIds: [ALICE_UID, CHARLIE_UID, DANA_UID],
    })
  );

  // Invite lifecycle: Admin creates invite, revoked invite cannot be used to join private community
  await assertSucceeds(
    aliceDb.collection("community_invites").doc("VIP-JOIN").set({
      code: "VIP-JOIN",
      serverId: "comm_1",
      createdByUid: ALICE_UID,
      isRevoked: false,
      maxUses: 5,
      usesCount: 0,
    })
  );
  await assertSucceeds(
    bobDb.collection("servers").doc("comm_1").update({
      memberIds: [ALICE_UID, CHARLIE_UID, DANA_UID, BOB_UID],
      lastInviteCodeUsed: "VIP-JOIN",
    })
  );

  // Revoke invite and verify newcomer cannot join with revoked invite
  await assertSucceeds(
    aliceDb.collection("community_invites").doc("VIP-JOIN").update({ isRevoked: true })
  );
  const EVE_UID = "eve_001";
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(
    eveDb.collection("servers").doc("comm_1").update({
      memberIds: [ALICE_UID, CHARLIE_UID, DANA_UID, BOB_UID, EVE_UID],
      lastInviteCodeUsed: "VIP-JOIN",
    })
  );

  // Owner (Alice) CAN delete the community
  await assertSucceeds(aliceDb.collection("servers").doc("comm_1").delete());
});

test("Raven Bots: owner isolation, token secret protection, handle ownership, server installs, and verified bot messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("bot_srv").set({
      name: "Bot Arena",
      ownerId: ALICE_UID,
      adminIds: [ALICE_UID],
      moderatorIds: [],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
    });
    await db.collection("servers").doc("bot_srv").collection("channels").doc("bot_ch").set({
      serverId: "bot_srv",
      name: "bot-lab",
      type: "TEXT",
      isPublic: false,
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const fakeSha256 = "a".repeat(64);

  // 1. Alice cannot leak plaintext token or tokenHash inside public /bots/{botId} doc
  await assertFails(
    aliceDb.collection("bots").doc("bot_leak").set({
      id: "bot_leak",
      ownerUid: ALICE_UID,
      name: "LeakBot",
      handle: "leakbot",
      isActive: true,
      token: "rvnbot_live_plaintext_forbidden",
    })
  );

  // 2. Alice reserves handle, creates bot doc (metadata only), and stores SHA-256 hash in /secrets
  await assertSucceeds(
    aliceDb.collection("bot_handles").doc("sentinel").set({
      handle: "sentinel",
      botId: "bot_alice_1",
      ownerUid: ALICE_UID,
      name: "Sentinel",
    })
  );
  await assertSucceeds(
    aliceDb.collection("bots").doc("bot_alice_1").set({
      id: "bot_alice_1",
      ownerUid: ALICE_UID,
      name: "Sentinel",
      handle: "sentinel",
      description: "Moderation & automation bot",
      isActive: true,
      isPublicCatalog: false,
      tokenPrefix: "rvnbot_live_abcd",
      tokenVersion: 1,
      defaultPermissions: ["SEND_MESSAGES", "USE_SLASH_COMMANDS"],
    })
  );
  await assertSucceeds(
    aliceDb.collection("bots").doc("bot_alice_1").collection("secrets").doc("token_meta").set({
      botId: "bot_alice_1",
      ownerUid: ALICE_UID,
      tokenHash: fakeSha256,
      tokenPrefix: "rvnbot_live_abcd",
      tokenVersion: 1,
    })
  );

  // 3. Bob CANNOT read or modify Alice's private bot, handle, or /secrets
  await assertFails(bobDb.collection("bots").doc("bot_alice_1").get());
  await assertFails(bobDb.collection("bots").doc("bot_alice_1").update({ name: "Hijacked", ownerUid: BOB_UID }));
  await assertFails(bobDb.collection("bots").doc("bot_alice_1").update({ isActive: false }));
  await assertFails(bobDb.collection("bots").doc("bot_alice_1").delete());
  await assertFails(bobDb.collection("bot_handles").doc("sentinel").delete());
  await assertFails(bobDb.collection("bots").doc("bot_alice_1").collection("secrets").doc("token_meta").get());
  await assertFails(
    bobDb.collection("bots").doc("bot_alice_1").collection("secrets").doc("token_meta").set({
      botId: "bot_alice_1",
      ownerUid: BOB_UID,
      tokenHash: "b".repeat(64),
    })
  );

  // 4. Non-admin member (Bob) CANNOT install a bot onto bot_srv
  await assertFails(
    bobDb.collection("servers").doc("bot_srv").collection("bots").doc("bot_alice_1").set({
      botId: "bot_alice_1",
      serverId: "bot_srv",
      botName: "Sentinel",
      botHandle: "sentinel",
      ownerUid: ALICE_UID,
      installedByUid: BOB_UID,
      grantedPermissions: ["SEND_MESSAGES"],
      isEnabledInServer: true,
    })
  );

  // 5. Uninstalled bot message spoofing is blocked by Firestore rules
  await assertFails(
    bobDb.collection("servers").doc("bot_srv").collection("channels").doc("bot_ch").collection("messages").doc("msg_spoof").set({
      senderId: "bot_bot_alice_1",
      senderName: "Sentinel",
      text: "Fake bot output",
      isBotMessage: true,
      botId: "bot_alice_1",
      triggeredByUid: BOB_UID,
      isPublic: false,
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
    })
  );

  // 6. Server Admin (Alice) CAN install bot onto bot_srv & assign role/permissions
  await assertSucceeds(
    aliceDb.collection("servers").doc("bot_srv").collection("bots").doc("bot_alice_1").set({
      botId: "bot_alice_1",
      serverId: "bot_srv",
      botName: "Sentinel",
      botHandle: "sentinel",
      ownerUid: ALICE_UID,
      installedByUid: ALICE_UID,
      grantedPermissions: ["SEND_MESSAGES", "USE_SLASH_COMMANDS", "MODERATION_SHIELD"],
      assignedRoleTitle: "Sentinelle Auto",
      isEnabledInServer: true,
    })
  );

  // 7. Verified bot message & activity log succeed once installed and enabled
  await assertSucceeds(
    bobDb.collection("servers").doc("bot_srv").collection("channels").doc("bot_ch").collection("messages").doc("msg_bot_ok").set({
      senderId: "bot_bot_alice_1",
      senderName: "Sentinel",
      text: "Pong ! 12ms",
      isBotMessage: true,
      botId: "bot_alice_1",
      triggeredByUid: BOB_UID,
      isPublic: false,
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
    })
  );
  await assertSucceeds(
    bobDb.collection("bots").doc("bot_alice_1").collection("logs").doc("log_1").set({
      id: "log_1",
      botId: "bot_alice_1",
      ownerUid: ALICE_UID,
      serverId: "bot_srv",
      actorUid: BOB_UID,
      eventType: "COMMAND_/ping",
      summary: "Executed /ping",
      status: "SUCCESS",
    })
  );

  // 8. Bob CANNOT remove the installed bot or read Alice's bot logs; Charlie (outsider from non-public bot) also blocked
  await assertFails(bobDb.collection("servers").doc("bot_srv").collection("bots").doc("bot_alice_1").delete());
  await assertFails(bobDb.collection("bots").doc("bot_alice_1").collection("logs").doc("log_1").get());
  await assertFails(charlieDb.collection("bots").doc("bot_alice_1").collection("logs").doc("log_1").get());

  // 9. Admin (Alice) CAN disable and remove bot from server, and owner Alice CAN delete bot
  await assertSucceeds(
    aliceDb.collection("servers").doc("bot_srv").collection("bots").doc("bot_alice_1").update({
      isEnabledInServer: false,
    })
  );
  await assertSucceeds(
    aliceDb.collection("servers").doc("bot_srv").collection("bots").doc("bot_alice_1").delete()
  );
  await assertSucceeds(aliceDb.collection("bots").doc("bot_alice_1").delete());
});

test("RAVEN AI conversations, immutable messages, server context access, and quota limits enforce owner isolation and anti-tamper", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("ai_srv_priv").set({
      name: "Secret Guild",
      ownerId: ALICE_UID,
      adminIds: [ALICE_UID],
      moderatorIds: [],
      isPublic: false,
      memberIds: [ALICE_UID],
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // 1. Alice can create her personal AI conversation & append immutable messages
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_1").set({
      id: "conv_1",
      userId: ALICE_UID,
      title: "Assistant Personnel",
      mode: "PERSONAL",
      modelName: "gemini-3.8-flash",
      lastMessagePreview: "Bonjour",
      messageCount: 1,
      createdAtMs: 1000,
      updatedAtMs: 1000,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_1")
      .collection("messages").doc("m_1").set({
        id: "m_1",
        conversationId: "conv_1",
        userId: ALICE_UID,
        role: "USER",
        content: "Bonjour RAVEN AI",
        taskCategory: "CHAT",
        timestampLabel: "10:00",
        createdAtMs: 1000,
      })
  );

  // Messages are immutable once written
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_1")
      .collection("messages").doc("m_1").update({ content: "Altéré" })
  );

  // 2. Alice can create a SERVER AI conversation on her own community, whereas Bob (outsider) CANNOT
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_srv").set({
      id: "conv_srv",
      userId: ALICE_UID,
      title: "Serveur Secret Guild",
      mode: "SERVER",
      serverId: "ai_srv_priv",
      serverName: "Secret Guild",
      modelName: "gemini-3.8-flash",
      messageCount: 0,
      createdAtMs: 1100,
      updatedAtMs: 1100,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).collection("ai_conversations").doc("conv_bob_hack").set({
      id: "conv_bob_hack",
      userId: BOB_UID,
      title: "Intrusion",
      mode: "SERVER",
      serverId: "ai_srv_priv",
      modelName: "gemini-3.8-flash",
      messageCount: 0,
      createdAtMs: 1200,
      updatedAtMs: 1200,
    })
  );

  // 3. Bob CANNOT read, update, or delete Alice's AI conversations, messages, or quota
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ai_conversations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_1").delete());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_1")
      .collection("messages").doc("m_1").get()
  );

  // 4. Quota creation succeeds within limits, and rejects tampering (inflating dailyLimit or decrementing same-day usage)
  const quotaRef = aliceDb.collection("users").doc(ALICE_UID).collection("ai_usage").doc("daily_quota");
  await assertSucceeds(
    quotaRef.set({
      userId: ALICE_UID,
      dayKey: "2026-11-17",
      requestsToday: 5,
      dailyLimit: 40,
      abuseStrikes: 0,
      blockedUntilMs: 0,
      estimatedCharsToday: 240,
    })
  );
  await assertFails(
    quotaRef.update({ dailyLimit: 500 })
  );
  await assertFails(
    quotaRef.update({ requestsToday: 0 })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("ai_usage").doc("daily_quota").get()
  );

  // 5. Alice can delete her own messages and conversation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_1")
      .collection("messages").doc("m_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_conversations").doc("conv_1").delete()
  );
});

test("Advanced Voice Channels: channel lock, capacity/role fields, kickedVoiceUids block, and staff voice peer kick", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("v_srv").set({
      name: "Voice Hub",
      ownerId: ALICE_UID,
      adminIds: [ALICE_UID],
      moderatorIds: [CHARLIE_UID],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
    });
    await db.collection("servers").doc("v_srv").collection("channels").doc("v_ch").set({
      serverId: "v_srv",
      name: "Vocal Géné",
      type: "VOICE",
      isPublic: false,
      isLocked: false,
      maxParticipants: 5,
      allowedRoleIds: ["vip_role"],
      kickedVoiceUids: [],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const chRefAlice = aliceDb.collection("servers").doc("v_srv").collection("channels").doc("v_ch");
  const chRefBob = bobDb.collection("servers").doc("v_srv").collection("channels").doc("v_ch");
  const chRefCharlie = charlieDb.collection("servers").doc("v_srv").collection("channels").doc("v_ch");

  // 1. Regular member (Bob) CANNOT lock channel or alter maxParticipants
  await assertFails(chRefBob.update({ isLocked: true }));
  await assertFails(chRefBob.update({ maxParticipants: 50 }));

  // 2. When unlocked, Bob CAN join voice_peers with speaking & connectionState metadata
  await assertSucceeds(
    chRefBob.collection("voice_peers").doc(BOB_UID).set({
      uid: BOB_UID,
      displayName: "Bob",
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
      isMuted: false,
      isDeafened: false,
      isCameraOn: false,
      isScreenSharing: false,
      isSpeaking: true,
      connectionState: "CONNECTED",
      reconnectAttempt: 0,
    })
  );

  // 3. Moderator (Charlie) CAN lock the channel and kick Bob from voice_peers
  await assertSucceeds(chRefCharlie.update({ isLocked: true }));
  await assertSucceeds(chRefCharlie.collection("voice_peers").doc(BOB_UID).delete());

  // 4. While channel is locked, regular member Bob CANNOT rejoin voice_peers, whereas Admin Alice CAN
  await assertFails(
    chRefBob.collection("voice_peers").doc(BOB_UID).set({
      uid: BOB_UID,
      displayName: "Bob",
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
      isSpeaking: false,
    })
  );
  await assertSucceeds(
    chRefAlice.collection("voice_peers").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
      isSpeaking: true,
    })
  );

  // 5. Even when unlocked, if Bob is in kickedVoiceUids, Bob CANNOT join voice_peers
  await assertSucceeds(chRefCharlie.update({ isLocked: false, kickedVoiceUids: [BOB_UID] }));
  await assertFails(
    chRefBob.collection("voice_peers").doc(BOB_UID).set({
      uid: BOB_UID,
      displayName: "Bob",
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
    })
  );
});

test("RAVEN Stories: publish validation, privacy isolation, self-view tracking, owner deletion, reporting, and staff moderation", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("story_srv").set({
      name: "Story Mod HQ",
      ownerId: ALICE_UID,
      adminIds: [ALICE_UID],
      moderatorIds: [CHARLIE_UID],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const EVE_UID = "eve_outsider";
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = Date.now();

  // 1. Reject insecure / spoofed story creation (javascript: URL, non-zero initial views, authorId spoof)
  await assertFails(
    aliceDb.collection("stories").doc("st_bad_url").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      mediaType: "IMAGE",
      mediaUrl: "javascript:alert(1)",
      visibility: "PUBLIC",
      durationHours: 24,
      createdAtMs: now,
      expiresAtMs: now + 3600000,
      viewCount: 0,
      viewerIds: [],
      reportCount: 0,
      moderatedHidden: false,
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("st_spoof").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      mediaType: "TEXT",
      textContent: "Spoof",
      visibility: "PUBLIC",
      durationHours: 24,
      createdAtMs: now,
      expiresAtMs: now + 3600000,
      viewCount: 0,
      viewerIds: [],
      reportCount: 0,
      moderatedHidden: false,
    })
  );
  await assertFails(
    aliceDb.collection("stories").doc("st_fake_views").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      mediaType: "TEXT",
      textContent: "Boosted views",
      visibility: "PUBLIC",
      durationHours: 24,
      createdAtMs: now,
      expiresAtMs: now + 3600000,
      viewCount: 99,
      viewerIds: [],
      reportCount: 0,
      moderatedHidden: false,
    })
  );

  // 2. Alice publishes valid PUBLIC text story and CUSTOM_USERS story restricted to Bob only
  await assertSucceeds(
    aliceDb.collection("stories").doc("st_pub").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      mediaType: "TEXT",
      textContent: "Bonjour RAVEN !",
      caption: "Public 24h",
      visibility: "PUBLIC",
      allowedViewerIds: [],
      moderatorIds: [CHARLIE_UID],
      moderationServerId: "story_srv",
      durationHours: 24,
      createdAtMs: now,
      expiresAtMs: now + 24 * 3600000,
      viewCount: 0,
      viewerIds: [],
      reportCount: 0,
      moderatedHidden: false,
    })
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("st_priv").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      mediaType: "IMAGE",
      mediaUrl: "https://cdn.example.com/story_alice.jpg",
      visibility: "CUSTOM_USERS",
      allowedViewerIds: [ALICE_UID, BOB_UID],
      moderatorIds: [],
      durationHours: 12,
      createdAtMs: now,
      expiresAtMs: now + 12 * 3600000,
      viewCount: 0,
      viewerIds: [],
      reportCount: 0,
      moderatedHidden: false,
    })
  );

  // 3. Privacy check: Bob can read st_priv; Eve (outsider) CANNOT read st_priv or run unfiltered queries
  await assertSucceeds(bobDb.collection("stories").doc("st_priv").get());
  await assertSucceeds(bobDb.collection("stories").where("visibility", "==", "PUBLIC").get());
  await assertSucceeds(bobDb.collection("stories").where("allowedViewerIds", "array-contains", BOB_UID).get());
  await assertFails(eveDb.collection("stories").doc("st_priv").get());
  await assertFails(eveDb.collection("stories").get());

  // 4. View tracking: Bob records his view on st_pub (+1 count & subcollection doc), CANNOT tamper content or spoof others
  await assertSucceeds(
    bobDb.collection("stories").doc("st_pub").collection("views").doc(BOB_UID).set({
      viewerId: BOB_UID,
      storyId: "st_pub",
      authorId: ALICE_UID,
      viewerName: "Bob",
      viewedAtMs: now + 1000,
    })
  );
  await assertSucceeds(
    bobDb.collection("stories").doc("st_pub").update({
      viewerIds: [BOB_UID],
      viewCount: 1,
    })
  );
  // Spoofing another viewer or modifying textContent must fail
  await assertFails(
    bobDb.collection("stories").doc("st_pub").collection("views").doc(EVE_UID).set({
      viewerId: EVE_UID,
      storyId: "st_pub",
      viewerName: "Eve",
      viewedAtMs: now + 1000,
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("st_pub").update({
      textContent: "Contenu piraté",
    })
  );
  // Bob CANNOT list viewers list of Alice's story; Alice CAN list viewers
  await assertFails(bobDb.collection("stories").doc("st_pub").collection("views").get());
  await assertSucceeds(aliceDb.collection("stories").doc("st_pub").collection("views").get());

  // 5. Reporting & Staff Moderation: Bob reports st_pub, non-mod Bob/Eve cannot moderate or resolve report, Moderator Charlie CAN
  await assertSucceeds(
    bobDb.collection("story_reports").doc("rep_1").set({
      storyId: "st_pub",
      storyAuthorId: ALICE_UID,
      storyAuthorName: "Alice",
      storyPreview: "Bonjour RAVEN !",
      storyMediaType: "TEXT",
      reporterUid: BOB_UID,
      reporterName: "Bob",
      reason: "SPAM",
      details: "Test report",
      status: "PENDING",
      moderatorIds: [CHARLIE_UID],
      moderationServerId: "story_srv",
      createdAtMs: now + 2000,
    })
  );
  await assertSucceeds(
    bobDb.collection("stories").doc("st_pub").update({
      reportCount: 1,
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("st_pub").update({
      moderatedHidden: true,
      moderationReason: "Bob fake mod",
      moderatedByUid: BOB_UID,
    })
  );
  await assertFails(
    bobDb.collection("story_reports").doc("rep_1").update({
      status: "DISMISSED",
      reviewedByUid: BOB_UID,
    })
  );

  // Moderator Charlie CAN hide story & resolve report
  await assertSucceeds(
    charlieDb.collection("stories").doc("st_pub").update({
      moderatedHidden: true,
      moderationReason: "Spam vérifié",
      moderatedByUid: CHARLIE_UID,
    })
  );
  await assertSucceeds(
    charlieDb.collection("story_reports").doc("rep_1").update({
      status: "REMOVED",
      reviewedByUid: CHARLIE_UID,
    })
  );
  // Once hidden by moderator, Eve can no longer read st_pub
  await assertFails(eveDb.collection("stories").doc("st_pub").get());

  // 6. Non-owner Bob CANNOT delete st_priv; Owner Alice CAN delete her story and views cascade
  await assertFails(bobDb.collection("stories").doc("st_priv").delete());
  await assertSucceeds(aliceDb.collection("stories").doc("st_priv").delete());
});

test("Advanced Server Customization: admin-only sensitive config, secret blockedWords isolation, channel order/icons, and invite privacy lock", async () => {
  const DANA_UID = "dana_admin";
  const EVE_UID = "eve_outsider";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("cust_srv").set({
      name: "Raven Custom HQ",
      uniqueHandle: "raven-custom-hq",
      description: "Serveur personnalisable",
      ownerId: ALICE_UID,
      adminIds: [ALICE_UID, DANA_UID],
      moderatorIds: [CHARLIE_UID],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID, DANA_UID],
      isPublic: false,
      verificationLevel: "LOW",
      privacyConfig: {
        discoverableInCatalog: false,
        allowInviteLinks: true,
        requireInviteApproval: false,
        allowMemberDms: true,
        hideMemberListForNonStaff: false,
      },
    });
    await db.collection("servers").doc("cust_srv").collection("channels").doc("ch_cust").set({
      serverId: "cust_srv",
      name: "general",
      type: "TEXT",
      isPublic: false,
      position: 0,
      iconKey: "tag",
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID, DANA_UID],
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const danaDb = testEnv.authenticatedContext(DANA_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  const srvRefDana = danaDb.collection("servers").doc("cust_srv");
  const srvRefCharlie = charlieDb.collection("servers").doc("cust_srv");
  const srvRefBob = bobDb.collection("servers").doc("cust_srv");

  // 1. Blocked words MUST NOT be placed inside readable servers/{serverId}.moderationConfig
  await assertFails(
    danaDb.collection("servers").doc("cust_srv").update({
      moderationConfig: {
        autoModEnabled: true,
        blockedWords: ["secret_bad_word"],
      },
    })
  );

  // 2. Admin (Dana) CAN customize theme, welcome, notifications, verification, privacy, channel position/iconKey, and admin_settings docs
  await assertSucceeds(
    srvRefDana.update({
      themeConfig: { accentColorHex: 0xFFEB459E, secondaryColorHex: 0xFF5865F2, surfaceStyle: "ROYAL_PURPLE", iconPresetKey: "rocket" },
      welcomeConfig: { welcomeEnabled: true, welcomeMessageTemplate: "Salut {user} !", goodbyeEnabled: true, goodbyeMessageTemplate: "Bye {user}" },
      notificationConfig: { defaultLevel: "ALL_MESSAGES", suppressEveryoneMentions: true, suppressRoleMentions: false, highlightAnnouncements: true },
      verificationLevel: "HIGH",
      serverRulesList: ["Respect mutuel", "Pas de spam"],
      privacyConfig: { discoverableInCatalog: false, allowInviteLinks: true, requireInviteApproval: true, allowMemberDms: false, hideMemberListForNonStaff: true },
    })
  );
  await assertSucceeds(
    srvRefDana.collection("channels").doc("ch_cust").update({
      position: 3,
      iconKey: "shield",
    })
  );
  await assertSucceeds(
    srvRefDana.collection("admin_settings").doc("security").set({
      serverId: "cust_srv",
      settingType: "security",
      updatedByUid: DANA_UID,
      verificationLevel: "HIGH",
      autoModEnabled: true,
      blockSpamAndLinks: true,
      blockBadWords: true,
      blockedWords: ["phishing", "scamlink"],
      defaultSlowmodeSeconds: 10,
      maxMentionsPerMessage: 3,
      antiRaidModeEnabled: false,
      requireInviteApproval: true,
    })
  );
  await assertSucceeds(
    srvRefDana.collection("admin_settings").doc("public_policy").set({
      serverId: "cust_srv",
      settingType: "public_policy",
      updatedByUid: DANA_UID,
      verificationLevel: "HIGH",
      requireRulesAcceptance: true,
      serverRulesList: ["Respect mutuel", "Pas de spam"],
      discoverableInCatalog: false,
      allowInviteLinks: true,
      allowMemberDms: false,
      hideMemberListForNonStaff: true,
    })
  );

  // 3. Moderator (Charlie) CANNOT alter sensitive customization settings, admin_settings, or reorder/re-icon channels
  await assertFails(srvRefCharlie.update({ themeConfig: { accentColorHex: 0xFF000000 } }));
  await assertFails(srvRefCharlie.update({ verificationLevel: "NONE" }));
  await assertFails(srvRefCharlie.update({ welcomeConfig: { welcomeEnabled: false } }));
  await assertFails(srvRefCharlie.update({ privacyConfig: { allowInviteLinks: false } }));
  await assertFails(srvRefCharlie.collection("channels").doc("ch_cust").update({ position: 9, iconKey: "star" }));
  await assertFails(
    srvRefCharlie.collection("admin_settings").doc("security").update({
      autoModEnabled: false,
      updatedByUid: CHARLIE_UID,
    })
  );
  await assertFails(
    srvRefCharlie.collection("admin_settings").doc("public_policy").update({
      hideMemberListForNonStaff: false,
      updatedByUid: CHARLIE_UID,
    })
  );
  // Moderator CAN still read admin_settings/security and moderate permitted channel flags
  await assertSucceeds(srvRefCharlie.collection("admin_settings").doc("security").get());
  await assertSucceeds(srvRefCharlie.collection("channels").doc("ch_cust").update({ slowmodeSeconds: 15, isLocked: true }));

  // 4. Regular member (Bob) and Outsider (Eve) CANNOT read admin_settings/security or write any customization
  await assertFails(srvRefBob.collection("admin_settings").doc("security").get());
  await assertFails(eveDb.collection("servers").doc("cust_srv").collection("admin_settings").doc("security").get());
  await assertSucceeds(srvRefBob.collection("admin_settings").doc("public_policy").get());
  await assertFails(srvRefBob.update({ verificationLevel: "NONE" }));
  await assertFails(srvRefBob.update({ welcomeConfig: { welcomeEnabled: false } }));
  await assertFails(srvRefBob.collection("channels").doc("ch_cust").update({ position: 5 }));

  // 5. Invite privacy lock: when privacyConfig.allowInviteLinks == false, creating invites and joining by invite FAIL server-side
  await assertSucceeds(
    danaDb.collection("community_invites").doc("ALLOW-1").set({
      code: "ALLOW-1",
      serverId: "cust_srv",
      createdByUid: DANA_UID,
      isRevoked: false,
      maxUses: 10,
      usesCount: 0,
    })
  );
  await assertSucceeds(
    srvRefDana.update({
      privacyConfig: { discoverableInCatalog: false, allowInviteLinks: false, requireInviteApproval: true, allowMemberDms: false, hideMemberListForNonStaff: true },
    })
  );
  await assertFails(
    danaDb.collection("community_invites").doc("BLOCK-1").set({
      code: "BLOCK-1",
      serverId: "cust_srv",
      createdByUid: DANA_UID,
      isRevoked: false,
      maxUses: 5,
      usesCount: 0,
    })
  );
  await assertFails(
    eveDb.collection("servers").doc("cust_srv").update({
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID, DANA_UID, EVE_UID],
      lastInviteCodeUsed: "ALLOW-1",
    })
  );

  // Admin cannot delete admin_settings (Owner-only)
  await assertFails(srvRefDana.collection("admin_settings").doc("security").delete());
  await assertSucceeds(aliceDb.collection("servers").doc("cust_srv").collection("admin_settings").doc("security").delete());
});

test("RAVEN Advanced Polls: draft isolation, publish immutability, single-vote/maxChoices, anti-fraud tally verification, anonymous voter privacy, role restrictions, expiration, and manual close", async () => {
  const EVE_UID = "eve_outsider";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("poll_srv").set({
      name: "Poll Guild",
      ownerId: ALICE_UID,
      adminIds: [ALICE_UID],
      moderatorIds: [CHARLIE_UID],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
    });
    await db.collection("servers").doc("poll_srv").collection("members").doc(BOB_UID).set({
      uid: BOB_UID,
      serverId: "poll_srv",
      name: "Bob",
      role: "MEMBER",
    });
    await db.collection("servers").doc("poll_srv").collection("members").doc(CHARLIE_UID).set({
      uid: CHARLIE_UID,
      serverId: "poll_srv",
      name: "Charlie",
      role: "MODERATOR",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = Date.now();

  // 1. Draft isolation: Alice creates a DRAFT; Bob CANNOT read or modify Alice's draft
  const draftDoc = {
    id: "poll_draft_1",
    serverId: "poll_srv",
    authorId: ALICE_UID,
    authorName: "Alice",
    question: "Choix initial brouillon ?",
    description: "Description brouillon",
    options: [
      { id: "opt_0", text: "Option A" },
      { id: "opt_1", text: "Option B" },
      { id: "opt_2", text: "Option C" },
    ],
    optionCounts: { opt_0: 0, opt_1: 0, opt_2: 0 },
    allowsMultiple: true,
    maxChoices: 2,
    allowVoteChange: true,
    isAnonymous: false,
    visibility: "PUBLIC",
    isPublic: false,
    allowedRoleIds: [],
    memberIds: [],
    status: "DRAFT",
    durationMinutes: 1440,
    totalVotes: 0,
    totalVoters: 0,
  };
  await assertSucceeds(aliceDb.collection("polls").doc("poll_draft_1").set(draftDoc));
  await assertFails(bobDb.collection("polls").doc("poll_draft_1").get());
  await assertFails(bobDb.collection("polls").doc("poll_draft_1").update({ question: "Piraté" }));

  // Alice CAN edit her draft BEFORE publication
  await assertSucceeds(
    aliceDb.collection("polls").doc("poll_draft_1").update({
      question: "Question révisée avant publication ?",
      totalVotes: 0,
      totalVoters: 0,
    })
  );

  // Alice publishes the draft; once PUBLISHED, even author CANNOT fraudulently edit question/options/tallies
  await assertSucceeds(
    aliceDb.collection("polls").doc("poll_draft_1").update({
      status: "PUBLISHED",
      isPublic: true,
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      publishedAtMs: now,
      expiresAtMs: now + 3600_000,
    })
  );
  await assertFails(
    aliceDb.collection("polls").doc("poll_draft_1").update({
      question: "Modification après publication interdite",
    })
  );
  await assertFails(
    bobDb.collection("polls").doc("poll_draft_1").update({
      totalVotes: 99,
      optionCounts: { opt_0: 99, opt_1: 0, opt_2: 0 },
    })
  );

  // 2. Anti-fraud voting checks via runTransaction / WriteBatch
  // 2a. Spoofing voteId != auth.uid MUST FAIL
  await assertFails(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_draft_1");
      const vRef = pRef.collection("votes").doc("fake_other_uid");
      tx.set(vRef, {
        pollId: "poll_draft_1",
        voterId: BOB_UID,
        voterName: "Bob",
        selectedOptionIds: ["opt_0"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 0, opt_2: 0 },
        totalVotes: 1,
        totalVoters: 1,
      });
    })
  );

  // 2b. Exceeding maxChoices (maxChoices=2, sending 3) MUST FAIL
  await assertFails(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_draft_1");
      const vRef = pRef.collection("votes").doc(BOB_UID);
      tx.set(vRef, {
        pollId: "poll_draft_1",
        voterId: BOB_UID,
        voterName: "Bob",
        selectedOptionIds: ["opt_0", "opt_1", "opt_2"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 1, opt_2: 1 },
        totalVotes: 3,
        totalVoters: 1,
      });
    })
  );

  // 2c. Fraudulent tally bump (+50 instead of +1) MUST FAIL
  await assertFails(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_draft_1");
      const vRef = pRef.collection("votes").doc(BOB_UID);
      tx.set(vRef, {
        pollId: "poll_draft_1",
        voterId: BOB_UID,
        voterName: "Bob",
        selectedOptionIds: ["opt_0"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 50, opt_1: 0, opt_2: 0 },
        totalVotes: 50,
        totalVoters: 1,
      });
    })
  );

  // 2d. Legitimate atomic vote by Bob succeeds (2 choices within maxChoices=2)
  await assertSucceeds(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_draft_1");
      const vRef = pRef.collection("votes").doc(BOB_UID);
      tx.set(vRef, {
        pollId: "poll_draft_1",
        voterId: BOB_UID,
        voterName: "Bob",
        selectedOptionIds: ["opt_0", "opt_1"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 1, opt_2: 0 },
        totalVotes: 2,
        totalVoters: 1,
      });
    })
  );

  // Charlie CANNOT delete or overwrite Bob's vote doc
  await assertFails(
    charlieDb.collection("polls").doc("poll_draft_1").collection("votes").doc(BOB_UID).delete()
  );

  // 3. Anonymous poll privacy: cannot expose non-anonymous voterName, and outsiders/other voters CANNOT list/get others' vote docs
  await assertSucceeds(
    aliceDb.collection("polls").doc("poll_anon_1").set({
      id: "poll_anon_1",
      serverId: "poll_srv",
      authorId: ALICE_UID,
      authorName: "Alice",
      question: "Vote secret unique ?",
      options: [
        { id: "opt_0", text: "Oui" },
        { id: "opt_1", text: "Non" },
      ],
      optionCounts: { opt_0: 0, opt_1: 0 },
      allowsMultiple: false,
      maxChoices: 1,
      allowVoteChange: false,
      isAnonymous: true,
      visibility: "SERVER",
      isPublic: false,
      allowedRoleIds: [],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      status: "PUBLISHED",
      durationMinutes: 60,
      publishedAtMs: now,
      expiresAtMs: now + 3600_000,
      totalVotes: 0,
      totalVoters: 0,
    })
  );

  // Trying to write real name 'Bob' on anonymous poll MUST FAIL
  await assertFails(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_anon_1");
      tx.set(pRef.collection("votes").doc(BOB_UID), {
        pollId: "poll_anon_1",
        voterId: BOB_UID,
        voterName: "Bob",
        selectedOptionIds: ["opt_0"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 0 },
        totalVotes: 1,
        totalVoters: 1,
      });
    })
  );

  // Trying to submit 2 choices on single-choice poll MUST FAIL
  await assertFails(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_anon_1");
      tx.set(pRef.collection("votes").doc(BOB_UID), {
        pollId: "poll_anon_1",
        voterId: BOB_UID,
        voterName: "Anonyme",
        selectedOptionIds: ["opt_0", "opt_1"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 1 },
        totalVotes: 2,
        totalVoters: 1,
      });
    })
  );

  // Legitimate anonymous vote with 'Anonyme' succeeds
  await assertSucceeds(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_anon_1");
      tx.set(pRef.collection("votes").doc(BOB_UID), {
        pollId: "poll_anon_1",
        voterId: BOB_UID,
        voterName: "Anonyme",
        selectedOptionIds: ["opt_0"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 0 },
        totalVotes: 1,
        totalVoters: 1,
      });
    })
  );

  // allowVoteChange=false -> Bob CANNOT change or delete his vote once cast!
  await assertFails(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_anon_1");
      tx.set(pRef.collection("votes").doc(BOB_UID), {
        pollId: "poll_anon_1",
        voterId: BOB_UID,
        voterName: "Anonyme",
        selectedOptionIds: ["opt_1"],
        votedAtMs: now + 1000,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 0, opt_1: 1 },
        totalVotes: 1,
        totalVoters: 1,
      });
    })
  );

  // Even poll author Alice CANNOT read or list /votes on an anonymous poll!
  await assertFails(aliceDb.collection("polls").doc("poll_anon_1").collection("votes").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("polls").doc("poll_anon_1").collection("votes").get());
  // Bob CAN read his own anonymous vote doc
  await assertSucceeds(bobDb.collection("polls").doc("poll_anon_1").collection("votes").doc(BOB_UID).get());

  // 4. Role-restricted poll: only MODERATOR/ADMIN allowed -> Member Bob REJECTED, Moderator Charlie ALLOWED, Outsider Eve REJECTED
  await assertSucceeds(
    aliceDb.collection("polls").doc("poll_mod_only").set({
      id: "poll_mod_only",
      serverId: "poll_srv",
      authorId: ALICE_UID,
      authorName: "Alice",
      question: "Décision réservée au staff ?",
      options: [
        { id: "opt_0", text: "Valider" },
        { id: "opt_1", text: "Refuser" },
      ],
      optionCounts: { opt_0: 0, opt_1: 0 },
      allowsMultiple: false,
      maxChoices: 1,
      allowVoteChange: true,
      isAnonymous: false,
      visibility: "ROLE_RESTRICTED",
      isPublic: false,
      allowedRoleIds: ["MODERATOR", "ADMIN"],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      status: "PUBLISHED",
      durationMinutes: 60,
      publishedAtMs: now,
      expiresAtMs: now + 3600_000,
      totalVotes: 0,
      totalVoters: 0,
    })
  );
  await assertFails(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_mod_only");
      tx.set(pRef.collection("votes").doc(BOB_UID), {
        pollId: "poll_mod_only",
        voterId: BOB_UID,
        voterName: "Bob",
        selectedOptionIds: ["opt_0"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 0 },
        totalVotes: 1,
        totalVoters: 1,
      });
    })
  );
  await assertFails(
    eveDb.runTransaction(async (tx) => {
      const pRef = eveDb.collection("polls").doc("poll_mod_only");
      tx.set(pRef.collection("votes").doc(EVE_UID), {
        pollId: "poll_mod_only",
        voterId: EVE_UID,
        voterName: "Eve",
        selectedOptionIds: ["opt_0"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 0 },
        totalVotes: 1,
        totalVoters: 1,
      });
    })
  );
  await assertSucceeds(
    charlieDb.runTransaction(async (tx) => {
      const pRef = charlieDb.collection("polls").doc("poll_mod_only");
      tx.set(pRef.collection("votes").doc(CHARLIE_UID), {
        pollId: "poll_mod_only",
        voterId: CHARLIE_UID,
        voterName: "Charlie",
        selectedOptionIds: ["opt_0"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 0 },
        totalVotes: 1,
        totalVoters: 1,
      });
    })
  );

  // 5. Expired poll blocks new votes, and manual close blocks further votes without altering tallies
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("polls").doc("poll_expired").set({
      id: "poll_expired",
      authorId: ALICE_UID,
      authorName: "Alice",
      question: "Sondage déjà expiré ?",
      options: [
        { id: "opt_0", text: "Oui" },
        { id: "opt_1", text: "Non" },
      ],
      optionCounts: { opt_0: 0, opt_1: 0 },
      allowsMultiple: false,
      maxChoices: 1,
      allowVoteChange: true,
      isAnonymous: false,
      visibility: "PUBLIC",
      isPublic: true,
      allowedRoleIds: [],
      memberIds: [ALICE_UID],
      status: "PUBLISHED",
      durationMinutes: 15,
      publishedAtMs: now - 3600_000,
      expiresAtMs: now - 60_000,
      totalVotes: 0,
      totalVoters: 0,
    });
  });
  await assertFails(
    bobDb.runTransaction(async (tx) => {
      const pRef = bobDb.collection("polls").doc("poll_expired");
      tx.set(pRef.collection("votes").doc(BOB_UID), {
        pollId: "poll_expired",
        voterId: BOB_UID,
        voterName: "Bob",
        selectedOptionIds: ["opt_0"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 0 },
        totalVotes: 1,
        totalVoters: 1,
      });
    })
  );

  // Manual close: Bob cannot close; Author/Staff Alice CAN close (without mutating tallies) -> subsequent votes FAIL
  await assertFails(bobDb.collection("polls").doc("poll_draft_1").update({ status: "CLOSED", closedAtMs: now }));
  await assertSucceeds(aliceDb.collection("polls").doc("poll_draft_1").update({ status: "CLOSED", closedAtMs: now }));
  await assertFails(
    charlieDb.runTransaction(async (tx) => {
      const pRef = charlieDb.collection("polls").doc("poll_draft_1");
      tx.set(pRef.collection("votes").doc(CHARLIE_UID), {
        pollId: "poll_draft_1",
        voterId: CHARLIE_UID,
        voterName: "Charlie",
        selectedOptionIds: ["opt_2"],
        votedAtMs: now,
      });
      tx.update(pRef, {
        optionCounts: { opt_0: 1, opt_1: 1, opt_2: 1 },
        totalVotes: 3,
        totalVoters: 2,
      });
    })
  );
});

test("RAVEN Events: server permissions, private invite isolation, atomic RSVP/waitlist FIFO promotion, reminders, and history", async () => {
  const EVE_UID = "eve_outsider_evt";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("servers").doc("evt_srv").set({
      name: "Events HQ",
      ownerId: ALICE_UID,
      adminIds: [ALICE_UID],
      moderatorIds: [CHARLIE_UID],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      isPublic: false,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = Date.now();
  const startAt = now + 3600_000;

  // 1. Unauthenticated read/write is rejected
  await assertFails(unauthDb.collection("events").doc("ev_pub_1").get());

  // 2. Non-staff member (Bob) CANNOT create a server event; Admin Alice CAN
  const serverEventPayload = {
    id: "ev_srv_1",
    serverId: "evt_srv",
    serverName: "Events HQ",
    creatorId: ALICE_UID,
    creatorName: "Alice",
    title: "Live Vocal Soirée",
    description: "Capacité 1 place pour tester la liste d'attente FIFO.",
    bannerColorHex: 0x5865F2,
    eventType: "VOICE",
    voiceChannelId: "vc_main",
    visibility: "SERVER",
    isPublic: false,
    status: "SCHEDULED",
    startAtMs: startAt,
    durationMinutes: 90,
    timezoneId: "Europe/Paris",
    location: "Vocal Général",
    maxParticipants: 1,
    goingIds: [ALICE_UID],
    interestedIds: [],
    waitlistIds: [],
    invitedUserIds: [],
    memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
    createdAtMs: now,
    updatedAtMs: now,
  };

  await assertFails(
    bobDb.collection("events").doc("ev_bob_forged").set({
      ...serverEventPayload,
      id: "ev_bob_forged",
      creatorId: BOB_UID,
      creatorName: "Bob",
      goingIds: [BOB_UID],
    })
  );
  await assertSucceeds(aliceDb.collection("events").doc("ev_srv_1").set(serverEventPayload));

  // Outsider Eve CANNOT read private SERVER event
  await assertFails(eveDb.collection("events").doc("ev_srv_1").get());

  // 3. Regular member Bob joins full event -> MUST enter waitlistIds (cannot force into goingIds when full)
  await assertFails(
    bobDb.collection("events").doc("ev_srv_1").update({
      goingIds: [ALICE_UID, BOB_UID],
      updatedAtMs: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("events").doc("ev_srv_1").update({
      waitlistIds: [BOB_UID],
      updatedAtMs: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("events").doc("ev_srv_1").collection("rsvps").doc(BOB_UID).set({
      uid: BOB_UID,
      eventId: "ev_srv_1",
      displayName: "Bob",
      status: "WAITLISTED",
      updatedAtMs: now,
    })
  );

  // 4. Bob creates his own private reminder; Alice CANNOT read or overwrite it
  await assertSucceeds(
    bobDb.collection("events").doc("ev_srv_1").collection("reminders").doc("rem_bob_15").set({
      id: "rem_bob_15",
      eventId: "ev_srv_1",
      userId: BOB_UID,
      minutesBefore: 15,
      triggerAtMs: startAt - 900_000,
      triggered: false,
      createdAtMs: now,
    })
  );
  await assertFails(aliceDb.collection("events").doc("ev_srv_1").collection("reminders").doc("rem_bob_15").get());

  // 5. Non-staff Charlie joins waitlist (behind Bob? No, Alice leaves promoting Bob, then Charlie waitlists, then Bob leaves promoting Charlie!)
  await assertSucceeds(
    aliceDb.collection("events").doc("ev_srv_1").update({
      goingIds: [BOB_UID],
      waitlistIds: [],
      updatedAtMs: now + 1000,
    })
  );
  // Now Bob (non-staff) is going (1/1) and non-staff Eve joins after being added or Charlie waitlists; actually use regular member DAVE_UID for non-staff RSVP promo!
  const DAVE_UID = "dave_member_evt";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("servers").doc("evt_srv").update({
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID, DAVE_UID],
    });
    await ctx.firestore().collection("events").doc("ev_srv_1").update({
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID, DAVE_UID],
      waitlistIds: [DAVE_UID],
    });
  });
  const daveDb = testEnv.authenticatedContext(DAVE_UID).firestore();
  // Non-staff Bob leaves goingIds -> non-staff FIFO promotion puts Dave into goingIds!
  await assertSucceeds(
    bobDb.collection("events").doc("ev_srv_1").update({
      goingIds: [DAVE_UID],
      waitlistIds: [],
      updatedAtMs: now + 1500,
    })
  );
  // Dave steps down -> allowed
  await assertSucceeds(
    daveDb.collection("events").doc("ev_srv_1").update({
      goingIds: [],
      updatedAtMs: now + 1800,
    })
  );

  // 6. Regular member Bob CANNOT tamper with event title, cancel it, or forge another user's history
  await assertFails(bobDb.collection("events").doc("ev_srv_1").update({ title: "Hijacked" }));
  await assertFails(bobDb.collection("events").doc("ev_srv_1").update({ status: "CANCELLED" }));
  await assertFails(
    bobDb.collection("events").doc("ev_srv_1").collection("history").doc("h_forged").set({
      id: "h_forged",
      eventId: "ev_srv_1",
      actorId: ALICE_UID,
      actorName: "Alice",
      actionType: "CANCELLED",
      summary: "Forged history",
      timestampLabel: "12:00",
      createdAtMs: now,
    })
  );

  // 7. Personal PRIVATE_INVITE event created by Bob inviting Charlie only -> Alice CANNOT view
  await assertSucceeds(
    bobDb.collection("events").doc("ev_priv_bob").set({
      id: "ev_priv_bob",
      serverId: null,
      creatorId: BOB_UID,
      creatorName: "Bob",
      title: "Atelier Secret",
      description: "Privé sur invitation",
      eventType: "EXTERNAL",
      visibility: "PRIVATE_INVITE",
      isPublic: false,
      status: "SCHEDULED",
      startAtMs: startAt + 3600_000,
      durationMinutes: 60,
      timezoneId: "UTC",
      location: "Paris",
      maxParticipants: 5,
      goingIds: [BOB_UID],
      interestedIds: [],
      waitlistIds: [],
      invitedUserIds: [CHARLIE_UID],
      memberIds: [],
      createdAtMs: now,
      updatedAtMs: now,
    })
  );
  await assertSucceeds(charlieDb.collection("events").doc("ev_priv_bob").get());
  await assertFails(aliceDb.collection("events").doc("ev_priv_bob").get());

  // 8. Admin Alice cancels server event -> subsequent RSVP by Charlie MUST FAIL
  await assertSucceeds(
    aliceDb.collection("events").doc("ev_srv_1").update({
      status: "CANCELLED",
      cancellationReason: "Report technique",
      updatedAtMs: now + 2000,
    })
  );
  await assertFails(
    charlieDb.collection("events").doc("ev_srv_1").update({
      interestedIds: [CHARLIE_UID],
      updatedAtMs: now + 3000,
    })
  );
});

// =========================================================================
// TEST 14: RAVEN SANDBOXED EXTENSIONS / PLUGINS & AUDIT LOGS SECURITY RULES
// =========================================================================
test("14. Sandboxed plugins state and logs enforce owner isolation, least privilege, and anti-DCL", async () => {
  const ALICE_UID = "alice_plug_uid";
  const BOB_UID = "bob_plug_uid";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com" }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  const now = Date.now();

  const validPluginDoc = {
    pluginId: "com.raven.ext.pomodoro",
    installedVersion: "1.2.0",
    isEnabled: true,
    lifecycleState: "ACTIVE",
    grantedPermissions: ["EXECUTE_SLASH_COMMAND", "SEND_NOTIFICATIONS"],
    deniedPermissions: ["POST_CHANNEL_MESSAGE"],
    configValues: {
      focus_minutes: "25",
      announce_in_channel: "false",
      focus_theme: "Deep Work",
    },
    consecutiveErrors: 0,
    lastErrorMessage: null,
    updatedAtMs: now,
  };

  // 1. Alice can create/update/read her own valid plugin state
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.pomodoro").set(validPluginDoc)
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.pomodoro").get()
  );

  // 2. Cross-user isolation: Bob CANNOT read, write, or delete Alice's plugin state or logs
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.pomodoro").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.pomodoro").update({
      isEnabled: false,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.pomodoro").delete()
  );

  // 3. Reject undeclared/unknown permission or permission present in both granted & denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.standup").set({
      ...validPluginDoc,
      pluginId: "com.raven.ext.standup",
      grantedPermissions: ["ALL_USER_DATA_ROOT"],
      deniedPermissions: [],
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.standup").set({
      ...validPluginDoc,
      pluginId: "com.raven.ext.standup",
      grantedPermissions: ["EXECUTE_SLASH_COMMAND"],
      deniedPermissions: ["EXECUTE_SLASH_COMMAND"],
    })
  );

  // 4. Reject inconsistent lifecycle (isEnabled=true while FAULTED or INCOMPATIBLE)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.spatial_audio_pro").set({
      ...validPluginDoc,
      pluginId: "com.raven.ext.spatial_audio_pro",
      isEnabled: true,
      lifecycleState: "INCOMPATIBLE",
    })
  );

  // 5. Reject dynamic code loading (DCL) fields / keys
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.standup").set({
      ...validPluginDoc,
      pluginId: "com.raven.ext.standup",
      dexUrl: "https://evil.example.com/payload.dex",
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugins").doc("com.raven.ext.standup").set({
      ...validPluginDoc,
      pluginId: "com.raven.ext.standup",
      configValues: { remoteScript: "eval(1)" },
    })
  );

  // 6. Audit logs: Alice can create valid log, cannot forge DCL markers, cannot update existing log (immutable), Bob cannot read
  const validLog = {
    id: "plog_test_01",
    pluginId: "com.raven.ext.pomodoro",
    pluginName: "Focus & Pomodoro",
    level: "SECURITY",
    action: "PERMISSION_DENIED_POST_MESSAGE",
    message: "Accès bloqué : permission POST_CHANNEL_MESSAGE requise.",
    timestampMs: now,
    timestampLabel: "12:00:00",
  };
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugin_logs").doc("plog_test_01").set(validLog)
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugin_logs").doc("plog_test_01").update({
      level: "INFO",
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("plugin_logs").doc("plog_dcl").set({
      ...validLog,
      id: "plog_dcl",
      message: "load payload.dex",
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("plugin_logs").doc("plog_test_01").get()
  );
});

// =========================================================================
// Test 15: RAVEN Friends System (Owner-Only Friends/Blocks, Canonical Pair Anti-Duplication, Privacy Policies & DMs)
// =========================================================================
test("15. RAVEN Friends System enforces owner-only friend/block lists, anti-duplication, privacy policies & blocked DM denial", async () => {
  const aliceCtx = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com" });
  const bobCtx = testEnv.authenticatedContext(BOB_UID, { email: "bob@test.com" });
  const charlieCtx = testEnv.authenticatedContext(CHARLIE_UID, { email: "charlie@test.com" });

  const aliceDb = aliceCtx.firestore();
  const bobDb = bobCtx.firestore();
  const charlieDb = charlieCtx.firestore();

  const pairAB = ALICE_UID <= BOB_UID ? `${ALICE_UID}__${BOB_UID}` : `${BOB_UID}__${ALICE_UID}`;
  const pairBC = BOB_UID <= CHARLIE_UID ? `${BOB_UID}__${CHARLIE_UID}` : `${CHARLIE_UID}__${BOB_UID}`;
  const now = Date.now();

  // Setup public profiles: Charlie forbids friend requests (NOBODY), Alice requires MUTUAL_SERVERS
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice",
      isPublic: true,
      friendRequestPolicy: "MUTUAL_SERVERS",
      allowDmsFrom: "FRIENDS_ONLY",
      showOnlineStatus: true,
      showLastActivity: true,
    });
    await adminDb.collection("public_profiles").doc(BOB_UID).set({
      userId: BOB_UID,
      name: "Bob",
      isPublic: true,
      friendRequestPolicy: "EVERYONE",
      allowDmsFrom: "EVERYONE",
    });
    await adminDb.collection("public_profiles").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      name: "Charlie",
      isPublic: true,
      friendRequestPolicy: "NOBODY",
      allowDmsFrom: "FRIENDS_ONLY",
    });
    await adminDb.collection("servers").doc("srv_shared_ab").set({
      name: "Shared Guild",
      ownerId: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID],
      isPublic: false,
    });
  });

  const baseReqAB = {
    id: pairAB,
    pairId: pairAB,
    fromUid: BOB_UID,
    fromName: "Bob",
    fromTag: "#2222",
    toUid: ALICE_UID,
    toName: "Alice",
    toTag: "#1111",
    participantIds: [ALICE_UID, BOB_UID].sort(),
    status: "PENDING",
    createdAtMs: now,
    updatedAtMs: now,
  };

  // 1. Privacy policy NOBODY denies friend request to Charlie
  await assertFails(
    bobDb.collection("friend_requests").doc(pairBC).set({
      ...baseReqAB,
      id: pairBC,
      pairId: pairBC,
      toUid: CHARLIE_UID,
      toName: "Charlie",
      participantIds: [BOB_UID, CHARLIE_UID].sort(),
    })
  );

  // 2. Privacy policy MUTUAL_SERVERS denies request without sharedServerId, allows with verified shared server
  await assertFails(
    bobDb.collection("friend_requests").doc(pairAB).set(baseReqAB)
  );
  await assertFails(
    bobDb.collection("friend_requests").doc("non_canonical_id").set({
      ...baseReqAB,
      id: "non_canonical_id",
      pairId: "non_canonical_id",
      sharedServerId: "srv_shared_ab",
    })
  );
  await assertSucceeds(
    bobDb.collection("friend_requests").doc(pairAB).set({
      ...baseReqAB,
      sharedServerId: "srv_shared_ab",
    })
  );

  // 3. Sender CANNOT accept their own request; recipient CANNOT cancel sender's pending request
  await assertFails(
    bobDb.collection("friend_requests").doc(pairAB).update({ status: "ACCEPTED" })
  );
  await assertFails(
    aliceDb.collection("friend_requests").doc(pairAB).update({ status: "CANCELLED" })
  );

  // 4. Cross-user friend list mutation MUST fail (Bob cannot touch Alice's /friends; Charlie cannot read Alice's /friends)
  const friendBobOnAlice = {
    friendUid: BOB_UID,
    pairId: pairAB,
    friendName: "Bob",
    friendTag: "#2222",
    privateNote: "Top duo",
    addedAtMs: now,
  };
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("friends").doc(BOB_UID).set(friendBobOnAlice)
  );
  await assertFails(
    charlieDb.collection("users").doc(ALICE_UID).collection("friends").get()
  );

  // 5. Alice accepts request and writes ONLY her own friend doc; Bob writes ONLY his own friend doc
  await assertSucceeds(
    aliceDb.collection("friend_requests").doc(pairAB).update({ status: "ACCEPTED", updatedAtMs: now + 10 })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("friends").doc(BOB_UID).set(friendBobOnAlice)
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("friends").doc(ALICE_UID).set({
      friendUid: ALICE_UID,
      pairId: pairAB,
      friendName: "Alice",
      friendTag: "#1111",
      addedAtMs: now,
    })
  );

  // 6. Coherent DM: Charlie (non-friend) denied by Alice's FRIENDS_ONLY; Bob (friend) allowed
  const dmAB = `dm_${pairAB}`;
  const pairAC = ALICE_UID <= CHARLIE_UID ? `${ALICE_UID}__${CHARLIE_UID}` : `${CHARLIE_UID}__${ALICE_UID}`;
  await assertFails(
    charlieDb.collection("direct_conversations").doc(`dm_${pairAC}`).set({
      id: `dm_${pairAC}`,
      participantIds: [ALICE_UID, CHARLIE_UID].sort(),
      dmPairKey: pairAC,
      isGroup: false,
      lastMessage: "Spam",
    })
  );
  await assertSucceeds(
    bobDb.collection("direct_conversations").doc(dmAB).set({
      id: dmAB,
      participantIds: [ALICE_UID, BOB_UID].sort(),
      dmPairKey: pairAB,
      isGroup: false,
      lastMessage: "Hello friend!",
    })
  );

  // 7. Blocking: Alice blocks Bob -> deletes her friend doc, cancels request, Bob cannot read/edit blocked_users or message in DM
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).set({
      blockedUid: BOB_UID,
      blockedByUid: ALICE_UID,
      name: "Bob",
      blockedAtMs: now + 20,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("friends").doc(BOB_UID).delete()
  );
  await assertSucceeds(
    aliceDb.collection("friend_requests").doc(pairAB).update({ status: "CANCELLED", updatedAtMs: now + 25 })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).get()
  );
  await assertFails(
    bobDb.collection("direct_conversations").doc(dmAB).collection("messages").doc("msg_after_block").set({
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Blocked message",
      participantIds: [ALICE_UID, BOB_UID].sort(),
    })
  );

  // Blocked pair also cannot create a new friend request
  await assertFails(
    bobDb.collection("friend_requests").doc(pairAB).set({
      ...baseReqAB,
      sharedServerId: "srv_shared_ab",
      status: "PENDING",
    })
  );
});

// =========================================================================
// Test 16: RAVEN Global Search (Owner-Only Search History & Permission-Scoped Query Isolation)
// =========================================================================
test("16. RAVEN Global Search enforces owner-only search_history and prevents cross-user/private collection leaks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com" }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@test.com" }).firestore();
  const now = Date.now();

  // Seed public vs private resources across domains
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("servers").doc("srv_pub_search").set({
      name: "Public Nexus",
      ownerId: ALICE_UID,
      memberIds: [ALICE_UID],
      isPublic: true,
    });
    await adminDb.collection("servers").doc("srv_priv_search").set({
      name: "Vault Classified",
      ownerId: ALICE_UID,
      memberIds: [ALICE_UID],
      isPublic: false,
    });
    await adminDb.collection("servers").doc("srv_priv_search").collection("channels").doc("ch_vault").set({
      serverId: "srv_priv_search",
      name: "secrets-room",
      type: "TEXT",
      isPublic: false,
      memberIds: [ALICE_UID],
    });
    await adminDb.collection("bots").doc("bot_priv_search").set({
      id: "bot_priv_search",
      ownerUid: ALICE_UID,
      name: "PrivateBot",
      handle: "privbot",
      isActive: true,
      isPublicCatalog: false,
    });
    await adminDb.collection("bots").doc("bot_pub_search").set({
      id: "bot_pub_search",
      ownerUid: ALICE_UID,
      name: "CatalogBot",
      handle: "catalogbot",
      isActive: true,
      isPublicCatalog: true,
    });
  });

  // 1. Alice can save and delete her own search_history entries
  const validHistory = {
    id: "sh_alice_1",
    userId: ALICE_UID,
    query: "Public Nexus",
    selectedTypeName: "SERVER",
    authorFilter: "",
    timestampMs: now,
  };
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("search_history").doc("sh_alice_1").set(validHistory)
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("search_history").get()
  );

  // 2. Invalid selectedTypeName or oversized query is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("search_history").doc("sh_bad").set({
      ...validHistory,
      id: "sh_bad",
      selectedTypeName: "ADMIN_DUMP",
    })
  );

  // 3. Bob CANNOT read, list, create, or delete Alice's search history
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("search_history").doc("sh_alice_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("search_history").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("search_history").doc("sh_bob_inject").set({
      ...validHistory,
      id: "sh_bob_inject",
      userId: ALICE_UID,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("search_history").doc("sh_alice_1").delete()
  );

  // 4. Scoped catalog list queries succeed for Bob ONLY when filtered to public items, while unfiltered/private list queries fail
  await assertSucceeds(
    bobDb.collection("servers").where("isPublic", "==", true).get()
  );
  await assertFails(
    bobDb.collection("servers").get()
  );
  await assertFails(
    bobDb.collection("servers").doc("srv_priv_search").collection("channels").where("isPublic", "==", false).get()
  );
  await assertSucceeds(
    bobDb.collection("bots").where("isPublicCatalog", "==", true).get()
  );
  await assertFails(
    bobDb.collection("bots").get()
  );

  // 5. Alice can delete her own history entry
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("search_history").doc("sh_alice_1").delete()
  );
});

// =========================================================================
// Test 17: RAVEN Account Security (Owner Isolation, Monotonic Epoch, No Plaintext Secrets, Immutable Audit Trails)
// =========================================================================
test("17. RAVEN Account Security enforces owner isolation, rejects plaintext secrets, protects monotonic sessionEpoch, and keeps logs/history immutable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice_sec@test.com" }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob_sec@test.com" }).firestore();
  const now = Date.now();

  const aliceSecRef = aliceDb.collection("users").doc(ALICE_UID).collection("security_profile").doc("account");
  const validProfile = {
    userId: ALICE_UID,
    sessionEpoch: 1,
    globalSignOutAfterMs: 0,
    lastSensitiveVerifiedAtMs: now,
    protectSuspiciousChanges: true,
    alertOnNewDevice: true,
    alertOnUnusualActivity: true,
    requireReauthForSensitiveActions: true,
    changeFreezeUntilMs: 0,
    cooldownUntilMs: 0,
    failedChallengeCount: 0,
    suspiciousAttemptCount: 0,
    pinHash: "a".repeat(64),
    pinSalt: "0123456789abcdef0123456789abcdef",
    twoFactorReady: true,
    preferredTwoFactorMethod: "AUTHENTICATOR_TOTP",
    enrolledFactorsMeta: [{ factorId: "f_1", method: "AUTHENTICATOR_TOTP", label: "Auth App", isEnabled: true }],
    backupCodesRemainingCount: 2,
    backupCodeHashes: ["b".repeat(64), "c".repeat(64)],
    updatedAtMs: now,
  };

  // 1. Plaintext secrets (rawPin, totpSecret, recoveryCodePlain, token) MUST be rejected
  await assertFails(
    aliceSecRef.set({
      ...validProfile,
      rawPin: "1234",
    })
  );
  await assertFails(
    aliceSecRef.set({
      ...validProfile,
      totpSecret: "JBSWY3DPEHPK3PXP",
    })
  );

  // 2. Valid hashed security_profile succeeds; sessionEpoch increment succeeds; decrement & delete MUST fail
  await assertSucceeds(aliceSecRef.set(validProfile));
  await assertSucceeds(
    aliceSecRef.update({
      sessionEpoch: 2,
      globalSignOutAfterMs: now + 500,
      updatedAtMs: now + 500,
    })
  );
  await assertFails(
    aliceSecRef.update({
      sessionEpoch: 1,
      updatedAtMs: now + 600,
    })
  );
  await assertFails(aliceSecRef.delete());

  // 3. Alice manages sessions, devices, alerts, immutable login_history, and immutable security_logs
  const sessRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_a1");
  await assertSucceeds(
    sessRef.set({
      sessionId: "sess_a1",
      userId: ALICE_UID,
      deviceIdHash: "devhash_abcdef1234",
      deviceName: "Pixel 9 Pro",
      platformLabel: "Android 15",
      appVersion: "1.0",
      networkRegionLabel: "Europe/Paris",
      status: "ACTIVE",
      sessionEpoch: 2,
      createdAtMs: now,
      lastActivityMs: now,
      expiresAtMs: now + 86400000,
    })
  );
  await assertSucceeds(
    sessRef.update({
      status: "REVOKED",
      revokedAtMs: now + 100,
      revokedReason: "Déconnexion globale",
    })
  );

  const devRef = aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("devhash_abcdef1234");
  await assertSucceeds(
    devRef.set({
      deviceIdHash: "devhash_abcdef1234",
      userId: ALICE_UID,
      displayName: "Pixel 9 Pro",
      modelName: "Pixel 9 Pro",
      osVersion: "Android 15",
      trustState: "TRUSTED",
      firstSeenAtMs: now,
      lastSeenAtMs: now,
      lastRegionLabel: "Europe/Paris",
      lastSessionId: "sess_a1",
    })
  );

  const lhRef = aliceDb.collection("users").doc(ALICE_UID).collection("login_history").doc("lh_1");
  await assertSucceeds(
    lhRef.set({
      id: "lh_1",
      userId: ALICE_UID,
      sessionId: "sess_a1",
      deviceIdHash: "devhash_abcdef1234",
      deviceName: "Pixel 9 Pro",
      authMethod: "GOOGLE_OIDC",
      outcome: "SUCCESS",
      regionLabel: "Europe/Paris",
      timestampMs: now,
    })
  );
  // Immutable login_history: even Alice CANNOT update or delete
  await assertFails(lhRef.update({ outcome: "BLOCKED" }));
  await assertFails(lhRef.delete());

  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("security_logs").doc("slog_1");
  await assertSucceeds(
    logRef.set({
      id: "slog_1",
      userId: ALICE_UID,
      category: "SESSION",
      severity: "HIGH",
      action: "GLOBAL_SIGN_OUT",
      summary: "Déconnexion globale",
      actorDeviceIdHash: "devhash_abcdef1234",
      timestampMs: now,
      timestampLabel: "10:00:00",
    })
  );
  // Immutable security_logs: even Alice CANNOT update or delete
  await assertFails(logRef.update({ summary: "Effacé" }));
  await assertFails(logRef.delete());

  // 4. Bob (cross-user attacker) CANNOT read, create, update, or delete any of Alice's security data
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("security_profile").doc("account").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_a1").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("login_history").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("security_logs").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("security_alerts").doc("sal_spoof").set({
      id: "sal_spoof",
      userId: ALICE_UID,
      title: "Spoof",
      description: "Attaque",
      severity: "HIGH",
      status: "OPEN",
      createdAtMs: now,
    })
  );
});
