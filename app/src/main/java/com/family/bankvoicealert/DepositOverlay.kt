package com.family.bankvoicealert

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 다른 앱(유튜브·음악·통화 등)을 쓰는 중에 들어온 입금을 보여주는 작은 입금 카드.
 *
 * DepositAlertActivity 는 액티비티라서 띄우는 순간 앞에 있던 앱이 일시정지 상태가 되고 포커스도 빼앗는다.
 * 이 카드는 '다른 앱 위에 표시' 창(TYPE_APPLICATION_OVERLAY)이어서 뒤의 앱은 그대로 돌아가고,
 * 카드 바깥을 누르면 뒤의 앱이 그 터치를 받는다. 카드를 누르거나 AUTO_DISMISS_MS 가 지나면 사라진다.
 * Google Play 정책상 다른 앱 위 창에는 광고를 실을 수 없으므로 광고는 넣지 않는다.
 */
class DepositOverlay private constructor(context: Context) {

    companion object {
        private const val TAG = "DepositOverlay"

        /** 카드가 저절로 사라지기까지의 시간. 뒤의 앱을 오래 가리지 않도록 입금 팝업 화면(90초)보다 짧게 둔다 */
        const val AUTO_DISMISS_MS = 20_000L

        /** 카드 창의 최대 너비(dp). 세로 화면에서는 화면 너비를 다 쓰고, 가로 화면에서는 가운데에 이 너비로 띄운다 */
        private const val MAX_CARD_WIDTH_DP = 480

        @Volatile
        private var INSTANCE: DepositOverlay? = null

        fun getInstance(context: Context): DepositOverlay {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DepositOverlay(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val appContext: Context = context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }

    // 떠 있는 카드와 그 카드를 붙인 WindowManager (창 컨텍스트의 것이어야 같은 창에서 뗄 수 있다)
    private var cardView: View? = null
    private var cardWindowManager: WindowManager? = null

    /** 카드가 떠 있는지 */
    val isShowing: Boolean
        get() = cardView != null

    /**
     * 카드를 띄운다. 이미 떠 있으면 금액만 바꾸고 자동 닫힘 시간을 다시 센다.
     * 권한이 없거나 창을 붙이지 못하면 false 를 돌려주므로, 호출한 쪽은 팝업 화면으로 대신 보여줄 수 있다.
     */
    fun show(amountText: String): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(amountText) }
            return true
        }
        if (!Settings.canDrawOverlays(appContext)) return false

        val existing = cardView
        if (existing != null) {
            bindAmount(existing, amountText)
            restartDismissTimer()
            return true
        }

        return try {
            val windowContext = createWindowContext()
            val windowManager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val themed = ContextThemeWrapper(windowContext, R.style.Theme_BankVoiceAlert)
            val card = LayoutInflater.from(themed).inflate(R.layout.view_deposit_overlay, null)
            bindAmount(card, amountText)
            card.setOnClickListener { dismiss() }
            windowManager.addView(card, buildLayoutParams(windowContext))
            cardView = card
            cardWindowManager = windowManager
            restartDismissTimer()
            Log.d(TAG, "Overlay card shown: $amountText")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay card", e)
            false
        }
    }

    /** 카드를 지운다 (어느 스레드에서 불러도 된다) */
    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        mainHandler.removeCallbacks(dismissRunnable)
        val card = cardView ?: return
        val windowManager = cardWindowManager
        cardView = null
        cardWindowManager = null
        try {
            windowManager?.removeView(card)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay card", e)
        }
    }

    private fun bindAmount(card: View, amountText: String) {
        card.findViewById<TextView>(R.id.tvDepositAmount).text = "$amountText 입금되었습니다"
    }

    private fun restartDismissTimer() {
        mainHandler.removeCallbacks(dismissRunnable)
        mainHandler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
    }

    /** Android 11+ 에서는 창 전용 컨텍스트를 써야 화면 회전·크기 정보가 창에 맞게 들어온다 */
    private fun createWindowContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return appContext
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return appContext
        return appContext.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    private fun buildLayoutParams(windowContext: Context): WindowManager.LayoutParams {
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val metrics = windowContext.resources.displayMetrics
        val maxWidthPx = (MAX_CARD_WIDTH_DP * metrics.density).toInt()
        val width = if (metrics.widthPixels > maxWidthPx) maxWidthPx else WindowManager.LayoutParams.MATCH_PARENT
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // 포커스를 빼앗지 않고(뒤의 앱이 계속 입력을 받음), 카드 바깥 터치는 뒤의 앱으로 보낸다
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            windowAnimations = android.R.style.Animation_Toast
        }
    }
}
