package com.family.bankvoicealert

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

class AdManager(private val activity: Activity) {

    private val BANNER_AD_ID = "ca-app-pub-8476619670449177/7746664082"
    private val ALERT_BANNER_AD_ID = "ca-app-pub-8476619670449177/3624863581"
    private val NATIVE_AD_ID = "ca-app-pub-8476619670449177/4134722132"
    private val TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"
    private val TEST_NATIVE_AD_ID = "ca-app-pub-3940256099942544/2247696110"
    private val TEST_DEVICE_IDS = listOf("B3EEABB8EE11C2BE770B684D95219ECB")
    private val USE_TEST_ADS = false

    private var bannerAdView: AdView? = null
    private var nativeAd: NativeAd? = null

    init {
        if (!USE_TEST_ADS) {
            val config = RequestConfiguration.Builder()
                .setTestDeviceIds(TEST_DEVICE_IDS)
                .build()
            MobileAds.setRequestConfiguration(config)
        }
        MobileAds.initialize(activity) { initializationStatus ->
            Log.d("AdMob", "AdMob initialized - Using ${if (USE_TEST_ADS) "TEST" else "PRODUCTION"} ads")
        }
    }

    fun loadBannerAd(adContainer: ViewGroup) {
        loadBannerAdInternal(adContainer, if (USE_TEST_ADS) TEST_BANNER_AD_ID else BANNER_AD_ID)
    }

    fun loadAlertBannerAd(adContainer: ViewGroup) {
        loadBannerAdInternal(adContainer, if (USE_TEST_ADS) TEST_BANNER_AD_ID else ALERT_BANNER_AD_ID)
    }

    private fun loadBannerAdInternal(adContainer: ViewGroup, adUnitId: String) {
        val adView = AdView(activity)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = adUnitId
        bannerAdView = adView
        adContainer.addView(adView)

        val adRequest = AdRequest.Builder().build()
        bannerAdView?.loadAd(adRequest)

        bannerAdView?.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdMob", "Banner ad loaded ($adUnitId)")
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.d("AdMob", "Banner ad failed to load: ${loadAdError.message}")
            }
        }
    }

    fun pauseBannerAd() {
        bannerAdView?.pause()
    }

    fun resumeBannerAd() {
        bannerAdView?.resume()
    }

    fun destroyBannerAd() {
        bannerAdView?.destroy()
        bannerAdView = null
    }

    fun loadNativeAd(adContainer: ViewGroup, onAdLoaded: () -> Unit = {}) {
        Log.d("AdMob", "Starting native ad load...")
        val adUnitId = if (USE_TEST_ADS) TEST_NATIVE_AD_ID else NATIVE_AD_ID

        val adLoader = AdLoader.Builder(activity, adUnitId)
            .forNativeAd { ad ->
                Log.d("AdMob", "Native ad received, populating view...")
                nativeAd?.destroy()
                nativeAd = ad

                val adView = LayoutInflater.from(activity)
                    .inflate(R.layout.native_ad_layout, null) as NativeAdView
                populateNativeAdView(ad, adView)

                adContainer.removeAllViews()
                adContainer.addView(adView)
                adContainer.visibility = View.VISIBLE
                Log.d("AdMob", "Native ad view added to container")
                onAdLoaded()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("AdMob", "Native ad failed to load: ${loadAdError.message}, code: ${loadAdError.code}")
                    adContainer.visibility = View.GONE
                }

                override fun onAdLoaded() {
                    Log.d("AdMob", "Native ad loaded successfully")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        Log.d("AdMob", "Requesting native ad...")
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.mediaView = adView.findViewById<MediaView>(R.id.ad_media)

        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        headlineView.text = nativeAd.headline
        adView.headlineView = headlineView

        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        bodyView.text = nativeAd.body
        adView.bodyView = bodyView

        val callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
        callToActionView.text = nativeAd.callToAction
        adView.callToActionView = callToActionView

        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
        nativeAd.icon?.let { icon ->
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        } ?: run {
            iconView.visibility = View.GONE
        }
        adView.iconView = iconView

        adView.setNativeAd(nativeAd)
    }

    fun destroyNativeAd() {
        nativeAd?.destroy()
        nativeAd = null
    }
}
