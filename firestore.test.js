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
