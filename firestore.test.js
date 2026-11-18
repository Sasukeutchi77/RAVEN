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



