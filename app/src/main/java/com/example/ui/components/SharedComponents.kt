package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.*
import com.example.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

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
    var loadFailed by remember(candidate) { mutableStateOf(false) }
    val isSupportedUri = !candidate.isNullOrBlank() && (
        candidate.startsWith("http") ||
            candidate.startsWith("content:") ||
            candidate.startsWith("file:") ||
            candidate.startsWith("/")
        )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SilangSurface)
            .border(1.dp, SilangSlate200, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isSupportedUri && !loadFailed) {
            AsyncImage(
                model = candidate,
                contentDescription = product.name,
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
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
                if (!delivery.paymentProofUri.isNullOrBlank()) {
                    Text("Payment Screenshot:", style = MaterialTheme.typography.labelMedium)
                    AsyncImage(
                        model = delivery.paymentProofUri,
                        contentDescription = "Payment proof screenshot",
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
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
