package com.hackproff.wallpapers4k

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Qualidade mínima exigida (lado maior x lado menor). No modo comprido o lado maior vira a altura. */
enum class Quality(val label: String, val longSide: Int, val shortSide: Int) {
    FULL_HD("Full HD", 1920, 1080),
    QHD("2K", 2560, 1440),
    UHD("4K", 3840, 2160),
}

class MainViewModel : ViewModel() {
    var query by mutableStateOf("")
    var orientation by mutableStateOf(Orientation.PORTRAIT)
    var quality by mutableStateOf(Quality.QHD)

    val items = mutableStateListOf<Wallpaper>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val sources: List<WallpaperSource> = listOf(WallhavenSource, SerperSource, PexelsSource)
    private var page = 1
    private var hasMore = true
    private var generation = 0
    private var job: Job? = null
    private var started = false

    val minW: Int get() = if (orientation == Orientation.PORTRAIT) quality.shortSide else quality.longSide
    val minH: Int get() = if (orientation == Orientation.PORTRAIT) quality.longSide else quality.shortSide

    fun startOnce() {
        if (started) return
        started = true
        search()
    }

    fun search() {
        job?.cancel()
        generation++
        items.clear()
        page = 1
        hasMore = true
        error = null
        loading = false
        loadMore()
    }

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        val gen = generation
        val q = query.trim()
        val o = orientation
        val w = minW
        val h = minH
        val p = page

        job = viewModelScope.launch {
            try {
                val results = coroutineScope {
                    sources.map { s ->
                        async {
                            runCatching {
                                val result = s.search(q, o, w, h, p)
                                if (s.needsVerification) result.copy(items = Verifier.filter(result.items)) else result
                            }
                        }
                    }.awaitAll()
                }
                if (gen != generation) return@launch
                val ok = results.mapNotNull { it.getOrNull() }
                if (ok.isEmpty()) {
                    error = results.firstNotNullOfOrNull { it.exceptionOrNull() }?.message ?: "Erro ao buscar"
                    hasMore = false
                } else {
                    val known = items.map { it.source + it.id }.toHashSet()
                    items += ok.flatMap { it.items }.filter { known.add(it.source + it.id) }
                    hasMore = ok.any { it.hasMore }
                    page = p + 1
                }
            } finally {
                if (gen == generation) loading = false
            }
        }
    }
}
