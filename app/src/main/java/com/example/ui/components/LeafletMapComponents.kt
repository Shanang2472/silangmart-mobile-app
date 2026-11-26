package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BranchRecord
import com.example.data.model.Delivery
import com.example.data.model.haversineDistanceKm
import com.example.data.network.RouteNetworkHelper
import com.example.ui.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.max

private enum class LeafletTileMode(val jsKey: String, val label: String) {
    STREET("esri_street", "Street"),
    OSM("osm", "OSM"),
    SATELLITE("satellite", "Satellite");

    fun next(): LeafletTileMode = when (this) {
        STREET -> OSM
        OSM -> SATELLITE
        SATELLITE -> STREET
    }
}

@Serializable
private data class LatLngDto(val lat: Double, val lng: Double)

@Serializable
private data class RouteMapPayload(
    val branchName: String,
    val branchLat: Double,
    val branchLng: Double,
    val customerName: String,
    val customerLat: Double,
    val customerLng: Double,
    val driverName: String? = null,
    val driverLat: Double? = null,
    val driverLng: Double? = null,
    val riderMovedFromStore: Boolean = false,
    val routePoints: List<LatLngDto> = emptyList(),
    val storeRoutePoints: List<LatLngDto> = emptyList(),
    val interactivePinSelection: Boolean = false,
    val tileMode: String = "esri_street"
)

@Serializable
private data class FleetBranchDto(
    val name: String,
    val lat: Double,
    val lng: Double
)

@Serializable
private data class FleetDeliveryDto(
    val id: String,
    val customerName: String,
    val status: String,
    val branch: String,
    val branchLat: Double,
    val branchLng: Double,
    val lat: Double,
    val lng: Double,
    val driverName: String,
    val driverLat: Double? = null,
    val driverLng: Double? = null,
    val isSelected: Boolean = false,
    val routePoints: List<LatLngDto> = emptyList()
)

@Serializable
private data class FleetMapPayload(
    val branches: List<FleetBranchDto>,
    val deliveries: List<FleetDeliveryDto>,
    val tileMode: String = "esri_street"
)

private val leafletJson = Json { encodeDefaults = true }

private class LeafletJsBridge(
    private val onReady: () -> Unit,
    private val onMapTapped: ((Double, Double) -> Unit)? = null,
    private val onDeliverySelected: ((String) -> Unit)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onLeafletReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onMapTap(lat: Double, lng: Double) {
        mainHandler.post { onMapTapped?.invoke(lat, lng) }
    }

    @JavascriptInterface
    fun onSelectDelivery(deliveryId: String) {
        mainHandler.post { onDeliverySelected?.invoke(deliveryId) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun LeafletWebMapView(
    mode: String,
    payloadJson: String,
    tileMode: LeafletTileMode,
    fitTrigger: Int,
    zoomDeltaTrigger: Pair<Int, Int>,
    centerLatLngTrigger: Triple<Int, Double, Double>?,
    onMapCoordinateSelected: ((Double, Double) -> Unit)? = null,
    onDeliverySelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }
    var isLeafletReady by remember { mutableStateOf(false) }

    val latestTapCallback by rememberUpdatedState(onMapCoordinateSelected)
    val latestSelectCallback by rememberUpdatedState(onDeliverySelected)

    val bridge = remember {
        LeafletJsBridge(
            onReady = { isLeafletReady = true },
            onMapTapped = { lat, lng -> latestTapCallback?.invoke(lat, lng) },
            onDeliverySelected = { id -> latestSelectCallback?.invoke(id) }
        )
    }

    LaunchedEffect(isPageLoaded, isLeafletReady, payloadJson, mode) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isPageLoaded || !isLeafletReady) return@LaunchedEffect
        val fn = if (mode == "fleet") "window.renderFleetState" else "window.renderRouteState"
        wv.evaluateJavascript("$fn($payloadJson);", null)
    }

    LaunchedEffect(isPageLoaded, isLeafletReady, tileMode) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isPageLoaded || !isLeafletReady) return@LaunchedEffect
        wv.evaluateJavascript("window.setBaseLayer('${tileMode.jsKey}');", null)
    }

    LaunchedEffect(isPageLoaded, isLeafletReady, fitTrigger) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isPageLoaded || !isLeafletReady || fitTrigger == 0) return@LaunchedEffect
        wv.evaluateJavascript("window.fitCurrentBounds();", null)
    }

    LaunchedEffect(isPageLoaded, isLeafletReady, zoomDeltaTrigger) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!isPageLoaded || !isLeafletReady || zoomDeltaTrigger.first == 0) return@LaunchedEffect
        if (zoomDeltaTrigger.second > 0) {
            wv.evaluateJavascript("window.mapZoomIn();", null)
        } else {
            wv.evaluateJavascript("window.mapZoomOut();", null)
        }
    }

    LaunchedEffect(isPageLoaded, isLeafletReady, centerLatLngTrigger) {
        val wv = webViewRef ?: return@LaunchedEffect
        val trigger = centerLatLngTrigger ?: return@LaunchedEffect
        if (!isPageLoaded || !isLeafletReady || trigger.first == 0) return@LaunchedEffect
        wv.evaluateJavascript("window.panToPin(${trigger.second}, ${trigger.third});", null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                removeJavascriptInterface("SilangMapBridge")
                destroy()
            }
            webViewRef = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.parseColor("#E2E8F0"))
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString =
                        "SilangMiniMartAndroid/1.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }

                addJavascriptInterface(bridge, "SilangMapBridge")

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        return url.startsWith("http://") || url.startsWith("https://")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        isPageLoaded = true
                        view?.evaluateJavascript("window.initSilangLeaflet('$mode');", null)
                    }
                }

                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }

                loadDataWithBaseURL(
                    "https://silangminimart.local/",
                    LEAFLET_HTML,
                    "text/html",
                    "UTF-8",
                    null
                )
                webViewRef = this
            }
        }
    )
}

