package com.meetmind.assistant.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Longest-side cap for imported images. The vision encoder resizes far below this;
 * capping here bounds decode memory and the size of the stored JPEG copy.
 */
private const val MAX_DIMENSION_PX = 2048
private const val JPEG_QUALITY = 90

/**
 * Copies a user-picked image into the session photo directory as a JPEG.
 *
 * Gallery images are often HEIC/WebP, which the native vision loader (stb_image in
 * mtmd) cannot decode — so the image is always decoded via [ImageDecoder] and
 * re-encoded as JPEG into `filesDir/photos/` using the same `IMG_<timestamp>.jpg`
 * naming as camera captures.
 *
 * @return Absolute path of the written JPEG, or failure on any decode/write error
 *   (no partial file is left behind).
 */
suspend fun importPhotoForAnalysis(context: Context, uri: Uri): Result<String> =
    withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        val outFile = File(photosDir, "IMG_${System.currentTimeMillis()}.jpg")
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Software bitmap: Bitmap.compress cannot read hardware bitmaps.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_DIMENSION_PX) {
                    val scale = MAX_DIMENSION_PX.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
            try {
                outFile.outputStream().use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        "JPEG encode failed"
                    }
                }
            } finally {
                bitmap.recycle()
            }
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            outFile.delete()
            Result.failure(e)
        }
    }
