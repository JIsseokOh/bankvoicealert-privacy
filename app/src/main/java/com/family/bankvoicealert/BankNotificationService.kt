package com.family.bankvoicealert

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

class BankNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotificationService"
    }

    private lateinit var ttsManager: TTSManager
    private var isServiceActive = false

    override fun onCreate() {
        super.onCreate()
        ttsManager = TTSManager.getInstance(this)
        isServiceActive = true
        Log.d(TAG, "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isServiceActive || !isBackgroundEnabled()) {
            return
        }

        try {
            val packageName = sbn.packageName
            val notification = sbn.notification

            Log.d(TAG, "Notification from: $packageName")

            // 차단된 앱인지 확인
            if (isBlockedApp(packageName)) {
                return
            }

            // 알림 텍스트 추출
            val text = extractNotificationText(notification) ?: return

            Log.d(TAG, "Text: $text")

            // 입금 알림인지 확인
            if (!isDepositNotification(text)) {
                return
            }

            // 금액 추출
            val amount = extractAmount(text)
            if (amount.isNotEmpty()) {
                Log.d(TAG, "입금 감지: ${amount}원")

                // 중복 확인
                if (!DuplicateChecker.getInstance().isDuplicate(amount, "")) {
                    ttsManager.speakSimple("입금확인", amount)
                } else {
                    Log.d(TAG, "중복 알림 무시: ${amount}원")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 알림 제거 시 처리 (필요시)
    }

    private fun extractNotificationText(notification: Notification): String? {
        val extras = notification.extras

        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString() ?: ""

        val allTexts = listOf(title, text, bigText, subText).filter { it.isNotEmpty() }
        return allTexts.joinToString(" ")
    }

    private fun isDepositNotification(text: String): Boolean {
        val lowerText = text.lowercase(Locale.ROOT)
        // "입금" 키워드와 금액 패턴 (xxx원) 확인
        return lowerText.contains("입금") &&
               Regex("[0-9,]+\\s?원(?![가-힣])").containsMatchIn(text)
    }

    private fun extractAmount(text: String): String {
        val regex = Regex("([0-9,]+)\\s?원(?![가-힣])")
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.replace(",", "") ?: ""
    }

    private fun isBlockedApp(packageName: String): Boolean {
        // 메신저 앱 등 차단 목록
        val blockedApps = listOf(
            "com.kakao.talk",           // 카카오톡
            "jp.naver.line.android",    // 라인
            "com.facebook.orca",        // 페이스북 메신저
            "com.whatsapp",             // 왓츠앱
            "org.telegram.messenger",   // 텔레그램
            "com.discord",              // 디스코드
            "com.skype.raider",         // 스카이프
            "com.viber.voip",           // 바이버
            "com.imo.android.imoim",    // 아이모
            "com.instagram.android",    // 인스타그램
            "com.snapchat.android",     // 스냅챗
            "com.tencent.mm",           // 위챗
            "com.bbm"                   // BBM
        )
        return blockedApps.contains(packageName)
    }

    private fun isBackgroundEnabled(): Boolean {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("background_enabled", false)
        Log.d(TAG, "Background enabled check: $enabled")
        return enabled
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceActive = false
        Log.d(TAG, "Service destroyed")
    }
}
