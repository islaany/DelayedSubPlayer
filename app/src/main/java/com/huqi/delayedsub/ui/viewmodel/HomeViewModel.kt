package com.huqi.delayedsub.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.huqi.delayedsub.DelayedSubApplication
import com.huqi.delayedsub.data.database.VideoEntity
import com.huqi.delayedsub.data.database.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: VideoRepository = (app as DelayedSubApplication).container.videoRepository

    val videos: Flow<List<VideoEntity>> = repo.observeAll()

    private val _imported = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val imported: SharedFlow<Long> = _imported.asSharedFlow()

    fun importVideo(videoUri: Uri, subtitleUri: Uri?) {
        viewModelScope.launch {
            val name = queryTitle(videoUri) ?: "未命名视频"
            val id = repo.add(videoUri.toString(), name, subtitleUri?.toString())
            _imported.tryEmit(id)
        }
    }

    fun remove(video: VideoEntity) = viewModelScope.launch { repo.delete(video) }

    private fun queryTitle(uri: Uri): String? {
        val cr = getApplication<Application>().contentResolver
        val raw = if (uri.scheme == "content") {
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
        } else {
            uri.lastPathSegment
        }
        return raw?.substringBeforeLast(".") ?: raw
    }

    companion object {
        fun Factory(app: Application) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(app) as T
        }
    }
}
