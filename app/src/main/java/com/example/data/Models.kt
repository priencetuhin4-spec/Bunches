package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MenuCategory(val label: String) {
    BURGERS("Burgers"),
    PIZZA("Pizza"),
    DRINKS("Drinks"),
    SIDES("Sides");

    companion object {
        fun fromRaw(raw: String?): MenuCategory =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: SIDES
    }
}

enum class OrderStatus(val label: String, val stepIndex: Int, val nextActionLabel: String?) {
    PENDING("Pending", 0, "Confirm Order"),
    CONFIRMED("Confirmed", 1, "Start Preparing"),
    PREPARING("Preparing", 2, "Mark Ready"),
    READY("Ready", 3, "Mark Delivered"),
    DELIVERED("Delivered", 4, null);

    fun nextStatus(): OrderStatus? = when (this) {
        PENDING -> CONFIRMED
        CONFIRMED -> PREPARING
        PREPARING -> READY
        READY -> DELIVERED
        DELIVERED -> null
    }

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class MenuItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = MenuCategory.BURGERS.label,
    val price: Long = 0L,
    val available: Boolean = true,
    val imageKey: String = "custom",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val name: String = "",
    val price: Long = 0L,
    val quantity: Long = 1L,
    val subtotal: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderNumber: Long = 1001L,
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val items: List<OrderLineItem> = emptyList(),
    val total: Long = 0L,
    val status: String = OrderStatus.PENDING.label,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomerNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderNumber: Long = 1001L,
    val message: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class CartItem(
    val menuItem: MenuItem,
    val quantity: Int
) {
    val subtotal: Long get() = menuItem.price * quantity
}
