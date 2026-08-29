package com.yunx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunx.app.data.provider.GlobalCloudFile
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.viewmodel.GlobalCloudUiState
import com.yunx.app.ui.viewmodel.GlobalCloudViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalCloudScreen(
    title: String,
    viewModel: GlobalCloudViewModel,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        if (state is GlobalCloudUiState.Idle) viewModel.loadRoot()
    }
    LaunchedEffect(viewModel.downloadStarted) {
        if (viewModel.downloadStarted) {
            onDownloadStarted()
            viewModel.consumeDownloadStarted()
        }
    }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            SnackbarController.show(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title)
                    if (viewModel.pathNames.isNotEmpty()) {
                        Text(
                            text = viewModel.pathNames.joinToString(" / "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (!viewModel.goBack()) onExit()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            }
        )

        when (state) {
            GlobalCloudUiState.Idle,
            GlobalCloudUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            is GlobalCloudUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is GlobalCloudUiState.Content -> {
                if (state.files.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("此文件夹为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.files, key = { it.id }) { file ->
                            GlobalCloudFileRow(
                                file = file,
                                onOpen = { viewModel.openFolder(file) },
                                onDownload = { viewModel.download(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalCloudFileRow(
    file: GlobalCloudFile,
    onOpen: () -> Unit,
    onDownload: () -> Unit
) {
    ListItem(
        headlineContent = { Text(file.name, maxLines = 2) },
        supportingContent = {
            if (!file.isFolder && file.size > 0) {
                Text(formatGlobalCloudBytes(file.size))
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (file.isFolder) Icons.Outlined.Folder else Icons.Outlined.Description,
                contentDescription = null
            )
        },
        trailingContent = {
            if (!file.isFolder) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = "下载")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = file.isFolder, onClick = onOpen)
            .padding(horizontal = 4.dp)
    )
}

private fun formatGlobalCloudBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format("%.1f %s", value, units[unit])
}
