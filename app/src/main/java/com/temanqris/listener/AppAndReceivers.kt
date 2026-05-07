package com.temanqris.listener

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.temanqris.listener.storage.PreferencesManager

class TemanQrisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Auto-start listener service kalau sebelumnya aktif
        val prefs = PreferencesManager(this)
        if (prefs.isListenerEnabled()) {
            ListenerKeepAliveService.start(this)
        }
    }
}

/**
 * Auto-restart listener setelah device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = PreferencesManager(context)
            if (prefs.isListenerEnabled()) {
                Log.i("BootReceiver", "Auto-starting listener after boot")
                ListenerKeepAliveService.start(context)
            }
        }
    }
}
