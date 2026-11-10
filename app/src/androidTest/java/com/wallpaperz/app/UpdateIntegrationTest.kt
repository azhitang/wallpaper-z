package com.wallpaperz.app

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.Direction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class UpdateIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.wallpaperApp
    private val installed = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
    private val bytes = "abc".toByteArray()
    private val release = UpdateRelease(app.packageName, installed + 1, "next", 34,
        "$UPDATE_REPOSITORY/releases/download/test/update.apk", sha256(bytes), bytes.size.toLong(), "更新说明")
    private fun controller(info: UpdateRelease = release, fail: Boolean = false) = AppUpdates(app, app.scope, object : UpdateSource {
        override fun latest(): UpdateRelease { if (fail) throw IOException("offline"); return info }
        override fun openApk(release: UpdateRelease) = bytes.inputStream()
    })

    @Test fun newerVersionIsOfferedAndSameOrOlderIsNot() = runBlocking {
        val newer = controller()
        newer.check(manual = true)
        assertTrue(newer.status.value is UpdateStatus.Available)
        assertTrue(newer.visible.value)
        for (version in listOf(installed, installed - 1)) {
            val current = controller(release.copy(versionCode = version))
            current.check(manual = true)
            assertEquals(UpdateStatus.Current, current.status.value)
        }
    }

    @Test fun connectionFailureIsVisibleForManualCheck() = runBlocking {
        val updates = controller(fail = true)
        updates.check(manual = true)
        assertTrue(updates.status.value is UpdateStatus.Failed)
        assertTrue(updates.visible.value)
    }

    @Test fun corruptDownloadIsRejectedAndPartialFileIsRemoved() = runBlocking {
        val updates = controller()
        updates.download(release.copy(sha256 = "0".repeat(64)))
        assertTrue((updates.status.value as UpdateStatus.Failed).message.contains("校验失败"))
        assertFalse(File(app.cacheDir, "updates/update.apk.part").exists())
    }

    @Test fun nonApkWithValidHashIsRejectedByPlatformParser() = runBlocking {
        val updates = controller()
        updates.download(release)
        assertTrue((updates.status.value as UpdateStatus.Failed).message.contains("无法读取安装包"))
        assertFalse(File(app.cacheDir, "updates/update.apk.part").exists())
    }

    @Test fun signedReleaseOpensSystemInstallerWithContentUri() {
        val file = File(app.getExternalFilesDir(null), "test-update.apk")
        assertTrue("Push the signed release APK into the app's external files directory before this test", file.isFile)
        val archive = app.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)!!
        val cert = archive.signingInfo!!.apkContentsSigners.single()
        val info = release.copy(versionCode = archive.longVersionCode, sizeBytes = file.length())
        validateUpdateArchive(info, archive.packageName, archive.longVersionCode, sha256(cert.toByteArray()))
        val cached = File(app.cacheDir, "updates/installer-test.apk").apply { parentFile!!.mkdirs(); file.copyTo(this, overwrite = true) }
        val updates = controller()
        val intent = updates.installIntent(UpdateStatus.Ready(info, cached))
        assertEquals("content", intent.data!!.scheme)
        assertEquals("${app.packageName}.updates", intent.data!!.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        if (!app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("Allow from this source")), 20_000))
            device.findObject(By.text("Allow from this source")).click()
            device.pressBack()
        }
        assertTrue(app.packageManager.canRequestPackageInstalls())
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Update")), 20_000))
        device.pressBack()
    }

    @Test fun manualCheckUsesPublishedGitHubManifest() {
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.wait(Until.findObject(By.res("settings_tab")), 20_000)!!.click()
        val list = device.wait(Until.findObject(By.res("settings_list")), 10_000)!!
        list.scrollUntil(Direction.DOWN, Until.findObject(By.res("check_updates")))!!.click()
        assertTrue(device.wait(Until.hasObject(By.text("已是最新版本")), 45_000))
        device.wait(Until.findObject(By.text("完成")), 10_000)!!.click()
    }
}
