package com.family.bankvoicealert

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Service check alarm triggered")

        // 사용자가 백그라운드 실행을 비활성화했는지 확인
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val backgroundEnabled = prefs.getBoolean("background_enabled", false)

        if (!backgroundEnabled) {
            Log.d(TAG, "Background service is disabled by user")
            return
        }

        // ForegroundService가 실행 중인지 확인
        if (!isServiceRunning(context, ForegroundService::class.java)) {
            Log.d(TAG, "ForegroundService not running, attempting to restart")
            try {
                ForegroundService.startService(context)
                Log.d(TAG, "ForegroundService restarted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart ForegroundService", e)
            }
        } else {
            Log.d(TAG, "ForegroundService is running normally")
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 8.0 미만에서는 getRunningServices 사용
            @Suppress("DEPRECATION")
            val services = activityManager.getRunningServices(Int.MAX_VALUE)
            services?.any { it.service.className == serviceClass.name } ?: false
        } else {
            // Android 8.0 이상에서는 서비스 시작 시도로 확인
            try {
                val intent = Intent(context, serviceClass)
                context.startService(intent) != null
            } catch (e: Exception) {
                Log.e(TAG, "Error checking service status", e)
                false
            }
        }
    }
}
