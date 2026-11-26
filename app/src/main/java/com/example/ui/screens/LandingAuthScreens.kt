package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.network.LocationSearchResult
import com.example.data.network.RouteNetworkHelper
import com.example.ui.components.RouteMapPreview
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    branchCoordinates: Map<String, Pair<Double, Double>>,
    onLoginStaffOrPartner: suspend (username: String, password: String, roleGroup: String) -> Result<*>,
    onLoginCustomer: suspend (nameOrPhone: String, pin: String) -> Result<*>,
    onRegisterCustomer: suspend (
        name: String,
        phone: String,
        pin: String,
        barangay: String,
        address: String,
        lat: Double,
        lng: Double,
        storeImageUri: String?
    ) -> Result<*>
) {
    var authModalOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("customer") } // customer, admin, rider, supplier
    var isRegisterMode by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Storefront background image
        Image(
            painter = painterResource(id = R.drawable.img_store_background),
            contentDescription = stringResource(R.string.cd_store_background),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Atmospheric gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.72f),
                            SilangSlate950.copy(alpha = 0.68f),
                            SilangSlate950.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Brand Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_store_logo),
                            contentDescription = stringResource(R.string.cd_store_logo),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    }
                    Column {
                        Text("SILANG MINI MART", style = MaterialTheme.typography.titleMedium, color = SilangWhite)
                        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.labelMedium, color = SilangOrangeBright)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = SilangGreen.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SilangGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SilangGreen))
                        Text(stringResource(R.string.system_online), style = MaterialTheme.typography.labelMedium, color = SilangWhite)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Hero Headline
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.welcome_headline),
                    style = MaterialTheme.typography.displaySmall,
                    color = SilangWhite
                )
                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Primary Role Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        selectedTab = "customer"
                        isRegisterMode = false
                        authModalOpen = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("landing_customer_login_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SilangOrange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.customer_login), fontWeight = FontWeight.ExtraBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedTab = "customer"
                            isRegisterMode = true
                            authModalOpen = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("landing_register_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SilangWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.register_customer), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            selectedTab = "supplier"
                            isRegisterMode = false
                            authModalOpen = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("landing_supplier_login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SilangGreen),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.supplier_login), fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            selectedTab = "admin"
                            isRegisterMode = false
                            authModalOpen = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("landing_staff_login_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SilangBlue,
                            contentColor = SilangWhite
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.staff_pos_login), fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            selectedTab = "rider"
                            isRegisterMode = false
                            authModalOpen = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("landing_rider_login_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SilangSlate800,
                            contentColor = SilangWhite
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.TwoWheeler, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.rider_login), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Feature Highlights
            Text("PORTAL MODULES", style = MaterialTheme.typography.labelMedium, color = SilangOrangeBright)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FeatureTile(
                        icon = Icons.Default.PointOfSale,
                        title = "POS Checkout",
                        subtitle = "Tingi/Sako variants, Senior/PWD 20%, QR Receipt",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureTile(
                        icon = Icons.Default.Inventory2,
                        title = "Stock & Expiry",
                        subtitle = "Multi-branch levels, batch traceability alerts",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FeatureTile(
                        icon = Icons.Default.LocationOn,
                        title = "GPS Dispatch",
                        subtitle = "Live route pins, payment verification & POD",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureTile(
                        icon = Icons.Default.PhoneAndroid,
                        title = "E-Load & Bills",
                        subtitle = "Smart/Globe/DITO, Meralco, Palawan & GCash",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (authModalOpen) {
        AuthModalSheet(
            initialTab = selectedTab,
            initialRegister = isRegisterMode,
            branchCoordinates = branchCoordinates,
            onDismiss = { authModalOpen = false },
            onLoginStaffOrPartner = onLoginStaffOrPartner,
            onLoginCustomer = onLoginCustomer,
            onRegisterCustomer = onRegisterCustomer
        )
    }
}

@Composable
private fun FeatureTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = SilangOrangeBright, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = SilangWhite)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthModalSheet(
    initialTab: String,
    initialRegister: Boolean,
    branchCoordinates: Map<String, Pair<Double, Double>>,
    onDismiss: () -> Unit,
    onLoginStaffOrPartner: suspend (String, String, String) -> Result<*>,
    onLoginCustomer: suspend (String, String) -> Result<*>,
    onRegisterCustomer: suspend (String, String, String, String, String, Double, Double, String?) -> Result<*>
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var roleTab by remember { mutableStateOf(initialTab) }
    var registerMode by remember { mutableStateOf(initialRegister && initialTab == "customer") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Login fields
    var identifier by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    // Customer register fields
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regPin by remember { mutableStateOf("") }
    var regBarangay by remember { mutableStateOf("Brgy. Sabutan") }
    var regAddress by remember { mutableStateOf("") }
    val defaultCoord = branchCoordinates.values.firstOrNull() ?: (14.2235 to 120.9739)
    var regLat by remember { mutableStateOf(defaultCoord.first) }
    var regLng by remember { mutableStateOf(defaultCoord.second) }
    var regStoreImageUri by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<LocationSearchResult>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) regStoreImageUri = uri.toString()
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SilangSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (registerMode) "Register Customer Account" else "Silang Portal Sign In",
                style = MaterialTheme.typography.headlineSmall
            )

            // Role Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "customer" to "Customer",
                    "admin" to "Staff",
                    "rider" to "Rider",
                    "supplier" to "Supplier"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = roleTab == key,
                        onClick = {
                            roleTab = key
                            if (key != "customer") registerMode = false
                            errorMsg = null
                        },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (roleTab == "customer") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !registerMode,
                        onClick = { registerMode = false; errorMsg = null },
                        label = { Text("Login") }
                    )
                    FilterChip(
                        selected = registerMode,
                        onClick = { registerMode = true; errorMsg = null },
                        label = { Text("New Account") }
                    )
                }
            } else {
                val hint = when (roleTab) {
                    "admin" -> "Default Staff: admin / admin  or  lorna / 1234"
                    "rider" -> "Default Rider: rider / 1234"
                    else -> "Default Supplier: supplier / 1234"
                }
                Surface(color = SilangBlueContainer, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = SilangBlueDark,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }

            if (errorMsg != null) {
                Surface(color = SilangRedContainer, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = SilangRed,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }

            if (!registerMode) {
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text(if (roleTab == "customer") "Customer Name or Phone" else "Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auth_identifier_input")
                )

                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(if (roleTab == "customer") "PIN" else "Password") },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (roleTab == "customer") KeyboardType.NumberPassword else KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle password visibility")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("auth_secret_input")
                )

                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        scope.launch {
                            isSubmitting = true
                            errorMsg = null
                            val res = if (roleTab == "customer") {
                                onLoginCustomer(identifier, secret)
                            } else {
                                onLoginStaffOrPartner(identifier, secret, roleTab)
                            }
                            isSubmitting = false
                            res.onSuccess { onDismiss() }
                                .onFailure { errorMsg = it.message ?: "Login failed." }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("auth_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SilangWhite, strokeWidth = 2.dp)
                    } else {
                        Text("Sign In", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                OutlinedTextField(
                    value = regName,
                    onValueChange = { regName = it },
                    label = { Text("Full Name / Store Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("register_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = regPhone,
                        onValueChange = { regPhone = it.filter(Char::isDigit).take(11) },
                        label = { Text("Phone (11 digits) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("register_phone_input")
                    )
                    OutlinedTextField(
                        value = regPin,
                        onValueChange = { regPin = it.filter(Char::isDigit).take(6) },
                        label = { Text("4-6 Digit PIN *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("register_pin_input")
                    )
                }
                OutlinedTextField(
                    value = regBarangay,
                    onValueChange = { regBarangay = it },
                    label = { Text("Barangay (e.g. Brgy. Sabutan)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = regAddress,
                    onValueChange = { regAddress = it },
                    label = { Text("House / Street / Landmark *") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Location Search via Photon
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Silang Location") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                searchResults = RouteNetworkHelper.searchLocations(searchQuery)
                            }
                        },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search location")
                    }
                }
                searchResults.forEach { hit ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                regLat = hit.lat
                                regLng = hit.lng
                                if (hit.barangay.isNotBlank()) regBarangay = hit.barangay
                                regAddress = hit.displayName
                                searchResults = emptyList()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = SilangWhite
                    ) {
                        Text(hit.displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                }

                RouteMapPreview(
                    branchName = "Poblacion",
                    branchLat = defaultCoord.first,
                    branchLng = defaultCoord.second,
                    customerName = regName.ifBlank { "Your Pin" },
                    customerLat = regLat,
                    customerLng = regLng,
                    interactivePinSelection = true,
                    onMapCoordinateSelected = { lat, lng ->
                        regLat = lat
                        regLng = lng
                    }
                )

                // Optional Store/House Photo via Zero-Permission Android Photo Picker
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (regStoreImageUri == null) "Store / Gate Photo (Optional)" else "Change Photo")
                    }
                    if (regStoreImageUri != null) {
                        AsyncImage(
                            model = regStoreImageUri,
                            contentDescription = "Selected store photo",
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        scope.launch {
                            isSubmitting = true
                            errorMsg = null
                            val res = onRegisterCustomer(
                                regName, regPhone, regPin, regBarangay, regAddress, regLat, regLng, regStoreImageUri
                            )
                            isSubmitting = false
                            res.onSuccess { onDismiss() }
                                .onFailure { errorMsg = it.message ?: "Registration failed." }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("register_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
                ) {
                    Text("Create Customer Account", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
