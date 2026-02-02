package com.family.bankvoicealert

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

class DepositAlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AMOUNT = "extra_amount"
        private const val TAG = "DepositAlertActivity"
        private const val BANNER_AD_ID = "ca-app-pub-8476619670449177/7746664082"
        private const val DISMISS_DELAY_MS = 5000L
    }

    private var bannerAdView: AdView? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deposit_alert)

        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: ""
        val tvAmount = findViewById<TextView>(R.id.tvDepositAmount)
        tvAmount.text = "${amount} 입금되었습니다"

        // Tap anywhere to dismiss
        findViewById<ViewGroup>(R.id.rootLayout).setOnClickListener {
            dismissHandler.removeCallbacks(dismissRunnable)
            finish()
        }

        // Load banner ad
        loadBannerAd()

        // Auto-dismiss after 3 seconds
        dismissHandler.postDelayed(dismissRunnable, DISMISS_DELAY_MS)
    }

    private fun loadBannerAd() {
        val adContainer = findViewById<LinearLayout>(R.id.alertAdContainer)
        val adView = AdView(this)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = BANNER_AD_ID
        bannerAdView = adView
        adContainer.addView(adView)

        adView.loadAd(AdRequest.Builder().build())
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "Alert banner ad loaded")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.d(TAG, "Alert banner ad failed: ${error.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissHandler.removeCallbacks(dismissRunnable)
        bannerAdView?.destroy()
        bannerAdView = null
    }
}
