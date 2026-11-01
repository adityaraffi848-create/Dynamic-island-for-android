package com.example.dynisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED &&
            c.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean("boot", false) &&
            Settings.canDrawOverlays(c)
        ) c.startForegroundService(Intent(c, IslandService::class.java))
    }
}
