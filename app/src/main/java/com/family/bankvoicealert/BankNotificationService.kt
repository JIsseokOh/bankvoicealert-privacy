package com.family.bankvoicealert

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.TimeUnit

class BankNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotificationService"
        private val VERSION_CHECK_INTERVAL = TimeUnit.HOURS.toMillis(1) // 1시간마다 체크
    }

    private lateinit var ttsManager: TTSManager
    private lateinit var depositDataManager: DepositDataManager
    private lateinit var updateChecker: UpdateChecker
    private var isServiceActive = false

    private var versionCheckHandler: Handler? = null
    private var versionCheckRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        ttsManager = TTSManager.getInstance(this)
        depositDataManager = DepositDataManager.getInstance(this)
        updateChecker = UpdateChecker(this)
        isServiceActive = true
        Log.d(TAG, "Service created")

        // 주기적 버전 체크 시작
        startPeriodicVersionCheck()
    }

    private fun startPeriodicVersionCheck() {
        versionCheckHandler = Handler(Looper.getMainLooper())
        versionCheckRunnable = object : Runnable {
            override fun run() {
                if (isServiceActive) {
                    checkVersionInBackground()
                    versionCheckHandler?.postDelayed(this, VERSION_CHECK_INTERVAL)
                }
            }
        }
        // 첫 체크는 1분 후에 시작 (서비스 초기화 완료 후)
        versionCheckHandler?.postDelayed(versionCheckRunnable!!, TimeUnit.MINUTES.toMillis(1))
        Log.d(TAG, "Periodic version check scheduled (every 1 hour)")
    }

    private fun checkVersionInBackground() {
        Log.d(TAG, "Checking version in background...")
        updateChecker.checkForUpdate(
            onUpdateNeeded = { versionInfo ->
                Log.d(TAG, "Update available: ${versionInfo.latestVersionName}, force: ${versionInfo.forceUpdate}")
            },
            onError = { e ->
                Log.e(TAG, "Version check failed", e)
            }
        )
    }

    private fun stopPeriodicVersionCheck() {
        versionCheckRunnable?.let { runnable ->
            versionCheckHandler?.removeCallbacks(runnable)
        }
        versionCheckHandler = null
        versionCheckRunnable = null
        Log.d(TAG, "Periodic version check stopped")
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
                    // 매출 집계용 데이터 저장 (음성 로직과 독립적)
                    try {
                        val sender = extractSender(text)
                        depositDataManager.addDeposit(amount, sender)
                        Log.d(TAG, "입금 기록 저장: ${amount}원, 입금자: $sender")
                    } catch (e: Exception) {
                        Log.e(TAG, "입금 기록 저장 실패", e)
                    }

                    // 업데이트 필요 여부 확인
                    val updateMessage = getUpdateTTSMessage()

                    // 음성 알림 (업데이트 필요 시 메시지 추가)
                    ttsManager.speakSimple("입금확인", amount, updateMessage)
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

    private fun extractSender(text: String): String {
        // 입금자 이름 추출 시도
        // 패턴 1: "홍길동님으로부터", "홍길동 입금"
        val patterns = listOf(
            Regex("([가-힣]{2,4})님?으?로?부터"),
            Regex("([가-힣]{2,4})\\s*입금"),
            Regex("입금\\s*([가-힣]{2,4})"),
            Regex("([가-힣]{2,4})님")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val sender = match.groupValues[1]
                // 은행 이름이나 일반적인 단어 제외
                val excludeWords = listOf("입금", "출금", "이체", "송금", "결제", "알림", "은행", "계좌")
                if (!excludeWords.contains(sender)) {
                    return sender
                }
            }
        }

        return "알수없음"
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

    private fun getUpdateTTSMessage(): String? {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val needsUpdate = prefs.getBoolean(UpdateChecker.PREF_NEEDS_UPDATE, false)
        return if (needsUpdate) {
            prefs.getString(UpdateChecker.PREF_UPDATE_TTS_MESSAGE, null)
        } else {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceActive = false
        stopPeriodicVersionCheck()
        Log.d(TAG, "Service destroyed")
    }
}
