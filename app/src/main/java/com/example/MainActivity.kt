package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.SilangViewModel
import com.example.ui.screens.*
import com.example.ui.theme.SilangMiniMartTheme
import com.example.ui.theme.SilangOrange

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SilangApplication

        setContent {
            SilangMiniMartTheme {
                val vm: SilangViewModel = viewModel(
                    factory = SilangViewModel.Factory(app.repository, app.aiRepository)
                )
                val state by vm.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(state.snackbarMessage) {
                    val msg = state.snackbarMessage
                    if (!msg.isNullOrBlank()) {
                        snackbarHostState.showSnackbar(msg)
                        vm.clearSnackbar()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        when {
                            !state.isReady -> {
                                CircularProgressIndicator(
                                    color = SilangOrange,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            state.loggedInUser == null -> {
                                LandingScreen(
                                    branchCoordinates = state.branchCoordinates,
                                    onLoginStaffOrPartner = vm::loginStaffOrPartner,
                                    onLoginCustomer = vm::loginCustomer,
                                    onRegisterCustomer = vm::registerCustomer
                                )
                            }
                            state.loggedInUser?.role == "customer" || state.previewAsCustomer -> {
                                CustomerPortalScreen(
                                    state = state,
                                    isAdminPreview = state.previewAsCustomer,
                                    isLiveGeminiEnabled = vm.aiRepository.isLiveGeminiEnabled,
                                    onExitPreview = { vm.setPreviewAsCustomer(false) },
                                    onLogout = vm::logout,
                                    onSelectBranch = vm::setBranch,
                                    onCreateDelivery = vm::createDelivery,
                                    onUpdateDelivery = vm::updateDelivery,
                                    onUpdateProfile = vm::updateCustomerProfile,
                                    onMarkNotificationRead = vm::markNotificationRead,
                                    onMarkAllNotificationsRead = vm::markAllNotificationsRead,
                                    onDeleteNotification = vm::deleteNotification,
                                    onClearNotifications = vm::clearNotifications,
                                    onSendAiMessage = vm::sendAiMessage,
                                    onMarkAiCartDone = vm::markAiCartActionDone,
                                    onShowMessage = vm::showMessage
                                )
                            }
                            state.loggedInUser?.role == "rider" -> {
                                RiderDashboardScreen(
                                    state = state,
                                    isLiveGeminiEnabled = vm.aiRepository.isLiveGeminiEnabled,
                                    onUpdateDelivery = vm::updateDelivery,
                                    onLogout = vm::logout,
                                    onMarkNotificationRead = vm::markNotificationRead,
                                    onMarkAllNotificationsRead = vm::markAllNotificationsRead,
                                    onDeleteNotification = vm::deleteNotification,
                                    onClearNotifications = vm::clearNotifications,
                                    onSendAiMessage = vm::sendAiMessage,
                                    onShowMessage = vm::showMessage
                                )
                            }
                            state.loggedInUser?.role == "supplier" -> {
                                SupplierDashboardScreen(
                                    state = state,
                                    onCreateSupplierDelivery = vm::createSupplierDelivery,
                                    onLogout = vm::logout,
                                    onMarkNotificationRead = vm::markNotificationRead,
                                    onMarkAllNotificationsRead = vm::markAllNotificationsRead,
                                    onDeleteNotification = vm::deleteNotification,
                                    onClearNotifications = vm::clearNotifications
                                )
                            }
                            else -> {
                                AdminShell(
                                    state = state,
                                    isLiveGeminiEnabled = vm.aiRepository.isLiveGeminiEnabled,
                                    onSelectTab = vm::setAdminTab,
                                    onSelectBranch = vm::setBranch,
                                    onSelectCashier = vm::setActiveCashier,
                                    onTogglePreviewCustomer = { vm.setPreviewAsCustomer(true) },
                                    onLogout = vm::logout,
                                    onQuickRestock = vm::adjustStock,
                                    onLoadStarterCatalog = vm::loadStarterCatalog,
                                    onCheckoutPos = vm::recordPosCheckout,
                                    onSaveProduct = vm::saveProduct,
                                    onDeleteProduct = vm::deleteProduct,
                                    onSaveCategories = vm::saveCategories,
                                    onRenameCategory = vm::renameCategory,
                                    onSetStockLimit = vm::setDefaultStockLimit,
                                    onReceiveSupplierDelivery = vm::receiveSupplierDelivery,
                                    onUpdateDelivery = vm::updateDelivery,
                                    onVerifyPayment = vm::verifyDeliveryPayment,
                                    onCreateDelivery = vm::createDelivery,
                                    onAddServiceTransaction = vm::addServiceTransaction,
                                    onSaveBranch = vm::saveBranch,
                                    onDeleteBranch = vm::deleteBranch,
                                    onCreateUser = vm::createUser,
                                    onDeleteUser = vm::deleteUser,
                                    onResetData = vm::resetOperationalData,
                                    onMarkNotificationRead = vm::markNotificationRead,
                                    onMarkAllNotificationsRead = vm::markAllNotificationsRead,
                                    onDeleteNotification = vm::deleteNotification,
                                    onClearNotifications = vm::clearNotifications,
                                    onSendAiMessage = vm::sendAiMessage,
                                    onShowMessage = vm::showMessage
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
