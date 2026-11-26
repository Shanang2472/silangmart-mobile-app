package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.*
import com.example.data.network.RouteNetworkHelper
import com.example.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.max
import kotlin.math.min

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var routePoints by remember(branchLat, branchLng, customerLat, customerLng, driverLat, driverLng) {
        mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    }

    val startLat = driverLat ?: branchLat
    val startLng = driverLng ?: branchLng
    val distanceKm = remember(startLat, startLng, customerLat, customerLng) {
        haversineDistanceKm(startLat, startLng, customerLat, customerLng)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = SilangOrangeBright, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (interactivePinSelection) "Tap Map to Adjust GPS Pin" else "GPS Route Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        color = SilangWhite
                    )
                }
                Text(
                    text = "${"%.2f".format(distanceKm)} km • ~${max(3, (distanceKm * 4).toInt())} mins",
                    style = MaterialTheme.typography.labelSmall,
                    color = SilangOrangeBright
                )
            }

            // Custom Vector Map Canvas with grid, OSRM road polyline, Store/Rider/Customer pins
            val pointsToBound = buildList {
                add(branchLat to branchLng)
                add(customerLat to customerLng)
                if (driverLat != null && driverLng != null) add(driverLat to driverLng)
                addAll(routePoints)
            }
            val minLat = (pointsToBound.minOfOrNull { it.first } ?: 14.21) - 0.004
            val maxLat = (pointsToBound.maxOfOrNull { it.first } ?: 14.24) + 0.004
            val minLng = (pointsToBound.minOfOrNull { it.second } ?: 120.96) - 0.004
            val maxLng = (pointsToBound.maxOfOrNull { it.second } ?: 120.98) + 0.004
            val latSpan = max(0.005, maxLat - minLat)
            val lngSpan = max(0.005, maxLng - minLng)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B1325))
                    .border(1.dp, SilangSlate800, RoundedCornerShape(12.dp))
                    .pointerInput(interactivePinSelection, minLat, latSpan, minLng, lngSpan) {
                        if (interactivePinSelection && onMapCoordinateSelected != null) {
                            detectTapGestures { offset ->
                                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                val pickedLng = minLng + normX * lngSpan
                                val pickedLat = maxLat - normY * latSpan
                                onMapCoordinateSelected(pickedLat, pickedLng)
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    fun toOffset(lat: Double, lng: Double): Offset {
                        val x = ((lng - minLng) / lngSpan * size.width).toFloat()
                        val y = ((maxLat - lat) / latSpan * size.height).toFloat()
                        return Offset(x, y)
                    }

                    // Subtle coordinate grid
                    for (i in 1..4) {
                        val gx = size.width * i / 5f
                        val gy = size.height * i / 5f
                        drawLine(Color(0xFF1A2742), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                        drawLine(Color(0xFF1A2742), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                    }

                    // Route Path (OSRM polyline or dashed fallback)
                    val path = Path()
                    if (routePoints.size >= 2) {
                        val first = toOffset(routePoints.first().first, routePoints.first().second)
                        path.moveTo(first.x, first.y)
                        routePoints.drop(1).forEach { pt ->
                            val o = toOffset(pt.first, pt.second)
                            path.lineTo(o.x, o.y)
                        }
                        drawPath(path, color = SilangOrangeBright, style = Stroke(width = 6f))
                    } else {
                        val pStart = toOffset(startLat, startLng)
                        val pEnd = toOffset(customerLat, customerLng)
                        path.moveTo(pStart.x, pStart.y)
                        path.lineTo(pEnd.x, pEnd.y)
                        drawPath(
                            path,
                            color = SilangOrangeBright,
                            style = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                        )
                    }

                    // Branch Pin (Blue)
                    val bOff = toOffset(branchLat, branchLng)
                    drawCircle(SilangBlue, radius = 14f, center = bOff)
                    drawCircle(Color.White, radius = 6f, center = bOff)

                    // Customer Pin (Emerald)
                    val cOff = toOffset(customerLat, customerLng)
                    drawCircle(SilangGreen, radius = 15f, center = cOff)
                    drawCircle(Color.White, radius = 6f, center = cOff)

                    // Rider Pin (Orange)
                    if (driverLat != null && driverLng != null) {
                        val dOff = toOffset(driverLat, driverLng)
                        drawCircle(SilangOrange, radius = 16f, center = dOff)
                        drawCircle(Color.White, radius = 7f, center = dOff)
                    }
                }

                // Legend overlay
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
                }
            }

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
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
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
