package com.wallpaperz.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Read-only snapshot for comparing private media and settings across a signed upgrade. */
@RunWith(AndroidJUnit4::class)
class SigningUpgradeTest {
    @Test fun releaseSignatureAndPreservedFiles() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyReleaseUpgrade") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        assertEquals(args.getString("expectedVersionCode")!!.toLong(), info.longVersionCode)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val signing = info.signingInfo!!
        assertEquals("5b7268a0347aa5b4ceb5d212d7f2ce32fe7ec73ca7074f18ba5b6a64b35e1490",
            digest(signing.apkContentsSigners.single().toByteArray()))
        assertTrue(signing.signingCertificateHistory.any {
            digest(it.toByteArray()) == "1c7ff5ca284d684218c8a7e056ac16775c6309c32460818b6f201c5706b61036"
        })
        val files = JSONObject()
        context.filesDir.walkTopDown().filter { it.isFile }.forEach {
            files.put(it.relativeTo(context.filesDir).invariantSeparatorsPath, digest(it.readBytes()))
        }
        val snapshot = JSONObject().put("files", files)
            .put("state", JSONObject(context.filesDir.resolve("datastore/carousel.json").readText()))
        instrumentation.sendStatus(0, Bundle().apply { putString("upgradeSnapshot", snapshot.toString()) })
    }
}