@Composable
fun RouteMapPreview(
    branchName: String,
    branchLat: Double,
    branchLng: Double,
    customerName: String,
    customerLat: Double,
    customerLng: Double,
    driverName: String? = null,
    driverLat: Double? = null,
    driverLng: Double? = null,
    interactivePinSelection: Boolean = false,
    onMapCoordinateSelected: ((Double, Double) -> Unit)? = null,
    title: String? = null,
    compactHeightDp: Int = 220,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var routePoints by remember(branchLat, branchLng, customerLat, customerLng, driverLat, driverLng) {
        mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    }
    var storeRoutePoints by remember(branchLat, branchLng, customerLat, customerLng) {
        mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    }
    var tileMode by remember { mutableStateOf(LeafletTileMode.STREET) }
    var showFullScreenMap by remember { mutableStateOf(false) }

    var compactFitTrigger by remember { mutableIntStateOf(0) }
    var compactZoomTrigger by remember { mutableStateOf(0 to 0) }
    var fullFitTrigger by remember { mutableIntStateOf(0) }
    var fullZoomTrigger by remember { mutableStateOf(0 to 0) }

    var lastCenteredLat by remember { mutableDoubleStateOf(customerLat) }
    var lastCenteredLng by remember { mutableDoubleStateOf(customerLng) }
    var panTrigger by remember { mutableStateOf<Triple<Int, Double, Double>?>(null) }

    val startLat = driverLat ?: branchLat
    val startLng = driverLng ?: branchLng
    val distanceKm = remember(startLat, startLng, customerLat, customerLng) {
        haversineDistanceKm(startLat, startLng, customerLat, customerLng)
    }

    LaunchedEffect(branchLat, branchLng, customerLat, customerLng) {
        storeRoutePoints = RouteNetworkHelper.fetchDrivingRoute(branchLat, branchLng, customerLat, customerLng)
    }

    LaunchedEffect(startLat, startLng, customerLat, customerLng) {
        routePoints = RouteNetworkHelper.fetchDrivingRoute(startLat, startLng, customerLat, customerLng)
    }

    LaunchedEffect(branchLat, branchLng, customerLat, customerLng, driverLat, driverLng, interactivePinSelection) {
        if (!interactivePinSelection) {
            compactFitTrigger++
            if (showFullScreenMap) fullFitTrigger++
        } else if (abs(customerLat - lastCenteredLat) > 0.00001 || abs(customerLng - lastCenteredLng) > 0.00001) {
            lastCenteredLat = customerLat
            lastCenteredLng = customerLng
            panTrigger = Triple((panTrigger?.first ?: 0) + 1, customerLat, customerLng)
        }
    }

    val riderMovedFromStore = remember(branchLat, branchLng, driverLat, driverLng) {
        driverLat != null && driverLng != null &&
            haversineDistanceKm(branchLat, branchLng, driverLat, driverLng) > 0.03
    }

    val payloadJson = remember(
        branchName,
        branchLat,
        branchLng,
        customerName,
        customerLat,
        customerLng,
        driverName,
        driverLat,
        driverLng,
        riderMovedFromStore,
        routePoints,
        storeRoutePoints,
        interactivePinSelection,
        tileMode
    ) {
        leafletJson.encodeToString(
            RouteMapPayload(
                branchName = branchName,
                branchLat = branchLat,
                branchLng = branchLng,
                customerName = customerName,
                customerLat = customerLat,
                customerLng = customerLng,
                driverName = driverName,
                driverLat = driverLat,
                driverLng = driverLng,
                riderMovedFromStore = riderMovedFromStore,
                routePoints = routePoints.map { LatLngDto(it.first, it.second) },
                storeRoutePoints = storeRoutePoints.map { LatLngDto(it.first, it.second) },
                interactivePinSelection = interactivePinSelection,
                tileMode = tileMode.jsKey
            )
        )
    }

    @Composable
    fun RouteMapOverlayBox(
        mapModifier: Modifier,
        fitTrigger: Int,
        zoomTrigger: Pair<Int, Int>,
        onZoomIn: () -> Unit,
        onZoomOut: () -> Unit,
        onRecenter: () -> Unit
    ) {
        Box(modifier = mapModifier) {
            LeafletWebMapView(
                mode = "route",
                payloadJson = payloadJson,
                tileMode = tileMode,
                fitTrigger = fitTrigger,
                zoomDeltaTrigger = zoomTrigger,
                centerLatLngTrigger = if (interactivePinSelection) panTrigger else null,
                onMapCoordinateSelected = onMapCoordinateSelected,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                onClick = { tileMode = tileMode.next() },
                shape = RoundedCornerShape(10.dp),
                color = SilangWhite.copy(alpha = 0.94f),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("map_tile_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Switch map layer", tint = SilangBlue, modifier = Modifier.size(16.dp))
                    Text(
                        text = tileMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = SilangSlate900,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onZoomIn,
                    containerColor = SilangWhite,
                    contentColor = SilangSlate900,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
                }
                SmallFloatingActionButton(
                    onClick = onZoomOut,
                    containerColor = SilangWhite,
                    contentColor = SilangSlate900,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
                }
                SmallFloatingActionButton(
                    onClick = onRecenter,
                    containerColor = SilangWhite,
                    contentColor = SilangBlue,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Center on pin", modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SilangSlate950.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(SilangBlue, branchName)
                if (!driverName.isNullOrBlank()) LegendDot(SilangOrange, "Rider")
                LegendDot(SilangGreen, customerName.take(12))
                LegendLine(SilangOrange, "Path")
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SilangSlate900),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = SilangOrangeBright, modifier = Modifier.size(18.dp))
                    Text(
                        text = title ?: if (interactivePinSelection) "Tap Map to Adjust GPS Pin" else "GPS Route Map (Leaflet)",
                        style = MaterialTheme.typography.titleSmall,
                        color = SilangWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${"%.2f".format(distanceKm)} km • ~${max(3, (distanceKm * 4).toInt())}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilangOrangeBright
                    )
                    IconButton(
                        onClick = { showFullScreenMap = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("expand_map_button")
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Expand map", tint = SilangWhite, modifier = Modifier.size(20.dp))
                    }
                    if (onClose != null) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("close_route_map_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Hide map", tint = SilangSlate400, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            RouteMapOverlayBox(
                mapModifier = Modifier
                    .fillMaxWidth()
                    .height(compactHeightDp.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SilangSlate800, RoundedCornerShape(12.dp)),
                fitTrigger = compactFitTrigger,
                zoomTrigger = compactZoomTrigger,
                onZoomIn = { compactZoomTrigger = (compactZoomTrigger.first + 1) to 1 },
                onZoomOut = { compactZoomTrigger = (compactZoomTrigger.first + 1) to -1 },
                onRecenter = {
                    if (interactivePinSelection) {
                        panTrigger = Triple((panTrigger?.first ?: 0) + 1, customerLat, customerLng)
                    } else {
                        compactFitTrigger++
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pin: ${"%.5f".format(customerLat)}, ${"%.5f".format(customerLng)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SilangSlate400
                )
                TextButton(
                    onClick = {
                        val uri = Uri.parse("geo:$customerLat,$customerLng?q=$customerLat,$customerLng(${Uri.encode(customerName)})")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = SilangOrangeBright, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open in Maps", color = SilangOrangeBright, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showFullScreenMap) {
        Dialog(
            onDismissRequest = { showFullScreenMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                color = SilangSlate950
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (interactivePinSelection) "Tap Map to Adjust GPS Pin" else "$branchName → $customerName",
                                style = MaterialTheme.typography.titleMedium,
                                color = SilangWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${"%.2f".format(distanceKm)} km • ~${max(3, (distanceKm * 4).toInt())} mins • Leaflet Map",
                                style = MaterialTheme.typography.labelSmall,
                                color = SilangOrangeBright
                            )
                        }
                        IconButton(
                            onClick = { showFullScreenMap = false },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("close_fullscreen_map_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close map", tint = SilangWhite)
                        }
                    }

                    if (interactivePinSelection) {
                        Surface(
                            color = SilangBlue.copy(alpha = 0.22f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = SilangOrangeBright, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Tap anywhere on the street map to set your exact delivery pin.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SilangWhite
                                )
                            }
                        }
                    }

                    RouteMapOverlayBox(
                        mapModifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        fitTrigger = fullFitTrigger,
                        zoomTrigger = fullZoomTrigger,
                        onZoomIn = { fullZoomTrigger = (fullZoomTrigger.first + 1) to 1 },
                        onZoomOut = { fullZoomTrigger = (fullZoomTrigger.first + 1) to -1 },
                        onRecenter = {
                            if (interactivePinSelection) {
                                panTrigger = Triple((panTrigger?.first ?: 0) + 1, customerLat, customerLng)
                            } else {
                                fullFitTrigger++
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SilangSlate900)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pin: ${"%.5f".format(customerLat)}, ${"%.5f".format(customerLng)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilangSlate400
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    val uri = Uri.parse("geo:$customerLat,$customerLng?q=$customerLat,$customerLng(${Uri.encode(customerName)})")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    runCatching { context.startActivity(intent) }
                                }
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, tint = SilangOrangeBright, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Maps App", color = SilangOrangeBright)
                            }
                            Button(
                                onClick = { showFullScreenMap = false },
                                colors = ButtonDefaults.buttonColors(containerColor = SilangGreen),
                                modifier = Modifier.testTag("confirm_fullscreen_map_button")
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FleetDeliveryMapCard(
    branches: List<BranchRecord>,
    deliveries: List<Delivery>,
    selectedDeliveryId: String?,
    onSelectDelivery: (Delivery) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tileMode by remember { mutableStateOf(LeafletTileMode.STREET) }
    var showFullScreen by remember { mutableStateOf(false) }

    var compactFitTrigger by remember { mutableIntStateOf(0) }
    var compactZoomTrigger by remember { mutableStateOf(0 to 0) }
    var fullFitTrigger by remember { mutableIntStateOf(0) }
    var fullZoomTrigger by remember { mutableStateOf(0 to 0) }

    val activeDeliveries = remember(deliveries) {
        deliveries.filter { it.status == "Pending" || it.status == "Preparing" || it.status == "Out for Delivery" }
    }
    var fleetRoutes by remember { mutableStateOf<Map<String, List<Pair<Double, Double>>>>(emptyMap()) }

    LaunchedEffect(activeDeliveries, branches) {
        val branchCoords = branches.associate { it.name to (it.lat to it.lng) }
        val updated = mutableMapOf<String, List<Pair<Double, Double>>>()
        activeDeliveries.forEach { d ->
            val b = branchCoords[d.branch] ?: (14.2235 to 120.9739)
            val sLat = d.driverLat ?: b.first
            val sLng = d.driverLng ?: b.second
            val pts = RouteNetworkHelper.fetchDrivingRoute(sLat, sLng, d.lat, d.lng)
            if (pts.size >= 2) updated[d.id] = pts
        }
        fleetRoutes = updated
    }

    val payloadJson = remember(branches, activeDeliveries, selectedDeliveryId, fleetRoutes, tileMode) {
        val branchCoords = branches.associate { it.name to (it.lat to it.lng) }
        val dto = FleetMapPayload(
            branches = branches.filter { it.isActive }.map { FleetBranchDto(it.name, it.lat, it.lng) },
            deliveries = activeDeliveries.map { d ->
                val b = branchCoords[d.branch] ?: (14.2235 to 120.9739)
                FleetDeliveryDto(
                    id = d.id,
                    customerName = d.customerName,
                    status = d.status,
                    branch = d.branch,
                    branchLat = b.first,
                    branchLng = b.second,
                    lat = d.lat,
                    lng = d.lng,
                    driverName = d.driverName,
                    driverLat = d.driverLat,
                    driverLng = d.driverLng,
                    isSelected = d.id == selectedDeliveryId,
                    routePoints = (fleetRoutes[d.id] ?: emptyList()).map { LatLngDto(it.first, it.second) }
                )
            },
            tileMode = tileMode.jsKey
        )
        leafletJson.encodeToString(dto)
    }

    val latestSelect by rememberUpdatedState(onSelectDelivery)

    @Composable
    fun FleetMapContent(
        mapModifier: Modifier,
        fitTrigger: Int,
        zoomTrigger: Pair<Int, Int>,
        onZoomIn: () -> Unit,
        onZoomOut: () -> Unit,
        onRecenter: () -> Unit
    ) {
        Box(modifier = mapModifier) {
            LeafletWebMapView(
                mode = "fleet",
                payloadJson = payloadJson,
                tileMode = tileMode,
                fitTrigger = fitTrigger,
                zoomDeltaTrigger = zoomTrigger,
                centerLatLngTrigger = null,
                onDeliverySelected = { deliveryId ->
                    activeDeliveries.firstOrNull { it.id == deliveryId }?.let { latestSelect(it) }
                },
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                onClick = { tileMode = tileMode.next() },
                shape = RoundedCornerShape(10.dp),
                color = SilangWhite.copy(alpha = 0.94f),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("map_tile_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Switch map layer", tint = SilangBlue, modifier = Modifier.size(16.dp))
                    Text(tileMode.label, style = MaterialTheme.typography.labelSmall, color = SilangSlate900, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onZoomIn,
                    containerColor = SilangWhite,
                    contentColor = SilangSlate900,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
                }
                SmallFloatingActionButton(
                    onClick = onZoomOut,
                    containerColor = SilangWhite,
                    contentColor = SilangSlate900,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
                }
                SmallFloatingActionButton(
                    onClick = onRecenter,
                    containerColor = SilangWhite,
                    contentColor = SilangBlue,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Fit all pins", modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SilangSlate950.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(SilangBlue, "Branches")
                LegendDot(SilangOrange, "Riders")
                LegendDot(SilangGreen, "Active (${activeDeliveries.size})")
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SilangSlate900),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = SilangOrangeBright, modifier = Modifier.size(18.dp))
                    Text("Live Fleet Map (${activeDeliveries.size} active)", style = MaterialTheme.typography.titleSmall, color = SilangWhite)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showFullScreen = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Expand fleet map", tint = SilangWhite, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Close, contentDescription = "Hide fleet map", tint = SilangSlate400, modifier = Modifier.size(20.dp))
                    }
                }
            }
            FleetMapContent(
                mapModifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SilangSlate800, RoundedCornerShape(12.dp)),
                fitTrigger = compactFitTrigger,
                zoomTrigger = compactZoomTrigger,
                onZoomIn = { compactZoomTrigger = (compactZoomTrigger.first + 1) to 1 },
                onZoomOut = { compactZoomTrigger = (compactZoomTrigger.first + 1) to -1 },
                onRecenter = { compactFitTrigger++ }
            )
            Text("Tap any green/orange pin on the Leaflet map to inspect its delivery route.", style = MaterialTheme.typography.labelSmall, color = SilangSlate400)
        }
    }

    if (showFullScreen) {
        Dialog(onDismissRequest = { showFullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = SilangSlate950) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Live Delivery Fleet (${activeDeliveries.size} active)", style = MaterialTheme.typography.titleMedium, color = SilangWhite)
                        IconButton(onClick = { showFullScreen = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Default.Close, contentDescription = "Close fleet map", tint = SilangWhite)
                        }
                    }
                    FleetMapContent(
                        mapModifier = Modifier.weight(1f).fillMaxWidth(),
                        fitTrigger = fullFitTrigger,
                        zoomTrigger = fullZoomTrigger,
                        onZoomIn = { fullZoomTrigger = (fullZoomTrigger.first + 1) to 1 },
                        onZoomOut = { fullZoomTrigger = (fullZoomTrigger.first + 1) to -1 },
                        onRecenter = { fullFitTrigger++ }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, color = SilangWhite, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LegendLine(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.width(12.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(text = label, color = SilangWhite, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val LEAFLET_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
  html, body, #map {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    background: #e2e8f0;
    overflow: hidden;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
  #map-status {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #334155;
    font-size: 13px;
    font-weight: 600;
    z-index: 500;
    pointer-events: none;
    background: #e2e8f0;
  }
  .silang-pin {
    width: 30px;
    height: 30px;
    border-radius: 50%;
    border: 2.5px solid #ffffff;
    box-shadow: 0 2px 6px rgba(15, 23, 42, 0.42);
    color: #ffffff;
    font-size: 13px;
    font-weight: 800;
    display: flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
  }
  .leaflet-control-attribution {
    font-size: 9px !important;
    background: rgba(255,255,255,0.75) !important;
    padding: 1px 4px !important;
  }
</style>
<script>
(function() {
  var cssUrls = [
    "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css",
    "https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css",
    "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css"
  ];
  var jsUrls = [
    "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js",
    "https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js",
    "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js"
  ];
  function loadCss(idx) {
    if (idx >= cssUrls.length) return;
    var link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = cssUrls[idx];
    link.onerror = function() { loadCss(idx + 1); };
    document.head.appendChild(link);
  }
  function loadJs(idx) {
    if (idx >= jsUrls.length) {
      var st = document.getElementById("map-status");
      if (st) st.textContent = "Unable to load Leaflet map library. Check connection.";
      return;
    }
    var script = document.createElement("script");
    script.src = jsUrls[idx];
    script.onload = function() {
      if (window._pendingInitMode && !window._leafletMap) {
        window.initSilangLeaflet(window._pendingInitMode);
      }
    };
    script.onerror = function() { loadJs(idx + 1); };
    document.head.appendChild(script);
  }
  loadCss(0);
  loadJs(0);
})();
</script>
</head>
<body>
<div id="map"></div>
<div id="map-status">Loading Leaflet map...</div>
<script>
window._leafletMap = null;
window._overlayGroup = null;
window._baseLayer = null;
window._currentTileMode = "esri_street";
window._appMode = "route";
window._lastPayload = null;
window._initialFramed = false;
window._osmFallbackActive = false;

function buildTileLayer(mode) {
  if (mode === "osm") {
    return L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap"
    });
  }
  if (mode === "satellite") {
    return L.tileLayer("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", {
      maxZoom: 19,
      attribution: "&copy; Esri"
    });
  }
  return L.tileLayer("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}", {
    maxZoom: 19,
    attribution: "&copy; Esri"
  });
}

window.setBaseLayer = function(mode) {
  if (!window._leafletMap || typeof L === "undefined") return;
  if (window._currentTileMode === mode && window._baseLayer) return;
  window._currentTileMode = mode;
  window._osmFallbackActive = false;
  if (window._baseLayer) {
    window._leafletMap.removeLayer(window._baseLayer);
  }
  var layer = buildTileLayer(mode);
  var errorCount = 0;
  layer.on("tileerror", function() {
    errorCount++;
    if (mode === "osm" && errorCount >= 3 && !window._osmFallbackActive) {
      window._osmFallbackActive = true;
      if (window._baseLayer) window._leafletMap.removeLayer(window._baseLayer);
      window._baseLayer = buildTileLayer("esri_street").addTo(window._leafletMap);
    }
  });
  window._baseLayer = layer.addTo(window._leafletMap);
};

window.initSilangLeaflet = function(mode) {
  window._pendingInitMode = mode;
  window._appMode = mode || "route";
  if (typeof L === "undefined") return;
  if (window._leafletMap) {
    window._leafletMap.invalidateSize({ animate: false });
    if (window.SilangMapBridge) window.SilangMapBridge.onLeafletReady();
    return;
  }

  var map = L.map("map", {
    center: [14.2235, 120.9739],
    zoom: 15,
    zoomControl: false,
    attributionControl: true
  });
  window._leafletMap = map;
  window._overlayGroup = L.layerGroup().addTo(map);
  window.setBaseLayer(window._currentTileMode);

  var statusEl = document.getElementById("map-status");
  if (statusEl) statusEl.style.display = "none";

  map.on("click", function(e) {
    if (window._appMode === "route" && window._lastPayload && window._lastPayload.interactivePinSelection) {
      if (window.SilangMapBridge) {
        window.SilangMapBridge.onMapTap(e.latlng.lat, e.latlng.lng);
      }
    }
  });

  if (typeof ResizeObserver !== "undefined") {
    new ResizeObserver(function() {
      if (window._leafletMap) window._leafletMap.invalidateSize({ animate: false });
    }).observe(document.getElementById("map"));
  }

  if (window.SilangMapBridge) {
    window.SilangMapBridge.onLeafletReady();
  }
};

function makePinIcon(bgColor, letter) {
  return L.divIcon({
    className: "",
    html: '<div class="silang-pin" style="background:' + bgColor + ';">' + letter + '</div>',
    iconSize: [30, 30],
    iconAnchor: [15, 15],
    popupAnchor: [0, -14]
  });
}

function escapeHtml(str) {
  return String(str || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function anchoredPath(sLat, sLng, eLat, eLng, waypoints) {
  var pts = [[sLat, sLng]];
  if (waypoints && waypoints.length >= 2) {
    for (var i = 0; i < waypoints.length; i++) {
      pts.push([waypoints[i].lat, waypoints[i].lng]);
    }
  }
  pts.push([eLat, eLng]);
  return pts;
}

window.fitCurrentBounds = function() {
  var map = window._leafletMap;
  var p = window._lastPayload;
  if (!map || !p) return;
  var pts = [];
  if (window._appMode === "fleet") {
    (p.branches || []).forEach(function(b) { pts.push([b.lat, b.lng]); });
    (p.deliveries || []).forEach(function(d) {
      pts.push([d.lat, d.lng]);
      if (d.driverLat != null && d.driverLng != null) pts.push([d.driverLat, d.driverLng]);
    });
  } else {
    if (p.interactivePinSelection) {
      map.setView([p.customerLat, p.customerLng], Math.max(map.getZoom(), 16), { animate: true });
      return;
    }
    pts.push([p.branchLat, p.branchLng]);
    pts.push([p.customerLat, p.customerLng]);
    if (p.driverLat != null && p.driverLng != null) pts.push([p.driverLat, p.driverLng]);
    (p.routePoints || []).forEach(function(pt) { pts.push([pt.lat, pt.lng]); });
  }
  if (pts.length >= 2) {
    var bounds = L.latLngBounds(pts);
    map.fitBounds(bounds, { padding: [36, 36], maxZoom: 17, animate: true });
  } else if (pts.length === 1) {
    map.setView(pts[0], 15, { animate: true });
  }
};

window.panToPin = function(lat, lng) {
  if (window._leafletMap) {
    window._leafletMap.panTo([lat, lng], { animate: true });
  }
};

window.mapZoomIn = function() {
  if (window._leafletMap) window._leafletMap.zoomIn();
};

window.mapZoomOut = function() {
  if (window._leafletMap) window._leafletMap.zoomOut();
};

window.renderRouteState = function(payload) {
  if (!window._leafletMap || !window._overlayGroup) return;
  window._lastPayload = payload;
  if (payload.tileMode) window.setBaseLayer(payload.tileMode);

  var group = window._overlayGroup;
  group.clearLayers();

  if (payload.riderMovedFromStore) {
    var basePts = anchoredPath(
      payload.branchLat, payload.branchLng,
      payload.customerLat, payload.customerLng,
      payload.storeRoutePoints
    );
    L.polyline(basePts, {
      color: "#2563EB",
      weight: 4,
      opacity: 0.85,
      dashArray: "8, 6"
    }).bindPopup(escapeHtml(payload.branchName) + " Branch &rarr; " + escapeHtml(payload.customerName)).addTo(group);
  }

  var startLat = payload.riderMovedFromStore ? payload.driverLat : payload.branchLat;
  var startLng = payload.riderMovedFromStore ? payload.driverLng : payload.branchLng;
  var activePts = anchoredPath(startLat, startLng, payload.customerLat, payload.customerLng, payload.routePoints);

  L.polyline(activePts, {
    color: "#ffffff",
    weight: 8,
    opacity: 0.95,
    lineCap: "round",
    lineJoin: "round"
  }).addTo(group);

  L.polyline(activePts, {
    color: "#F97316",
    weight: 4.5,
    opacity: 0.98,
    lineCap: "round",
    lineJoin: "round"
  }).addTo(group);

  L.marker([payload.branchLat, payload.branchLng], {
    icon: makePinIcon("#0052CC", "S")
  }).bindPopup("<b>" + escapeHtml(payload.branchName) + " Branch</b>").addTo(group);

  L.marker([payload.customerLat, payload.customerLng], {
    icon: makePinIcon("#16A34A", "C")
  }).bindPopup("<b>" + escapeHtml(payload.customerName) + "</b><br/>Drop-off Pin").addTo(group);

  if (payload.driverLat != null && payload.driverLng != null) {
    L.marker([payload.driverLat, payload.driverLng], {
      icon: makePinIcon("#F97316", "R")
    }).bindPopup("<b>" + escapeHtml(payload.driverName || "Rider") + "</b><br/>Live Rider GPS").addTo(group);
  }

  if (!window._initialFramed) {
    window._initialFramed = true;
    if (payload.interactivePinSelection) {
      window._leafletMap.setView([payload.customerLat, payload.customerLng], 16, { animate: false });
    } else {
      window.fitCurrentBounds();
    }
  }
};

window.renderFleetState = function(payload) {
  if (!window._leafletMap || !window._overlayGroup) return;
  window._lastPayload = payload;
  if (payload.tileMode) window.setBaseLayer(payload.tileMode);

  var group = window._overlayGroup;
  group.clearLayers();

  (payload.deliveries || []).forEach(function(d) {
    var startLat = d.driverLat != null ? d.driverLat : d.branchLat;
    var startLng = d.driverLng != null ? d.driverLng : d.branchLng;
    var pts = anchoredPath(startLat, startLng, d.lat, d.lng, d.routePoints);

    L.polyline(pts, {
      color: "#ffffff",
      weight: d.isSelected ? 8 : 5.5,
      opacity: 0.92
    }).addTo(group);

    L.polyline(pts, {
      color: d.isSelected ? "#DC2626" : "#F97316",
      weight: d.isSelected ? 5 : 3.2,
      opacity: 0.98
    }).addTo(group);
  });

  (payload.branches || []).forEach(function(b) {
    L.marker([b.lat, b.lng], {
      icon: makePinIcon("#0052CC", "S")
    }).bindPopup("<b>" + escapeHtml(b.name) + " Branch</b>").addTo(group);
  });

  (payload.deliveries || []).forEach(function(d) {
    var custMarker = L.marker([d.lat, d.lng], {
      icon: makePinIcon(d.isSelected ? "#DC2626" : "#16A34A", d.isSelected ? "★" : "C")
    }).bindPopup("<b>" + escapeHtml(d.id) + " &bull; " + escapeHtml(d.customerName) + "</b><br/>" + escapeHtml(d.status));
    custMarker.on("click", function() {
      if (window.SilangMapBridge) window.SilangMapBridge.onSelectDelivery(d.id);
    });
    custMarker.addTo(group);

    if (d.driverLat != null && d.driverLng != null && d.status === "Out for Delivery") {
      var riderMarker = L.marker([d.driverLat, d.driverLng], {
        icon: makePinIcon("#F97316", "R")
      }).bindPopup("<b>" + escapeHtml(d.driverName) + "</b> (" + escapeHtml(d.id) + ")");
      riderMarker.on("click", function() {
        if (window.SilangMapBridge) window.SilangMapBridge.onSelectDelivery(d.id);
      });
      riderMarker.addTo(group);
    }
  });

  if (!window._initialFramed) {
    window._initialFramed = true;
    window.fitCurrentBounds();
  }
};
</script>
</body>
</html>"""
