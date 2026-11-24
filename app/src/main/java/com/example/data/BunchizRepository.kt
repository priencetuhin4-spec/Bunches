package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BunchizRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) {
            "User must be signed in with Google before accessing Bunchiz data."
        }
        return uid
    }

    private fun userRoot(uid: String) = db.collection("users").document(uid)

    fun observeMenuItems(userId: String): Flow<List<MenuItem>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/menuItems"
        emitAll(
            userRoot(userId).collection("menuItems")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MenuItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<MenuItem> {
                            when (MenuCategory.fromRaw(it.category)) {
                                MenuCategory.BURGERS -> 0
                                MenuCategory.PIZZA -> 1
                                MenuCategory.SIDES -> 2
                                MenuCategory.DRINKS -> 3
                            }
                        }.thenBy { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeOrders(userId: String): Flow<List<Order>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/orders"
        emitAll(
            userRoot(userId).collection("orders")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Order::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.orderNumber }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeCustomerProfile(userId: String): Flow<CustomerProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId/customerProfile/info"
        emitAll(
            userRoot(userId).collection("customerProfile").document("info")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            CustomerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeNotifications(userId: String): Flow<List<CustomerNotification>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/notifications"
        emitAll(
            userRoot(userId).collection("notifications")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomerNotification::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    suspend fun ensureDefaultMenuSeeded(userId: String): Result<Unit> {
        val path = "users/$userId/menuItems"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch during menu seeding." }
            val existing = userRoot(uid).collection("menuItems").limit(1).get().await()
            if (!existing.isEmpty) {
                return Result.success(Unit)
            }
            val batch = db.batch()
            val defaults = listOf(
                Triple(
                    "chicken_burger",
                    mapOf(
                        "userId" to uid,
                        "name" to "Chicken Burger",
                        "description" to "Crispy golden fried chicken on toasted brioche with melted cheddar & house sauce.",
                        "category" to MenuCategory.BURGERS.label,
                        "price" to 180L,
                        "available" to true,
                        "imageKey" to "chicken_burger",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    Unit
                ),
                Triple(
                    "pizza",
                    mapOf(
                        "userId" to uid,
                        "name" to "Pizza",
                        "description" to "Stone-baked artisan crust topped with bubbling mozzarella, pepperoni & fresh basil.",
                        "category" to MenuCategory.PIZZA.label,
                        "price" to 250L,
                        "available" to true,
                        "imageKey" to "pizza",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    Unit
                ),
                Triple(
                    "coke",
                    mapOf(
                        "userId" to uid,
                        "name" to "Coke",
                        "description" to "Ice-cold sparkling cola served chilled with crisp citrus lime.",
                        "category" to MenuCategory.DRINKS.label,
                        "price" to 50L,
                        "available" to true,
                        "imageKey" to "coke",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    Unit
                )
            )
            defaults.forEach { (docId, payload, _) ->
                val ref = userRoot(uid).collection("menuItems").document(docId)
                batch.set(ref, payload)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addFood(
        name: String,
        description: String,
        category: MenuCategory,
        price: Long
    ): Result<Unit> {
        val cleanName = name.trim()
        val cleanDesc = description.trim().ifEmpty { "Chef's fresh kitchen creation." }
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/menuItems"
        return try {
            val uid = requireUserId()
            require(cleanName.length in 1..80) { "Dish name must be between 1 and 80 characters." }
            require(cleanDesc.length <= 240) { "Description must be 240 characters or less." }
            require(price in 1L..100000L) { "Price must be between ৳1 and ৳100,000." }

            val docRef = userRoot(uid).collection("menuItems").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "description" to cleanDesc,
                "category" to category.label,
                "price" to price,
                "available" to true,
                "imageKey" to "custom",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateFoodPrice(itemId: String, newPrice: Long): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/menuItems/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Menu item ID is required." }
            require(newPrice in 1L..100000L) { "Price must be between ৳1 and ৳100,000." }
            val docRef = userRoot(uid).collection("menuItems").document(itemId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "price" to newPrice,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setFoodAvailability(itemId: String, available: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/menuItems/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Menu item ID is required." }
            val docRef = userRoot(uid).collection("menuItems").document(itemId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "available" to available,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFood(itemId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/menuItems/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Menu item ID is required." }
            val docRef = userRoot(uid).collection("menuItems").document(itemId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCustomerProfile(name: String, phone: String, address: String): Result<Unit> {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        val cleanAddress = address.trim()
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/customerProfile/info"
        return try {
            val uid = requireUserId()
            require(cleanName.length in 1..80) { "Name must be between 1 and 80 characters." }
            require(cleanPhone.length in 3..30) { "Phone number must be between 3 and 30 characters." }
            require(cleanAddress.length in 3..240) { "Address must be between 3 and 240 characters." }

            val docRef = userRoot(uid).collection("customerProfile").document("info")
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "phone" to cleanPhone,
                        "address" to cleanAddress,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "phone" to cleanPhone,
                        "address" to cleanAddress,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun placeCustomerOrder(
        customerName: String,
        phone: String,
        address: String,
        cartItems: List<CartItem>,
        existingOrders: List<Order>
    ): Result<Long> {
        val cleanName = customerName.trim()
        val cleanPhone = phone.trim()
        val cleanAddress = address.trim()
        var opPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders"
        return try {
            val uid = requireUserId()
            require(cartItems.isNotEmpty() && cartItems.size <= 30) {
                "Cart must contain between 1 and 30 dishes."
            }
            require(cleanName.length in 1..80) { "Customer name must be 1–80 characters." }
            require(cleanPhone.length in 3..30) { "Phone number must be 3–30 characters." }
            require(cleanAddress.length in 3..240) { "Delivery address must be 3–240 characters." }

            val orderNumber = if (existingOrders.isEmpty()) {
                1001L
            } else {
                (existingOrders.maxOf { it.orderNumber } + 1L).coerceAtLeast(1001L + existingOrders.size)
            }
            val total = cartItems.sumOf { it.subtotal }
            require(total in 1L..10000000L) { "Order total is out of allowed range." }

            val lineItemMaps = cartItems.map { item ->
                mapOf(
                    "name" to item.menuItem.name,
                    "price" to item.menuItem.price,
                    "quantity" to item.quantity.toLong(),
                    "subtotal" to item.subtotal
                )
            }

            val profileRef = userRoot(uid).collection("customerProfile").document("info")
            val profileSnapshot = profileRef.get().await()
            val orderRef = userRoot(uid).collection("orders").document("order_$orderNumber")
            val notifRef = userRoot(uid).collection("notifications").document()
            opPath = orderRef.path

            val batch = db.batch()
            if (profileSnapshot.exists()) {
                batch.update(
                    profileRef,
                    mapOf(
                        "name" to cleanName,
                        "phone" to cleanPhone,
                        "address" to cleanAddress,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    profileRef,
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "phone" to cleanPhone,
                        "address" to cleanAddress,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.set(
                orderRef,
                mapOf(
                    "userId" to uid,
                    "orderNumber" to orderNumber,
                    "customerName" to cleanName,
                    "customerPhone" to cleanPhone,
                    "customerAddress" to cleanAddress,
                    "items" to lineItemMaps,
                    "total" to total,
                    "status" to OrderStatus.PENDING.label,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                notifRef,
                mapOf(
                    "userId" to uid,
                    "orderNumber" to orderNumber,
                    "message" to "Your order #$orderNumber has been received.",
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(orderNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, opPath)
            Result.failure(e)
        }
    }

    suspend fun advanceOrderStatus(order: Order): Result<OrderStatus> {
        val current = OrderStatus.fromRaw(order.status)
        val next = current.nextStatus()
            ?: return Result.failure(IllegalStateException("Order #${order.orderNumber} is already Delivered."))
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders/${order.id}"
        return try {
            val uid = requireUserId()
            require(order.id.isNotBlank()) { "Order document ID cannot be blank." }
            val orderRef = userRoot(uid).collection("orders").document(order.id)
            val notifRef = userRoot(uid).collection("notifications").document()
            docPath = orderRef.path

            val statusMessage = when (next) {
                OrderStatus.CONFIRMED -> "Order #${order.orderNumber}: Confirmed by Bunchiz kitchen."
                OrderStatus.PREPARING -> "Order #${order.orderNumber}: Chef is preparing your meal."
                OrderStatus.READY -> "Order #${order.orderNumber}: Ready for dispatch!"
                OrderStatus.DELIVERED -> "Order #${order.orderNumber}: Delivered. Enjoy your meal!"
                OrderStatus.PENDING -> "Order #${order.orderNumber}: Pending."
            }

            val batch = db.batch()
            batch.update(
                orderRef,
                mapOf(
                    "status" to next.label,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                notifRef,
                mapOf(
                    "userId" to uid,
                    "orderNumber" to order.orderNumber,
                    "message" to statusMessage,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(next)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notifications/$notificationId"
        return try {
            val uid = requireUserId()
            require(notificationId.isNotBlank()) { "Notification ID cannot be blank." }
            val ref = userRoot(uid).collection("notifications").document(notificationId)
            docPath = ref.path
            ref.update(mapOf("read" to true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(unreadIds: List<String>): Result<Unit> {
        if (unreadIds.isEmpty()) return Result.success(Unit)
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notifications"
        return try {
            val uid = requireUserId()
            path = "users/$uid/notifications"
            val batch = db.batch()
            unreadIds.forEach { id ->
                if (id.isNotBlank()) {
                    batch.update(userRoot(uid).collection("notifications").document(id), mapOf("read" to true))
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
