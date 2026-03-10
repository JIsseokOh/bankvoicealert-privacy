package com.family.bankvoicealert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Service check alarm triggered")

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("background_enabled", false)) {
            Log.d(TAG, "Background service is disabled by user")
            return
        }

        try {
            ForegroundService.startService(context)
            Log.d(TAG, "ForegroundService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart ForegroundService", e)
        }
    }
}
