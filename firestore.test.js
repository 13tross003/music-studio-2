const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_producer";
const BOB_UID = "bob_producer";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSessionPayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    title: "Midnight Quantum",
    genreMood: "Atmospheric Trap Soul",
    musicalKey: "C Minor",
    bpm: 92,
    hpfHz: 90,
    subBoostDb: 4.5,
    airPresenceDb: 3.0,
    saturation: 0.25,
    compression: 0.45,
    swing: 0.08,
    kickPattern: [120,0,0,0, 0,0,100,0, 0,0,115,0, 0,0,0,80],
    snarePattern: [0,0,0,0, 120,0,0,0, 0,0,0,0, 120,0,0,40],
    hihatPattern: [90,50,80,50, 90,50,80,60, 90,50,80,50, 90,70,100,60],
    bassNotes: [36,0,0,0, 0,0,36,0, 43,0,41,0, 39,0,36,0],
    melodyNotes: [60,0,63,0, 67,0,0,63, 70,0,67,0, 63,0,60,0],
    vocalGuideNotes: [72,0,0,75, 0,72,0,0, 79,0,75,0, 72,0,0,0],
    activeLyrics: "Moonlight dripping on the 808 glass...",
    createdAt: now,
    updatedAt: now,
  };
}

function validMessagePayload(uid, speaker = "ENGINEER", now = new Date(Date.now() - 1000)) {
  return {
    userId: uid,
    speaker,
    text: "Carved 90Hz HPF off the keys so that 808 sub hits straight in the chest.",
    sampleReference: "Drum pocket inspired by Kanye West - Flashing Lights",
    mixSummary: "92 BPM • C Minor • HPF 90Hz • Sub +4.5dB",
    hasStemAttachment: true,
    stemTitle: "Midnight Quantum v1",
    stemBpm: 92,
    stemKey: "C Minor",
    createdAt: now,
    updatedAt: now,
  };
}

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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read or write sessions or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set(validSessionPayload(ALICE_UID))
  );
});

test("Owner can create, list, update, and add messages to session", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");
  await assertSucceeds(sessRef.set(validSessionPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get());

  await assertSucceeds(
    sessRef.update({
      bpm: 96,
      hpfHz: 110,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  const msgRef = sessRef.collection("messages").doc("msg_1");
  await assertSucceeds(msgRef.set(validMessagePayload(ALICE_UID, "ENGINEER")));
  await assertSucceeds(sessRef.collection("messages").orderBy("createdAt").get());
});

test("Rejects cross-user access, ghost fields, invalid speakers, and immutable field tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const aliceSess = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");
  await assertSucceeds(aliceSess.set(validSessionPayload(ALICE_UID)));

  // Cross-user read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").update({ bpm: 140 }));

  // Ghost field rejected (Shadow Update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_2").set({
      ...validSessionPayload(ALICE_UID),
      isAdmin: true,
    })
  );

  // @DocumentId 'id' field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_3").set({
      ...validSessionPayload(ALICE_UID),
      id: "sess_3",
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceSess.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Invalid speaker rejected
  await assertFails(
    aliceSess.collection("messages").doc("msg_bad").set(validMessagePayload(ALICE_UID, "UNKNOWN_BOT"))
  );

  // Orphan message without parent session rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("missing_sess")
      .collection("messages").doc("msg_orphan").set(validMessagePayload(ALICE_UID, "PRODUCER"))
  );
});
