package com.hackproff.wallpapers4k

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** true = os links vêm de sites de terceiros; testamos se baixam e se não são arquivos minúsculos. */
    val needsVerification: Boolean get() = false

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

    /** Cliente com timeouts curtos, usado só para testar se um link de imagem baixa. */
    val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

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

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .post(json.toRequestBody("application/json".toMediaType()))
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string() ?: error("Resposta vazia")
            }
        }
}

/**
 * Descarta resultados de sites de terceiros que não servem: link quebrado, bloqueio de hotlink,
 * resposta que não é imagem (HTML, SVG) ou arquivo pequeno demais (miniatura / qualidade ruim).
 * Faz uma requisição de 1 byte (Range), sem baixar a imagem inteira.
 */
object Verifier {
    private const val MIN_BYTES = 150_000L
    private val gate = Semaphore(8)

    suspend fun filter(items: List<Wallpaper>): List<Wallpaper> = coroutineScope {
        items.map { wp -> async { if (isGood(wp.fullUrl)) wp else null } }.awaitAll().filterNotNull()
    }

    private suspend fun isGood(url: String): Boolean = gate.withPermit {
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url)
                    .header("Range", "bytes=0-0")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36",
                    )
                    .build()
                Http.probeClient.newCall(req).execute().use { resp ->
                    if (resp.code != 200 && resp.code != 206) return@use false
                    val type = resp.header("Content-Type").orEmpty().lowercase()
                    if (!type.startsWith("image/") || type.contains("svg")) return@use false
                    val total = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: if (resp.code == 200) resp.header("Content-Length")?.toLongOrNull() else null
                    total == null || total >= MIN_BYTES
                }
            }.getOrDefault(false)
        }
    }
}

/**
 * Google Imagens via Serper.dev (chave gratuita com 2.500 consultas: https://serper.dev).
 * A API oficial Custom Search do Google está fechada para novos usuários e encerra em 01/01/2027.
 * Usa os filtros do Google (tamanho mínimo + alto/largo) e depois filtra pela resolução real informada.
 */
object SerperSource : WallpaperSource {
    override val name = "Google"
    override val needsVerification = true

    private const val PAGE_SIZE = 20
    private const val MAX_PAGES = 5

    override suspend fun search(
        query: String,
        orientation: Orientation,
        minW: Int,
        minH: Int,
        page: Int,
    ): ResultPage {
        val key = BuildConfig.SERPER_KEY
        if (key.isBlank() || page > MAX_PAGES) return ResultPage(emptyList(), false)

        val portrait = orientation == Orientation.PORTRAIT
        val area = minW.toLong() * minH
        val megapixels = listOf(70, 40, 20, 15, 12, 10, 8, 6, 4, 2).firstOrNull { area >= it * 1_000_000L } ?: 2
        val tbs = "isz:lt,islt:${megapixels}mp,iar:${if (portrait) "t" else "w"}"
        val q = if (query.contains("wallpaper", ignoreCase = true)) query else "$query wallpaper".trim()

        val body = JSONObject()
            .put("q", q)
            .put("num", PAGE_SIZE)
            .put("page", page)
            .put("tbs", tbs)
            .toString()

        val json = JSONObject(Http.postJson("https://google.serper.dev/images", body, mapOf("X-API-KEY" to key)))
        val images = json.optJSONArray("images") ?: return ResultPage(emptyList(), false)

        val items = buildList {
            for (i in 0 until images.length()) {
                val o = images.getJSONObject(i)
                val url = o.optString("imageUrl")
                val w = o.optInt("imageWidth")
                val h = o.optInt("imageHeight")
                if (url.isBlank() || w < minW || h < minH) continue
                if (portrait && h <= w || !portrait && w <= h) continue
                add(
                    Wallpaper(
                        id = url.hashCode().toUInt().toString(16),
                        source = name,
                        thumbUrl = o.optString("thumbnailUrl").ifBlank { url },
                        fullUrl = url,
                        width = w,
                        height = h,
                    )
                )
            }
        }
        return ResultPage(items, images.length() >= PAGE_SIZE && page < MAX_PAGES)
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
