package com.family.bankvoicealert

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TTSManager private constructor(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val appContext = context.applicationContext
    private var isInitialized = false
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val depositDataManager = DepositDataManager(appContext)

    companion object {
        @Volatile
        private var INSTANCE: TTSManager? = null

        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context).also { INSTANCE = it }
            }
        }
    }

    init {
        initTTS()
    }

    private fun initTTS() {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTSManager", "Korean language is not supported")
                isInitialized = false
            } else {
                isInitialized = true
                // 초기 속도 설정
                val speed = prefs.getFloat("speech_rate", 1.0f)
                setSpeed(speed)
            }
        } else {
            Log.e("TTSManager", "TTS initialization failed")
            isInitialized = false
        }
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    fun speakDeposit(depositorName: String, amount: String) {
        if (!isInitialized) {
            Log.e("TTSManager", "TTS not initialized")
            return
        }

        // 매출 데이터 저장
        depositDataManager.saveDeposit(depositorName, amount)

        // 원본 음량 저장
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        // 설정된 볼륨 퍼센트 가져오기
        val volumePercent = prefs.getInt("volume_percent", 100)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (maxVolume * volumePercent / 100).coerceIn(0, maxVolume)

        // 볼륨 설정
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

        // 금액 포맷팅
        val formattedAmount = formatAmount(amount)

        // TTS 메시지
        val message = "입금확인 $formattedAmount"

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "deposit_${System.currentTimeMillis()}")
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                // 음량 복원
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            }
            override fun onError(utteranceId: String?) {
                // 음량 복원
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            }
        })

        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "deposit_${System.currentTimeMillis()}")
    }

    private fun formatAmount(amount: String): String {
        val cleanAmount = amount.replace(",", "").replace("원", "").trim()

        return try {
            val amountLong = cleanAmount.toLong()
            when {
                amountLong >= 100000000 -> {
                    val eok = amountLong / 100000000
                    val man = (amountLong % 100000000) / 10000
                    if (man > 0) {
                        "${eok}억 ${man}만원"
                    } else {
                        "${eok}억원"
                    }
                }
                amountLong >= 10000 -> {
                    val man = amountLong / 10000
                    val won = amountLong % 10000
                    if (won > 0) {
                        "${man}만 ${won}원"
                    } else {
                        "${man}만원"
                    }
                }
                else -> "${amountLong}원"
            }
        } catch (e: Exception) {
            amount
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}