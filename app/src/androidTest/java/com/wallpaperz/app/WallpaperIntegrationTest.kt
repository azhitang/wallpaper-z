package com.wallpaperz.app

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.PowerManager
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.Direction
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WallpaperIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.wallpaperApp
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var saved: CarouselState

    @Before fun prepare() = runBlocking {
        saved = app.repository.read()
        app.controller.pause()
        app.repository.update { CarouselState() }
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("pm grant com.wallpaperz.app android.permission.POST_NOTIFICATIONS")
        // Starting instrumentation replaces the app process and can leave the emulator's previous
        // wallpaper binding dead. Re-select through the real system UI once per test run.
        if (!wallpaperActivated || !LiveWallpaperService.isActive(app)) {
            app.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, LiveWallpaperService.component(app))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            device.wait(Until.findObject(By.text("Set wallpaper")), 30_000)!!.click()
            device.wait(Until.findObject(By.text("Home screen")), 10_000)!!.click()
            eventually(15_000) { LiveWallpaperService.isActive(app) }
            wallpaperActivated = true
        }
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("壁纸z")), 30_000))
    }

    @After fun cleanUp() = runBlocking {
        app.controller.pause()
        val fixtureIds = app.repository.read().groups.flatMap { it.photos }.map { it.id }.toSet() - saved.groups.flatMap { it.photos }.map { it.id }.toSet()
        app.repository.update { saved.copy(automatic = false) }
        fixtureIds.forEach { WallpaperMedia.delete(app, it) }
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        Unit
    }

    private suspend fun importFixtures(): List<WallpaperItem> {
        val colors = listOf(Color.rgb(33, 93, 70), Color.rgb(45, 88, 142), Color.rgb(174, 112, 55))
        val names = listOf("松间.jpg", "海风.jpg", "落日.jpg")
        val sources = names.mapIndexed { index, name ->
            File(app.cacheDir, name).also { file ->
                val bitmap = Bitmap.createBitmap(if (index == 1) 1200 else 600, if (index == 1) 600 else 1200, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(colors[index])
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bitmap.recycle()
            }
        }
        app.controller.importPhotos(sources.map(Uri::fromFile))
        sources.forEach { it.delete() }
        return app.repository.read().photos.also { assertEquals(3, it.size) }
    }

    @Test fun importCopyOrderShuffleDeleteAndDesktopOnly() = runBlocking {
        val photos = importFixtures()
        photos.forEach { assertTrue(WallpaperMedia.file(app, it.id).exists()) }
        val manager = WallpaperManager.getInstance(app)
        assertTrue("Enable the live wallpaper on the emulator before running tests", LiveWallpaperService.isActive(app))
        val lockId = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        assertEquals(RotationController.Result.APPLIED, app.controller.next())
        assertEquals(photos[0].id, app.repository.read().currentId)
        val first = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        app.controller.reorder(listOf(photos[0].id, photos[2].id, photos[1].id))
        app.controller.next()
        assertEquals(photos[2].id, app.repository.read().currentId)
        assertEquals(first, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        assertTrue(LiveWallpaperService.isActive(app))
        assertEquals(lockId, manager.getWallpaperId(WallpaperManager.FLAG_LOCK))
        app.controller.mode(PlayMode.SHUFFLE)
        app.controller.next()
        assertEquals(3, app.repository.read().roundPlayedIds.size)
        app.controller.reorder(photos.map { it.id })
        assertEquals(3, app.repository.read().roundPlayedIds.size)
        val bad = File(app.cacheDir, "bad-image.jpg").apply { writeText("not an image") }
        app.controller.importPhotos(listOf(Uri.fromFile(bad)))
        bad.delete()
        assertEquals(3, app.repository.read().photos.size)
        assertNotNull(app.repository.read().lastError)
        photos.forEach { app.controller.delete(setOf(it.id)) }
        assertTrue(app.repository.read().photos.isEmpty())
        assertFalse(app.repository.read().automatic)
        assertTrue(manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) > 0)
    }

    @Test fun failureDoesNotAdvanceCursorOrRandomQueue() = runBlocking {
        val photos = importFixtures()
        app.controller.next(selectedId = photos[0].id)
        app.controller.mode(PlayMode.SHUFFLE)
        app.repository.update { it.copy(roundPlayedIds = listOf(photos[0].id, photos[2].id)) }
        WallpaperMedia.file(app, photos[1].id).delete()
        val before = app.repository.read()
        val wallpaperId = WallpaperManager.getInstance(app).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        assertEquals(RotationController.Result.FAILED, app.controller.next())
        val after = app.repository.read()
        assertEquals(before.currentId, after.currentId)
        assertEquals(before.roundPlayedIds, after.roundPlayedIds)
        assertEquals(before.lastChangedAt, after.lastChangedAt)
        assertEquals(wallpaperId, WallpaperManager.getInstance(app).getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        assertNotNull(after.lastError)
    }

    @Test fun mixedVideoImportRendersLoopsAndDeletesPrivateCopy() = runBlocking {
        val photos = importFixtures()
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "motion.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/WallpaperZTest")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        })!!
        try {
            instrumentation.context.assets.open("motion.mp4").use { input -> resolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            app.controller.importPhotos(listOf(uri))
        } finally { resolver.delete(uri, null, null) }
        val movie = app.repository.read().photos.last()
        assertEquals(4, app.repository.read().photos.size)
        assertEquals(MediaKind.VIDEO, movie.kind)
        assertTrue(movie.durationMs in 2900..3200)
        assertTrue(WallpaperMedia.video(app, movie.id).exists())
        assertTrue(WallpaperMedia.file(app, movie.id).exists())
        app.controller.next(selectedId = movie.id)
        device.pressHome()
        delay(6000) // Allow the emulator's first codec initialization to complete.
        val first = instrumentation.uiAutomation.takeScreenshot()
        delay(700)
        val second = instrumentation.uiAutomation.takeScreenshot()
        delay(3500) // Beyond the three-second clip: the wallpaper must still be playing.
        val third = instrumentation.uiAutomation.takeScreenshot()
        fun differences(a: Bitmap, b: Bitmap): Int {
            var count = 0
            for (y in 350 until minOf(1100, a.height) step 8) for (x in 80 until minOf(640, a.width) step 8)
                if (a.getPixel(x,y) != b.getPixel(x,y)) count++
            return count
        }
        listOf(first, second, third).forEachIndexed { index, bitmap ->
            File(app.getExternalFilesDir(null), "video-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        assertTrue("Video frames should move: ${differences(first, second)} changed sample pixels", differences(first, second) > 150)
        assertTrue("Video should loop after its duration: ${differences(second, third)} changed sample pixels", differences(second, third) > 150)
        listOf(first, second, third).forEach(Bitmap::recycle)
        assertEquals(movie.id, app.repository.read().currentId)
        assertNull(app.repository.read().lastError)
        app.controller.next()
        assertEquals(photos[0].id, app.repository.read().currentId)
        app.controller.delete(setOf(movie.id))
        assertFalse(WallpaperMedia.video(app, movie.id).exists())
        assertFalse(WallpaperMedia.file(app, movie.id).exists())
    }

    @Test fun desktopTransitionContainsIntermediateBlendedFrame() = runBlocking {
        val photos = importFixtures()
        app.controller.next(selectedId = photos[0].id)
        device.pressHome()
        eventually(15_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            val color = frame.getPixel(frame.width / 2, frame.height / 2)
            frame.recycle()
            kotlin.math.abs(Color.red(color) - 33) < 5 && kotlin.math.abs(Color.blue(color) - 70) < 5
        }
        val before = instrumentation.uiAutomation.takeScreenshot()
        val switching = async { app.controller.next(selectedId = photos[1].id) }
        var middle: Bitmap? = null
        val deadline = System.currentTimeMillis() + 2000
        while (middle == null && System.currentTimeMillis() < deadline) {
            delay(60)
            val frame = instrumentation.uiAutomation.takeScreenshot()
            val blue = Color.blue(frame.getPixel(frame.width / 2, frame.height / 2))
            if (blue in 75..137) middle = frame else frame.recycle()
        }
        assertEquals(RotationController.Result.APPLIED, switching.await())
        delay(500)
        val after = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("A transition must contain a blended frame between the two fixtures", middle)
        val blended = requireNotNull(middle)
        // Empty desktop area, away from icons and the widget. The two fixtures are solid colors.
        val x = before.width / 2; val y = before.height / 2
        val a = before.getPixel(x,y); val m = blended.getPixel(x,y); val b = after.getPixel(x,y)
        assertNotEquals("A transition must contain an intermediate frame", a, m)
        assertNotEquals("The intermediate frame must differ from the destination", b, m)
        assertTrue(Color.blue(m) in (minOf(Color.blue(a),Color.blue(b))+2)..(maxOf(Color.blue(a),Color.blue(b))-2))
        listOf(before,blended,after).forEachIndexed { index, bitmap ->
            File(app.getExternalFilesDir(null), "transition-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }

    @Test fun onlySelectedScreenEventAdvances() = runBlocking {
        importFixtures()
        app.controller.trigger(Trigger.SCREEN_ON)
        app.controller.next(enableAutomatic = true)
        delay(2000)
        device.pressHome()
        eventually { app.desktopPlayback.value.visible }
        val before = app.repository.read()
        assertEquals(RotationController.Result.IGNORED, app.controller.next(expectedTrigger = Trigger.UNLOCK))
        assertEquals(before.currentId, app.repository.read().currentId)
        device.sleep(); delay(1000); device.wakeUp()
        eventually { app.repository.read().lastChangedAt > before.lastChangedAt }
        val changed = app.repository.read()
        device.executeShellCommand("wm dismiss-keyguard")
        delay(1800)
        assertEquals(changed.currentId, app.repository.read().currentId)
        app.controller.pause()
        val paused = app.repository.read()
        device.sleep(); delay(700); device.wakeUp()
        delay(1500)
        assertEquals(paused.lastChangedAt, app.repository.read().lastChangedAt)
    }

    @Test fun oneMinuteTimerContinuesUnderFullScreenAppAndScreenOff() = runBlocking {
        val photos = importFixtures()
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(60)
        app.controller.next(enableAutomatic = true)
        val first = app.repository.read()
        assertFalse(app.desktopPlayback.value.visible)
        eventually(75000) { app.repository.read().lastChangedAt > first.lastChangedAt }
        assertEquals(photos[1].id, app.repository.read().currentId)
        // Make the next minute overdue while screen-off, without adding a short production interval.
        device.sleep()
        app.repository.update { it.updateGroup { group -> group.copy(lastChangedAt = System.currentTimeMillis() - 61000) } }
        eventually(12000) { app.repository.read().currentId == photos[2].id }
        assertFalse(app.getSystemService(PowerManager::class.java).isInteractive)
        app.controller.pause()
        val stopped = app.repository.read()
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        delay(1500)
        assertEquals(stopped.lastChangedAt, app.repository.read().lastChangedAt)
        assertEquals(RotationController.Result.APPLIED, app.controller.next())
        assertFalse(app.repository.read().automatic)
    }

    @Test fun unlockEventAdvancesExactlyOnce() = runBlocking {
        val photos = importFixtures()
        app.controller.trigger(Trigger.UNLOCK)
        app.controller.next(enableAutomatic = true)
        device.pressHome()
        eventually { app.desktopPlayback.value.visible }
        delay(1800)
        val before = app.repository.read()
        device.sleep(); delay(1200); device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        eventually { app.repository.read().lastChangedAt > before.lastChangedAt }
        val after = app.repository.read()
        assertEquals(photos[1].id, after.currentId)
        delay(2000)
        assertEquals(after.lastChangedAt, app.repository.read().lastChangedAt)
    }

    @Test fun batchDeleteKeepsUnselectedCurrentAndPausesWhenEmpty() = runBlocking {
        val photos = importFixtures()
        app.controller.next(selectedId = photos[1].id, enableAutomatic = true)
        app.controller.mode(PlayMode.SHUFFLE)
        app.repository.update { it.copy(roundPlayedIds = listOf(photos[0].id, photos[1].id)) }
        val before = app.repository.read()
        app.controller.delete(setOf(photos[0].id, photos[2].id))
        val after = app.repository.read()
        assertEquals(listOf(photos[1]), after.photos)
        assertEquals(before.currentId, after.currentId)
        assertEquals(before.lastChangedAt, after.lastChangedAt)
        assertTrue(after.automatic)
        assertEquals(before.roundPlayedIds, after.roundPlayedIds)
        assertFalse(WallpaperMedia.file(app, photos[0].id).exists())
        assertFalse(WallpaperMedia.file(app, photos[2].id).exists())
        assertTrue(WallpaperMedia.file(app, photos[1].id).exists())
        app.controller.delete(setOf(photos[1].id))
        assertTrue(app.repository.read().photos.isEmpty())
        assertFalse(app.repository.read().automatic)
        assertNull(app.repository.read().currentId)
    }

    @Test fun longPressMultiSelectPreservesGridAndDeletesOnlySelection() = runBlocking {
        val photos = importFixtures()
        openDefaultCategory()
        fun tile(index: Int) = device.wait(Until.findObject(By.res("photo_${photos[index].id}")), 15_000)!!
        val bounds = photos.indices.map { android.graphics.Rect(tile(it).visibleBounds) }
        tile(0).longClick()
        assertTrue(device.wait(Until.hasObject(By.res("selection_delete")), 5000))
        photos.indices.forEach { assertEquals("Entering selection must preserve tile positions", bounds[it], tile(it).visibleBounds) }
        tile(1).click()
        device.findObject(By.res("selection_delete")).click()
        assertTrue(device.wait(Until.hasObject(By.text("删除选中的 2 个素材？")), 5000))
        device.findObject(By.text("取消")).click()
        device.waitForIdle()
        assertEquals(3, app.repository.read().photos.size)
        device.findObject(By.res("selection_delete")).click()
        device.wait(Until.findObject(By.text("删除")), 5000)!!.click()
        eventually { app.repository.read().photos.size == 1 }
        assertEquals(photos[2].id, app.repository.read().photos.single().id)
        assertFalse(WallpaperMedia.file(app, photos[0].id).exists())
        assertFalse(WallpaperMedia.file(app, photos[1].id).exists())
        assertTrue(WallpaperMedia.file(app, photos[2].id).exists())
    }

    @Test fun singleSelectionCanReorderAndBackExitsSelection() = runBlocking {
        val photos = importFixtures()
        openDefaultCategory()
        device.wait(Until.findObject(By.res("photo_${photos[0].id}")), 15_000)!!.longClick()
        device.wait(Until.findObject(By.res("selection_next")), 5000)!!.click()
        eventually { app.repository.read().photos.first().id == photos[1].id }
        assertEquals(listOf(photos[1].id, photos[0].id, photos[2].id), app.repository.read().photos.map { it.id })
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.res("group_detail")), 5000))
        assertFalse(device.hasObject(By.res("selection_delete")))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.res("category_header_default")), 5000))
    }

    @Test fun themesKeepIndependentSettingsAndMoveWithoutCopyingMedia() = runBlocking {
        val photos = importFixtures()
        val original = app.repository.read().activeGroupId
        app.controller.mode(PlayMode.SHUFFLE)
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(300)
        app.controller.next(selectedId = photos[0].id, enableAutomatic = true)
        app.controller.createGroup("旅行")
        val travel = app.repository.read().group.id
        assertTrue(app.repository.read().photos.isEmpty())
        assertTrue(app.repository.read().automatic)
        assertEquals(original, app.repository.read().playingGroupId)
        assertEquals(PlayMode.ORDERED, app.repository.read().mode)
        app.controller.selectGroup(original)
        app.controller.moveToGroup(setOf(photos[1].id, photos[2].id), travel)
        photos.forEach { assertTrue(WallpaperMedia.file(app, it.id).exists()) }
        app.controller.selectGroup(travel)
        app.controller.trigger(Trigger.UNLOCK)
        app.controller.interval(900)
        app.controller.next()
        assertEquals(travel, app.repository.read().playingGroupId)
        assertEquals(photos[1].id, app.repository.read().currentId)
        app.controller.selectGroup(original) // Editing a theme must not change the displayed wallpaper.
        assertEquals(travel, app.repository.read().playingGroupId)
        assertEquals(RotationController.Result.IGNORED, app.controller.next(expectedTrigger = Trigger.INTERVAL))
        assertEquals(PlayMode.SHUFFLE, app.repository.read().mode)
        assertEquals(300, app.repository.read().intervalSeconds)
        app.controller.next()
        assertEquals(travel, app.repository.read().playingGroupId)
        assertEquals(photos[2].id, app.repository.read().playbackGroup.currentId)
        app.controller.selectGroup(travel)
        assertEquals(900, app.repository.read().intervalSeconds)
        app.controller.enableGroup(travel, false)
        app.controller.next()
        assertEquals(original, app.repository.read().playingGroupId)
        app.controller.enableGroup(original, false)
        assertFalse(app.repository.read().automatic)
        app.controller.renameGroup(travel, "城市")
        app.controller.deleteGroup(travel)
        assertEquals(original, app.repository.read().activeGroupId)
        assertTrue(WallpaperMedia.file(app, photos[0].id).exists())
        assertFalse(WallpaperMedia.file(app, photos[1].id).exists())
        assertFalse(WallpaperMedia.file(app, photos[2].id).exists())
        assertEquals(1, app.repository.read().groups.size)
    }

    @Test fun mixedThemeSchedulerUsesDisplayedThemeThenNextEligibleTheme() = runBlocking {
        val photos = importFixtures()
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(60)
        app.controller.next(selectedId = photos[0].id, enableAutomatic = true)
        app.controller.createGroup("解锁主题")
        val second = app.repository.read().activeGroupId
        app.controller.selectGroup("default")
        app.controller.moveToGroup(setOf(photos[1].id, photos[2].id), second)
        app.controller.selectGroup(second)
        app.controller.trigger(Trigger.UNLOCK)
        assertEquals(RotationController.Result.IGNORED, app.controller.next(expectedTrigger = Trigger.UNLOCK))
        app.repository.update { it.updateGroup("default") { group -> group.copy(lastChangedAt = System.currentTimeMillis() - 61000) } }
        eventually { app.repository.read().playingGroupId == second }
        device.pressHome()
        eventually { app.desktopPlayback.value.visible }
        app.controller.selectGroup("default")
        assertEquals(RotationController.Result.IGNORED, app.controller.next(expectedTrigger = Trigger.INTERVAL))
        device.sleep(); delay(1000); device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        eventually { app.repository.read().playbackGroup.currentId == photos[2].id }
        val changed = app.repository.read().playbackGroup.lastChangedAt
        delay(1500)
        assertEquals(changed, app.repository.read().playbackGroup.lastChangedAt)
        assertEquals(photos.map { it.id }.toSet(), app.repository.read().roundPlayedIds.toSet())
        app.controller.next()
        assertEquals("default", app.repository.read().playingGroupId)
    }

    @Test fun createThemeAndMoveSelectedFromGrid() = runBlocking {
        val photos = importFixtures()
        device.wait(Until.findObject(By.res("theme_picker")), 10_000)!!.click()
        device.wait(Until.findObject(By.res("create_theme")), 5000)!!.click()
        device.wait(Until.findObject(By.res("theme_name")), 5000)!!.text = "旅行"
        device.wait(Until.findObject(By.res("save_theme").enabled(true)), 5000)!!.click()
        eventually { app.repository.read().groups.size == 2 }
        val destination = app.repository.read().activeGroupId
        assertEquals("旅行", app.repository.read().group.name)
        device.pressBack()
        device.wait(Until.findObject(By.res("category_header_default")), 5000)!!.click()
        eventually { app.repository.read().activeGroupId == "default" }
        device.wait(Until.findObject(By.res("photo_${photos[0].id}")), 5000)!!.longClick()
        device.wait(Until.findObject(By.res("selection_move")), 5000)!!.click()
        device.wait(Until.findObject(By.text("旅行")), 5000)!!.click()
        eventually { app.repository.read().photos.size == 2 }
        assertEquals(listOf(photos[0]), app.repository.read().groups.first { it.id == destination }.photos)
        assertTrue(WallpaperMedia.file(app, photos[0].id).exists())
    }

    @Test fun settingsControlsPersistAndAutomaticSwitchStartsAndPauses() = runBlocking {
        importFixtures()
        device.wait(Until.findObject(By.res("settings_tab")), 10_000)!!.click()
        device.wait(Until.findObject(By.text("随机")), 5000)!!.click()
        eventually { app.repository.read().mode == PlayMode.SHUFFLE }
        device.wait(Until.findObject(By.text("定时")), 5000)!!.click()
        eventually { app.repository.read().trigger == Trigger.INTERVAL }
        device.wait(Until.findObject(By.res("interval_picker")), 5000)!!.click()
        device.wait(Until.findObject(By.text("5 分钟")), 5000)!!.click()
        eventually { app.repository.read().intervalSeconds == 300 }
        device.wait(Until.findObject(By.text("每次解锁")), 5000)!!.click()
        eventually { app.repository.read().trigger == Trigger.UNLOCK }
        device.wait(Until.findObject(By.res("automatic_switch").enabled(true)), 5000)!!.click()
        eventually { app.repository.read().automatic && !app.controller.switching.value }
        device.wait(Until.findObject(By.res("automatic_switch").enabled(true)), 5000)!!.click()
        eventually { !app.repository.read().automatic }
    }

    @Test fun categoriesShowTwoAndHalfCoversAndOpenDetailWithoutApplying() = runBlocking {
        val photos = importFixtures()
        app.controller.next(selectedId = photos[0].id)
        app.controller.createGroup("空主题")
        val emptyId = app.repository.read().activeGroupId
        app.controller.selectGroup("default")
        val before = app.repository.read().playbackGroup
        fun cover(index: Int) = device.wait(Until.findObject(By.res("category_cover_default_${photos[index].id}")), 10_000)!!
        val viewport = device.wait(Until.findObject(By.res("category_previews_default")), 10_000)!!.visibleBounds
        val first = cover(0).visibleBounds
        val second = cover(1).visibleBounds
        val third = cover(2).visibleBounds
        val thirdWidth = minOf(third.right, viewport.right) - maxOf(third.left, viewport.left)
        assertTrue(kotlin.math.abs(first.width() - second.width()) <= 2)
        assertTrue("Third cover should be half visible: $first $second $third / $viewport", thirdWidth in (first.width() * .4f).toInt()..(first.width() * .6f).toInt())
        // Scroll to the end without leaving a fling for the following tap to stop.
        device.findObject(By.res("category_previews_default")).scroll(Direction.RIGHT, 1f)
        device.waitForIdle()
        assertTrue(cover(2).visibleBounds.width() > thirdWidth + first.width() * .2f)
        cover(2).click()
        val opened = device.wait(Until.hasObject(By.res("group_detail")), 5000)
        if (!opened) device.takeScreenshot(File(app.cacheDir, "category-navigation-failure.png"))
        assertTrue("Tapping the settled category cover should open its detail", opened)
        photos.forEach { assertNotNull(device.findObject(By.res("photo_${it.id}"))) }
        assertFalse(device.hasObject(By.text("壁纸预览")))
        assertEquals(before, app.repository.read().playbackGroup)
        device.findObject(By.res("group_settings")).click()
        assertTrue(device.wait(Until.hasObject(By.res("settings_list")), 5000))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.res("group_detail")), 5000))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.res("category_list")), 5000))
        device.wait(Until.findObject(By.res("category_header_$emptyId")), 5000)!!.click()
        assertTrue(device.wait(Until.hasObject(By.text("这个主题还没有素材")), 5000))
        assertEquals(emptyId, app.repository.read().activeGroupId)
        assertEquals(before, app.repository.read().playbackGroup)
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.res("category_list")), 5000))
    }

    @Test fun photoPreviewSwipesFromTappedItemKeepsProgressAndStopsAtEdges() = runBlocking {
        val photos = importFixtures()
        app.controller.next(selectedId = photos[0].id)
        val before = app.repository.read().playbackGroup
        openDefaultCategory()
        device.wait(Until.findObject(By.res("photo_${photos[1].id}")), 5000)!!.click()
        assertPreview(photos[1].name, "2 / 3")
        swipePreview(Direction.LEFT, photos[2].name, "3 / 3")
        swipePreview(Direction.LEFT, photos[2].name, "3 / 3")
        swipePreview(Direction.RIGHT, photos[1].name, "2 / 3")
        swipePreview(Direction.RIGHT, photos[0].name, "1 / 3")
        swipePreview(Direction.RIGHT, photos[0].name, "1 / 3")
        assertEquals(before, app.repository.read().playbackGroup)
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.res("group_detail")), 5000))
        app.controller.delete(setOf(photos[1].id, photos[2].id))
        device.wait(Until.findObject(By.res("photo_${photos[0].id}")), 5000)!!.click()
        assertPreview(photos[0].name, "1 / 1")
        swipePreview(Direction.LEFT, photos[0].name, "1 / 1")
        swipePreview(Direction.RIGHT, photos[0].name, "1 / 1")
        assertEquals(before.currentId, app.repository.read().playbackGroup.currentId)
        device.pressBack()
        Unit
    }

    @Test fun videoPreviewSwipesBackAndAppliesVisibleItemFromItsOwnTheme() = runBlocking {
        val photos = importFixtures()
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "motion.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/WallpaperZTest")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        })!!
        try {
            instrumentation.context.assets.open("motion.mp4").use { input -> resolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            app.controller.importPhotos(listOf(uri))
        } finally { resolver.delete(uri, null, null) }
        val movie = app.repository.read().photos.last()
        app.controller.next(selectedId = photos[0].id)
        val before = app.repository.read().playbackGroup
        openDefaultCategory()
        device.wait(Until.findObject(By.res("photo_${photos[2].id}")), 5000)!!.click()
        assertPreview(photos[2].name, "3 / 4")
        swipePreview(Direction.LEFT, "motion.mp4", "4 / 4")
        assertTrue(device.wait(Until.hasObject(By.res("preview_kind").text("视频 · 静音循环")), 5000))
        delay(3500)
        assertPreview("motion.mp4", "4 / 4")
        assertEquals(before, app.repository.read().playbackGroup)
        swipePreview(Direction.RIGHT, photos[2].name, "3 / 4")
        swipePreview(Direction.LEFT, "motion.mp4", "4 / 4")
        device.wait(Until.findObject(By.res("apply_button").enabled(true)), 5000)!!.click()
        eventually { app.repository.read().playbackGroup.currentId == movie.id && !app.controller.switching.value }
        assertTrue(device.wait(Until.hasObject(By.res("group_detail")), 5000))
        assertFalse(device.hasObject(By.res("preview_pager")))
        app.controller.createGroup("其他主题")
        val editor = app.repository.read().activeGroupId
        device.pressBack()
        device.wait(Until.findObject(By.text("此刻的桌面")), 5000)!!.click()
        assertPreview("motion.mp4", "4 / 4")
        swipePreview(Direction.RIGHT, photos[2].name, "3 / 4")
        assertEquals(editor, app.repository.read().activeGroupId)
        assertEquals(movie.id, app.repository.read().playbackGroup.currentId)
        device.pressBack()
        Unit
    }

    private suspend fun importMovie(asset: String = "motion.mp4"): WallpaperItem {
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, asset)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/WallpaperZTest")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        })!!
        try {
            instrumentation.context.assets.open(asset).use { input -> resolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            app.controller.importPhotos(listOf(uri))
        } finally { resolver.delete(uri, null, null) }
        return app.repository.read().photos.last().also { assertEquals(MediaKind.VIDEO, it.kind) }
    }

    @Test fun videoFinishesAndSwitchesUnderFullScreenApp() = runBlocking {
        val photos = importFixtures()
        val movie = importMovie()
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(60)
        app.controller.next(selectedId = movie.id, enableAutomatic = true)
        // Stay inside the opaque app: the hidden wallpaper video must still reach its real end.
        assertFalse(app.desktopPlayback.value.visible)
        eventually(15000) { app.repository.read().currentId == photos[0].id }
        assertFalse(app.desktopPlayback.value.visible)
        assertNull(app.repository.read().lastError)
    }

    @Test fun videoFinishesAndSwitchesWhileScreenOff() = runBlocking {
        val photos = importFixtures()
        val movie = importMovie("long-motion.mp4")
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(60)
        app.controller.next(selectedId = movie.id, enableAutomatic = true)
        device.pressHome()
        eventually(15000) { app.desktopPlayback.value.let { it.visible && it.ready && it.itemId == movie.id } }
        device.sleep()
        eventually(15000) { app.repository.read().currentId == photos[0].id }
        assertFalse(app.getSystemService(PowerManager::class.java).isInteractive)
        assertNull(app.repository.read().lastError)
    }

    @Test fun singleVideoReplaysAfterEachCompletion() = runBlocking {
        val movie = importMovie()
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(60)
        app.controller.next(enableAutomatic = true)
        var generation = app.repository.read().lastChangedAt
        device.pressHome()
        repeat(2) {
            eventually(15000) { app.repository.read().lastChangedAt > generation }
            generation = app.repository.read().lastChangedAt
            assertEquals(movie.id, app.repository.read().currentId)
        }
        assertNull(app.repository.read().lastError)
    }

    @Test fun shortVideoLoopsUntilTheFullMinuteInterval() = runBlocking {
        val photos = importFixtures()
        val movie = importMovie()
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(60)
        app.controller.videoTiming(VideoTiming.INTERVAL)
        app.controller.next(selectedId = movie.id, enableAutomatic = true)
        val generation = app.repository.read().lastChangedAt
        eventually { app.desktopPlayback.value.let { it.itemId == movie.id && it.ready } }
        delay(8000)
        assertEquals(movie.id, app.repository.read().currentId)
        assertEquals(generation, app.repository.read().lastChangedAt)
        assertFalse(app.desktopPlayback.value.ended)
        eventually(65_000) { app.repository.read().currentId == photos[0].id }
        assertTrue(app.repository.read().lastChangedAt - generation >= 60_000)
        assertNull(app.repository.read().lastError)
    }

    @Test fun intervalDeadlineCutsVideoBeforeItsEnd() = runBlocking {
        val photos = importFixtures()
        val movie = importMovie("interval-motion.mp4")
        assertTrue(movie.durationMs in 69_000..71_000)
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(60)
        app.controller.videoTiming(VideoTiming.INTERVAL)
        app.controller.next(selectedId = movie.id, enableAutomatic = true)
        val generation = app.repository.read().lastChangedAt
        eventually { app.desktopPlayback.value.let { it.itemId == movie.id && it.ready } }
        eventually(65_000) { app.repository.read().currentId == photos[0].id }
        val elapsed = app.repository.read().lastChangedAt - generation
        assertTrue("A 70-second clip must be cut at the one-minute interval: $elapsed", elapsed in 60_000..65_000)
        assertNull(app.repository.read().lastError)
    }

    @Test fun videoTimingChangesApplyToThePlayingVideoWithoutResettingRound() = runBlocking {
        val photos = importFixtures()
        val movie = importMovie("long-motion.mp4")
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.next(selectedId = movie.id, enableAutomatic = true)
        eventually { app.desktopPlayback.value.let { it.itemId == movie.id && it.ready } }
        val original = app.repository.read()
        app.controller.videoTiming(VideoTiming.INTERVAL)
        assertEquals(original.lastChangedAt, app.repository.read().lastChangedAt)
        assertEquals(original.roundPlayedIds, app.repository.read().roundPlayedIds)
        delay(8500)
        assertEquals(movie.id, app.repository.read().currentId)
        assertFalse(app.desktopPlayback.value.ended)
        app.controller.videoTiming(VideoTiming.FINISH)
        assertEquals(original.lastChangedAt, app.repository.read().lastChangedAt)
        eventually(10_000) { app.repository.read().currentId == photos[0].id }
        assertNull(app.repository.read().lastError)
    }

    @Test fun videoTimingControlsKeepIndependentThemeSettings() = runBlocking {
        importFixtures()
        app.controller.trigger(Trigger.INTERVAL)
        device.wait(Until.findObject(By.res("settings_tab")), 10_000)!!.click()
        device.wait(Until.findObject(By.text("跟随间隔")), 10_000)!!.click()
        eventually { app.repository.read().videoTiming == VideoTiming.INTERVAL }
        app.controller.createGroup("视频时长测试")
        assertEquals(VideoTiming.FINISH, app.repository.read().videoTiming)
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.selectGroup("default")
        eventually { app.repository.read().videoTiming == VideoTiming.INTERVAL }
        assertTrue(device.wait(Until.hasObject(By.text("轮播间隔")), 8000))
        device.waitForIdle()
        val frame = instrumentation.uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null), "v064-video-settings.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
        frame.recycle()
    }

    @Test fun longVideoSwitchesAtCompletionInsteadOfWaitingOneMinute() = runBlocking {
        val photos = importFixtures()
        val movie = importMovie("long-motion.mp4")
        assertTrue(movie.durationMs in 6900..7100)
        app.controller.trigger(Trigger.INTERVAL)
        app.controller.interval(60)
        app.controller.next(selectedId = movie.id, enableAutomatic = true)
        val generation = app.repository.read().lastChangedAt
        device.pressHome()
        eventually(15000) { app.desktopPlayback.value.let { it.visible && it.ready && it.itemId == movie.id } }
        delay(3800)
        assertEquals("A long video must not be cut at the photo interval", movie.id, app.repository.read().currentId)
        assertEquals(generation, app.repository.read().lastChangedAt)
        eventually(6000) { app.repository.read().currentId == photos[0].id }
        assertNull(app.repository.read().lastError)
    }

    @Test fun unevenThemesDoNotRepeatBeforeEveryWallpaperWasPlayed() = runBlocking {
        val photos = importFixtures()
        app.controller.createGroup("单张主题")
        val other = app.repository.read().activeGroupId
        app.controller.selectGroup("default")
        app.controller.moveToGroup(setOf(photos[2].id), other)
        app.controller.mode(PlayMode.FAVOR_NEW)
        app.controller.selectGroup(other)
        app.controller.mode(PlayMode.SHUFFLE)
        repeat(4) {
            val seen = (1..3).map {
                assertEquals(RotationController.Result.APPLIED, app.controller.next())
                app.repository.read().playbackGroup.currentId
            }
            assertEquals(photos.map { it.id }.toSet(), seen.toSet())
        }
    }

    @Test fun favorNewAndMinuteIntervalsPersistWithoutThreeSeconds() = runBlocking {
        val photos = importFixtures()
        assertTrue(photos.all { it.importedAt > 0 })
        device.wait(Until.findObject(By.res("settings_tab")), 10000)!!.click()
        device.wait(Until.findObject(By.text("偏爱新图")), 5000)!!.click()
        eventually { app.repository.read().mode == PlayMode.FAVOR_NEW }
        device.wait(Until.findObject(By.text("定时")), 5000)!!.click()
        device.wait(Until.findObject(By.res("interval_picker")), 5000)!!.click()
        assertFalse(device.hasObject(By.text("3 秒")))
        device.wait(Until.findObject(By.text("5 分钟")), 5000)!!.click()
        eventually { app.repository.read().intervalSeconds == 300 }
        assertTrue(device.wait(Until.gone(By.text("图片停留时间")), 5000))
        assertTrue(device.wait(Until.hasObject(By.text("5 分钟")), 5000))
        device.waitForIdle()
        val frame = instrumentation.uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null), "v051-settings.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
        frame.recycle()
        assertEquals(PlayMode.FAVOR_NEW, app.repository.read().mode)
    }

    private fun assertPreview(name: String, position: String) {
        val nameFound = device.wait(Until.hasObject(By.res("preview_name").text(name)), 8000)
        assertTrue("Expected preview name $name; actual ${device.findObject(By.res("preview_name"))?.text}", nameFound)
        val positionFound = device.wait(Until.hasObject(By.res("preview_position").text(position)), 8000)
        assertTrue("Expected page $position; actual ${device.findObject(By.res("preview_position"))?.text}", positionFound)
        assertTrue(device.wait(Until.hasObject(By.res("apply_button").enabled(true)), 8000))
    }

    private fun swipePreview(direction: Direction, name: String, position: String) {
        device.wait(Until.findObject(By.res("preview_pager")), 5000)!!.swipe(direction, .8f, 1000)
        assertPreview(name, position)
    }

    private fun openDefaultCategory() {
        device.wait(Until.findObject(By.res("category_header_default")), 10_000)!!.click()
        assertTrue(device.wait(Until.hasObject(By.res("group_detail")), 5000))
    }

    private suspend fun eventually(timeout: Long = 8000, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition() && System.currentTimeMillis() < deadline) delay(200)
        assertTrue("Timed out after ${timeout}ms", condition())
    }

    companion object { private var wallpaperActivated = false }
}
