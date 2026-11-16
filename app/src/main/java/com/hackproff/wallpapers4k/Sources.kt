package com.hackproff.wallpapers4k

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class Orientation(val label: String) {
    PORTRAIT("Comprido"),
    LANDSCAPE("Horizontal"),
}

data class Wallpaper(
    val id: String,
    val source: String,
    val thumbUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
)

data class ResultPage(val items: List<Wallpaper>, val hasMore: Boolean)

interface WallpaperSource {
    val name: String
    suspend fun search(
        query: String,
        orientation: Orientation,
        minW: Int,
        minH: Int,
        page: Int,
    ): ResultPage
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string() ?: error("Resposta vazia")
            }
        }
}

/** Wallhaven: filtro nativo de resolução mínima ("atleast") e proporção (portrait/landscape). Sem chave para conteúdo SFW. */
object WallhavenSource : WallpaperSource {
    override val name = "Wallhaven"

    override suspend fun search(
        query: String,
        orientation: Orientation,
        minW: Int,
        minH: Int,
        page: Int,
    ): ResultPage {
        val url = "https://wallhaven.cc/api/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("atleast", "${minW}x$minH")
            .addQueryParameter("ratios", if (orientation == Orientation.PORTRAIT) "portrait" else "landscape")
            .addQueryParameter("sorting", if (query.isBlank()) "toplist" else "relevance")
            .addQueryParameter("categories", "111")
            .addQueryParameter("purity", "100") // somente SFW
            .addQueryParameter("page", page.toString())
            .build()
            .toString()

        val json = JSONObject(Http.getString(url))
        val data = json.getJSONArray("data")
        val items = buildList {
            for (i in 0 until data.length()) {
                val o = data.getJSONObject(i)
                val res = o.optString("resolution").split("x")
                val w = res.getOrNull(0)?.toIntOrNull() ?: continue
                val h = res.getOrNull(1)?.toIntOrNull() ?: continue
                add(
                    Wallpaper(
                        id = o.getString("id"),
                        source = name,
                        thumbUrl = o.getJSONObject("thumbs").getString("large"),
                        fullUrl = o.getString("path"),
                        width = w,
                        height = h,
                    )
                )
            }
        }
        val meta = json.getJSONObject("meta")
        return ResultPage(items, meta.optInt("current_page", page) < meta.optInt("last_page", page))
    }
}

/** Pexels: precisa de chave gratuita (PEXELS_KEY). Sem chave, retorna vazio. Filtramos a resolução no app. */
object PexelsSource : WallpaperSource {
    override val name = "Pexels"

    override suspend fun search(
        query: String,
        orientation: Orientation,
        minW: Int,
        minH: Int,
        page: Int,
    ): ResultPage {
        val key = BuildConfig.PEXELS_KEY
        if (key.isBlank()) return ResultPage(emptyList(), false)

        val url = "https://api.pexels.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query.ifBlank { "wallpaper" })
            .addQueryParameter("orientation", if (orientation == Orientation.PORTRAIT) "portrait" else "landscape")
            .addQueryParameter("size", "large")
            .addQueryParameter("per_page", "40")
            .addQueryParameter("page", page.toString())
            .build()
            .toString()

        val json = JSONObject(Http.getString(url, mapOf("Authorization" to key)))
        val photos = json.getJSONArray("photos")
        val items = buildList {
            for (i in 0 until photos.length()) {
                val o = photos.getJSONObject(i)
                val w = o.getInt("width")
                val h = o.getInt("height")
                if (w < minW || h < minH) continue
                val src = o.getJSONObject("src")
                add(
                    Wallpaper(
                        id = o.getLong("id").toString(),
                        source = name,
                        thumbUrl = src.getString("medium"),
                        fullUrl = src.getString("original"),
                        width = w,
                        height = h,
                    )
                )
            }
        }
        return ResultPage(items, json.has("next_page"))
    }
}
