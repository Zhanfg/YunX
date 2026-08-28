package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 下载页 ViewModel：任务列表（Room Flow → StateFlow）+ 实时统计 + 操作转发。
 */
class DownloadViewModel(internal val manager: DownloadManager) : ViewModel() {

    val tasks: StateFlow<List<DownloadTaskEntity>> = manager.tasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 实时下载统计：任务 id → 速度/剩余时间/线程数 */
    val stats: StateFlow<Map<Long, DownloadStats>> = manager.stats

    /** 添加下载任务（headers 可携带 Referer/OAuth Bearer/Cookie 等；敏感值不进入 UI）。 */
    fun enqueue(
        url: String,
        fileName: String,
        headers: Map<String, String> = emptyMap(),
        size: Long = -1L
    ) {
        viewModelScope.launch { manager.enqueue(url, fileName, headers, size) }
    }

    fun pause(id: Long) = manager.pause(id)

    fun resume(id: Long) = manager.start(id)

    fun remove(id: Long, deleteLocal: Boolean = false) = manager.remove(id, deleteLocal)

    fun pauseAll() {
        tasks.value.filter {
            it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                it.status == DownloadTaskEntity.STATUS_PENDING
        }.forEach { manager.pause(it.id) }
    }

    fun resumeAll() {
        tasks.value.filter {
            it.status == DownloadTaskEntity.STATUS_PAUSED ||
                it.status == DownloadTaskEntity.STATUS_FAILED
        }.forEach { manager.start(it.id) }
    }

    fun removeAll(deleteLocal: Boolean = false) {
        tasks.value.toList().forEach { manager.remove(it.id, deleteLocal) }
    }

    class Factory(private val manager: DownloadManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DownloadViewModel::class.java))
            return DownloadViewModel(manager) as T
        }
    }
}
