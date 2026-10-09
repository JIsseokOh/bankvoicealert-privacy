package com.family.bankvoicealert

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnPreDraw
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

    companion object {
        private const val TAG = "AdMob"

        // 적응형 배너가 지원하는 최소 너비(dp). 이보다 좁은 화면에서는 이 값으로 요청한다.
        private const val MIN_ADAPTIVE_WIDTH_DP = 320

        @Volatile
        private var isAdMobInitialized = false
    }

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
        if (!isAdMobInitialized) {
            MobileAds.initialize(activity) { _ ->
                isAdMobInitialized = true
                Log.d(TAG, "AdMob initialized - Using ${if (USE_TEST_ADS) "TEST" else "PRODUCTION"} ads")
            }
        }
    }

    fun loadBannerAd(adContainer: ViewGroup) {
        loadBannerAdInternal(adContainer, if (USE_TEST_ADS) TEST_BANNER_AD_ID else BANNER_AD_ID)
    }

    fun loadAlertBannerAd(adContainer: ViewGroup) {
        loadBannerAdInternal(adContainer, if (USE_TEST_ADS) TEST_BANNER_AD_ID else ALERT_BANNER_AD_ID)
    }

    /**
     * 앵커 적응형 배너를 로드한다.
     * 고정 320x50 대신 컨테이너의 실제 너비에 맞는 크기를 요청하므로 기기마다 화면 폭을 꽉 채운다.
     * 컨테이너 너비는 레이아웃이 끝난 뒤(onPreDraw)에 읽어야 정확하다.
     */
    private fun loadBannerAdInternal(adContainer: ViewGroup, adUnitId: String) {
        destroyBannerAd()

        val adView = AdView(activity)
        adView.adUnitId = adUnitId
        bannerAdView = adView
        adContainer.addView(adView)

        adContainer.doOnPreDraw {
            // 그 사이 destroyBannerAd()가 호출됐으면 로드하지 않는다
            if (bannerAdView !== adView) return@doOnPreDraw

            val adSize = anchoredAdaptiveSize(adContainer)
            adView.setAdSize(adSize)

            // 광고가 도착하기 전에도 자리를 미리 잡아 화면이 튀지 않게 한다
            adContainer.minimumHeight = adSize.getHeightInPixels(activity) +
                adContainer.paddingTop + adContainer.paddingBottom

            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner ad loaded ($adUnitId, ${adSize.width}x${adSize.height}dp)")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d(TAG, "Banner ad failed to load: ${loadAdError.message}")
                }
            }
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    /** 컨테이너의 내부 너비(dp)에 맞는 앵커 적응형 배너 크기 */
    private fun anchoredAdaptiveSize(adContainer: ViewGroup): AdSize {
        val metrics = activity.resources.displayMetrics
        var widthPx = adContainer.width - adContainer.paddingLeft - adContainer.paddingRight
        if (widthPx <= 0) {
            widthPx = metrics.widthPixels
        }
        val widthDp = (widthPx / metrics.density).toInt().coerceAtLeast(MIN_ADAPTIVE_WIDTH_DP)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
    }

    fun pauseBannerAd() {
        bannerAdView?.pause()
    }

    fun resumeBannerAd() {
        bannerAdView?.resume()
    }

    fun destroyBannerAd() {
        val adView = bannerAdView ?: return
        bannerAdView = null
        adView.destroy()
        (adView.parent as? ViewGroup)?.removeView(adView)
    }

    /**
     * 네이티브 광고를 로드해 컨테이너에 넣는다. 로드 전에는 컨테이너를 숨겨 두고 성공했을 때만 보여준다.
     * 미디어 영역은 가로형으로 요청해 다이얼로그 안에서 높이를 작게 유지한다.
     */
    fun loadNativeAd(adContainer: ViewGroup, onAdLoaded: () -> Unit = {}) {
        Log.d(TAG, "Starting native ad load...")
        val adUnitId = if (USE_TEST_ADS) TEST_NATIVE_AD_ID else NATIVE_AD_ID

        val adLoader = AdLoader.Builder(activity, adUnitId)
            .forNativeAd { ad ->
                if (activity.isFinishing || activity.isDestroyed) {
                    ad.destroy()
                    return@forNativeAd
                }
                nativeAd?.destroy()
                nativeAd = ad

                val adView = LayoutInflater.from(activity)
                    .inflate(R.layout.native_ad_layout, adContainer, false) as NativeAdView
                populateNativeAdView(ad, adView)

                adContainer.removeAllViews()
                adContainer.addView(adView)
                adContainer.visibility = View.VISIBLE
                Log.d(TAG, "Native ad view added to container")
                onAdLoaded()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Native ad failed to load: ${loadAdError.message}, code: ${loadAdError.code}")
                    adContainer.visibility = View.GONE
                }

                override fun onAdLoaded() {
                    Log.d(TAG, "Native ad loaded successfully")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .build()
            )
            .build()

        Log.d(TAG, "Requesting native ad...")
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
        mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        adView.mediaView = mediaView

        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        headlineView.text = nativeAd.headline
        adView.headlineView = headlineView

        // 본문과 버튼은 광고에 따라 없을 수 있다
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        val body = nativeAd.body
        if (body.isNullOrEmpty()) {
            bodyView.visibility = View.GONE
        } else {
            bodyView.text = body
            bodyView.visibility = View.VISIBLE
        }
        adView.bodyView = bodyView

        val callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
        val callToAction = nativeAd.callToAction
        if (callToAction.isNullOrEmpty()) {
            callToActionView.visibility = View.GONE
        } else {
            callToActionView.text = callToAction
            callToActionView.visibility = View.VISIBLE
        }
        adView.callToActionView = callToActionView

        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val icon = nativeAd.icon
        if (icon == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        }
        adView.iconView = iconView

        adView.setNativeAd(nativeAd)
    }

    fun destroyNativeAd() {
        nativeAd?.destroy()
        nativeAd = null
    }
}
