package com.wallpaperz.app

import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

const val UPDATE_REPOSITORY = "https://github.com/azhitang/wallpaper-z"
const val UPDATE_MANIFEST_URL = "$UPDATE_REPOSITORY/releases/latest/download/update.json"
const val RELEASE_CERT_SHA256 = "5b7268a0347aa5b4ceb5d212d7f2ce32fe7ec73ca7074f18ba5b6a64b35e1490"

@Serializable data class UpdateRelease(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String
) {
    fun validate(deviceSdk: Int) {
        require(packageName == "com.wallpaperz.app") { "更新包不属于壁纸z" }
        require(versionCode > 0 && versionName.isNotBlank()) { "更新版本信息无效" }
        require(minSdk <= deviceSdk) { "新版需要更高版本的 Android" }
        require(apkUrl.startsWith("$UPDATE_REPOSITORY/releases/download/")) { "更新下载地址无效" }
        require(sizeBytes > 0 && sha256.matches(Regex("[0-9a-f]{64}"))) { "更新校验信息无效" }
    }
}

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }

/** Copies an update while checking the exact published size and SHA-256. */
fun copyUpdate(input: InputStream, target: File, release: UpdateRelease, onProgress: (Long) -> Unit) {
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    target.outputStream().use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            total += size
            require(total <= release.sizeBytes) { "下载大小与发布信息不一致" }
            digest.update(buffer, 0, size)
            output.write(buffer, 0, size)
            onProgress(total)
        }
    }
    require(total == release.sizeBytes) { "下载不完整，请重新下载" }
    require(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "安装包校验失败，请重新下载" }
}

fun validateUpdateArchive(release: UpdateRelease, packageName: String, versionCode: Long, certSha256: String) {
    require(packageName == release.packageName) { "安装包包名不匹配" }
    require(versionCode == release.versionCode) { "安装包版本不匹配" }
    require(certSha256 == RELEASE_CERT_SHA256) { "安装包正式签名不匹配" }
}
