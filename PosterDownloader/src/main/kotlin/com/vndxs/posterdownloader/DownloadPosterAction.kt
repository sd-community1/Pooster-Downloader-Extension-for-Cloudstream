package com.vndxs.posterdownloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Prototype action for CloudStream's video-action menu.
 * It looks for a poster/image URL on the episode/video object using reflection,
 * then downloads it to Pictures/CloudStream Posters using MediaStore.
 */
class DownloadPosterAction : VideoClickAction() {
    override val name: UiText = UiText.StringValue("تنزيل البوستر")

    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean = video != null

    override suspend fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        val ctx = context ?: return
        val posterUrl = findPosterUrl(video) ?: findPosterUrl(result)
        if (posterUrl == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, "لم أجد رابط البوستر في بيانات هذا الفيديو", Toast.LENGTH_LONG).show()
            }
            return
        }

        val title = findTitle(video) ?: "CloudStream Poster"
        val saved = withContext(Dispatchers.IO) {
            runCatching { downloadToGallery(ctx, posterUrl, title) }.getOrNull()
        }
        withContext(Dispatchers.Main) {
            val message = if (saved != null) "تم حفظ البوستر في المعرض" else "تعذّر تنزيل البوستر من هذا المصدر"
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun findPosterUrl(target: Any): String? {
        val hints = listOf("posterurl", "poster", "imageurl", "image", "coverurl", "cover", "thumbnail", "thumb")
        val candidates = mutableListOf<Pair<String, String>>()
        var type: Class<*>? = target.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (field.type == String::class.java) {
                    runCatching {
                        field.isAccessible = true
                        val value = field.get(target) as? String
                        if (!value.isNullOrBlank() && value.startsWith("http", true)) {
                            candidates.add(field.name.lowercase(Locale.ROOT) to value)
                        }
                    }
                }
            }
            type = type.superclass
        }
        // Some models expose a getter rather than a directly readable field.
        for (method in target.javaClass.methods) {
            val methodName = method.name.lowercase(Locale.ROOT)
            if (method.parameterTypes.isEmpty() && hints.any { methodName.contains(it) }) {
                runCatching {
                    val value = method.invoke(target) as? String
                    if (!value.isNullOrBlank() && value.startsWith("http", true)) {
                        candidates.add(methodName to value)
                    }
                }
            }
        }
        return candidates.firstOrNull { (key, _) -> hints.any { key.contains(it) } }?.second
            ?: candidates.firstOrNull { (_, value) ->
                val lower = value.lowercase(Locale.ROOT)
                listOf(".jpg", ".jpeg", ".png", ".webp", ".avif", "image=").any { lower.contains(it) }
            }?.second
    }

    private fun findTitle(target: Any): String? {
        val names = listOf("name", "title", "showname")
        for (field in target.javaClass.declaredFields) {
            if (field.type == String::class.java && names.any { field.name.equals(it, true) }) {
                val value = runCatching { field.isAccessible = true; field.get(target) as? String }.getOrNull()
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    private fun downloadToGallery(context: Context, imageUrl: String, title: String): Uri {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val mime = connection.contentType?.substringBefore(';')?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val extension = when (mime.lowercase(Locale.ROOT)) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/avif" -> ".avif"
                "image/gif" -> ".gif"
                else -> ".jpg"
            }
            val safeTitle = title.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF _.-]"), "_")
                .trim().take(90).ifBlank { "CloudStream Poster" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$safeTitle$extension")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CloudStream Posters")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create gallery item")
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 25L * 1024 * 1024) error("Image exceeds 25 MB")
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("Could not open gallery output")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    context.contentResolver.update(uri, ready, null, null)
                }
                return uri
            } catch (t: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw t
            }
        } finally {
            connection.disconnect()
        }
    }
}
