package com.family.bankvoicealert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class BankNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotificationService"
        private val VERSION_CHECK_INTERVAL = TimeUnit.HOURS.toMillis(1) // 1시간마다 체크
        private const val DEPOSIT_CHANNEL_ID = "deposit_alert"
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
        ttsManager.useCloudTTS = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("use_cloud_tts", false)
        depositDataManager = DepositDataManager.getInstance(this)
        updateChecker = UpdateChecker(this)
        isServiceActive = true
        createDepositNotificationChannel()
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

            // SMS 앱에서 온 알림은 은행 관련 키워드가 있는지 추가 확인
            if (isSmsApp(packageName) && !hasBankingContext(text)) {
                Log.d(TAG, "SMS without banking context, skipping: $text")
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

                    // 화면 푸쉬 알림 표시 (설정이 켜져 있을 때만)
                    if (isPopupAlertEnabled()) {
                        showDepositNotification(amount)
                    }

                    // 음성 알림 (업데이트 필요 시 메시지 추가)
                    ttsManager.speakSimple("띵동", amount, updateMessage)
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
        if (!lowerText.contains("입금") ||
            !Regex("[0-9,]+\\s?원(?![가-힣])").containsMatchIn(text)) {
            return false
        }

        // 비입금 알림 제외 (신용정보 조회, 채권추심, 잔액 안내 등)
        val excludeKeywords = listOf(
            "신용정보",     // 신용정보 회사 (고려신용정보 등)
            "추심",         // 채권추심
            "예금주",       // 계좌 정보 안내 (잔액 조회)
            "기준"          // "~일 기준" 잔액/입금액 안내
        )

        if (excludeKeywords.any { text.contains(it) }) {
            Log.d(TAG, "Excluded non-deposit notification: $text")
            return false
        }

        return true
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

    private fun createDepositNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DEPOSIT_CHANNEL_ID,
                "입금 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "입금 감지 시 화면에 알림을 표시합니다"
                setShowBadge(true)
                enableLights(true)
                enableVibration(false) // 진동은 TTS쪽에서 처리
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // 소리는 TTS에서 처리
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun showDepositNotification(amount: String) {
        try {
            val formattedAmount = try {
                val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
                numberFormat.format(amount.toLong()) + "원"
            } catch (e: Exception) {
                "${amount}원"
            }

            val intent = Intent(this, DepositAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(DepositAlertActivity.EXTRA_AMOUNT, formattedAmount)
            }

            startActivity(intent)
            Log.d(TAG, "입금 팝업 직접 실행: $formattedAmount")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing deposit notification", e)
        }
    }

    private fun isSmsApp(packageName: String): Boolean {
        val smsApps = listOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.messaging"
        )
        return smsApps.contains(packageName) ||
               packageName.contains("messaging") ||
               packageName.contains(".mms") ||
               packageName.contains(".sms")
    }

    private fun hasBankingContext(text: String): Boolean {
        val bankKeywords = listOf(
            // 은행명
            "은행", "KB", "NH", "IBK", "SC", "BNK", "DGB", "JB",
            "카카오뱅크", "토스뱅크", "케이뱅크",
            "국민", "신한", "하나", "우리", "농협", "기업", "수협", "산업",
            "광주", "전북", "경남", "대구", "부산", "제주",
            // 금융 서비스
            "카드", "증권", "보험", "페이", "pay",
            // 거래 관련
            "잔액", "계좌", "이체"
        )
        return bankKeywords.any { text.contains(it, ignoreCase = true) }
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

    private fun isPopupAlertEnabled(): Boolean {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("popup_alert_enabled", true)
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
