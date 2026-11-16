package com.hackproff.wallpapers4k

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object Downloader {
    /** Salva a imagem original em Pictures/Wallpapers4K (galeria). */
    suspend fun save(context: Context, wp: Wallpaper): Uri = withContext(Dispatchers.IO) {
        val ext = wp.fullUrl.substringBefore('?').substringAfterLast('.', "jpg").lowercase()
            .let { if (it in setOf("jpg", "jpeg", "png", "webp")) it else "jpg" }
        val mime = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${wp.source}_${wp.id}_${wp.width}x${wp.height}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Wallpapers4K")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o arquivo")

        try {
            val req = Request.Builder().url(wp.fullUrl).build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("Resposta vazia")
                val out = resolver.openOutputStream(uri) ?: error("Não foi possível gravar")
                out.use { body.byteStream().copyTo(it) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        uri
    }
}
