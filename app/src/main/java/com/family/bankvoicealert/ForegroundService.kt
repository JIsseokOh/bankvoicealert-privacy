package com.family.bankvoicealert

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.TimeUnit

class ForegroundService : Service() {

    companion object {
        private const val TAG = "ForegroundService"
        const val ACTION_START = "com.family.bankvoicealert.START_SERVICE"
        const val ACTION_STOP = "com.family.bankvoicealert.STOP_SERVICE"
        private const val SERVICE_DURATION_HOURS = 24L
        private const val SERVICE_CHECK_INTERVAL_MINUTES = 15L

        fun startService(context: Context) {
            try {
                val intent = Intent(context, ForegroundService::class.java).apply {
                    action = ACTION_START
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12 이상에서는 포그라운드 서비스 시작 제한이 있음
                    try {
                        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val processes = activityManager.runningAppProcesses
                        val isInForeground = processes?.any {
                            it.processName == context.packageName &&
                            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        } ?: false

                        if (isInForeground) {
                            context.startForegroundService(intent)
                        } else {
                            Log.w(TAG, "Cannot start foreground service from background on Android 12+")
                        }
                    } catch (e: ForegroundServiceStartNotAllowedException) {
                        Log.e(TAG, "ForegroundServiceStartNotAllowedException: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting foreground service on Android 12+", e)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        context.startForegroundService(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting foreground service", e)
                        try {
                            context.startService(intent)
                        } catch (e2: Exception) {
                            Log.e(TAG, "Failed to start service", e2)
                        }
                    }
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, ForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
                try {
                    context.stopService(Intent(context, ForegroundService::class.java))
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to stop service directly", e2)
                }
            }
        }
    }

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "bank_alert_service"

    private var isServiceStarted = false
    private var serviceStartTime = 0L

    private var autoStopHandler: Handler? = null
    private var autoStopRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ForegroundService onCreate")

        try {
            createNotificationChannel()
            val notification = createNotification()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Android 14+
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10-13
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                isServiceStarted = true
                serviceStartTime = System.currentTimeMillis()

                // Wake lock 제거: NotificationListenerService는 시스템이 관리하며,
                // TTS 재생 시에만 TTSManager에서 wake lock을 사용합니다.
                // 이를 통해 Android vitals의 "불필요한 부분적인 wake lock" 문제를 해결합니다.
                scheduleAutoStop()
                setupServiceProtection()

            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "ForegroundServiceStartNotAllowedException in onCreate", e)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground in onCreate", e)
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")

        try {
            when (intent?.action) {
                ACTION_START -> {
                    if (!isServiceStarted) {
                        Log.d(TAG, "Service starting...")
                        startForeground(NOTIFICATION_ID, createNotification())
                        isServiceStarted = true
                    } else {
                        Log.d(TAG, "Service already started")
                    }
                    return START_STICKY
                }
                ACTION_STOP -> {
                    Log.d(TAG, "Service stop requested")
                    stopServiceGracefully()
                    return START_NOT_STICKY
                }
            }

            // 액션이 없는 경우에도 서비스 시작
            if (!isServiceStarted) {
                startForeground(NOTIFICATION_ID, createNotification())
                isServiceStarted = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "띵동 입금알리미 서비스",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "백그라운드에서 은행 입금 알림을 감지합니다"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (isIgnoringBatteryOptimizations()) {
            "백그라운드 실행 중 - 상시 가동 모드"
        } else {
            val remainingHours = ((TimeUnit.HOURS.toMillis(24) - (System.currentTimeMillis() - serviceStartTime)) / TimeUnit.HOURS.toMillis(1)).toInt()
            if (remainingHours > 0) {
                "백그라운드 실행 중 - 약 ${remainingHours}시간 후 자동 종료"
            } else {
                "백그라운드 실행 중 - 곧 자동 종료됩니다"
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("띵동 입금알리미")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun scheduleAutoStop() {
        if (isIgnoringBatteryOptimizations()) {
            Log.d(TAG, "Battery optimization ignored - running in unlimited mode")
            return
        }

        autoStopRunnable?.let { runnable ->
            autoStopHandler?.removeCallbacks(runnable)
        }

        autoStopHandler = Handler(Looper.getMainLooper())
        autoStopRunnable = Runnable {
            Log.d(TAG, "Auto-stopping service after 24 hours")
            stopServiceGracefully()
        }

        autoStopHandler?.postDelayed(
            autoStopRunnable!!,
            TimeUnit.HOURS.toMillis(SERVICE_DURATION_HOURS)
        )

        updateNotificationPeriodically()
    }

    private fun updateNotificationPeriodically() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (isServiceStarted) {
                try {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.notify(NOTIFICATION_ID, createNotification())

                    if (!isIgnoringBatteryOptimizations()) {
                        updateNotificationPeriodically()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating notification", e)
                }
            }
        }, TimeUnit.HOURS.toMillis(1))
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun stopServiceGracefully() {
        Log.d(TAG, "Stopping service gracefully")
        try {
            isServiceStarted = false
            cancelAllHandlers()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping foreground", e)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    stopSelf()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in stopSelf", e)
                }
            }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
            try {
                stopSelf()
            } catch (e2: Exception) {
                Log.e(TAG, "Error in fallback stopSelf", e2)
            }
        }
    }

    private fun cancelAllHandlers() {
        try {
            autoStopRunnable?.let { runnable ->
                autoStopHandler?.removeCallbacks(runnable)
            }
            autoStopRunnable = null
            autoStopHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling handlers", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ForegroundService onDestroy")

        try {
            isServiceStarted = false
            cancelAllHandlers()
            cancelServiceProtection()

            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling notification", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved called - keeping service alive")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "System low memory warning")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "Trim memory level: $level")
    }

    private fun setupServiceProtection() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            val intervalMillis = TimeUnit.MINUTES.toMillis(SERVICE_CHECK_INTERVAL_MINUTES)
            val triggerTime = SystemClock.elapsedRealtime() + intervalMillis

            // ELAPSED_REALTIME (비-WAKEUP) 사용: 기기를 깨우지 않고 다음 깨어날 때 실행
            // 이를 통해 불필요한 wake lock 발생을 방지합니다.
            // NotificationListenerService는 시스템이 알림 도착 시 자동으로 깨우므로
            // 별도의 WAKEUP 알람이 필요하지 않습니다.
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
            Log.d(TAG, "Service protection alarm set (${SERVICE_CHECK_INTERVAL_MINUTES} minutes interval, non-wakeup)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup service protection", e)
        }
    }

    private fun cancelServiceProtection() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Service protection alarm cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel service protection", e)
        }
    }
}
