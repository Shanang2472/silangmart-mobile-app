package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.model.*
import com.example.data.repository.SilangRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

enum class AdminTab(val label: String) {
    DASHBOARD("Overview"),
    POS("Cash Register"),
    INVENTORY("Stocks"),
    DELIVERIES("Deliveries"),
    CUSTOMERS("Customers"),
    SERVICES("Services"),
    ANALYTICS("Reports"),
    SETTINGS("Settings")
}

data class SilangUiState(
    val isReady: Boolean = false,
    val loggedInUser: LoggedInUser? = null,
    val activeAdminTab: AdminTab = AdminTab.DASHBOARD,
    val currentBranch: String = "Poblacion",
    val activeCashier: String = "Ate Lorna (Cashier)",
    val previewAsCustomer: Boolean = false,
    val branches: List<BranchRecord> = emptyList(),
    val users: List<UserRecord> = emptyList(),
    val customers: List<CustomerRecord> = emptyList(),
    val products: List<Product> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val deliveries: List<Delivery> = emptyList(),
    val serviceTransactions: List<ServiceTransaction> = emptyList(),
    val supplierDeliveries: List<SupplierDelivery> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val categories: List<String> = SilangRepository.DEFAULT_CATEGORIES,
    val defaultStockLimit: Int = 500,
    val aiMessages: List<AiMessage> = emptyList(),
    val isAiLoading: Boolean = false,
    val snackbarMessage: String? = null
) {
    val branchCoordinates: Map<String, Pair<Double, Double>>
        get() = branches.associate { it.name to (it.lat to it.lng) }

    val activeNotificationsForRole: List<AppNotification>
        get() {
            val user = loggedInUser ?: return emptyList()
            val role = user.role
            return notifications.filter { n ->
                when (role) {
                    "admin" -> n.role == "admin"
                    "customer" -> n.role == "customer" && (n.userId == user.name || n.userId == user.username)
                    "rider" -> n.role == "rider" && (n.userId == user.name || n.userId == user.username)
                    "supplier" -> n.role == "supplier" && (n.userId == user.name || n.userId == user.username)
                    else -> false
                }
            }
        }

    val unreadNotificationCount: Int
        get() = activeNotificationsForRole.count { !it.isRead }

    val lowStockCount: Int
        get() = products.count { it.stockForBranch(currentBranch) <= it.lowStockThreshold }
}

