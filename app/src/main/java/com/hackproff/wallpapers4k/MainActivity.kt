package com.hackproff.wallpapers4k

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App()
            }
        }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<Wallpaper?>(null) }
    var downloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.startOnce() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text("Buscar (ex.: space, anime, nature)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Orientation.values().forEach { o ->
                    FilterChip(
                        selected = vm.orientation == o,
                        onClick = { vm.orientation = o; vm.search() },
                        label = { Text(o.label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4).forEach { s ->
                    FilterChip(
                        selected = vm.scale == s,
                        onClick = { vm.scale = s; vm.search() },
                        label = { Text("${s}x") },
                    )
                }
            }
            Text(
                "Mínimo: ${vm.minW}×${vm.minH} (${vm.scale}x a tela do Redmi 13C)",
                style = MaterialTheme.typography.labelMedium,
            )

            val gridState = rememberLazyGridState()
            val nearEnd by remember {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
                }
            }
            LaunchedEffect(nearEnd, vm.items.size) {
                if (nearEnd) vm.loadMore()
            }

            Box(Modifier.fillMaxSize()) {
                val ratio = if (vm.orientation == Orientation.PORTRAIT) 9f / 16f else 16f / 9f
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(vm.items, key = { it.source + it.id }) { wp ->
                        AsyncImage(
                            model = wp.thumbUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selected = wp },
                        )
                    }
                }
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                if (!vm.loading && vm.items.isEmpty()) {
                    Text(
                        vm.error ?: "Nada encontrado nessa resolução. Tente 2x ou outra busca.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            }
        }
    }

    selected?.let { wp ->
        AlertDialog(
            onDismissRequest = { if (!downloading) selected = null },
            title = { Text("${wp.width}×${wp.height} · ${wp.source}") },
            text = {
                AsyncImage(
                    model = wp.thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = !downloading,
                    onClick = {
                        scope.launch {
                            downloading = true
                            try {
                                Downloader.save(context, wp)
                                snackbar.showSnackbar("Salvo em Pictures/Wallpapers4K")
                                selected = null
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Falha: ${e.message}")
                            } finally {
                                downloading = false
                            }
                        }
                    },
                ) { Text(if (downloading) "Baixando…" else "Baixar") }
            },
            dismissButton = {
                TextButton(enabled = !downloading, onClick = { selected = null }) { Text("Fechar") }
            },
        )
    }
}
