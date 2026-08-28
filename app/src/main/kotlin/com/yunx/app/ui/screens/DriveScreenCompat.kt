package com.yunx.app.ui.screens

import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.ui.viewmodel.DownloadViewModel
import com.yunx.app.ui.viewmodel.DriveQuotaViewModel
import com.yunx.app.ui.viewmodel.Pan123CloudViewModel
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import com.yunx.app.ui.viewmodel.UCCoudViewModel
import com.yunx.app.ui.viewmodel.XunleiCloudViewModel

/**
 * Compatibility overload for the existing MainScreen call site.
 *
 * The Activity-level DownloadViewModel already owns the process's DownloadManager. Reusing it here
 * keeps Google Drive / OneDrive tasks under the exact same pause/resume/remove lifecycle as every
 * existing provider without expanding MainScreen's constructor chain.
 */
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
    val sharedDownloadViewModel: DownloadViewModel = viewModel()
    DriveScreen(
        scrollBehavior = scrollBehavior,
        quarkAccount = quarkAccount,
        ucAccount = ucAccount,
        xunleiAccount = xunleiAccount,
        baiduAccount = baiduAccount,
        c139Account = c139Account,
        pan123Account = pan123Account,
        quarkCloudViewModel = quarkCloudViewModel,
        ucCloudViewModel = ucCloudViewModel,
        xunleiCloudViewModel = xunleiCloudViewModel,
        baiduCloudViewModel = baiduCloudViewModel,
        c139CloudViewModel = c139CloudViewModel,
        pan123CloudViewModel = pan123CloudViewModel,
        driveQuotaViewModel = driveQuotaViewModel,
        downloadManager = sharedDownloadViewModel.manager,
        onQuarkLogin = onQuarkLogin,
        onQuarkLogout = onQuarkLogout,
        onDownloadStarted = onDownloadStarted,
        onUCLogin = onUCLogin,
        onUCLogout = onUCLogout,
        onXunleiLogin = onXunleiLogin,
        onXunleiLogout = onXunleiLogout,
        onBaiduLogin = onBaiduLogin,
        onBaiduLogout = onBaiduLogout,
        onC139Login = onC139Login,
        onC139Logout = onC139Logout,
        onPan123Login = onPan123Login,
        onPan123Logout = onPan123Logout,
        modifier = modifier
    )
}
