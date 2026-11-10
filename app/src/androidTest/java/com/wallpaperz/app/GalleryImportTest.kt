package com.wallpaperz.app

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class GalleryImportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.wallpaperApp
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var saved: CarouselState
    private val sources = mutableListOf<Uri>()
    private val monitors = mutableListOf<Instrumentation.ActivityMonitor>()

    @Before fun prepare() = runBlocking {
        saved = app.repository.read()
        app.controller.pause()
        app.repository.update { CarouselState() }
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.wait(Until.findObject(By.res("category_header_default")), 30_000)!!.click()
        assertTrue(device.wait(Until.hasObject(By.res("import_button")), 30_000))
    }

    @After fun cleanUp() = runBlocking {
        monitors.forEach(instrumentation::removeMonitor)
        val fixtureIds = app.repository.read().groups.flatMap { it.photos }.map { it.id }.toSet() - saved.groups.flatMap { it.photos }.map { it.id }.toSet()
        app.repository.update { saved.copy(automatic = false) }
        fixtureIds.forEach { WallpaperMedia.delete(app, it) }
        sources.forEach { app.contentResolver.delete(it, null, null) }
    }

    private fun source(video: Boolean = false): Uri {
        val uri = app.contentResolver.insert(
            if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, if (video) "gallery-video.mp4" else "gallery-photo.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/WallpaperZTest" else "Pictures/WallpaperZTest")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })!!
        sources += uri
        app.contentResolver.openOutputStream(uri)!!.use { output ->
            if (video) instrumentation.context.assets.open("motion.mp4").use { it.copyTo(output) }
            else {
                val bitmap = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                bitmap.recycle()
            }
        }
        app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    // The emulator has no Xiaomi Gallery. Intercept the external activity launch only;
    // exercise the real Compose button, result callback and private-media import.
    private fun galleryResult(code: Int, result: Intent?): AtomicReference<Intent> {
        val request = AtomicReference<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.`package` != "com.miui.gallery") return null
                request.set(intent)
                return Instrumentation.ActivityResult(code, result)
            }
        }
        monitors += monitor
        instrumentation.addMonitor(monitor)
        device.wait(Until.findObject(By.res("import_button")), 10_000)!!.click()
        return request
    }

    @Test fun xiaomiGalleryMixedSelectionCopiesIntoEditedTheme() = runBlocking {
        app.controller.createGroup("旅行")
        val photo = source()
        val video = source(video = true)
        val result = Intent().setData(photo).apply {
            clipData = ClipData.newUri(app.contentResolver, "photo", photo).apply { addItem(ClipData.Item(video)) }
        }
        val request = galleryResult(Activity.RESULT_OK, result)
        eventually { app.repository.read().photos.size == 2 && !app.controller.importing.value }
        assertEquals(Intent.ACTION_GET_CONTENT, request.get().action)
        assertEquals("com.miui.gallery", request.get().`package`)
        assertEquals("*/*", request.get().type)
        assertArrayEquals(arrayOf("image/*", "video/*"), request.get().getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertTrue(request.get().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        val state = app.repository.read()
        assertEquals("旅行", state.group.name)
        assertTrue(state.groups.first { it.id == "default" }.photos.isEmpty())
        assertEquals(listOf(MediaKind.PHOTO, MediaKind.VIDEO), state.photos.map { it.kind })
        assertEquals(listOf("gallery-photo.jpg", "gallery-video.mp4"), state.photos.map { it.name })
        sources.forEach { app.contentResolver.delete(it, null, null) }
        sources.clear()
        state.photos.forEach { assertTrue(WallpaperMedia.file(app, it.id).exists()) }
        assertTrue(WallpaperMedia.video(app, state.photos.last().id).exists())
    }

    @Test fun singleSelectionImportsDataUri() = runBlocking {
        galleryResult(Activity.RESULT_OK, Intent().setData(source()))
        eventually { app.repository.read().photos.size == 1 && !app.controller.importing.value }
        assertEquals("gallery-photo.jpg", app.repository.read().photos.single().name)
    }

    @Test fun cancellingGalleryDoesNotChangeTheme() = runBlocking {
        val before = app.repository.read()
        val request = galleryResult(Activity.RESULT_CANCELED, Intent().setData(source()))
        eventually { request.get() != null }
        delay(500)
        assertEquals(before, app.repository.read())
        assertFalse(app.controller.importing.value)
    }

    @Test fun missingXiaomiGalleryShowsMessageAndStaysInApp() {
        assumeTrue(device.executeShellCommand("pm path com.miui.gallery").isBlank())
        device.wait(Until.findObject(By.res("import_button")), 10_000)!!.click()
        assertTrue(device.wait(Until.hasObject(By.text("无法打开小米相册，请确认系统相册已安装并启用")), 10_000))
        assertTrue(device.hasObject(By.res("import_button")))
    }

    private suspend fun eventually(condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition() && System.currentTimeMillis() < deadline) delay(100)
        assertTrue("Timed out waiting for gallery result", condition())
    }
}
