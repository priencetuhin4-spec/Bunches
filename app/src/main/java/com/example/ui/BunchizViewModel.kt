package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BunchizRepository
import com.example.data.CartItem
import com.example.data.CustomerNotification
import com.example.data.CustomerProfile
import com.example.data.MenuCategory
import com.example.data.MenuItem
import com.example.data.Order
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class BunchizViewModel(
    private val repository: BunchizRepository,
    private val currentUserId: String
) : ViewModel() {

    val menuState: StateFlow<UiState<List<MenuItem>>> =
        repository.observeMenuItems(currentUserId)
            .toUiState("menu")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val ordersState: StateFlow<UiState<List<Order>>> =
        repository.observeOrders(currentUserId)
            .toUiState("orders")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val customerProfileState: StateFlow<UiState<CustomerProfile?>> =
        repository.observeCustomerProfile(currentUserId)
            .toUiState("customer profile")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val notificationsState: StateFlow<UiState<List<CustomerNotification>>> =
        repository.observeNotifications(currentUserId)
            .toUiState("notifications")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    val cartTotal: StateFlow<Long> = _cart
        .map { items -> items.sumOf { it.subtotal } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0L
        )

    val cartCount: StateFlow<Int> = _cart
        .map { items -> items.sumOf { it.quantity } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0
        )

    private val _isSubmittingOrder = MutableStateFlow(false)
    val isSubmittingOrder: StateFlow<Boolean> = _isSubmittingOrder.asStateFlow()

    private val _isSavingAction = MutableStateFlow(false)
    val isSavingAction: StateFlow<Boolean> = _isSavingAction.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultMenuSeeded(currentUserId)
                .onFailure { error ->
                    _feedbackMessage.value = error.localizedMessage ?: "Could not initialize menu."
                }
        }
    }

    private fun <T> Flow<T>.toUiState(label: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load $label"))
            }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun addToCart(menuItem: MenuItem) {
        if (!menuItem.available) {
            _feedbackMessage.value = "${menuItem.name} is currently unavailable."
            return
        }
        _cart.update { current ->
            val totalLines = current.size
            val existing = current.firstOrNull { it.menuItem.id == menuItem.id }
            if (existing != null) {
                current.map {
                    if (it.menuItem.id == menuItem.id) it.copy(quantity = (it.quantity + 1).coerceAtMost(99))
                    else it
                }
            } else if (totalLines >= 30) {
                _feedbackMessage.value = "Cart cannot exceed 30 distinct dishes."
                current
            } else {
                current + CartItem(menuItem = menuItem, quantity = 1)
            }
        }
    }

    fun decrementFromCart(menuItemId: String) {
        _cart.update { current ->
            current.mapNotNull { item ->
                if (item.menuItem.id == menuItemId) {
                    if (item.quantity > 1) item.copy(quantity = item.quantity - 1) else null
                } else {
                    item
                }
            }
        }
    }

    fun removeFromCart(menuItemId: String) {
        _cart.update { current -> current.filterNot { it.menuItem.id == menuItemId } }
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    fun placeOrder(
        customerName: String,
        phone: String,
        address: String,
        onOrderCreated: (Long) -> Unit
    ) {
        val currentCart = _cart.value
        if (currentCart.isEmpty()) {
            _feedbackMessage.value = "Your cart is empty."
            return
        }
        val existingOrders = (ordersState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            _isSubmittingOrder.value = true
            val result = repository.placeCustomerOrder(
                customerName = customerName,
                phone = phone,
                address = address,
                cartItems = currentCart,
                existingOrders = existingOrders
            )
            _isSubmittingOrder.value = false
            result.onSuccess { orderNumber ->
                _cart.value = emptyList()
                _feedbackMessage.value = "Order #$orderNumber placed!"
                onOrderCreated(orderNumber)
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "Failed to place order."
            }
        }
    }

    fun advanceOrderStatus(order: Order) {
        viewModelScope.launch {
            _isSavingAction.value = true
            val result = repository.advanceOrderStatus(order)
            _isSavingAction.value = false
            result.onSuccess { nextStatus ->
                _feedbackMessage.value = "Order #${order.orderNumber}: ${nextStatus.label}"
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "Unable to update order status."
            }
        }
    }

    fun addFood(
        name: String,
        description: String,
        category: MenuCategory,
        price: Long,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSavingAction.value = true
            val result = repository.addFood(name, description, category, price)
            _isSavingAction.value = false
            result.onSuccess {
                _feedbackMessage.value = "Added $name to Bunchiz menu."
                onSuccess()
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "Failed to add dish."
            }
        }
    }

    fun updateFoodPrice(itemId: String, itemName: String, newPrice: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSavingAction.value = true
            val result = repository.updateFoodPrice(itemId, newPrice)
            _isSavingAction.value = false
            result.onSuccess {
                _feedbackMessage.value = "Updated $itemName price to ৳$newPrice."
                onSuccess()
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "Failed to update price."
            }
        }
    }

    fun setFoodAvailability(itemId: String, itemName: String, available: Boolean) {
        viewModelScope.launch {
            val result = repository.setFoodAvailability(itemId, available)
            result.onSuccess {
                val label = if (available) "Available" else "Unavailable"
                _feedbackMessage.value = "$itemName marked $label."
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "Failed to update availability."
            }
        }
    }

    fun deleteFood(itemId: String, itemName: String) {
        viewModelScope.launch {
            val result = repository.deleteFood(itemId)
            result.onSuccess {
                removeFromCart(itemId)
                _feedbackMessage.value = "Removed $itemName from menu."
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "Failed to remove dish."
            }
        }
    }

    fun saveCustomerProfile(name: String, phone: String, address: String) {
        viewModelScope.launch {
            _isSavingAction.value = true
            val result = repository.saveCustomerProfile(name, phone, address)
            _isSavingAction.value = false
            result.onSuccess {
                _feedbackMessage.value = "Delivery profile saved."
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "Failed to save profile."
            }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun markAllNotificationsRead(notifications: List<CustomerNotification>) {
        val unreadIds = notifications.filter { !it.read }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            repository.markAllNotificationsRead(unreadIds)
        }
    }

    private companion object {
        const val TAG = "BunchizVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
