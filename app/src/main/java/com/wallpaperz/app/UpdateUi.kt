package com.wallpaperz.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.Locale

@Composable fun UpdateSettingsRow() {
    val app = LocalContext.current.wallpaperApp
    val status by app.updates.status.collectAsStateWithLifecycle()
    val subtitle = when (val current = status) {
        UpdateStatus.Idle -> "每天首次打开时自动检查"
        UpdateStatus.Checking -> "正在检查新版本…"
        UpdateStatus.Current -> "已是最新版本"
        is UpdateStatus.Available -> "发现新版本 ${current.release.versionName}"
        is UpdateStatus.Downloading -> "正在下载 ${current.bytes * 100 / current.release.sizeBytes}%"
        is UpdateStatus.Ready -> "已下载，轻点安装更新"
        is UpdateStatus.Failed -> "检查或下载失败，轻点重试"
    }
    ZSettingsRow("版本更新", subtitle, ZSymbol.DOWN, MaterialTheme.colorScheme.primary, {
        if (status is UpdateStatus.Ready || status is UpdateStatus.Downloading) app.updates.visible.value = true
        else app.scope.launch { app.updates.check(manual = true) }
    }, Modifier.testTag("check_updates"), value = app.updates.versionName)
}

@Composable fun AppUpdateHost(app: WallpaperZApp) {
    val updates = app.updates
    val status by updates.status.collectAsStateWithLifecycle()
    val visible by updates.visible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun install() {
        val ready = updates.status.value as? UpdateStatus.Ready ?: return
        try { installer.launch(updates.installIntent(ready)) }
        catch (_: ActivityNotFoundException) { updates.installError("未找到系统安装器，请稍后重试") }
    }
    val allowInstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
        else updates.installError("请先允许壁纸z安装更新，再点击安装")
    }
    if (!visible) return
    val release = when (val current = status) {
        is UpdateStatus.Available -> current.release
        is UpdateStatus.Downloading -> current.release
        is UpdateStatus.Ready -> current.release
        is UpdateStatus.Failed -> current.release
        else -> null
    }
    ZSheet(if (release == null) "版本更新" else "发现新版本 ${release.versionName}", { updates.visible.value = false },
        "当前版本 ${updates.versionName} · 更新会保留图库和设置") {
        Column(Modifier.padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
            if (release != null) {
                Text(String.format(Locale.ROOT, "安装包 %.1f MB", release.sizeBytes / 1_000_000.0),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (release.notes.isNotBlank()) Text(release.notes, Modifier.padding(top = 14.dp, bottom = 20.dp),
                    fontSize = 15.sp, lineHeight = 23.sp)
            }
            when (val current = status) {
                UpdateStatus.Idle, UpdateStatus.Checking -> Text("正在检查新版本…", Modifier.padding(vertical = 20.dp))
                UpdateStatus.Current -> {
                    Text("已是最新版本", Modifier.padding(vertical = 20.dp), fontWeight = FontWeight.Medium)
                    ZButton("完成", { updates.visible.value = false }, Modifier.fillMaxWidth())
                }
                is UpdateStatus.Available -> ZButton("下载更新", { app.scope.launch { updates.download(current.release) } },
                    Modifier.fillMaxWidth().testTag("download_update"), symbol = ZSymbol.DOWN)
                is UpdateStatus.Downloading -> {
                    val progress = current.bytes.toFloat() / current.release.sizeBytes
                    Text("正在下载 ${(progress * 100).toInt()}%", Modifier.padding(bottom = 12.dp), fontSize = 16.sp)
                    Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.secondaryContainer)) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    }
                    Text("下载时请保持应用打开", Modifier.padding(top = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is UpdateStatus.Ready -> {
                    Text(current.error ?: "下载与签名校验已完成", Modifier.padding(bottom = 14.dp), fontSize = 14.sp,
                        color = if (current.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    ZButton("安装更新", {
                        if (context.packageManager.canRequestPackageInstalls()) install()
                        else allowInstall.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    }, Modifier.fillMaxWidth().testTag("install_update"))
                }
                is UpdateStatus.Failed -> {
                    Text(current.message, Modifier.padding(vertical = 16.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    ZButton(if (current.release == null) "重新检查" else "重新下载", {
                        app.scope.launch { if (current.release == null) updates.check(manual = true) else updates.download(current.release) }
                    }, Modifier.fillMaxWidth().testTag("retry_update"))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
