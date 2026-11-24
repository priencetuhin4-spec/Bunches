package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.BunchizRepository
import com.example.data.OrderStatus
import com.example.data.signOutFromBunchiz
import com.example.ui.BunchizViewModel
import com.example.ui.UiState
import com.example.ui.components.CartCheckoutSheet
import com.example.ui.components.NotificationsSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.StaffAdminScreen
import com.example.ui.theme.BunchizTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_MENU = "menu"
const val ROUTE_ORDERS = "orders"
const val ROUTE_STAFF = "staff"
const val ROUTE_PROFILE = "profile"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val DESTINATIONS = listOf(
    NavDestination(ROUTE_MENU, "Menu", Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu),
    NavDestination(ROUTE_ORDERS, "Orders", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    NavDestination(ROUTE_STAFF, "Staff", Icons.Filled.Storefront, Icons.Outlined.Storefront),
    NavDestination(ROUTE_PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BunchizTheme {
                BunchizAppContent()
            }
        }
    }
}

@Composable
fun BunchizAppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            BunchizAuthenticatedShell(
                currentUserId = uid,
                userEmail = user.email.orEmpty(),
                userDisplayName = user.displayName.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BunchizAuthenticatedShell(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: BunchizViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BunchizViewModel(BunchizRepository(db), currentUserId)
            }
        }
    )

    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val profileState by viewModel.customerProfileState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val cartTotal by viewModel.cartTotal.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val isSubmittingOrder by viewModel.isSubmittingOrder.collectAsStateWithLifecycle()
    val isSavingAction by viewModel.isSavingAction.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val notifications = (notificationsState as? UiState.Success)?.data ?: emptyList()
    val unreadNotifications = notifications.count { !it.read }
    val activeOrderCount = ((ordersState as? UiState.Success)?.data ?: emptyList())
        .count { OrderStatus.fromRaw(it.status) != OrderStatus.DELIVERED }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_MENU

    val snackbarHostState = remember { SnackbarHostState() }
    var showCartSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentRoute) {
                                ROUTE_MENU -> "Bunchiz"
                                ROUTE_ORDERS -> "Your Orders"
                                ROUTE_STAFF -> "Staff & Kitchen"
                                ROUTE_PROFILE -> "Profile"
                                else -> "Bunchiz"
                            },
                            style = MaterialTheme.typography.headlineMedium
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showNotificationsSheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifications > 0) {
                                        Badge { Text("$unreadNotifications") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Order Alerts"
                                )
                            }
                        }
                        IconButton(
                            onClick = { showCartSheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_cart_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (cartCount > 0) {
                                        Badge { Text("$cartCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingBag,
                                    contentDescription = "Open Bunchiz Cart"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    val badgeCount = when (dest.route) {
                                        ROUTE_ORDERS -> activeOrderCount
                                        else -> 0
                                    }
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text("$badgeCount") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_MENU,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(ROUTE_MENU) {
                        MenuScreen(
                            menuState = menuState,
                            cart = cart,
                            cartTotal = cartTotal,
                            cartCount = cartCount,
                            onAddToCart = viewModel::addToCart,
                            onDecrementCart = viewModel::decrementFromCart,
                            onOpenCart = { showCartSheet = true }
                        )
                    }
                    composable(ROUTE_ORDERS) {
                        OrdersScreen(
                            ordersState = ordersState,
                            notifications = notifications,
                            onBrowseMenu = { navigateTo(ROUTE_MENU) },
                            onOpenNotifications = { showNotificationsSheet = true }
                        )
                    }
                    composable(ROUTE_STAFF) {
                        StaffAdminScreen(
                            menuState = menuState,
                            ordersState = ordersState,
                            isSaving = isSavingAction,
                            onAdvanceOrderStatus = viewModel::advanceOrderStatus,
                            onAddFood = viewModel::addFood,
                            onUpdatePrice = viewModel::updateFoodPrice,
                            onSetAvailability = viewModel::setFoodAvailability,
                            onDeleteFood = viewModel::deleteFood
                        )
                    }
                    composable(ROUTE_PROFILE) {
                        ProfileScreen(
                            userEmail = userEmail,
                            userDisplayName = userDisplayName,
                            profileState = profileState,
                            notifications = notifications,
                            isSaving = isSavingAction,
                            onSaveProfile = viewModel::saveCustomerProfile,
                            onOpenNotifications = { showNotificationsSheet = true },
                            onSignOut = {
                                signOutFromBunchiz(credentialManager, scope)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCartSheet) {
        CartCheckoutSheet(
            cart = cart,
            cartTotal = cartTotal,
            customerProfile = (profileState as? UiState.Success)?.data,
            defaultUserName = userDisplayName,
            isSubmitting = isSubmittingOrder,
            onIncrement = viewModel::addToCart,
            onDecrement = viewModel::decrementFromCart,
            onRemove = viewModel::removeFromCart,
            onPlaceOrder = { name, phone, address ->
                viewModel.placeOrder(name, phone, address) {
                    showCartSheet = false
                    navigateTo(ROUTE_ORDERS)
                }
            },
            onDismiss = { showCartSheet = false }
        )
    }

    if (showNotificationsSheet) {
        NotificationsSheet(
            notifications = notifications,
            onMarkRead = viewModel::markNotificationRead,
            onMarkAllRead = { viewModel.markAllNotificationsRead(notifications) },
            onDismiss = { showNotificationsSheet = false }
        )
    }
}
