package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.local.DeviceImageStorage
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DeviceImagePickerField(
    label: String,
    imageUri: String?,
    onImageSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowUrlFallback: Boolean = false,
    testTagPrefix: String = "device_image"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlDraft by remember { mutableStateOf("") }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun handlePickedUri(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            isSaving = true
            statusMessage = null
            val persisted = DeviceImageStorage.persistImageFromUri(context, uri)
            isSaving = false
            if (persisted != null) {
                onImageSelected(persisted)
                statusMessage = "Saved to device storage"
            } else {
                statusMessage = "Could not read selected image"
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> handlePickedUri(uri) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> handlePickedUri(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val camUri = pendingCameraUri
        pendingCameraUri = null
        if (success && camUri != null) {
            handlePickedUri(camUri)
        }
    }

    fun launchCamera() {
        runCatching {
            val tempUri = DeviceImageStorage.createTempCameraUri(context)
            pendingCameraUri = tempUri
            cameraLauncher.launch(tempUri)
        }.onFailure {
            statusMessage = "Camera unavailable on this device"
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else statusMessage = "Camera permission denied — use Gallery or Files"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SilangSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SilangSlate200)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = SilangSlate900,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!imageUri.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            onImageSelected(null)
                            statusMessage = null
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove image", tint = SilangRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Remove", color = SilangRed, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (!imageUri.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SilangWhite)
                        .border(1.dp, SilangSlate200, RoundedCornerShape(10.dp))
                        .clickable { showPreviewDialog = true }
                        .padding(8.dp)
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = label,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (imageUri.startsWith("http")) "Cloud / Web Image" else "Device Image Attached",
                            style = MaterialTheme.typography.labelMedium,
                            color = SilangGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap preview to enlarge",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilangSlate600
                        )
                    }
                    Icon(Icons.Default.ZoomIn, contentDescription = "Preview image", tint = SilangBlue, modifier = Modifier.size(20.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !isSaving,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("${testTagPrefix}_gallery_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gallery", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("image/*")) },
                    enabled = !isSaving,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("${testTagPrefix}_files_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Device Files", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) launchCamera()
                        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    },
                    enabled = !isSaving,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("${testTagPrefix}_camera_button")
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Camera", style = MaterialTheme.typography.labelSmall)
                }

                if (allowUrlFallback) {
                    OutlinedButton(
                        onClick = { showUrlInput = !showUrlInput },
                        enabled = !isSaving,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("URL", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (showUrlInput && allowUrlFallback) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("https:// image URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val trimmed = urlDraft.trim()
                            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                onImageSelected(trimmed)
                                showUrlInput = false
                                urlDraft = ""
                                statusMessage = "Cloud URL attached"
                            } else {
                                statusMessage = "Enter a valid http/https URL"
                            }
                        }
                    ) { Text("Use") }
                }
            }

            if (isSaving) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Saving image from device...", style = MaterialTheme.typography.labelSmall, color = SilangSlate600)
                }
            } else if (!statusMessage.isNullOrBlank()) {
                Text(statusMessage!!, style = MaterialTheme.typography.labelSmall, color = SilangSlate600)
            }
        }
    }

    if (showPreviewDialog && !imageUri.isNullOrBlank()) {
        Dialog(onDismissRequest = { showPreviewDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = SilangSlate950) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.titleSmall, color = SilangWhite)
                        IconButton(onClick = { showPreviewDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close preview", tint = SilangWhite)
                        }
                    }
                    AsyncImage(
                        model = imageUri,
                        contentDescription = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 380.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceMultiImagePickerField(
    label: String,
    images: List<String>,
    onImagesChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlDraft by remember { mutableStateOf("") }

    fun addPersistedUri(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            isSaving = true
            statusMessage = null
            val saved = DeviceImageStorage.persistImageFromUri(context, uri)
            isSaving = false
            if (saved != null) {
                onImagesChanged(images + saved)
                statusMessage = "Photo saved to device (${images.size + 1} total)"
            } else {
                statusMessage = "Could not load selected image"
            }
        }
    }

    val galleryMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isSaving = true
            statusMessage = null
            val savedList = uris.mapNotNull { DeviceImageStorage.persistImageFromUri(context, it) }
            isSaving = false
            if (savedList.isNotEmpty()) {
                onImagesChanged(images + savedList)
                statusMessage = "Added ${savedList.size} device photo(s)"
            } else {
                statusMessage = "Could not read selected images"
            }
        }
    }

    val filesMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isSaving = true
            statusMessage = null
            val savedList = uris.mapNotNull { DeviceImageStorage.persistImageFromUri(context, it) }
            isSaving = false
            if (savedList.isNotEmpty()) {
                onImagesChanged(images + savedList)
                statusMessage = "Added ${savedList.size} file(s) from device"
            } else {
                statusMessage = "Could not read selected files"
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val camUri = pendingCameraUri
        pendingCameraUri = null
        if (success && camUri != null) addPersistedUri(camUri)
    }

    fun launchCamera() {
        runCatching {
            val tempUri = DeviceImageStorage.createTempCameraUri(context)
            pendingCameraUri = tempUri
            cameraLauncher.launch(tempUri)
        }.onFailure {
            statusMessage = "Camera unavailable on this device"
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else statusMessage = "Camera permission denied — use Gallery or Files"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SilangSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SilangSlate200)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "$label (${images.size})",
                style = MaterialTheme.typography.labelMedium,
                color = SilangSlate900,
                fontWeight = FontWeight.SemiBold
            )

            if (images.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    images.forEachIndexed { idx, img ->
                        Box(modifier = Modifier.size(64.dp)) {
                            AsyncImage(
                                model = img,
                                contentDescription = "Product image ${idx + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, SilangSlate200, RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(SilangSlate950.copy(alpha = 0.78f))
                                    .clickable {
                                        onImagesChanged(images.filterIndexed { i, _ -> i != idx })
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = SilangWhite, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        galleryMultiLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !isSaving,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("product_gallery_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gallery", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { filesMultiLauncher.launch(arrayOf("image/*")) },
                    enabled = !isSaving,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("product_files_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Device Files", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) launchCamera()
                        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    },
                    enabled = !isSaving,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("product_camera_button")
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Camera", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { showUrlInput = !showUrlInput },
                    enabled = !isSaving,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("URL", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (showUrlInput) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("https:// image URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val trimmed = urlDraft.trim()
                            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                onImagesChanged(images + trimmed)
                                showUrlInput = false
                                urlDraft = ""
                            } else {
                                statusMessage = "Enter a valid http/https URL"
                            }
                        }
                    ) { Text("Add") }
                }
            }

            if (isSaving) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Copying image(s) to device storage...", style = MaterialTheme.typography.labelSmall, color = SilangSlate600)
                }
            } else if (!statusMessage.isNullOrBlank()) {
                Text(statusMessage!!, style = MaterialTheme.typography.labelSmall, color = SilangSlate600)
            }
        }
    }
}
