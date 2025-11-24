package com.family.bankvoicealert

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.regex.Pattern

class NotificationListener : NotificationListenerService() {

    private lateinit var ttsManager: TTSManager
    private lateinit var depositDataManager: DepositDataManager

    override fun onCreate() {
        super.onCreate()
        ttsManager = TTSManager.getInstance(this)
        depositDataManager = DepositDataManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras

        // 알림 텍스트 가져오기
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        val bigText = extras.getString("android.bigText") ?: ""

        // 전체 텍스트 조합
        val fullText = "$title $text $bigText"

        Log.d("NotificationListener", "Package: $packageName")
        Log.d("NotificationListener", "Full text: $fullText")

        // 은행 앱 패키지 확인 및 입금 알림 처리
        when (packageName) {
            "com.kbankwith.smartbank", // 케이뱅크
            "com.kakaobank.channel", // 카카오뱅크
            "com.tossbank", // 토스뱅크
            "com.shinhan.sbanking", // 신한은행
            "com.kbstar.kbbank", // KB국민은행
            "com.wooribank.smarwoori", // 우리은행
            "com.hanabank.ebk.channel.android.hananbank", // 하나은행
            "com.ibk.android.ionebank", // IBK기업은행
            "com.epost.psf.sdsi", // 우체국
            "com.nh.mobilenew", // NH농협
            "kr.co.citibank.citimobile", // 씨티은행
            "com.sc.danb.scbankapp", // SC제일은행
            "com.kebhana.hanapush", // 하나원큐
            "com.toss" // 토스
            -> {
                processDepositNotification(fullText)
            }
        }
    }

    private fun processDepositNotification(text: String) {
        // 입금 패턴 매칭
        val patterns = listOf(
            Pattern.compile("입금\\s*([0-9,]+)원"),
            Pattern.compile("([0-9,]+)원\\s*입금"),
            Pattern.compile("입금.*?([0-9,]+)원"),
            Pattern.compile("\\+([0-9,]+)원"),
            Pattern.compile("받으셨습니다.*?([0-9,]+)원"),
            Pattern.compile("입금되었습니다.*?([0-9,]+)원")
        )

        var amount: String? = null
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                amount = matcher.group(1)
                break
            }
        }

        if (amount != null) {
            // 입금자 이름 추출 시도
            val depositorName = extractDepositorName(text)

            Log.d("NotificationListener", "Deposit detected: $depositorName - $amount원")

            // TTS로 알림 (데이터 저장도 함께 처리됨)
            ttsManager.speakDeposit(depositorName, amount)
        }
    }

    private fun extractDepositorName(text: String): String {
        // 입금자 이름 추출 패턴
        val patterns = listOf(
            Pattern.compile("([가-힣]+)\\s*님"),
            Pattern.compile("([가-힣]+)\\s*입금"),
            Pattern.compile("입금자\\s*[:：]\\s*([가-힣]+)"),
            Pattern.compile("보낸사람\\s*[:：]\\s*([가-힣]+)"),
            Pattern.compile("([가-힣]{2,})\\s*→"),
            Pattern.compile("from\\s+([가-힣]+)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1) ?: "알수없음"
            }
        }

        // 이름을 찾을 수 없는 경우
        return "알수없음"
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // 알림이 제거될 때의 처리 (필요시)
    }
}