package com.wallpaperz.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

interface UpdateSource {
    fun latest(): UpdateRelease
    fun openApk(release: UpdateRelease): InputStream
}

class GitHubUpdateSource : UpdateSource {
    private fun open(url: String): InputStream {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "WallpaperZ-Android")
        try {
            check(connection.responseCode == 200) { "暂时无法连接更新源，请稍后重试" }
            return object : FilterInputStream(connection.inputStream) {
                override fun close() { try { super.close() } finally { connection.disconnect() } }
            }
        } catch (e: Exception) { connection.disconnect(); throw e }
    }
    override fun latest(): UpdateRelease = open(UPDATE_MANIFEST_URL).bufferedReader().use {
        Json.decodeFromString<UpdateRelease>(it.readText())
    }
    override fun openApk(release: UpdateRelease): InputStream = open(release.apkUrl)
}

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val release: UpdateRelease) : UpdateStatus
    data class Downloading(val release: UpdateRelease, val bytes: Long) : UpdateStatus
    data class Ready(val release: UpdateRelease, val file: File, val error: String? = null) : UpdateStatus
    data class Failed(val message: String, val release: UpdateRelease? = null) : UpdateStatus
}

@Serializable private data class UpdateChecks(val lastCheckAt: Long = 0)

class AppUpdates(private val context: Context, scope: CoroutineScope, private val source: UpdateSource = GitHubUpdateSource()) {
    private val mutableStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status = mutableStatus.asStateFlow()
    val visible = MutableStateFlow(false)
    private val operation = Mutex()
    val installedVersion = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    private val checks by lazy {
        DataStoreFactory.create(serializer = object : Serializer<UpdateChecks> {
            override val defaultValue = UpdateChecks()
            override suspend fun readFrom(input: InputStream) = Json.decodeFromString<UpdateChecks>(input.readBytes().decodeToString())
            override suspend fun writeTo(t: UpdateChecks, output: OutputStream) = output.write(Json.encodeToString(t).encodeToByteArray())
        }, scope = scope, produceFile = { context.dataStoreFile("updates.json") })
    }

    suspend fun check(manual: Boolean = false) {
        if (manual) visible.value = true
        if (!operation.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                if (!manual) {
                    val now = System.currentTimeMillis()
                    if (now - checks.data.first().lastCheckAt < 24 * 60 * 60 * 1000L) return@withContext
                    checks.updateData { UpdateChecks(now) }
                }
                mutableStatus.value = UpdateStatus.Checking
                val release = source.latest().also { it.validate(Build.VERSION.SDK_INT) }
                mutableStatus.value = if (release.versionCode > installedVersion) UpdateStatus.Available(release) else UpdateStatus.Current
                if (release.versionCode > installedVersion) visible.value = true
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutableStatus.value = UpdateStatus.Failed(message(e)) }
        finally { operation.unlock() }
    }

    suspend fun download(release: UpdateRelease) {
        if (!operation.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                release.validate(Build.VERSION.SDK_INT)
                require(release.versionCode > installedVersion) { "该版本无需更新" }
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                val partial = File(directory, "update.apk.part")
                val target = File(directory, "update.apk")
                mutableStatus.value = UpdateStatus.Downloading(release, 0)
                try {
                    val coroutine = currentCoroutineContext()
                    var lastPercent = -1L
                    source.openApk(release).use { input ->
                        copyUpdate(input, partial, release) { bytes ->
                            coroutine.ensureActive()
                            val percent = bytes * 100 / release.sizeBytes
                            if (percent != lastPercent) {
                                lastPercent = percent
                                mutableStatus.value = UpdateStatus.Downloading(release, bytes)
                            }
                        }
                    }
                    val archive = context.packageManager.getPackageArchiveInfo(partial.path, PackageManager.GET_SIGNING_CERTIFICATES)
                    requireNotNull(archive) { "无法读取安装包" }
                    val cert = archive.signingInfo?.apkContentsSigners?.singleOrNull()
                    requireNotNull(cert) { "无法读取安装包签名" }
                    validateUpdateArchive(release, archive.packageName, archive.longVersionCode, sha256(cert.toByteArray()))
                    require(partial.renameTo(target)) { "无法保存安装包，请重试" }
                    mutableStatus.value = UpdateStatus.Ready(release, target)
                } finally { partial.delete() }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutableStatus.value = UpdateStatus.Failed(message(e), release) }
        finally { operation.unlock() }
    }

    fun installIntent(ready: UpdateStatus.Ready): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.file), "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun installError(message: String) {
        (mutableStatus.value as? UpdateStatus.Ready)?.let { mutableStatus.value = it.copy(error = message) }
    }

    private fun message(e: Exception) = if (e is IllegalArgumentException || e is IllegalStateException)
        e.message ?: "更新信息无效，请稍后重试" else "暂时无法完成更新，请检查网络后重试"
}
