package com.wallpaperz.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.launch

class NextWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.wallpaperApp.scope.launch {
            try { updateAll(context) } finally { pending.finish() }
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NEXT) { super.onReceive(context, intent); return }
        val pending = goAsync()
        context.wallpaperApp.scope.launch {
            try { context.wallpaperApp.controller.next() } finally { pending.finish() }
        }
    }

    companion object {
        const val NEXT = "com.wallpaperz.app.NEXT"
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextWidget::class.java))
            if (ids.isEmpty()) return
            val state = context.wallpaperApp.repository.read()
            val needsSetup = state.enabledGroups.isEmpty() || !LiveWallpaperService.isActive(context)
            val views = RemoteViews(context.packageName, R.layout.widget_next)
            val status = when {
                state.enabledGroups.isEmpty() -> "点击选择参与轮播的主题"
                !LiveWallpaperService.isActive(context) -> "点击启用动态壁纸"
                state.lastError != null -> "打开壁纸z查看提示"
                else -> "换一张壁纸，混播 ${state.enabledGroups.size} 个主题"
            }
            views.setContentDescription(R.id.widget_root, status)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val click = if (needsSetup) PendingIntent.getActivity(context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags)
            else PendingIntent.getBroadcast(context, 0,
                Intent(context, NextWidget::class.java).setAction(NEXT).addFlags(Intent.FLAG_RECEIVER_FOREGROUND), flags)
            views.setOnClickPendingIntent(R.id.widget_root, click)
            manager.updateAppWidget(ids, views)
        }
    }
}
