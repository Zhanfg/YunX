package com.yunx.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunx.app.data.auth.GoogleDriveAuthorization
import com.yunx.app.data.auth.OAuthRedirectBus
import com.yunx.app.data.auth.OneDriveAuthorization
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.provider.GoogleDriveApi
import com.yunx.app.data.provider.OneDriveApi
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.ui.viewmodel.DriveQuotaViewModel
import com.yunx.app.ui.viewmodel.GlobalCloudViewModel
import com.yunx.app.ui.viewmodel.Pan123CloudViewModel
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import com.yunx.app.ui.viewmodel.UCCoudViewModel
import com.yunx.app.ui.viewmodel.XunleiCloudViewModel

private data class DriveAccount(
    val id: String,
    val name: String,
    val description: String,
    val avatarText: String,
    val isLoggedIn: Boolean = false,
    val actionLabel: String = "去登录"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    quarkAccount: QuarkAccountEntity?,
    ucAccount: UCAccountEntity?,
    xunleiAccount: XunleiAccountEntity?,
    baiduAccount: BaiduAccountEntity?,
    c139Account: C139AccountEntity?,
    pan123Account: Pan123AccountEntity?,
    quarkCloudViewModel: QuarkCloudViewModel,
    ucCloudViewModel: UCCoudViewModel,
    xunleiCloudViewModel: XunleiCloudViewModel,
    baiduCloudViewModel: BaiduCloudViewModel,
    c139CloudViewModel: C139CloudViewModel,
    pan123CloudViewModel: Pan123CloudViewModel,
    driveQuotaViewModel: DriveQuotaViewModel,
    downloadManager: DownloadManager,
    onQuarkLogin: () -> Unit,
    onQuarkLogout: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    onUCLogin: () -> Unit,
    onUCLogout: () -> Unit,
    onXunleiLogin: () -> Unit,
    onXunleiLogout: () -> Unit,
    onBaiduLogin: () -> Unit,
    onBaiduLogout: () -> Unit,
    onC139Login: () -> Unit,
    onC139Logout: () -> Unit,
    onPan123Login: () -> Unit,
    onPan123Logout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val googleAuthorization = remember(activity) { activity?.let(::GoogleDriveAuthorization) }
    val oneDriveAuthorization = remember(context) {
        OneDriveAuthorization(context, HttpClients.apiClient())
    }
    var googleAccessToken by remember { mutableStateOf<String?>(null) }
    var oneDriveAccessToken by remember {
        mutableStateOf(oneDriveAuthorization.loadToken()?.accessToken)
    }

    var showQuarkSheet by remember { mutableStateOf(false) }
    var showUCSheet by remember { mutableStateOf(false) }
    var showXunleiSheet by remember { mutableStateOf(false) }
    var showBaiduSheet by remember { mutableStateOf(false) }
    var showC139Sheet by remember { mutableStateOf(false) }
    var showPan123Sheet by remember { mutableStateOf(false) }
    var showCloud by rememberSaveable { mutableStateOf(false) }
    var showUCCloud by rememberSaveable { mutableStateOf(false) }
    var showXunleiCloud by rememberSaveable { mutableStateOf(false) }
    var showBaiduCloud by rememberSaveable { mutableStateOf(false) }
    var showC139Cloud by rememberSaveable { mutableStateOf(false) }
    var showPan123Cloud by rememberSaveable { mutableStateOf(false) }
    var showGoogleCloud by rememberSaveable { mutableStateOf(false) }
    var showOneDriveCloud by rememberSaveable { mutableStateOf(false) }

    val googleDriveApi = remember { GoogleDriveApi(HttpClients.apiClient()) }
    val oneDriveApi = remember { OneDriveApi(HttpClients.apiClient()) }
    val googleCloudViewModel: GlobalCloudViewModel = viewModel(
        key = "global-google-drive",
        factory = GlobalCloudViewModel.Factory(
            providerId = "google_drive",
            api = googleDriveApi,
            accessTokenProvider = { googleAccessToken },
            downloadManager = downloadManager
        )
    )
    val oneDriveCloudViewModel: GlobalCloudViewModel = viewModel(
        key = "global-onedrive",
        factory = GlobalCloudViewModel.Factory(
            providerId = "onedrive",
            api = oneDriveApi,
            accessTokenProvider = {
                oneDriveAuthorization.validAccessToken().getOrNull()?.also {
                    oneDriveAccessToken = it
                }
            },
            downloadManager = downloadManager
        )
    )

    val googleResolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            SnackbarController.show("Google Drive 授权已取消")
            return@rememberLauncherForActivityResult
        }
        when (val authResult = googleAuthorization?.consumeResolution(result.data)) {
            is GoogleDriveAuthorization.Result.Granted -> {
                googleAccessToken = authResult.accessToken
                showGoogleCloud = true
            }
            is GoogleDriveAuthorization.Result.Failed -> SnackbarController.show(authResult.message)
            is GoogleDriveAuthorization.Result.NeedsResolution -> Unit
            null -> SnackbarController.show("当前环境无法使用 Google 授权")
        }
    }

    val startGoogleAuthorization: () -> Unit = {
        val authorization = googleAuthorization
        if (authorization == null) {
            SnackbarController.show("当前 Activity 无法启动 Google 授权")
        } else {
            authorization.authorize { result ->
                when (result) {
                    is GoogleDriveAuthorization.Result.Granted -> {
                        googleAccessToken = result.accessToken
                        showGoogleCloud = true
                    }
                    is GoogleDriveAuthorization.Result.NeedsResolution -> {
                        runCatching {
                            googleResolutionLauncher.launch(
                                IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                            )
                        }.onFailure {
                            SnackbarController.show("无法打开 Google 授权页面")
                        }
                    }
                    is GoogleDriveAuthorization.Result.Failed -> SnackbarController.show(result.message)
                }
            }
        }
    }

    val startOneDriveAuthorization: () -> Unit = {
        oneDriveAuthorization.authorizationIntent()
            .onSuccess { intent ->
                runCatching { context.startActivity(intent) }
                    .onFailure { SnackbarController.show("无法打开 Microsoft 授权页面") }
            }
            .onFailure { SnackbarController.show(it.message ?: "OneDrive OAuth 尚未配置") }
    }

    // Google Play services 会缓存用户已授予的 scope；进入网盘页时只做静默恢复，不主动弹授权页。
    LaunchedEffect(googleAuthorization) {
        googleAuthorization?.authorize { result ->
            if (result is GoogleDriveAuthorization.Result.Granted) {
                googleAccessToken = result.accessToken
            }
        }
    }

    // 系统浏览器 OAuth 回调：只接收 code/state，绝不读取浏览器 Cookie。
    LaunchedEffect(oneDriveAuthorization) {
        OAuthRedirectBus.redirects.collect { uri ->
            if (oneDriveAuthorization.isRedirect(uri)) {
                oneDriveAuthorization.completeRedirect(uri)
                    .onSuccess { token ->
                        oneDriveAccessToken = token.accessToken
                        showOneDriveCloud = true
                        SnackbarController.show("OneDrive 授权完成")
                    }
                    .onFailure { SnackbarController.show(it.message ?: "OneDrive 授权失败") }
            }
        }
    }

    val quark = DriveAccount(
        id = "quark", name = "夸克网盘",
        description = quarkAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "夸", isLoggedIn = quarkAccount != null
    )
    val uc = DriveAccount(
        id = "uc", name = "UC网盘",
        description = ucAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "UC", isLoggedIn = ucAccount != null
    )
    val xunlei = DriveAccount(
        id = "xunlei", name = "迅雷网盘",
        description = xunleiAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "迅", isLoggedIn = xunleiAccount != null
    )
    val baidu = DriveAccount(
        id = "baidu", name = "百度网盘",
        description = baiduAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "度", isLoggedIn = baiduAccount != null
    )
    val c139 = DriveAccount(
        id = "c139", name = "139网盘",
        description = c139Account?.nickname ?: "点击登录，支持解析下载",
        avatarText = "139", isLoggedIn = c139Account != null
    )
    val pan123 = DriveAccount(
        id = "pan123", name = "123云盘",
        description = pan123Account?.nickname ?: "点击登录，支持解析下载",
        avatarText = "123", isLoggedIn = pan123Account != null
    )
    val googleDrive = DriveAccount(
        id = "google_drive",
        name = "Google Drive",
        description = if (googleAccessToken != null) "已授权，可浏览和下载" else "Google 官方授权，仅读取和下载 Drive 文件",
        avatarText = "G",
        isLoggedIn = googleAccessToken != null,
        actionLabel = "去授权"
    )
    val oneDrive = DriveAccount(
        id = "onedrive",
        name = "OneDrive",
        description = if (oneDriveAccessToken != null) "已授权，可浏览和下载" else "Microsoft OAuth + PKCE，使用 Files.Read",
        avatarText = "1D",
        isLoggedIn = oneDriveAccessToken != null,
        actionLabel = "去授权"
    )
    val iCloud = DriveAccount(
        id = "icloud",
        name = "iCloud Drive",
        description = "支持识别公开分享；Apple 暂无第三方 Android 私人 iCloud Drive 文件 API",
        avatarText = "iC",
        actionLabel = "说明"
    )

    LaunchedEffect(Unit) { driveQuotaViewModel.loadAll() }
    val isRefreshing by driveQuotaViewModel.loading.collectAsState()

    AnimatedContent(
        targetState = when {
            showCloud -> 1
            showUCCloud -> 2
            showXunleiCloud -> 3
            showBaiduCloud -> 4
            showC139Cloud -> 5
            showPan123Cloud -> 6
            showGoogleCloud -> 7
            showOneDriveCloud -> 8
            else -> 0
        },
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f))
                .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.98f))
        },
        label = "driveContent"
    ) { target ->
        when (target) {
            1 -> CloudDriveScreen(
                viewModel = quarkCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            2 -> UCCoudScreen(
                viewModel = ucCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showUCCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            3 -> XunleiCloudScreen(
                viewModel = xunleiCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showXunleiCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            4 -> BaiduCloudScreen(
                viewModel = baiduCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showBaiduCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            5 -> C139CloudScreen(
                viewModel = c139CloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showC139Cloud = false },
                onDownloadStarted = onDownloadStarted
            )
            6 -> Pan123CloudScreen(
                viewModel = pan123CloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showPan123Cloud = false },
                onDownloadStarted = onDownloadStarted
            )
            7 -> GlobalCloudScreen(
                title = "Google Drive",
                viewModel = googleCloudViewModel,
                onExit = { showGoogleCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            8 -> GlobalCloudScreen(
                title = "OneDrive",
                viewModel = oneDriveCloudViewModel,
                onExit = { showOneDriveCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { driveQuotaViewModel.loadAll() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "登录/授权后即可携带凭证浏览与下载；OAuth 平台不会提取授权页 Cookie",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    item(key = quark.id) {
                        DriveAccountCard(
                            account = quark,
                            quota = driveQuotaViewModel.quarkQuota.collectAsState().value,
                            onClick = if (quark.isLoggedIn) ({ showCloud = true }) else onQuarkLogin,
                            onMoreClick = if (quark.isLoggedIn) ({ showQuarkSheet = true }) else null
                        )
                    }
                    item(key = uc.id) {
                        DriveAccountCard(
                            account = uc,
                            quota = driveQuotaViewModel.ucQuota.collectAsState().value,
                            onClick = if (uc.isLoggedIn) ({ showUCCloud = true }) else onUCLogin,
                            onMoreClick = if (uc.isLoggedIn) ({ showUCSheet = true }) else null
                        )
                    }
                    item(key = xunlei.id) {
                        DriveAccountCard(
                            account = xunlei,
                            quota = driveQuotaViewModel.xunleiQuota.collectAsState().value,
                            onClick = if (xunlei.isLoggedIn) ({ showXunleiCloud = true }) else onXunleiLogin,
                            onMoreClick = if (xunlei.isLoggedIn) ({ showXunleiSheet = true }) else null
                        )
                    }
                    item(key = baidu.id) {
                        DriveAccountCard(
                            account = baidu,
                            quota = driveQuotaViewModel.baiduQuota.collectAsState().value,
                            onClick = if (baidu.isLoggedIn) ({ showBaiduCloud = true }) else onBaiduLogin,
                            onMoreClick = if (baidu.isLoggedIn) ({ showBaiduSheet = true }) else null
                        )
                    }
                    item(key = c139.id) {
                        DriveAccountCard(
                            account = c139,
                            quota = driveQuotaViewModel.c139Quota.collectAsState().value,
                            onClick = if (c139.isLoggedIn) ({ showC139Cloud = true }) else onC139Login,
                            onMoreClick = if (c139.isLoggedIn) ({ showC139Sheet = true }) else null
                        )
                    }
                    item(key = pan123.id) {
                        DriveAccountCard(
                            account = pan123,
                            quota = driveQuotaViewModel.pan123Quota.collectAsState().value,
                            onClick = if (pan123.isLoggedIn) ({ showPan123Cloud = true }) else onPan123Login,
                            onMoreClick = if (pan123.isLoggedIn) ({ showPan123Sheet = true }) else null
                        )
                    }
                    item(key = googleDrive.id) {
                        DriveAccountCard(
                            account = googleDrive,
                            onClick = if (googleDrive.isLoggedIn) ({ showGoogleCloud = true }) else startGoogleAuthorization
                        )
                    }
                    item(key = oneDrive.id) {
                        DriveAccountCard(
                            account = oneDrive,
                            onClick = if (oneDrive.isLoggedIn) ({ showOneDriveCloud = true }) else startOneDriveAuthorization
                        )
                    }
                    item(key = iCloud.id) {
                        DriveAccountCard(
                            account = iCloud,
                            onClick = {
                                SnackbarController.show(
                                    "iCloud Drive 当前仅接公开分享识别；Apple 未提供第三方 Android 私人文件 API"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showQuarkSheet && quarkAccount != null) {
        QuarkAccountSheet(
            account = quarkAccount,
            onLogout = { onQuarkLogout(); showQuarkSheet = false },
            onDismiss = { showQuarkSheet = false }
        )
    }
    if (showUCSheet && ucAccount != null) {
        UCAccountSheet(
            account = ucAccount,
            onLogout = { onUCLogout(); showUCSheet = false },
            onDismiss = { showUCSheet = false }
        )
    }
    if (showXunleiSheet && xunleiAccount != null) {
        XunleiAccountSheet(
            account = xunleiAccount,
            onLogout = { onXunleiLogout(); showXunleiSheet = false },
            onDismiss = { showXunleiSheet = false }
        )
    }
    if (showBaiduSheet && baiduAccount != null) {
        BaiduAccountSheet(
            account = baiduAccount,
            onLogout = { onBaiduLogout(); showBaiduSheet = false },
            onDismiss = { showBaiduSheet = false }
        )
    }
    if (showC139Sheet && c139Account != null) {
        C139AccountSheet(
            account = c139Account,
            onLogout = { onC139Logout(); showC139Sheet = false },
            onDismiss = { showC139Sheet = false }
        )
    }
    if (showPan123Sheet && pan123Account != null) {
        Pan123AccountSheet(
            account = pan123Account,
            onLogout = { onPan123Logout(); showPan123Sheet = false },
            onDismiss = { showPan123Sheet = false }
        )
    }
}

@Composable
private fun DriveAccountCard(
    account: DriveAccount,
    quota: QuotaInfo? = null,
    onClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null
) {
    val cardShape = MaterialTheme.shapes.large
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val content: @Composable () -> Unit = {
        DriveAccountCardContent(
            account = account,
            quota = quota,
            clickable = onClick != null,
            onMoreClick = onMoreClick
        )
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors
        ) { content() }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors
        ) { content() }
    }
}

@Composable
private fun DriveAccountCardContent(
    account: DriveAccount,
    quota: QuotaInfo? = null,
    clickable: Boolean,
    onMoreClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = if (account.isLoggedIn) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = account.avatarText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = account.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(
                visible = account.isLoggedIn && quota != null,
                enter = fadeIn(tween(300)) + expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(300)
                ),
                exit = fadeOut(tween(200)) + shrinkVertically(animationSpec = tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    quota?.let { QuotaInlineBar(it) }
                }
            }
        }

        when {
            account.isLoggedIn && onMoreClick != null -> IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            account.isLoggedIn -> LoginBadge(isLoggedIn = true)
            clickable -> Text(
                text = account.actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            else -> LoginBadge(isLoggedIn = false)
        }
    }
}

@Composable
private fun QuotaInlineBar(quota: QuotaInfo) {
    val ratio = if (quota.total > 0) {
        (quota.used.toFloat() / quota.total.toFloat()).coerceIn(0f, 1f)
    } else 0f
    Column {
        Text(
            text = "已用 ${formatBytes(quota.used)} / ${formatBytes(quota.total)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun LoginBadge(isLoggedIn: Boolean) {
    val (label, color) = if (isLoggedIn) {
        "已授权" to MaterialTheme.colorScheme.primary
    } else {
        "未登录" to MaterialTheme.colorScheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
}
