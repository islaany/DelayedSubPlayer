package com.huqi.delayedsub.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.huqi.delayedsub.DelayedSubApplication
import com.huqi.delayedsub.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as DelayedSubApplication
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(app))
    val videos by vm.videos.collectAsState(initial = emptyList())

    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showSubtitlePrompt by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        pendingVideoUri = uri
        showSubtitlePrompt = true
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val v = pendingVideoUri
        if (v != null) {
            val sub = if (uri != null) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                uri
            } else null
            vm.importVideo(v, sub)
        }
        pendingVideoUri = null
        showSubtitlePrompt = false
    }

    LaunchedEffect(Unit) {
        vm.imported.collect { id -> navController.navigate("player/$id") }
    }

    if (showSubtitlePrompt) {
        AlertDialog(
            onDismissRequest = {
                pendingVideoUri?.let { vm.importVideo(it, null) }
                pendingVideoUri = null
                showSubtitlePrompt = false
            },
            title = { Text("选择外部字幕（可选）") },
            text = { Text("视频内嵌的字幕会在播放页自动识别并使用。若另有 .srt 字幕文件，可在此选择；没有的话直接跳过即可。") },
            confirmButton = {
                TextButton(onClick = {
                    showSubtitlePrompt = false
                    subtitlePicker.launch(arrayOf("application/x-subrip", "text/plain", "application/octet-stream"))
                }) { Text("选择字幕") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingVideoUri?.let { vm.importVideo(it, null) }
                    pendingVideoUri = null
                    showSubtitlePrompt = false
                }) { Text("跳过") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("最近学习") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                Icon(Icons.Default.Add, contentDescription = "导入视频")
            }
        }
    ) { padding ->
        if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有视频，点击右下角导入", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videos, key = { it.id }) { v ->
                    Card(Modifier.fillMaxWidth().clickable { navController.navigate("player/${v.id}") }) {
                        ListItem(
                            headlineContent = { Text(v.title) },
                            supportingContent = {
                                Text(
                                    "进度 ${formatMs(v.lastPositionMs)}" +
                                        if (v.subtitleUri != null) " · 含字幕" else ""
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Movie, contentDescription = null) },
                            trailingContent = {
                                IconButton(onClick = { vm.remove(v) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
