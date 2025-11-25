package com.family.bankvoicealert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

class TTSManager private constructor(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TTSManager"

        @Volatile
        private var INSTANCE: TTSManager? = null

        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // 은행 이름 발음 변환 맵
        private val BANK_PRONUNCIATION = mapOf(
            "KB국민은행" to "케이비 국민은행",
            "KB" to "케이비",
            "NH농협은행" to "엔에이치 농협은행",
            "NH" to "엔에이치",
            "IBK기업은행" to "아이비케이 기업은행",
            "IBK" to "아이비케이",
            "KEB하나은행" to "케이이비 하나은행",
            "SC제일은행" to "에스씨 제일은행",
            "SC" to "에스씨",
            "DGB대구은행" to "디지비 대구은행",
            "BNK부산은행" to "비엔케이 부산은행",
            "BNK경남은행" to "비엔케이 경남은행",
            "신한은행" to "신한은행",
            "우리은행" to "우리은행",
            "하나은행" to "하나은행",
            "카카오뱅크" to "카카오뱅크",
            "토스뱅크" to "토스뱅크",
            "케이뱅크" to "케이뱅크",
            "농협은행" to "농협은행",
            "수협은행" to "수협은행",
            "산업은행" to "산업은행",
            "우체국" to "우체국",
            "새마을금고" to "새마을금고",
            "신협" to "신협"
        )
    }

    private val context: Context = context
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val speechQueue = ConcurrentLinkedQueue<SpeechItem>()

    private var tts: TextToSpeech? = null
    private var audioFocusRequest: Any? = null
    private var isInitialized = false
    private var isSpeaking = false

    data class SpeechItem(
        val message: String,
        val speechRate: Float,
        val volumePercent: Int,
        val utteranceId: String = UUID.randomUUID().toString()
    )

    init {
        tts = TextToSpeech(context, this)
        setupTTSListener()
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS initialization failed")
            return
        }

        val result = tts?.setLanguage(Locale.KOREAN)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "Korean language not supported")
            return
        }

        isInitialized = true
        setOptimalVoice()
        Log.d(TAG, "TTS initialized successfully")
    }

    private fun setOptimalVoice() {
        val voices = tts?.voices ?: return

        // 한국어 음성 필터링
        val koreanVoices = voices.filter { it.locale.language == "ko" }

        // Google 음성 우선 선택
        var selectedVoice: Voice? = koreanVoices.find {
            it.name.contains("Google", ignoreCase = true)
        }

        // Google 음성이 없으면 여성 음성 선택
        if (selectedVoice == null) {
            selectedVoice = koreanVoices.find { voice ->
                voice.name.contains("female", ignoreCase = true) ||
                voice.name.contains("여", ignoreCase = true)
            }
        }

        // 그래도 없으면 첫 번째 한국어 음성 선택
        if (selectedVoice == null) {
            selectedVoice = koreanVoices.firstOrNull()
        }

        selectedVoice?.let { voice ->
            tts?.voice = voice
            Log.d(TAG, "Selected voice: ${voice.name}")
        }
    }

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS completed: $utteranceId")
                restoreVolume()
                releaseAudioFocus()
                isSpeaking = false
                processNextInQueue()
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
                restoreVolume()
                releaseAudioFocus()
                isSpeaking = false
                processNextInQueue()
            }
        })
    }

    fun speakSimple(message: String, amount: String, suffix: String? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized")
            return
        }

        val fullMessage = if (suffix != null) {
            "$message. ${formatAmount(amount)}. $suffix"
        } else {
            "$message. ${formatAmount(amount)}"
        }
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val speechRate = prefs.getFloat("speech_rate", 1.0f)
        val volumePercent = prefs.getInt("volume_percent", 100)

        speechQueue.offer(SpeechItem(fullMessage, speechRate, volumePercent))
        Log.d(TAG, "Added to queue: $fullMessage (queue size: ${speechQueue.size})")

        if (!isSpeaking) {
            processNextInQueue()
        }
    }

    fun speakDeposit(bank: String, amount: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized")
            return
        }

        val fullMessage = "입금확인. ${formatAmount(amount)}"
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val speechRate = prefs.getFloat("speech_rate", 1.0f)
        val volumePercent = prefs.getInt("volume_percent", 100)

        speechQueue.offer(SpeechItem(fullMessage, speechRate, volumePercent))
        Log.d(TAG, "Added to queue: $fullMessage (queue size: ${speechQueue.size})")

        if (!isSpeaking) {
            processNextInQueue()
        }
    }

    private fun processNextInQueue() {
        if (speechQueue.isEmpty() || isSpeaking) {
            return
        }

        val item = speechQueue.poll() ?: return
        isSpeaking = true
        speakNow(item)
    }

    private fun speakNow(item: SpeechItem) {
        // 약간의 피치 변화를 주어 자연스러운 음성 생성
        val randomPitch = 0.8f + (Random.nextFloat() * 0.2f)

        tts?.setSpeechRate(item.speechRate)
        tts?.setPitch(randomPitch)

        setMaxVolume(item.volumePercent)
        requestAudioFocus()

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(item.message, TextToSpeech.QUEUE_FLUSH, params, item.utteranceId)
        vibrateWithIntensity(item.volumePercent)

        Log.d(TAG, "Speaking now: ${item.message} (rate: ${item.speechRate}, pitch: $randomPitch, volume: ${item.volumePercent}%)")
    }

    private fun convertBankName(bank: String): String {
        // 정확히 일치하는 경우
        BANK_PRONUNCIATION[bank]?.let { return it }

        // 부분 일치하는 경우
        for ((key, value) in BANK_PRONUNCIATION) {
            if (bank.contains(key)) {
                return bank.replace(key, value)
            }
        }

        return bank
    }

    private fun formatAmount(amount: String): String {
        val cleanAmount = Regex("[^0-9]").replace(amount, "")
        val amountLong = cleanAmount.toLongOrNull() ?: return amount

        return when {
            amountLong >= 100_000_000 -> {
                val eok = amountLong / 100_000_000
                val man = (amountLong % 100_000_000) / 10_000
                if (man > 0) "${eok}억 ${man}만원" else "${eok}억원"
            }
            amountLong >= 10_000 -> {
                val man = amountLong / 10_000
                val won = amountLong % 10_000
                if (won > 0) "${man}만 ${won}원" else "${man}만원"
            }
            else -> "${amountLong}원"
        }
    }

    private fun setMaxVolume(percent: Int) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolume = (maxVolume * percent) / 100

            // 원본 볼륨 저장
            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit()
                .putInt("temp_original_volume", originalVolume)
                .apply()

            // 볼륨 설정
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

            // 링 볼륨도 설정
            val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxRingVolume * percent) / 100, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    private fun restoreVolume() {
        try {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val originalVolume = prefs.getInt("temp_original_volume", -1)

            if (originalVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                prefs.edit().remove("temp_original_volume").apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring volume", e)
        }
    }

    private fun vibrateWithIntensity(percent: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = ((percent * 255) / 100).coerceIn(1, 255)
                vibrator.vibrate(VibrationEffect.createOneShot(200, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating", e)
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { }
                    .build()

                audioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
        }
    }

    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? AudioFocusRequest)?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio focus", e)
        }
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    fun shutdown() {
        releaseAudioFocus()
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
    }
}
