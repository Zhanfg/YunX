package com.yunx.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.provider.GlobalCloudApi
import com.yunx.app.data.provider.GlobalCloudFile
import kotlinx.coroutines.launch

sealed interface GlobalCloudUiState {
    data object Idle : GlobalCloudUiState
    data object Loading : GlobalCloudUiState
    data class Content(val files: List<GlobalCloudFile>) : GlobalCloudUiState
    data class Error(val message: String) : GlobalCloudUiState
}

class GlobalCloudViewModel(
    val providerId: String,
    private val api: GlobalCloudApi,
    private val accessTokenProvider: suspend () -> String?,
    private val enqueueDownload: (url: String, fileName: String, headers: Map<String, String>, size: Long) -> Unit
) : ViewModel() {
    var uiState by mutableStateOf<GlobalCloudUiState>(GlobalCloudUiState.Idle)
        private set

    var pathNames by mutableStateOf<List<String>>(emptyList())
        private set

    var downloadStarted by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    private data class Directory(val id: String?, val name: String)
    private val stack = ArrayDeque<Directory>()
    private var current = Directory(null, "")

    fun loadRoot() {
        stack.clear()
        current = Directory(null, "")
        pathNames = emptyList()
        loadCurrent()
    }

    fun refresh() = loadCurrent()

    fun openFolder(file: GlobalCloudFile) {
        if (!file.isFolder) return
        stack.addLast(current)
        current = Directory(file.id, file.name)
        pathNames = pathNames + file.name
        loadCurrent()
    }

    fun goBack(): Boolean {
        if (stack.isEmpty()) return false
        current = stack.removeLast()
        pathNames = pathNames.dropLast(1)
        loadCurrent()
        return true
    }

    fun download(file: GlobalCloudFile) {
        if (file.isFolder) return
        viewModelScope.launch {
            val token = accessTokenProvider()
            if (token.isNullOrBlank()) {
                message = "授权已失效，请重新授权"
                return@launch
            }
            api.getDownloadSource(file, token)
                .onSuccess { source ->
                    enqueueDownload(
                        source.primaryUrl,
                        file.name,
                        source.headers,
                        file.size.takeIf { it > 0 } ?: -1L
                    )
                    downloadStarted = true
                    message = "已加入下载任务"
                }
                .onFailure { message = it.message ?: "获取下载地址失败" }
        }
    }

    fun consumeDownloadStarted() {
        downloadStarted = false
    }

    fun consumeMessage() {
        message = null
    }

    private fun loadCurrent() {
        viewModelScope.launch {
            uiState = GlobalCloudUiState.Loading
            val token = accessTokenProvider()
            if (token.isNullOrBlank()) {
                uiState = GlobalCloudUiState.Error("授权已失效，请重新授权")
                return@launch
            }
            api.listFiles(current.id, token)
                .onSuccess { files ->
                    uiState = GlobalCloudUiState.Content(
                        files.sortedWith(
                            compareByDescending<GlobalCloudFile> { it.isFolder }
                                .thenBy { it.name.lowercase() }
                        )
                    )
                }
                .onFailure { uiState = GlobalCloudUiState.Error(it.message ?: "读取网盘失败") }
        }
    }

    class Factory(
        private val providerId: String,
        private val api: GlobalCloudApi,
        private val accessTokenProvider: suspend () -> String?,
        private val enqueueDownload: (String, String, Map<String, String>, Long) -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GlobalCloudViewModel::class.java))
            return GlobalCloudViewModel(providerId, api, accessTokenProvider, enqueueDownload) as T
        }
    }
}
