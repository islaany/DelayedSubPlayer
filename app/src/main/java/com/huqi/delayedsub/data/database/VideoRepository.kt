package com.huqi.delayedsub.data.database

import kotlinx.coroutines.flow.Flow

class VideoRepository(private val dao: VideoDao) {

    fun observeAll(): Flow<List<VideoEntity>> = dao.observeAll()

    suspend fun get(id: Long): VideoEntity? = dao.get(id)

    suspend fun add(videoUri: String, title: String, subtitleUri: String? = null): Long =
        dao.insert(VideoEntity(videoUri = videoUri, subtitleUri = subtitleUri, title = title))

    /** 更新最近访问时间与播放进度（断点续播）。 */
    suspend fun touch(id: Long, lastPositionMs: Long) {
        val v = dao.get(id) ?: return
        dao.update(v.copy(lastAccessedAt = System.currentTimeMillis(), lastPositionMs = lastPositionMs))
    }

    suspend fun delete(video: VideoEntity) = dao.delete(video)
}
