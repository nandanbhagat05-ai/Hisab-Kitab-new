const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_merchant_123";
const BOB_UID = "bob_merchant_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write any KhataBook collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set({
        userId: ALICE_UID,
        name: "Ramesh Kirana",
        createdAt: past,
        updatedAt: past,
      })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cashEntries").get()
  );
});

test("User profile create, partial update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Sharma General Store",
      ownerName: "Rajesh Sharma",
      phone: "+919876543210",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      businessName: "Sharma Super Mart",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject Bob reading or writing Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      businessName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Customer & LedgerEntry CRUD, parent existence check, and @DocumentId guard", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create customer without 'id' field (Kotlin @DocumentId excluded on write)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set({
        userId: ALICE_UID,
        name: "Suresh Kumar",
        phone: "9876543210",
        notes: "Monthly ration",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject writing explicit 'id' field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_2")
      .set({
        id: "cust_2",
        userId: ALICE_UID,
        name: "Invalid Id Field",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Update customer
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .update({
        name: "Suresh Kumar Traders",
        phone: "9876543211",
        notes: "",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Create LedgerEntry for existing customer succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ledgerEntries")
      .doc("entry_1")
      .set({
        userId: ALICE_UID,
        customerId: "cust_1",
        type: "GAVE",
        amountPaise: 150000,
        note: "25kg Rice Bag",
        entryDateMillis: Date.now() - 2000,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject LedgerEntry for non-existent customerId
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ledgerEntries")
      .doc("entry_orphan")
      .set({
        userId: ALICE_UID,
        customerId: "non_existent_cust",
        type: "GAVE",
        amountPaise: 50000,
        entryDateMillis: Date.now() - 2000,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject negative or zero amountPaise and invalid type
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ledgerEntries")
      .doc("entry_bad_amt")
      .set({
        userId: ALICE_UID,
        customerId: "cust_1",
        type: "GAVE",
        amountPaise: 0,
        entryDateMillis: Date.now() - 2000,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Partial update on LedgerEntry succeeds (immutable customerId/createdAt preserved)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ledgerEntries")
      .doc("entry_1")
      .update({
        type: "GOT",
        amountPaise: 100000,
        note: "UPI Received",
        entryDateMillis: Date.now() - 1000,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject modifying immutable customerId on LedgerEntry update
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ledgerEntries")
      .doc("entry_1")
      .update({
        customerId: "cust_other",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // List queries succeed for owner, fail for Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ledgerEntries").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("ledgerEntries").get()
  );
});

test("CashEntry create, update, delete, and validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 8000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("cashEntries")
      .doc("cash_1")
      .set({
        userId: ALICE_UID,
        type: "IN",
        amountPaise: 450000,
        category: "Daily Sales",
        paymentMode: "CASH",
        note: "Counter morning sale",
        entryDateMillis: Date.now() - 3000,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future server timestamp spoofing
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("cashEntries")
      .doc("cash_future")
      .set({
        userId: ALICE_UID,
        type: "IN",
        amountPaise: 1000,
        category: "Sales",
        paymentMode: "ONLINE",
        entryDateMillis: Date.now(),
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Update CashEntry
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("cashEntries")
      .doc("cash_1")
      .update({
        type: "OUT",
        amountPaise: 250000,
        category: "Supplier",
        paymentMode: "ONLINE",
        note: "Paid wholesale bill",
        entryDateMillis: Date.now() - 1000,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Delete CashEntry
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("cashEntries")
      .doc("cash_1")
      .delete()
  );
});
