package com.family.bankvoicealert

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class DepositAlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AMOUNT = "extra_amount"
        private const val TAG = "DepositAlertActivity"
        private const val DISMISS_DELAY_MS = 90_000L  // 90 seconds
    }

    private lateinit var adManager: AdManager
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { finish() }

    // 광고는 팝업당 한 번만, 사용자가 실제로 화면을 보고 있을 때만 로드한다
    private var adLoaded = false
    private var userPresentReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 창 플래그로 화면을 켜거나 잠금화면 위에 올리기 전에, 사용자가 보고 있던 상태였는지 먼저 기록한다
        val attendedAtLaunch = isUserAttending()

        // 잠금화면 위에 표시 및 화면 켜기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // 최상위 표시 플래그
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        setContentView(R.layout.activity_deposit_alert)
        adManager = AdManager(this)

        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: ""
        val tvAmount = findViewById<TextView>(R.id.tvDepositAmount)
        tvAmount.text = "${amount} 입금되었습니다"

        val rootLayout = findViewById<ViewGroup>(R.id.rootLayout)

        // 상태바·제스처 바·노치 영역만큼 여백을 줘서 카드와 광고가 시스템 바에 가려지지 않게 한다 (Android 15+ 엣지 투 엣지)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Tap anywhere to dismiss
        rootLayout.setOnClickListener {
            dismissHandler.removeCallbacks(dismissRunnable)
            finish()
        }

        // Google Play 정책상 잠금화면을 수익화할 수 없고, 꺼진 화면을 앱이 켜서 띄운 광고는 무효 트래픽이 된다.
        // 팝업 자체는 그대로 띄우되, 광고는 화면이 켜져 있고 잠금이 풀린 상태에서만 싣는다.
        // 잠긴 상태라면 사용자가 잠금을 해제했을 때(ACTION_USER_PRESENT) 그때 로드한다.
        if (attendedAtLaunch) {
            loadAdIfAttended()
        } else {
            Log.d(TAG, "Screen off or locked at launch - ad deferred until the user unlocks")
            registerUserPresentReceiver()
        }

        // Auto-dismiss after 90 seconds
        dismissHandler.postDelayed(dismissRunnable, DISMISS_DELAY_MS)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val amount = intent?.getStringExtra(EXTRA_AMOUNT) ?: ""
        val tvAmount = findViewById<TextView>(R.id.tvDepositAmount)
        tvAmount.text = "${amount} 입금되었습니다"

        // Reset 90-second timer
        dismissHandler.removeCallbacks(dismissRunnable)
        dismissHandler.postDelayed(dismissRunnable, DISMISS_DELAY_MS)

        loadAdIfAttended()
    }

    override fun onResume() {
        super.onResume()
        adManager.resumeBannerAd()
        loadAdIfAttended()
    }

    override fun onPause() {
        super.onPause()
        adManager.pauseBannerAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissHandler.removeCallbacks(dismissRunnable)
        unregisterUserPresentReceiver()
        adManager.destroyBannerAd()
    }

    /** 화면이 켜져 있고 잠금이 풀려 있어 사용자가 실제로 보고 있는 상태인지 */
    private fun isUserAttending(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return powerManager.isInteractive && !keyguardManager.isKeyguardLocked
    }

    /** 아직 광고를 싣지 않았고 사용자가 보고 있는 상태라면 광고를 로드한다 (팝업당 1회) */
    private fun loadAdIfAttended() {
        if (adLoaded || isFinishing || isDestroyed) return
        if (!isUserAttending()) return

        adLoaded = true
        unregisterUserPresentReceiver()
        val adContainer = findViewById<LinearLayout>(R.id.alertAdContainer)
        adManager.loadAlertBannerAd(adContainer)
    }

    private fun registerUserPresentReceiver() {
        if (userPresentReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    Log.d(TAG, "User unlocked the device - loading the deferred ad")
                    loadAdIfAttended()
                }
            }
        }
        userPresentReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterUserPresentReceiver() {
        val receiver = userPresentReceiver ?: return
        userPresentReceiver = null
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // 이미 해제된 경우
        }
    }
}
