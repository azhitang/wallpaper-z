package com.wallpaperz.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.wallpaperApp
        app.scope.launch {
            try { app.controller.restoreService() }
            catch (e: Exception) { app.controller.report(e.message ?: "自动轮播恢复失败，请打开应用重试") }
            finally { pending.finish() }
        }
    }
}
