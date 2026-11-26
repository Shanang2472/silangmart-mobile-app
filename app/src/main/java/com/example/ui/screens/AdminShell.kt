package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.*
import com.example.ui.AdminTab
import com.example.ui.SilangUiState
import com.example.ui.components.AiAssistantSheet
import com.example.ui.components.NotificationCenterSheet
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShell(
    state: SilangUiState,
    isLiveGeminiEnabled: Boolean,
    onSelectTab: (AdminTab) -> Unit,
    onSelectBranch: (String) -> Unit,
    onSelectCashier: (String) -> Unit,
    onTogglePreviewCustomer: () -> Unit,
    onLogout: () -> Unit,
    onQuickRestock: (String, String, Int) -> Unit,
    onLoadStarterCatalog: () -> Unit,
    onCheckoutPos: suspend (Transaction, List<CartItem>) -> Result<Unit>,
    onSaveProduct: (Product) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onSaveCategories: (List<String>) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onSetStockLimit: (Int) -> Unit,
    onReceiveSupplierDelivery: (String, String) -> Unit,
    onUpdateDelivery: (Delivery) -> Unit,
    onVerifyPayment: (String, Boolean, String?) -> Unit,
    onCreateDelivery: suspend (Delivery, Map<String, Int>) -> Result<Unit>,
    onAddServiceTransaction: (ServiceTransaction) -> Unit,
    onSaveBranch: (BranchRecord, String?) -> Unit,
    onDeleteBranch: (Long) -> Unit,
    onCreateUser: suspend (String, String, String, String) -> Result<Unit>,
    onDeleteUser: (Long) -> Unit,
    onResetData: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: (String) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onClearNotifications: (String) -> Unit,
    onSendAiMessage: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showNotifications by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var branchMenuOpen by remember { mutableStateOf(false) }

    if (state.activeAdminTab != AdminTab.DASHBOARD) {
        BackHandler { onSelectTab(AdminTab.DASHBOARD) }
    }

    val tabIcons: Map<AdminTab, ImageVector> = remember {
        mapOf(
            AdminTab.DASHBOARD to Icons.Default.Dashboard,
            AdminTab.POS to Icons.Default.PointOfSale,
            AdminTab.INVENTORY to Icons.Default.Inventory2,
            AdminTab.DELIVERIES to Icons.Default.LocalShipping,
            AdminTab.CUSTOMERS to Icons.Default.People,
            AdminTab.SERVICES to Icons.Default.PhoneAndroid,
            AdminTab.ANALYTICS to Icons.Default.BarChart,
            AdminTab.SETTINGS to Icons.Default.Settings
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = SilangSlate900) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SILANG MINI MART", style = MaterialTheme.typography.titleLarge, color = SilangOrangeBright)
                    Text("${state.loggedInUser?.name ?: "Admin"} • ${state.activeCashier}", style = MaterialTheme.typography.bodySmall, color = SilangSlate400)
                }
                HorizontalDivider(color = SilangSlate800)
                Spacer(Modifier.height(8.dp))
                AdminTab.entries.forEach { tab ->
                    NavigationDrawerItem(
                        label = { Text(tab.label) },
                        selected = state.activeAdminTab == tab,
                        onClick = {
                            onSelectTab(tab)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(tabIcons[tab] ?: Icons.Default.Circle, contentDescription = null) },
                        badge = {
                            if (tab == AdminTab.INVENTORY && state.lowStockCount > 0) {
                                Badge(containerColor = SilangRed) { Text("${state.lowStockCount}") }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = SilangOrange,
                            selectedIconColor = SilangWhite,
                            selectedTextColor = SilangWhite,
                            unselectedIconColor = SilangSlate400,
                            unselectedTextColor = SilangWhite
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                HorizontalDivider(color = SilangSlate800, modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Preview Customer Portal") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onTogglePreviewCustomer()
                    },
                    icon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(unselectedIconColor = SilangOrangeBright, unselectedTextColor = SilangOrangeBright),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Log Out") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(unselectedIconColor = SilangRed, unselectedTextColor = SilangRed),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(state.activeAdminTab.label, style = MaterialTheme.typography.titleMedium)
                                Text(state.currentBranch, style = MaterialTheme.typography.labelSmall, color = SilangOrange)
                            }
                        },
                        navigationIcon = {
                            if (!isWide) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("admin_menu_button")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open Navigation")
                                }
                            }
                        },
                        actions = {
                            Box {
                                TextButton(onClick = { branchMenuOpen = true }) {
                                    Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(state.currentBranch)
                                }
                                DropdownMenu(expanded = branchMenuOpen, onDismissRequest = { branchMenuOpen = false }) {
                                    state.branches.filter { it.isActive }.forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text(b.name) },
                                            onClick = {
                                                onSelectBranch(b.name)
                                                branchMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            BadgedBox(
                                badge = {
                                    if (state.unreadNotificationCount > 0) {
                                        Badge { Text("${state.unreadNotificationCount}") }
                                    }
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { showNotifications = true },
                                    modifier = Modifier.testTag("admin_notifications_button")
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.cd_notifications))
                                }
                            }
                            IconButton(onClick = onLogout, modifier = Modifier.testTag("admin_logout_button")) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.cd_logout))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = SilangWhite)
                    )
                },
                bottomBar = {
                    if (!isWide) {
                        NavigationBar(containerColor = SilangWhite) {
                            listOf(
                                AdminTab.DASHBOARD,
                                AdminTab.POS,
                                AdminTab.INVENTORY,
                                AdminTab.DELIVERIES,
                                AdminTab.SERVICES
                            ).forEach { tab ->
                                NavigationBarItem(
                                    selected = state.activeAdminTab == tab,
                                    onClick = { onSelectTab(tab) },
                                    icon = { Icon(tabIcons[tab] ?: Icons.Default.Circle, contentDescription = null) },
                                    label = { Text(tab.label, maxLines = 1) }
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAiSheet = true },
                        containerColor = SilangSlate900,
                        contentColor = SilangOrangeBright,
                        modifier = Modifier.testTag("ai_assistant_fab")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.cd_ai_assistant))
                    }
                },
                containerColor = SilangSurface
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (isWide) {
                        NavigationRail(containerColor = SilangWhite) {
                            AdminTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = state.activeAdminTab == tab,
                                    onClick = { onSelectTab(tab) },
                                    icon = { Icon(tabIcons[tab] ?: Icons.Default.Circle, contentDescription = null) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (state.activeAdminTab) {
                            AdminTab.DASHBOARD -> AdminDashboardOverview(
                                state = state,
                                onNavigateTab = onSelectTab,
                                onQuickRestock = onQuickRestock,
                                onLoadStarterCatalog = onLoadStarterCatalog
                            )
                            AdminTab.POS -> AdminPosScreen(
                                state = state,
                                onCheckout = onCheckoutPos,
                                onShowMessage = onShowMessage
                            )
                            AdminTab.INVENTORY -> AdminInventoryScreen(
                                state = state,
                                onSaveProduct = onSaveProduct,
                                onAdjustStock = onQuickRestock,
                                onDeleteProduct = onDeleteProduct,
                                onSaveCategories = onSaveCategories,
                                onRenameCategory = onRenameCategory,
                                onSetStockLimit = onSetStockLimit,
                                onReceiveSupplierDelivery = onReceiveSupplierDelivery,
                                onLoadStarterCatalog = onLoadStarterCatalog
                            )
                            AdminTab.DELIVERIES -> AdminDeliveriesScreen(
                                state = state,
                                onUpdateDelivery = onUpdateDelivery,
                                onVerifyPayment = onVerifyPayment,
                                onCreateDelivery = onCreateDelivery,
                                onShowMessage = onShowMessage
                            )
                            AdminTab.CUSTOMERS -> AdminCustomersScreen(state = state)
                            AdminTab.SERVICES -> AdminServicesScreen(
                                state = state,
                                onAddServiceTransaction = onAddServiceTransaction,
                                onShowMessage = onShowMessage
                            )
                            AdminTab.ANALYTICS -> AdminAnalyticsScreen(state = state)
                            AdminTab.SETTINGS -> AdminSettingsScreen(
                                state = state,
                                onSaveBranch = onSaveBranch,
                                onDeleteBranch = onDeleteBranch,
                                onCreateUser = onCreateUser,
                                onDeleteUser = onDeleteUser,
                                onLoadStarterCatalog = onLoadStarterCatalog,
                                onResetData = onResetData
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNotifications) {
        NotificationCenterSheet(
            notifications = state.activeNotificationsForRole,
            onMarkRead = onMarkNotificationRead,
            onMarkAllRead = { onMarkAllNotificationsRead("admin") },
            onDelete = onDeleteNotification,
            onClearAll = { onClearNotifications("admin") },
            onDismiss = { showNotifications = false }
        )
    }

    if (showAiSheet) {
        AiAssistantSheet(
            role = "admin",
            messages = state.aiMessages,
            isLoading = state.isAiLoading,
            isLiveGeminiEnabled = isLiveGeminiEnabled,
            onSendMessage = onSendAiMessage,
            onAddToCartFromAi = { _, _ -> },
            onDismiss = { showAiSheet = false }
        )
    }
}
