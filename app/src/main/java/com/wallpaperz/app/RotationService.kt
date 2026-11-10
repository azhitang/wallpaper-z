package com.wallpaperz.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RotationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = wallpaperApp
    private var screenEventQueued = false

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> { screenEventQueued = false }
                Intent.ACTION_SCREEN_ON -> {
                    advanceFor(Trigger.SCREEN_ON)
                }
                Intent.ACTION_USER_PRESENT -> advanceFor(Trigger.UNLOCK)
            }
        }
    }

    private fun advanceFor(trigger: Trigger) {
        val state = app.repository.state.value ?: return
        if (!screenEventQueued && state.automatic && state.playbackGroup.trigger == trigger) {
            screenEventQueued = true
            scope.launch { app.controller.next(expectedTrigger = trigger, expectedGeneration = state.playbackGroup.lastChangedAt) }
        }
    }

    private data class Schedule(val automatic: Boolean, val trigger: Trigger,
                                val seconds: Int, val lastChangedAt: Long, val themeName: String,
                                val item: WallpaperItem?)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "自动壁纸轮播", NotificationManager.IMPORTANCE_LOW))
        registerReceiver(events, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        // USER_PRESENT can originate from SystemUI's own UID. All three actions are system-protected.
        }, RECEIVER_EXPORTED)
        scope.launch {
            val schedule = app.repository.changes.map {
                val group = it.playbackGroup
                Schedule(it.automatic, group.trigger, group.intervalSeconds, group.lastChangedAt, group.name,
                    group.photos.firstOrNull { photo -> photo.id == group.currentId })
            }.distinctUntilChanged()
            combine(schedule, app.desktopPlayback) { settings, playback -> settings to playback }
                .collectLatest { (settings, playback) ->
                if (!settings.automatic) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(settings.trigger, settings.seconds, settings.themeName))
                if (settings.trigger != Trigger.INTERVAL || settings.item == null) return@collectLatest
                if (settings.item.kind == MediaKind.VIDEO) {
                    if (playback.itemId != settings.item.id || playback.generation != settings.lastChangedAt || !playback.ended)
                        return@collectLatest
                } else {
                    delay((settings.lastChangedAt + settings.seconds * 1000L - System.currentTimeMillis()).coerceAtLeast(0))
                }
                while (isActive) {
                    val result = app.controller.next(expectedTrigger = Trigger.INTERVAL, expectedGeneration = settings.lastChangedAt)
                    if (result == RotationController.Result.APPLIED) return@collectLatest
                    // A failed switch retains round progress and retries at the configured interval.
                    delay(settings.seconds * 1000L)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = app.repository.state.value
        startForeground(NOTIFICATION, notification(state?.playbackGroup?.trigger ?: Trigger.UNLOCK, state?.playbackGroup?.intervalSeconds ?: 60, state?.playbackGroup?.name ?: "默认主题"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (intent?.action == PAUSE) app.scope.launch { app.controller.pause() }
        return START_STICKY
    }

    private fun notification(trigger: Trigger, seconds: Int, themeName: String): Notification {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val next = PendingIntent.getBroadcast(this, 11,
            Intent(this, NextWidget::class.java).setAction(NextWidget.NEXT).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 12,
            Intent(this, RotationService::class.java).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val text = if (trigger == Trigger.INTERVAL) "图片 ${intervalLabel(seconds)} · 视频播完切换" else "${trigger.title}切换"
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("壁纸z · $themeName").setContentText(text).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "下一张", next).build())
            .addAction(Notification.Action.Builder(null, "暂停", pause).build()).build()
    }

    override fun onDestroy() {
        unregisterReceiver(events)
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val PAUSE = "com.wallpaperz.app.PAUSE"
        private const val CHANNEL = "rotation"
        private const val NOTIFICATION = 7
    }
}
