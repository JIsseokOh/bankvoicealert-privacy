package com.family.bankvoicealert

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DepositAlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AMOUNT = "extra_amount"
        private const val DISMISS_DELAY_MS = 90_000L  // 90 seconds
    }

    private lateinit var adManager: AdManager
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        setContentView(R.layout.activity_deposit_alert)
        adManager = AdManager(this)

        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: ""
        val tvAmount = findViewById<TextView>(R.id.tvDepositAmount)
        tvAmount.text = "${amount} 입금되었습니다"

        // Tap anywhere to dismiss
        findViewById<ViewGroup>(R.id.rootLayout).setOnClickListener {
            dismissHandler.removeCallbacks(dismissRunnable)
            finish()
        }

        // Load banner ad
        val adContainer = findViewById<LinearLayout>(R.id.alertAdContainer)
        adManager.loadAlertBannerAd(adContainer)

        // Auto-dismiss after 10 minutes
        dismissHandler.postDelayed(dismissRunnable, DISMISS_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        adManager.resumeBannerAd()
    }

    override fun onPause() {
        super.onPause()
        adManager.pauseBannerAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissHandler.removeCallbacks(dismissRunnable)
        adManager.destroyBannerAd()
    }
}
