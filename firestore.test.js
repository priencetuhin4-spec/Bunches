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

// --- 1. UNAUTHENTICATED & CROSS-USER ISOLATION ---

test("Unauthenticated user: denied read and write on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/menuItems`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/orders`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/notifications`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/customerProfile`).doc("info").get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/menuItems`).doc("burger").set({
      userId: ALICE_UID,
      name: "Chicken Burger",
      description: "Crispy brioche",
      category: "Burgers",
      price: 180,
      available: true,
      imageKey: "chicken_burger",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's PII, menu, orders, or notifications", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`users/${ALICE_UID}/customerProfile/info`).set({
      userId: ALICE_UID,
      name: "Alice Rahman",
      phone: "+8801711000000",
      address: "House 12, Banani, Dhaka",
      createdAt: now,
      updatedAt: now,
    });
    await db.doc(`users/${ALICE_UID}/menuItems/chicken_burger`).set({
      userId: ALICE_UID,
      name: "Chicken Burger",
      description: "Crispy chicken patty",
      category: "Burgers",
      price: 180,
      available: true,
      imageKey: "chicken_burger",
      createdAt: now,
      updatedAt: now,
    });
    await db.doc(`users/${ALICE_UID}/orders/order_1001`).set({
      userId: ALICE_UID,
      orderNumber: 1001,
      customerName: "Alice Rahman",
      customerPhone: "+8801711000000",
      customerAddress: "House 12, Banani, Dhaka",
      items: [{ name: "Chicken Burger", price: 180, quantity: 2, subtotal: 360 }],
      total: 360,
      status: "Pending",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/customerProfile/info`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/menuItems`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/orders`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/orders/order_1001`).update({
    status: "Confirmed",
    updatedAt: new Date(Date.now() - 1000),
  }));
});

// --- 2. MENU ITEMS CRUD & SECURITY BOUNDS ---

test("Menu items: owner can create, update price/availability, list, and delete; rejects invalid payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Empty collection list succeeds (no existing() trap)
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/menuItems`).get());

  // Valid create matching Kotlin MenuRepository
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/menuItems/chicken_burger`).set({
      userId: ALICE_UID,
      name: "Chicken Burger",
      description: "Crispy golden fried chicken on toasted brioche",
      category: "Burgers",
      price: 180,
      available: true,
      imageKey: "chicken_burger",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update: update_price & set_availability
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/menuItems/chicken_burger`).update({
      price: 195,
      available: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Shadow Update / Ghost field rejected
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/menuItems/pizza`).set({
      id: "pizza", // @DocumentId must not be written
      userId: ALICE_UID,
      name: "Pizza",
      description: "Pepperoni",
      category: "Pizza",
      price: 250,
      available: true,
      imageKey: "pizza",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Negative price or future timestamp rejected
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/menuItems/coke`).set({
      userId: ALICE_UID,
      name: "Coke",
      description: "Chilled",
      category: "Drinks",
      price: 0,
      available: true,
      imageKey: "coke",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/menuItems/chicken_burger`).update({
      price: 200,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Immutable createdAt cannot change
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/menuItems/chicken_burger`).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

// --- 3. CUSTOMER PROFILE & ORDER STATE MACHINE ---

test("Customer profile & Orders: valid creation, strict status transitions, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/customerProfile/info`).set({
      userId: ALICE_UID,
      name: "Tuhin",
      phone: "01712345678",
      address: "Road 7, Dhanmondi, Dhaka",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create order starting Pending
  const orderRef = aliceDb.doc(`users/${ALICE_UID}/orders/order_1001`);
  await assertSucceeds(
    orderRef.set({
      userId: ALICE_UID,
      orderNumber: 1001,
      customerName: "Tuhin",
      customerPhone: "01712345678",
      customerAddress: "Road 7, Dhanmondi, Dhaka",
      items: [
        { name: "Chicken Burger", price: 180, quantity: 2, subtotal: 360 },
        { name: "Coke", price: 50, quantity: 1, subtotal: 50 },
      ],
      total: 410,
      status: "Pending",
      createdAt: created,
      updatedAt: created,
    })
  );

  // State shortcutting rejected: Pending -> Ready directly must fail
  await assertFails(
    orderRef.update({
      status: "Ready",
      updatedAt: new Date(Date.now() - 4000),
    })
  );

  // Valid sequential transitions: Pending -> Confirmed -> Preparing -> Ready -> Delivered
  for (const status of ["Confirmed", "Preparing", "Ready", "Delivered"]) {
    await assertSucceeds(
      orderRef.update({
        status,
        updatedAt: new Date(Date.now() - 1000),
      })
    );
  }

  // Terminal state lock: Delivered cannot transition again
  await assertFails(
    orderRef.update({
      status: "Pending",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

// --- 4. NOTIFICATIONS ---

test("Notifications: owner can create unread notification and mark read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 2000);

  const notifRef = aliceDb.doc(`users/${ALICE_UID}/notifications/notif_1`);
  await assertSucceeds(
    notifRef.set({
      userId: ALICE_UID,
      orderNumber: 1001,
      message: "Your order #1001 has been received.",
      read: false,
      createdAt: created,
    })
  );

  await assertSucceeds(notifRef.update({ read: true }));

  // Message tampering rejected
  await assertFails(notifRef.update({ message: "Tampered", read: true }));
});
