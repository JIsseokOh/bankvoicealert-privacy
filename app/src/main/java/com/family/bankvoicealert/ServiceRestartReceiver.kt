package com.family.bankvoicealert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import java.util.concurrent.TimeUnit

class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
        private const val CHECK_INTERVAL_MINUTES = 15L
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Service check alarm triggered")

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("background_enabled", false)) {
            Log.d(TAG, "Background service is disabled by user")
            return
        }

        // Re-schedule the next alarm (setAndAllowWhileIdle is one-shot)
        scheduleNextAlarm(context)

        // Ensure NotificationListener is connected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, BankNotificationService::class.java)
                )
                Log.d(TAG, "NotificationListener rebind requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request rebind", e)
            }
        }

        // Try to restart ForegroundService
        try {
            ForegroundService.startService(context)
            Log.d(TAG, "ForegroundService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart ForegroundService", e)
        }
    }

    private fun scheduleNextAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            val intervalMillis = TimeUnit.MINUTES.toMillis(CHECK_INTERVAL_MINUTES)
            val triggerTime = SystemClock.elapsedRealtime() + intervalMillis

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    triggerTime,
                    intervalMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Next service check scheduled in $CHECK_INTERVAL_MINUTES minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next alarm", e)
        }
    }
}
