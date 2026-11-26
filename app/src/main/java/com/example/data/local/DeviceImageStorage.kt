package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object DeviceImageStorage {
    private const val UPLOADS_DIR = "device_uploads"
    private const val CAMERA_TEMP_DIR = "camera_temp"
    private const val MAX_DIM_PX = 1600

    fun createTempCameraUri(context: Context): Uri {
        val tempDir = File(context.cacheDir, CAMERA_TEMP_DIR).apply { mkdirs() }
        val tempFile = File(tempDir, "cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    suspend fun persistImageFromUri(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val uploadsDir = File(context.filesDir, UPLOADS_DIR).apply { mkdirs() }
            val mimeType = context.contentResolver.getType(sourceUri).orEmpty()
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.lowercase()
            val isPngOrWebp = ext == "png" || ext == "webp"

            val outExt = if (isPngOrWebp) ext else "jpg"
            val destFile = File(uploadsDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$outExt")

            val rawBytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return@runCatching null

            if (rawBytes.isEmpty()) return@runCatching null

            // Try downscaling huge camera/gallery photos while preserving format cleanly
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
            val width = boundsOpts.outWidth
            val height = boundsOpts.outHeight

            if (width > MAX_DIM_PX || height > MAX_DIM_PX) {
                var sampleSize = 1
                while (width / sampleSize > MAX_DIM_PX || height / sampleSize > MAX_DIM_PX) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                if (bmp != null) {
                    FileOutputStream(destFile).use { fos ->
                        val format = if (isPngOrWebp && bmp.hasAlpha()) {
                            Bitmap.CompressFormat.PNG
                        } else {
                            Bitmap.CompressFormat.JPEG
                        }
                        bmp.compress(format, 88, fos)
                    }
                    bmp.recycle()
                    return@runCatching Uri.fromFile(destFile).toString()
                }
            }

            // Otherwise write exact original image bytes directly
            FileOutputStream(destFile).use { fos ->
                fos.write(rawBytes)
            }
            Uri.fromFile(destFile).toString()
        }.getOrNull()
    }
}
