package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.MenuCategory
import com.example.data.MenuItem
import com.example.data.Order
import com.example.data.OrderStatus
import com.example.ui.UiState
import com.example.ui.components.PriceBadge
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun StaffAdminScreen(
    menuState: UiState<List<MenuItem>>,
    ordersState: UiState<List<Order>>,
    isSaving: Boolean,
    onAdvanceOrderStatus: (Order) -> Unit,
    onAddFood: (name: String, description: String, category: MenuCategory, price: Long, onDone: () -> Unit) -> Unit,
    onUpdatePrice: (itemId: String, itemName: String, newPrice: Long, onDone: () -> Unit) -> Unit,
    onSetAvailability: (itemId: String, itemName: String, available: Boolean) -> Unit,
    onDeleteFood: (itemId: String, itemName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Kitchen Queue, 1 = Menu Admin
    var showAddFoodDialog by remember { mutableStateOf(false) }
    var editingPriceItem by remember { mutableStateOf<MenuItem?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Kitchen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("staff_tab_kitchen")
                ) {
                    Text("Kitchen Queue")
                }
                SegmentedButton(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.RestaurantMenu,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("staff_tab_menu_admin")
                ) {
                    Text("Menu Admin")
                }
            }

            if (selectedSection == 0) {
                KitchenQueueContent(
                    ordersState = ordersState,
                    isSaving = isSaving,
                    onAdvanceOrderStatus = onAdvanceOrderStatus
                )
            } else {
                MenuAdminContent(
                    menuState = menuState,
                    onOpenAddDialog = { showAddFoodDialog = true },
                    onEditPrice = { editingPriceItem = it },
                    onToggleAvailability = onSetAvailability,
                    onDeleteFood = onDeleteFood
                )
            }
        }
    }

    if (showAddFoodDialog) {
        AddFoodDialog(
            isSaving = isSaving,
            onConfirm = { name, desc, cat, price ->
                onAddFood(name, desc, cat, price) {
                    showAddFoodDialog = false
                }
            },
            onDismiss = { showAddFoodDialog = false }
        )
    }

    editingPriceItem?.let { item ->
        EditPriceDialog(
            item = item,
            isSaving = isSaving,
            onConfirm = { newPrice ->
                onUpdatePrice(item.id, item.name, newPrice) {
                    editingPriceItem = null
                }
            },
            onDismiss = { editingPriceItem = null }
        )
    }
}

@Composable
private fun KitchenQueueContent(
    ordersState: UiState<List<Order>>,
    isSaving: Boolean,
    onAdvanceOrderStatus: (Order) -> Unit
) {
    when (ordersState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Text(
                text = ordersState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        is UiState.Success -> {
            val orders = ordersState.data
            if (orders.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Kitchen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text("Kitchen queue is clear", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Orders placed from the Menu tab appear here for staff confirmation and dispatch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(orders, key = { it.id }) { order ->
                        val status = OrderStatus.fromRaw(order.status)
                        val nextAction = status.nextActionLabel
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("staff_order_card_${order.orderNumber}")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Order #${order.orderNumber}",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    StatusPill(
                                        text = status.label,
                                        isPositive = status == OrderStatus.READY || status == OrderStatus.DELIVERED
                                    )
                                }

                                Text(
                                    text = "${order.customerName} • ${order.customerPhone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = order.items.joinToString { "${it.name} ×${it.quantity}" },
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PriceBadge(price = order.total)

                                    if (nextAction != null) {
                                        Button(
                                            onClick = { onAdvanceOrderStatus(order) },
                                            enabled = !isSaving,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("advance_order_${order.orderNumber}")
                                        ) {
                                            Text(nextAction)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Completed",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAdminContent(
    menuState: UiState<List<MenuItem>>,
    onOpenAddDialog: () -> Unit,
    onEditPrice: (MenuItem) -> Unit,
    onToggleAvailability: (itemId: String, itemName: String, available: Boolean) -> Unit,
    onDeleteFood: (itemId: String, itemName: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onOpenAddDialog,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("add_food_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add New Dish to Menu")
        }

        when (menuState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Text(
                    text = menuState.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(menuState.data, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_menu_card_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        PriceBadge(price = item.price)
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { onEditPrice(item) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("edit_price_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit price for ${item.name}"
                                        )
                                    }
                                    Switch(
                                        checked = item.available,
                                        onCheckedChange = { checked ->
                                            onToggleAvailability(item.id, item.name, checked)
                                        },
                                        modifier = Modifier.testTag("toggle_available_${item.id}")
                                    )
                                    IconButton(
                                        onClick = { onDeleteFood(item.id, item.name) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_food_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete ${item.name}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFoodDialog(
    isSaving: Boolean,
    onConfirm: (name: String, description: String, category: MenuCategory, price: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MenuCategory.BURGERS) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Dish", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("Dish Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_food_name_input")
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                    },
                    label = { Text("Price (৳ BDT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_food_price_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        errorMsg = null
                    },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_food_desc_input")
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MenuCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val parsedPrice = priceText.toLongOrNull() ?: 0L
                    when {
                        cleanName.isEmpty() || cleanName.length > 80 -> errorMsg = "Enter dish name (1–80 chars)."
                        parsedPrice !in 1L..100000L -> errorMsg = "Enter a valid price (৳1–৳100,000)."
                        else -> onConfirm(cleanName, description.trim(), category, parsedPrice)
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("confirm_add_food_button")
            ) {
                Text("Add Dish")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditPriceDialog(
    item: MenuItem,
    isSaving: Boolean,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var priceText by remember(item) { mutableStateOf(item.price.toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Price: ${item.name}", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                    },
                    label = { Text("New Price (৳ BDT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_price_input")
                )
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = priceText.toLongOrNull() ?: 0L
                    if (parsed !in 1L..100000L) {
                        errorMsg = "Price must be between ৳1 and ৳100,000."
                    } else {
                        onConfirm(parsed)
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("confirm_edit_price_button")
            ) {
                Text("Save Price")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
