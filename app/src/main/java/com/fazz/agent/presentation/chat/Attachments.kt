package com.fazz.agent.presentation.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copies picked/captured images into app-private storage as compact JPEGs (max 1280 px). */
object Attachments {
    private fun dir(context: Context) = File(context.filesDir, "attachments").apply { mkdirs() }

    /** A new empty file + content Uri for the camera to write into. */
    fun newCameraTarget(context: Context): Pair<File, Uri> {
        val f = File(dir(context), "cam-${UUID.randomUUID()}.jpg")
        return f to FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    suspend fun importUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                    val s = info.size
                    val scale = 1280f / maxOf(s.width, s.height)
                    if (scale < 1f) d.setTargetSize((s.width * scale).toInt(), (s.height * scale).toInt())
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            }
            compress(context, bmp)
        }.getOrNull()
    }

    suspend fun normalizeFile(context: Context, file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, opts)
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > 2560) sample *= 2
            val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            compress(context, bmp).also { file.delete() }
        }.getOrNull()
    }

    private fun compress(context: Context, src: Bitmap): String {
        val scale = 1280f / maxOf(src.width, src.height)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val out = File(dir(context), "img-${UUID.randomUUID()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return out.path
    }
}