class SilangViewModel(
    private val repository: SilangRepository,
    val aiRepository: AiRepository
) : ViewModel() {

    private val _session = MutableStateFlow(SessionState())

    private data class SessionState(
        val isReady: Boolean = false,
        val loggedInUser: LoggedInUser? = null,
        val activeAdminTab: AdminTab = AdminTab.DASHBOARD,
        val currentBranch: String = "Poblacion",
        val activeCashier: String = "Ate Lorna (Cashier)",
        val previewAsCustomer: Boolean = false,
        val aiMessages: List<AiMessage> = emptyList(),
        val isAiLoading: Boolean = false,
        val snackbarMessage: String? = null
    )

    private val storeDataFlow = combine(
        repository.branches,
        repository.users,
        repository.customers,
        repository.products,
        repository.transactions
    ) { branches, users, customers, products, transactions ->
        StoreDataPart1(branches, users, customers, products, transactions)
    }

    private val opsDataFlow = combine(
        repository.deliveries,
        repository.serviceTransactions,
        repository.supplierDeliveries,
        repository.notifications,
        repository.categories,
        repository.defaultStockLimit
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        StoreDataPart2(
            deliveries = arr[0] as List<Delivery>,
            services = arr[1] as List<ServiceTransaction>,
            supplierDeliveries = arr[2] as List<SupplierDelivery>,
            notifications = arr[3] as List<AppNotification>,
            categories = arr[4] as List<String>,
            defaultStockLimit = arr[5] as Int
        )
    }

    val uiState: StateFlow<SilangUiState> = combine(_session, storeDataFlow, opsDataFlow) { session, p1, p2 ->
        val validBranch = if (p1.branches.any { it.name == session.currentBranch }) {
            session.currentBranch
        } else {
            p1.branches.firstOrNull()?.name ?: "Poblacion"
        }
        SilangUiState(
            isReady = session.isReady,
            loggedInUser = session.loggedInUser,
            activeAdminTab = session.activeAdminTab,
            currentBranch = validBranch,
            activeCashier = session.activeCashier,
            previewAsCustomer = session.previewAsCustomer,
            branches = p1.branches,
            users = p1.users,
            customers = p1.customers,
            products = p1.products,
            transactions = p1.transactions,
            deliveries = p2.deliveries,
            serviceTransactions = p2.services,
            supplierDeliveries = p2.supplierDeliveries,
            notifications = p2.notifications,
            categories = p2.categories,
            defaultStockLimit = p2.defaultStockLimit,
            aiMessages = session.aiMessages,
            isAiLoading = session.isAiLoading,
            snackbarMessage = session.snackbarMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SilangUiState())

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            _session.update { it.copy(isReady = true) }
            resetAiGreeting("customer", "Ka-Suki")
        }
    }

    fun clearSnackbar() {
        _session.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(message: String) {
        _session.update { it.copy(snackbarMessage = message) }
    }

    fun setBranch(branch: String) {
        _session.update { it.copy(currentBranch = branch) }
    }

    fun setAdminTab(tab: AdminTab) {
        _session.update { it.copy(activeAdminTab = tab) }
    }

    fun setActiveCashier(name: String) {
        _session.update { it.copy(activeCashier = name) }
    }

    fun setPreviewAsCustomer(preview: Boolean) {
        _session.update { it.copy(previewAsCustomer = preview) }
    }

    fun logout() {
        _session.update {
            it.copy(
                loggedInUser = null,
                previewAsCustomer = false,
                activeAdminTab = AdminTab.DASHBOARD
            )
        }
        resetAiGreeting("customer", "Ka-Suki")
    }

    suspend fun loginStaffOrPartner(username: String, password: String, roleGroup: String): Result<LoggedInUser> {
        val result = repository.loginStaffOrPartner(username, password, roleGroup)
        result.onSuccess { user ->
            _session.update {
                it.copy(
                    loggedInUser = user,
                    activeCashier = if (user.role == "admin") user.name else it.activeCashier,
                    previewAsCustomer = false
                )
            }
            resetAiGreeting(user.role, user.name)
        }
        return result
    }

    suspend fun loginCustomer(nameOrPhone: String, pin: String): Result<LoggedInUser> {
        val result = repository.loginCustomer(nameOrPhone, pin)
        result.onSuccess { user ->
            _session.update { it.copy(loggedInUser = user, previewAsCustomer = false) }
            resetAiGreeting("customer", user.name)
        }
        return result
    }

    suspend fun registerCustomer(
        name: String,
        phone: String,
        pin: String,
        barangay: String,
        address: String,
        lat: Double,
        lng: Double,
        storeImageUri: String?
    ): Result<CustomerRecord> {
        val res = repository.registerCustomer(name, phone, pin, barangay, address, lat, lng, storeImageUri)
        res.onSuccess { cust ->
            val user = LoggedInUser(username = cust.phone, role = "customer", name = cust.name)
            _session.update { it.copy(loggedInUser = user, snackbarMessage = "Welcome, ${cust.name}!") }
            resetAiGreeting("customer", cust.name)
        }
        return res
    }

    fun updateCustomerProfile(
        name: String,
        phone: String,
        barangay: String,
        address: String,
        lat: Double,
        lng: Double,
        newPin: String?,
        storeImageUri: String?
    ) {
        viewModelScope.launch {
            val res = repository.updateCustomerProfile(name, phone, barangay, address, lat, lng, newPin, storeImageUri)
            showMessage(res.fold({ "Customer profile saved." }, { it.message ?: "Unable to update profile." }))
        }
    }

    // ─── Products & Inventory ───
    fun saveProduct(product: Product) {
        viewModelScope.launch {
            repository.saveProduct(product)
            showMessage("Saved ${product.name}.")
        }
    }

    fun adjustStock(productId: String, branchName: String, delta: Int) {
        viewModelScope.launch {
            repository.adjustStock(productId, branchName, delta)
        }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch {
            repository.deleteProduct(id)
            showMessage("Product deleted.")
        }
    }

    fun loadStarterCatalog() {
        viewModelScope.launch {
            repository.loadSilangStarterCatalog()
            showMessage("Loaded Silang Mini Mart starter catalog.")
        }
    }

    fun saveCategories(list: List<String>) {
        viewModelScope.launch { repository.saveCategories(list) }
    }

    fun renameCategory(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.renameCategory(oldName, newName, uiState.value.categories)
        }
    }

    fun setDefaultStockLimit(limit: Int) {
        viewModelScope.launch {
            repository.setDefaultStockLimit(limit)
            showMessage("Stock limit updated to $limit for all products.")
        }
    }

    // ─── POS ───
    suspend fun recordPosCheckout(transaction: Transaction, cartItems: List<CartItem>): Result<Unit> {
        val result = repository.recordPosCheckout(transaction, cartItems)
        result.onFailure { showMessage(it.message ?: "Checkout failed.") }
        return result
    }

    // ─── Deliveries ───
    suspend fun createDelivery(delivery: Delivery, deductUnitsByProductId: Map<String, Int>): Result<Unit> {
        val result = repository.createDelivery(delivery, deductUnitsByProductId)
        result.onSuccess { showMessage("Order ${delivery.id} created.") }
        result.onFailure { showMessage(it.message ?: "Failed to create delivery.") }
        return result
    }

    fun updateDelivery(delivery: Delivery) {
        viewModelScope.launch {
            repository.updateDelivery(delivery)
        }
    }

    fun verifyDeliveryPayment(deliveryId: String, approved: Boolean, reason: String? = null) {
        viewModelScope.launch {
            repository.verifyDeliveryPayment(deliveryId, approved, reason)
            showMessage(if (approved) "Payment marked as Paid." else "Payment marked as Failed.")
        }
    }

    // ─── Services ───
    fun addServiceTransaction(service: ServiceTransaction) {
        viewModelScope.launch {
            repository.addServiceTransaction(service)
        }
    }

    // ─── Supplier Deliveries ───
    fun createSupplierDelivery(delivery: SupplierDelivery) {
        viewModelScope.launch {
            repository.createSupplierDelivery(delivery)
            showMessage("Supplier delivery ${delivery.id} submitted.")
        }
    }

    fun receiveSupplierDelivery(deliveryId: String, receivedBy: String) {
        viewModelScope.launch {
            repository.receiveSupplierDelivery(deliveryId, receivedBy)
            showMessage("Stock received from $deliveryId.")
        }
    }

    // ─── Branches & Users ───
    fun saveBranch(branch: BranchRecord, previousName: String? = null) {
        viewModelScope.launch {
            repository.saveBranch(branch, previousName)
            showMessage("Saved branch ${branch.name}.")
        }
    }

    fun deleteBranch(id: Long) {
        viewModelScope.launch {
            repository.deleteBranch(id)
            showMessage("Branch removed.")
        }
    }

    suspend fun createUser(name: String, username: String, password: String, role: String): Result<Unit> {
        val res = repository.createUser(name, username, password, role)
        res.onSuccess { showMessage("Created $role account @$username.") }
        return res
    }

    fun deleteUser(id: Long) {
        viewModelScope.launch {
            repository.deleteUser(id)
            showMessage("User account removed.")
        }
    }

    fun resetOperationalData() {
        viewModelScope.launch {
            repository.resetOperationalData()
            showMessage("Operational data cleared.")
        }
    }

    // ─── Notifications ───
    fun markNotificationRead(id: String) = viewModelScope.launch { repository.markNotificationRead(id) }
    fun markAllNotificationsRead(role: String) = viewModelScope.launch { repository.markAllNotificationsRead(role) }
    fun deleteNotification(id: String) = viewModelScope.launch { repository.deleteNotification(id) }
    fun clearNotifications(role: String) = viewModelScope.launch { repository.clearNotifications(role) }

    // ─── AI Assistant ───
    private fun resetAiGreeting(role: String, name: String) {
        val greeting = when (role) {
            "admin" -> "Mabuhay, Manager $name! Ako si Ka-Sosyong AI. Handa akong tumulong sa stock alerts, analytics, at operasyon ng Silang Mini Mart."
            "rider" -> "Magandang araw, Kuya Rider $name! Ako si Kuya Dispatch. Magtanong tungkol sa ruta, traffic sa Silang, o delivery safety."
            else -> "Kumusta po, $name! Ako po si Ate Digy, ang inyong shopping helper. Mag-type o gumamit ng mic para maghanap o mag-order!"
        }
        _session.update {
            it.copy(
                aiMessages = listOf(
                    AiMessage(id = "greet-${Instant.now().toEpochMilli()}", isUser = false, text = greeting)
                )
            )
        }
    }

    fun sendAiMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val user = uiState.value.loggedInUser
        val role = user?.role ?: "customer"
        val name = user?.name ?: "Ka-Suki"

        val userMsg = AiMessage(id = "u-${System.currentTimeMillis()}", isUser = true, text = trimmed)
        _session.update { it.copy(aiMessages = it.aiMessages + userMsg, isAiLoading = true) }

        viewModelScope.launch {
            val reply = aiRepository.generateReply(
                role = role,
                userName = name,
                userMessage = trimmed,
                products = uiState.value.products,
                activeBranch = uiState.value.currentBranch
            )
            val botMsg = AiMessage(
                id = "a-${System.currentTimeMillis()}",
                isUser = false,
                text = reply.text,
                cartActions = reply.cartActions
            )
            _session.update { it.copy(aiMessages = it.aiMessages + botMsg, isAiLoading = false) }
        }
    }

    fun markAiCartActionDone(messageId: String) {
        _session.update { state ->
            state.copy(
                aiMessages = state.aiMessages.map {
                    if (it.id == messageId) it.copy(cartActionDone = true) else it
                }
            )
        }
    }

    private data class StoreDataPart1(
        val branches: List<BranchRecord>,
        val users: List<UserRecord>,
        val customers: List<CustomerRecord>,
        val products: List<Product>,
        val transactions: List<Transaction>
    )

    private data class StoreDataPart2(
        val deliveries: List<Delivery>,
        val services: List<ServiceTransaction>,
        val supplierDeliveries: List<SupplierDelivery>,
        val notifications: List<AppNotification>,
        val categories: List<String>,
        val defaultStockLimit: Int
    )

    class Factory(
        private val repository: SilangRepository,
        private val aiRepository: AiRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SilangViewModel(repository, aiRepository) as T
        }
    }
}
