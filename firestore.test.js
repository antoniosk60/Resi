const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("/usr/local/lib/node_modules/@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_EMAIL = "angelfernandoantonioflores4@gmail.com";
const CAP1_EMAIL = "capturista1@gmail.com";
const CAP2_EMAIL = "capturista2@gmail.com";
const CAP3_EMAIL = "capturista3@gmail.com";
const CAP4_EMAIL = "capturista4@gmail.com";
const CAP5_EMAIL = "capturista5@gmail.com";
const CAP6_EMAIL = "capturista6@gmail.com";
const OUTSIDER_EMAIL = "intruso@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function authedDb(uid, email, emailVerified = true) {
  return testEnv
    .authenticatedContext(uid, {
      email,
      email_verified: emailVerified,
    })
    .firestore();
}

async function seedCapturista(email, slotId) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("capturista_slots").doc(slotId).set({
      email,
      slotId,
      assignedAtMillis: 1700000000000,
    });
    await db.collection("authorized_users").doc(email).set({
      email,
      displayName: `Capturista ${slotId}`,
      role: "CAPTURISTA",
      slotId,
      uid: "",
      photoUrl: "",
      addedBy: ADMIN_EMAIL,
      addedAtMillis: 1700000000000,
      lastActiveMillis: 0,
    });
  });
}

function sampleResident(id = 101) {
  return {
    id,
    fullName: "Sofía Garza Ortiz",
    houseNumber: "Edificio A Depto 104",
    building: "Edificio A",
    apartment: "104",
    accessCode: "CLV-A104",
    blockOrSection: "Etapa 1",
    phone: "55-1234-5678",
    email: "sofia@example.com",
    vehiclePlates: "ABC-123",
    notes: "",
    isMoroso: false,
    isActive: true,
    registeredDateMillis: 1700000000000,
    createdBy: ADMIN_EMAIL,
    lastModifiedBy: ADMIN_EMAIL,
    lastModifiedDateMillis: 1700000000000,
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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write residents or authorized_users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("residents").get());
  await assertFails(unauthDb.collection("authorized_users").get());
  await assertFails(unauthDb.collection("residents").doc("res_101").set(sampleResident(101)));
});

test("Unauthorized Google email cannot read residents, visits, or register itself", async () => {
  const outsiderDb = authedDb("outsider_uid", OUTSIDER_EMAIL, true);
  await assertFails(outsiderDb.collection("residents").get());
  await assertFails(outsiderDb.collection("visits").get());
  await assertFails(outsiderDb.collection("residents").doc("res_101").set(sampleResident(101)));
  await assertFails(
    outsiderDb.collection("authorized_users").doc(OUTSIDER_EMAIL).set({
      email: OUTSIDER_EMAIL,
      displayName: "Intruso",
      role: "ADMINISTRADOR",
      slotId: "0",
      addedBy: OUTSIDER_EMAIL,
      addedAtMillis: 1700000000000,
    })
  );
});

test("Unverified email token is rejected even for Admin email", async () => {
  const unverifiedAdmin = authedDb("admin_uid", ADMIN_EMAIL, false);
  await assertFails(unverifiedAdmin.collection("residents").get());
});

test("Bootstrapped Admin can create own Admin doc and add up to 5 Capturistas, 6th fails", async () => {
  const adminDb = authedDb("admin_uid", ADMIN_EMAIL, true);

  // Admin self-init
  await assertSucceeds(
    adminDb.collection("authorized_users").doc(ADMIN_EMAIL).set({
      email: ADMIN_EMAIL,
      displayName: "Admin Gavilan",
      role: "ADMINISTRADOR",
      slotId: "0",
      uid: "admin_uid",
      photoUrl: "",
      addedBy: ADMIN_EMAIL,
      addedAtMillis: 1700000000000,
      lastActiveMillis: 1700000000000,
    })
  );

  // Add 5 Capturistas in slots 1..5 atomically
  const caps = [
    [CAP1_EMAIL, "1"],
    [CAP2_EMAIL, "2"],
    [CAP3_EMAIL, "3"],
    [CAP4_EMAIL, "4"],
    [CAP5_EMAIL, "5"],
  ];

  for (const [email, slotId] of caps) {
    const batch = adminDb.batch();
    batch.set(adminDb.collection("capturista_slots").doc(slotId), {
      email,
      slotId,
      assignedAtMillis: 1700000000000,
    });
    batch.set(adminDb.collection("authorized_users").doc(email), {
      email,
      displayName: `Capturista ${slotId}`,
      role: "CAPTURISTA",
      slotId,
      uid: "",
      photoUrl: "",
      addedBy: ADMIN_EMAIL,
      addedAtMillis: 1700000000000,
      lastActiveMillis: 0,
    });
    await assertSucceeds(batch.commit());
  }

  // Attempting a 6th Capturista with invalid slot "6" fails
  const batchInvalidSlot = adminDb.batch();
  batchInvalidSlot.set(adminDb.collection("capturista_slots").doc("6"), {
    email: CAP6_EMAIL,
    slotId: "6",
    assignedAtMillis: 1700000000000,
  });
  batchInvalidSlot.set(adminDb.collection("authorized_users").doc(CAP6_EMAIL), {
    email: CAP6_EMAIL,
    displayName: "Capturista 6",
    role: "CAPTURISTA",
    slotId: "6",
    uid: "",
    photoUrl: "",
    addedBy: ADMIN_EMAIL,
    addedAtMillis: 1700000000000,
    lastActiveMillis: 0,
  });
  await assertFails(batchInvalidSlot.commit());

  // Attempting to overwrite occupied slot "1" fails
  await assertFails(
    adminDb.collection("capturista_slots").doc("1").set({
      email: CAP6_EMAIL,
      slotId: "1",
      assignedAtMillis: 1700000000000,
    })
  );
});

test("Authorized Capturista can read/write residents and visits, cannot escalate role or delete residents", async () => {
  await seedCapturista(CAP1_EMAIL, "1");
  const capDb = authedDb("cap1_uid", CAP1_EMAIL, true);

  // Can update own session metadata
  await assertSucceeds(
    capDb.collection("authorized_users").doc(CAP1_EMAIL).update({
      uid: "cap1_uid",
      displayName: "Capturista Uno",
      lastActiveMillis: 1700000001000,
    })
  );

  // Cannot escalate own role to ADMINISTRADOR
  await assertFails(
    capDb.collection("authorized_users").doc(CAP1_EMAIL).update({
      role: "ADMINISTRADOR",
    })
  );

  // Cannot add another Capturista
  await assertFails(
    capDb.collection("capturista_slots").doc("2").set({
      email: CAP2_EMAIL,
      slotId: "2",
      assignedAtMillis: 1700000000000,
    })
  );

  // Can create & read residents
  await assertSucceeds(capDb.collection("residents").doc("res_101").set(sampleResident(101)));
  await assertSucceeds(capDb.collection("residents").get());

  // Cannot delete resident (Admin only)
  await assertFails(capDb.collection("residents").doc("res_101").delete());

  // Can create audit log, cannot update/delete audit log
  await assertSucceeds(
    capDb.collection("audit_logs").doc("log_1").set({
      id: 1,
      timestampMillis: 1700000000000,
      userName: "Capturista Uno",
      userRole: "Capturista",
      actionType: "CREAR",
      entityType: "Residente",
      entityId: 101,
      title: "Alta",
      details: "Test",
    })
  );
  await assertFails(capDb.collection("audit_logs").doc("log_1").delete());
});
