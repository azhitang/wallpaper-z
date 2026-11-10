package com.wallpaperz.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateReleaseTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = "abc".toByteArray()
    private val release = UpdateRelease("com.wallpaperz.app", 12, "0.6.1", 34,
        "$UPDATE_REPOSITORY/releases/download/v0.6.1/wallpaperz-v0.6.1.apk",
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", 3, "更新说明")

    @Test fun validReleaseIsAcceptedOnSupportedAndroid() { release.validate(34) }

    @Test fun wrongPackageMetadataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { release.copy(packageName = "other.app").validate(36) }
    }

    @Test fun insecureAndOtherRepositoryDownloadsAreRejected() {
        listOf("http://github.com/azhitang/wallpaper-z/releases/download/a.apk",
            "https://github.com/another/repo/releases/download/a.apk",
            "https://github.com.attacker.example/azhitang/wallpaper-z/releases/download/a.apk").forEach {
            assertThrows(IllegalArgumentException::class.java) { release.copy(apkUrl = it).validate(36) }
        }
    }

    @Test fun unsupportedAndroidIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { release.copy(minSdk = 37).validate(36) }
    }

    @Test fun missingIntegrityMetadataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { release.copy(sha256 = "").validate(36) }
        assertThrows(IllegalArgumentException::class.java) { release.copy(sizeBytes = 0).validate(36) }
    }

    @Test fun invalidVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { release.copy(versionCode = 0).validate(36) }
        assertThrows(IllegalArgumentException::class.java) { release.copy(versionName = "").validate(36) }
    }

    @Test fun completeDownloadMatchesExactBytesAndProgress() {
        val target = temporary.newFile()
        var progress = 0L
        bytes.inputStream().use { copyUpdate(it, target, release) { received -> progress = received } }
        assertArrayEquals(bytes, target.readBytes())
        assertEquals(3L, progress)
    }

    @Test fun truncatedDownloadIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            "ab".byteInputStream().use { copyUpdate(it, temporary.newFile(), release) {} }
        }
    }

    @Test fun oversizedDownloadIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            "abcd".byteInputStream().use { copyUpdate(it, temporary.newFile(), release) {} }
        }
    }

    @Test fun sameSizeCorruptedDownloadIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            "abd".byteInputStream().use { copyUpdate(it, temporary.newFile(), release) {} }
        }
    }

    @Test fun archiveWithExpectedPackageVersionAndFixedSignerIsAccepted() {
        validateUpdateArchive(release, "com.wallpaperz.app", 12, RELEASE_CERT_SHA256)
    }

    @Test fun wrongArchivePackageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { validateUpdateArchive(release, "other.app", 12, RELEASE_CERT_SHA256) }
    }

    @Test fun wrongArchiveVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { validateUpdateArchive(release, "com.wallpaperz.app", 11, RELEASE_CERT_SHA256) }
    }

    @Test fun untrustedArchiveSignerIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { validateUpdateArchive(release, "com.wallpaperz.app", 12, "0".repeat(64)) }
    }
}
