package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.*
import com.example.data.network.RouteNetworkHelper
import com.example.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.max

private val CartoVoyagerTileSource = XYTileSource(
    "CartoVoyager",
    0,
    19,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

fun generateQrBitmap(content: String, sizePx: Int = 400): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bmp
}.getOrNull()

@Composable
fun ProductThumbnail(
    product: Product,
    modifier: Modifier = Modifier.size(56.dp),
    overrideImage: String? = null
) {
    val candidate = overrideImage ?: product.images.firstOrNull()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SilangSurface)
            .border(1.dp, SilangSlate200, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!candidate.isNullOrBlank() && (candidate.startsWith("http") || candidate.startsWith("content:") || candidate.startsWith("file:"))) {
            AsyncImage(
                model = candidate,
                contentDescription = product.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = categoryFallbackEmoji(product.category),
                fontSize = 24.sp
            )
        }
    }
}

private fun createPinDrawable(context: Context, fillColorInt: Int, badgeLetter: String): BitmapDrawable {
    val size = 76
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(70, 0, 0, 0) }
    canvas.drawCircle(cx, cy + 3f, 26f, shadowPaint)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawCircle(cx, cy, 24f, borderPaint)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColorInt }
    canvas.drawCircle(cx, cy, 20f, fillPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(badgeLetter, cx, textY, textPaint)
    return BitmapDrawable(context.resources, bmp)
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun OsmInteractiveMapView(
    branchName: String,
    branchLat: Double,
    branchLng: Double,
    customerName: String,
    customerLat: Double,
    customerLng: Double,
    driverName: String?,
    driverLat: Double?,
    driverLng: Double?,
    routePoints: List<Pair<Double, Double>>,
    storeRoutePoints: List<Pair<Double, Double>>,
    interactivePinSelection: Boolean,
    onMapCoordinateSelected: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var useVoyagerTiles by remember { mutableStateOf(true) }
    var lastCenteredLat by remember { mutableStateOf(customerLat) }
    var lastCenteredLng by remember { mutableStateOf(customerLng) }

    val latestCallback by rememberUpdatedState(onMapCoordinateSelected)
    val latestInteractive by rememberUpdatedState(interactivePinSelection)

    val branchPinIcon = remember(context) {
        createPinDrawable(context, android.graphics.Color.parseColor("#0052CC"), "S")
    }
    val customerPinIcon = remember(context) {
        createPinDrawable(context, android.graphics.Color.parseColor("#16A34A"), "C")
    }
    val riderPinIcon = remember(context) {
        createPinDrawable(context, android.graphics.Color.parseColor("#F97316"), "R")
    }

    fun frameRouteOrPin(map: MapView, animate: Boolean) {
        val dist = haversineDistanceKm(branchLat, branchLng, customerLat, customerLng)
        if (!interactivePinSelection && dist > 0.05) {
            val pts = buildList {
                add(GeoPoint(branchLat, branchLng))
                add(GeoPoint(customerLat, customerLng))
                if (driverLat != null && driverLng != null) {
                    add(GeoPoint(driverLat, driverLng))
                }
                routePoints.forEach { add(GeoPoint(it.first, it.second)) }
            }
            map.post {
                runCatching {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts).increaseByScale(1.35f), animate)
                }.onFailure {
                    if (animate) map.controller.animateTo(GeoPoint(customerLat, customerLng))
                    else map.controller.setCenter(GeoPoint(customerLat, customerLng))
                }
            }
        } else {
            if (animate) map.controller.animateTo(GeoPoint(customerLat, customerLng))
            else map.controller.setCenter(GeoPoint(customerLat, customerLng))
        }
    }

    LaunchedEffect(Unit) {
        val appCtx = context.applicationContext
        Configuration.getInstance().apply {
            userAgentValue = "SilangMiniMartAndroid/1.0 (${appCtx.packageName})"
            osmdroidBasePath = File(appCtx.cacheDir, "osmdroid")
            osmdroidTileCache = File(appCtx.cacheDir, "osmdroid/tiles")
        }
    }

    LaunchedEffect(branchLat, branchLng, customerLat, customerLng, driverLat, driverLng, interactivePinSelection, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        if (!interactivePinSelection) {
            frameRouteOrPin(map, animate = true)
        } else if (kotlin.math.abs(customerLat - lastCenteredLat) > 0.00001 ||
            kotlin.math.abs(customerLng - lastCenteredLng) > 0.00001
        ) {
            lastCenteredLat = customerLat
            lastCenteredLng = customerLng
            map.controller.animateTo(GeoPoint(customerLat, customerLng))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapRef?.onPause()
            mapRef?.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().apply {
                    userAgentValue = "SilangMiniMartAndroid/1.0 (${ctx.applicationContext.packageName})"
                    osmdroidBasePath = File(ctx.applicationContext.cacheDir, "osmdroid")
                    osmdroidTileCache = File(ctx.applicationContext.cacheDir, "osmdroid/tiles")
                }
                MapView(ctx).apply {
                    setTileSource(if (useVoyagerTiles) CartoVoyagerTileSource else TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(15.5)
                    controller.setCenter(GeoPoint(customerLat, customerLng))
                    addOnFirstLayoutListener { _, _, _, _, _ ->
                        frameRouteOrPin(this, animate = false)
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
                    onResume()
                    mapRef = this
                }
            },
            update = { mapView ->
                val targetSource = if (useVoyagerTiles) CartoVoyagerTileSource else TileSourceFactory.MAPNIK
                if (mapView.tileProvider.tileSource.name() != targetSource.name()) {
                    mapView.setTileSource(targetSource)
                }

                mapView.overlays.clear()

                if (latestInteractive) {
                    mapView.overlays.add(
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                latestCallback?.invoke(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        })
                    )
                }

                // Helper to anchor polyline endpoints tightly to exact start/end coordinates
                fun buildAnchoredPath(
                    startLat: Double,
                    startLng: Double,
                    endLat: Double,
                    endLng: Double,
                    waypoints: List<Pair<Double, Double>>
                ): List<GeoPoint> {
                    return if (waypoints.size >= 2) {
                        buildList {
                            add(GeoPoint(startLat, startLng))
                            waypoints.forEach { add(GeoPoint(it.first, it.second)) }
                            add(GeoPoint(endLat, endLng))
                        }
                    } else {
                        listOf(GeoPoint(startLat, startLng), GeoPoint(endLat, endLng))
                    }
                }

                // 1) Store -> Customer base delivery path (blue dashed reference when rider is mid-route)
                val riderMovedFromStore = driverLat != null && driverLng != null &&
                    haversineDistanceKm(branchLat, branchLng, driverLat, driverLng) > 0.03
                if (riderMovedFromStore) {
                    val baseGeoPoints = buildAnchoredPath(branchLat, branchLng, customerLat, customerLng, storeRoutePoints)
                    val storePath = Polyline(mapView).apply {
                        setPoints(baseGeoPoints)
                        title = "$branchName Branch → $customerName"
                        outlinePaint.color = android.graphics.Color.parseColor("#2563EB")
                        outlinePaint.strokeWidth = 7f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.isAntiAlias = true
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
                    }
                    mapView.overlays.add(storePath)
                }

                // 2) Active delivery path Polyline layer (Store -> Customer, or Rider -> Customer when en route)
                val pathStartLat = if (riderMovedFromStore) driverLat!! else branchLat
                val pathStartLng = if (riderMovedFromStore) driverLng!! else branchLng
                val activeGeoPoints = buildAnchoredPath(pathStartLat, pathStartLng, customerLat, customerLng, routePoints)

                val casingPolyline = Polyline(mapView).apply {
                    setPoints(activeGeoPoints)
                    outlinePaint.color = android.graphics.Color.WHITE
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                }
                mapView.overlays.add(casingPolyline)

                val routePolyline = Polyline(mapView).apply {
                    setPoints(activeGeoPoints)
                    title = "Delivery Path (${"%.2f".format(haversineDistanceKm(pathStartLat, pathStartLng, customerLat, customerLng))} km)"
                    outlinePaint.color = android.graphics.Color.parseColor("#F97316")
                    outlinePaint.strokeWidth = 8.5f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                }
                mapView.overlays.add(routePolyline)

                // Branch Marker (Blue)
                val branchMarker = Marker(mapView).apply {
                    position = GeoPoint(branchLat, branchLng)
                    title = "$branchName Branch"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = branchPinIcon
                }
                mapView.overlays.add(branchMarker)

                // Customer Marker (Green)
                val customerMarker = Marker(mapView).apply {
                    position = GeoPoint(customerLat, customerLng)
                    title = customerName
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = customerPinIcon
                }
                mapView.overlays.add(customerMarker)

                // Rider Marker (Orange)
                if (driverLat != null && driverLng != null) {
                    val riderMarker = Marker(mapView).apply {
                        position = GeoPoint(driverLat, driverLng)
                        title = driverName ?: "Rider"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = riderPinIcon
                    }
                    mapView.overlays.add(riderMarker)
                }

                mapView.invalidate()
            }
        )

        // Tile Layer Toggle (Top-Start)
        Surface(
            onClick = { useVoyagerTiles = !useVoyagerTiles },
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
                    text = if (useVoyagerTiles) "Street" else "OSM",
                    style = MaterialTheme.typography.labelSmall,
                    color = SilangSlate900,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Zoom + Recenter Controls (Top-End)
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { mapRef?.controller?.zoomIn() },
                containerColor = SilangWhite,
                contentColor = SilangSlate900,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
            }
            SmallFloatingActionButton(
                onClick = { mapRef?.controller?.zoomOut() },
                containerColor = SilangWhite,
                contentColor = SilangSlate900,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
            }
            SmallFloatingActionButton(
                onClick = {
                    val map = mapRef ?: return@SmallFloatingActionButton
                    frameRouteOrPin(map, animate = true)
                },
                containerColor = SilangWhite,
                contentColor = SilangBlue,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on pin", modifier = Modifier.size(18.dp))
            }
        }

        // Legend overlay (Bottom-Start)
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
    var showFullScreenMap by remember { mutableStateOf(false) }

    val startLat = driverLat ?: branchLat
    val startLng = driverLng ?: branchLng
    val distanceKm = remember(startLat, startLng, customerLat, customerLng) {
        haversineDistanceKm(startLat, startLng, customerLat, customerLng)
    }

    LaunchedEffect(branchLat, branchLng, customerLat, customerLng) {
        storeRoutePoints = RouteNetworkHelper.fetchDrivingRoute(branchLat, branchLng, customerLat, customerLng)
    }

    LaunchedEffect(startLat, startLng, customerLat, customerLng) {
        val fetched = RouteNetworkHelper.fetchDrivingRoute(startLat, startLng, customerLat, customerLng)
        routePoints = fetched
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
                        text = title ?: if (interactivePinSelection) "Tap Map to Adjust GPS Pin" else "GPS Route Map (Leaflet / OSM)",
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

            OsmInteractiveMapView(
                branchName = branchName,
                branchLat = branchLat,
                branchLng = branchLng,
                customerName = customerName,
                customerLat = customerLat,
                customerLng = customerLng,
                driverName = driverName,
                driverLat = driverLat,
                driverLng = driverLng,
                routePoints = routePoints,
                storeRoutePoints = storeRoutePoints,
                interactivePinSelection = interactivePinSelection,
                onMapCoordinateSelected = onMapCoordinateSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(compactHeightDp.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SilangSlate800, RoundedCornerShape(12.dp))
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
                                text = "${"%.2f".format(distanceKm)} km • ~${max(3, (distanceKm * 4).toInt())} mins • OpenStreetMap",
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

                    OsmInteractiveMapView(
                        branchName = branchName,
                        branchLat = branchLat,
                        branchLng = branchLng,
                        customerName = customerName,
                        customerLat = customerLat,
                        customerLng = customerLng,
                        driverName = driverName,
                        driverLat = driverLat,
                        driverLng = driverLng,
                        routePoints = routePoints,
                        storeRoutePoints = storeRoutePoints,
                        interactivePinSelection = interactivePinSelection,
                        onMapCoordinateSelected = onMapCoordinateSelected,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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

@SuppressLint("ClickableViewAccessibility")
@Composable
fun FleetDeliveryMapCard(
    branches: List<BranchRecord>,
    deliveries: List<Delivery>,
    selectedDeliveryId: String?,
    onSelectDelivery: (Delivery) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var useVoyagerTiles by remember { mutableStateOf(true) }
    var showFullScreen by remember { mutableStateOf(false) }

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

    val branchIcon = remember(context) { createPinDrawable(context, android.graphics.Color.parseColor("#0052CC"), "S") }
    val orderIcon = remember(context) { createPinDrawable(context, android.graphics.Color.parseColor("#16A34A"), "C") }
    val selectedIcon = remember(context) { createPinDrawable(context, android.graphics.Color.parseColor("#DC2626"), "★") }
    val riderIcon = remember(context) { createPinDrawable(context, android.graphics.Color.parseColor("#F97316"), "R") }
    val latestSelect by rememberUpdatedState(onSelectDelivery)

    fun fitAllPoints(map: MapView, animate: Boolean) {
        val pts = buildList {
            branches.filter { it.isActive }.forEach { add(GeoPoint(it.lat, it.lng)) }
            activeDeliveries.forEach { d ->
                add(GeoPoint(d.lat, d.lng))
                if (d.driverLat != null && d.driverLng != null) add(GeoPoint(d.driverLat, d.driverLng))
            }
        }
        if (pts.size >= 2) {
            map.post {
                runCatching {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts).increaseByScale(1.35f), animate)
                }
            }
        } else if (pts.size == 1) {
            if (animate) map.controller.animateTo(pts.first())
            else map.controller.setCenter(pts.first())
        }
    }

    @Composable
    fun FleetMapContent(mapModifier: Modifier) {
        var instanceMapRef by remember { mutableStateOf<MapView?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                instanceMapRef?.onPause()
                instanceMapRef?.onDetach()
            }
        }

        Box(modifier = mapModifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().apply {
                        userAgentValue = "SilangMiniMartAndroid/1.0 (${ctx.applicationContext.packageName})"
                        osmdroidBasePath = File(ctx.applicationContext.cacheDir, "osmdroid")
                        osmdroidTileCache = File(ctx.applicationContext.cacheDir, "osmdroid/tiles")
                    }
                    MapView(ctx).apply {
                        setTileSource(if (useVoyagerTiles) CartoVoyagerTileSource else TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(14.5)
                        val firstPt = branches.firstOrNull()?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(14.2235, 120.9739)
                        controller.setCenter(firstPt)
                        addOnFirstLayoutListener { _, _, _, _, _ -> fitAllPoints(this, animate = false) }
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        onResume()
                        instanceMapRef = this
                    }
                },
                update = { mapView ->
                    val targetSource = if (useVoyagerTiles) CartoVoyagerTileSource else TileSourceFactory.MAPNIK
                    if (mapView.tileProvider.tileSource.name() != targetSource.name()) {
                        mapView.setTileSource(targetSource)
                    }
                    mapView.overlays.clear()

                    val branchCoords = branches.associate { it.name to (it.lat to it.lng) }

                    activeDeliveries.forEach { d ->
                        val b = branchCoords[d.branch] ?: (14.2235 to 120.9739)
                        val startLat = d.driverLat ?: b.first
                        val startLng = d.driverLng ?: b.second
                        val roadPts = fleetRoutes[d.id]
                        val geoPts = if (!roadPts.isNullOrEmpty() && roadPts.size >= 2) {
                            buildList {
                                add(GeoPoint(startLat, startLng))
                                roadPts.forEach { add(GeoPoint(it.first, it.second)) }
                                add(GeoPoint(d.lat, d.lng))
                            }
                        } else {
                            listOf(GeoPoint(startLat, startLng), GeoPoint(d.lat, d.lng))
                        }
                        val casing = Polyline(mapView).apply {
                            setPoints(geoPts)
                            outlinePaint.color = android.graphics.Color.WHITE
                            outlinePaint.strokeWidth = if (d.id == selectedDeliveryId) 15f else 10f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            outlinePaint.isAntiAlias = true
                        }
                        mapView.overlays.add(casing)

                        val poly = Polyline(mapView).apply {
                            setPoints(geoPts)
                            title = "${d.id}: ${d.branch} → ${d.customerName}"
                            outlinePaint.color = if (d.id == selectedDeliveryId) android.graphics.Color.parseColor("#DC2626") else android.graphics.Color.parseColor("#F97316")
                            outlinePaint.strokeWidth = if (d.id == selectedDeliveryId) 9.5f else 6f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            outlinePaint.isAntiAlias = true
                        }
                        mapView.overlays.add(poly)
                    }

                    branches.filter { it.isActive }.forEach { b ->
                        val m = Marker(mapView).apply {
                            position = GeoPoint(b.lat, b.lng)
                            title = "${b.name} Branch"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = branchIcon
                        }
                        mapView.overlays.add(m)
                    }

                    activeDeliveries.forEach { d ->
                        val custMarker = Marker(mapView).apply {
                            position = GeoPoint(d.lat, d.lng)
                            title = "${d.id} • ${d.customerName} (${d.status})"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = if (d.id == selectedDeliveryId) selectedIcon else orderIcon
                            setOnMarkerClickListener { marker, _ ->
                                marker.showInfoWindow()
                                latestSelect(d)
                                true
                            }
                        }
                        mapView.overlays.add(custMarker)

                        if (d.driverLat != null && d.driverLng != null && d.status == "Out for Delivery") {
                            val rMarker = Marker(mapView).apply {
                                position = GeoPoint(d.driverLat, d.driverLng)
                                title = "${d.driverName} (${d.id})"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = riderIcon
                                setOnMarkerClickListener { marker, _ ->
                                    marker.showInfoWindow()
                                    latestSelect(d)
                                    true
                                }
                            }
                            mapView.overlays.add(rMarker)
                        }
                    }
                    mapView.invalidate()
                }
            )

            Surface(
                onClick = { useVoyagerTiles = !useVoyagerTiles },
                shape = RoundedCornerShape(10.dp),
                color = SilangWhite.copy(alpha = 0.94f),
                shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).minimumInteractiveComponentSize()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Switch map layer", tint = SilangBlue, modifier = Modifier.size(16.dp))
                    Text(if (useVoyagerTiles) "Street" else "OSM", style = MaterialTheme.typography.labelSmall, color = SilangSlate900, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallFloatingActionButton(onClick = { instanceMapRef?.controller?.zoomIn() }, containerColor = SilangWhite, contentColor = SilangSlate900, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
                }
                SmallFloatingActionButton(onClick = { instanceMapRef?.controller?.zoomOut() }, containerColor = SilangWhite, contentColor = SilangSlate900, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
                }
                SmallFloatingActionButton(onClick = { instanceMapRef?.let { fitAllPoints(it, animate = true) } }, containerColor = SilangWhite, contentColor = SilangBlue, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Fit all pins", modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(SilangSlate950.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp),
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
                Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, SilangSlate800, RoundedCornerShape(12.dp))
            )
            Text("Tap any green/orange pin on the map to inspect its delivery route.", style = MaterialTheme.typography.labelSmall, color = SilangSlate400)
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
                    FleetMapContent(Modifier.weight(1f).fillMaxWidth())
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

@Composable
fun PosReceiptDialog(
    transaction: Transaction,
    onDismiss: () -> Unit
) {
    val qrText = remember(transaction) {
        "SILANG MINI MART\nOfficial Receipt\nID: ${transaction.id}\nBranch: ${transaction.branch}\n" +
            "VAT: PHP ${"%.2f".format(transaction.vatAmount)}\nTotal: PHP ${"%.2f".format(transaction.total)}\nSalamat po!"
    }
    val qrBitmap = remember(qrText) { generateQrBitmap(qrText, 320) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_pos_receipt_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
            ) {
                Text("Done")
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("SILANG MINI MART", style = MaterialTheme.typography.titleLarge, color = SilangBlue)
                Text("OFFICIAL POS RECEIPT", style = MaterialTheme.typography.labelMedium, color = SilangGreen)
                Text("${transaction.branch} Branch • ${transaction.id}", style = MaterialTheme.typography.labelSmall, color = SilangSlate600)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Cashier: ${transaction.cashier}", style = MaterialTheme.typography.bodySmall)
                Text("Payment: ${transaction.paymentMethod}", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                transaction.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.quantity}x ${item.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(formatPhp(item.price * item.quantity), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider()
                ReceiptLine("Subtotal", formatPhp(transaction.subtotal))
                if (transaction.discountAmount > 0) {
                    ReceiptLine("Discount (${transaction.discountType})", "-${formatPhp(transaction.discountAmount)}")
                }
                ReceiptLine("12% VAT Included", formatPhp(transaction.vatAmount))
                ReceiptLine("TOTAL", formatPhp(transaction.total), bold = true)
                ReceiptLine("Paid (${transaction.paymentMethod})", formatPhp(transaction.amountPaid))
                ReceiptLine("Change", formatPhp(transaction.changeAmount))

                if (qrBitmap != null) {
                    Spacer(Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_qr_receipt),
                            modifier = Modifier.size(130.dp)
                        )
                    }
                }
                Text(
                    "Salamat po sa pagbili sa Silang Mini Mart!",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilangSlate600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
fun DeliveryReceiptDialog(
    delivery: Delivery,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_delivery_receipt_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
            ) {
                Text("Close")
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("SILANG MINI MART", style = MaterialTheme.typography.titleLarge, color = SilangBlue)
                Text("DELIVERY RECEIPT • ${delivery.id}", style = MaterialTheme.typography.labelMedium, color = SilangOrange)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Customer: ${delivery.customerName} (${delivery.customerPhone})", style = MaterialTheme.typography.bodySmall)
                Text("Address: ${delivery.address}, ${delivery.barangay}", style = MaterialTheme.typography.bodySmall)
                Text("Branch: ${delivery.branch} • Rider: ${delivery.driverName}", style = MaterialTheme.typography.bodySmall)
                Text("Payment: ${delivery.paymentMethod} (${delivery.paymentStatus})", style = MaterialTheme.typography.bodySmall)
                if (!delivery.paymentReference.isNullOrBlank()) {
                    Text("Ref: ${delivery.paymentReference}", style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
                delivery.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.quantity}x ${item.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(formatPhp(item.price * item.quantity), style = MaterialTheme.typography.labelSmall)
                    }
                }
                HorizontalDivider()
                ReceiptLine("Delivery Fee", formatPhp(delivery.deliveryFee))
                ReceiptLine("Total Amount", formatPhp(delivery.totalAmount), bold = true)
                if (!delivery.podPhotoUri.isNullOrBlank()) {
                    Text("Proof of Delivery Photo:", style = MaterialTheme.typography.labelMedium)
                    AsyncImage(
                        model = delivery.podPhotoUri,
                        contentDescription = "Proof of delivery",
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    )
}

@Composable
fun ServiceReceiptDialog(
    service: ServiceTransaction,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_service_receipt_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
            ) {
                Text("Tapos Na")
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("SILANG MINI MART", style = MaterialTheme.typography.titleLarge, color = SilangBlue)
                Text("VALUE ADDED SERVICES", style = MaterialTheme.typography.labelMedium, color = SilangGreen)
                Text("${service.branch} Branch • ${service.id}", style = MaterialTheme.typography.labelSmall, color = SilangSlate600)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReceiptLine("Service", service.type)
                ReceiptLine("Provider", service.provider)
                ReceiptLine("Sender / Name", service.senderOrAccountName)
                ReceiptLine("Recipient / Acct", service.recipient)
                HorizontalDivider()
                ReceiptLine("Amount", formatPhp(service.amount))
                ReceiptLine("Service Fee", formatPhp(service.serviceFee))
                ReceiptLine("Total Collected", formatPhp(service.totalAmount), bold = true)
            }
        }
    )
}

@Composable
private fun ReceiptLine(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (bold) SilangSlate900 else SilangSlate600
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
            color = SilangSlate900
        )
    }
}
