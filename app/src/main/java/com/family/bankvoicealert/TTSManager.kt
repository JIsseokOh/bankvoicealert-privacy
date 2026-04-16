package com.family.bankvoicealert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        private const val WATCHDOG_TIMEOUT_MS = 10_000L
        private const val MAX_INIT_RETRIES = 3
        private const val INIT_RETRY_DELAY_MS = 2_000L

        @Volatile
        private var INSTANCE: TTSManager? = null

        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val context: Context = context
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val bluetoothAudioManager: BluetoothAudioManager = BluetoothAudioManager.getInstance(context)
    private val speechQueue = ConcurrentLinkedQueue<SpeechItem>()
    private val pendingQueue = ConcurrentLinkedQueue<SpeechItem>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var audioFocusRequest: Any? = null
    @Volatile private var isInitialized = false
    @Volatile private var isSpeaking = false
    @Volatile private var speakingStartTime = 0L
    private var initRetryCount = 0

    private val speakingWatchdog = Runnable {
        if (isSpeaking && speakingStartTime > 0) {
            val elapsed = System.currentTimeMillis() - speakingStartTime
            if (elapsed > WATCHDOG_TIMEOUT_MS) {
                Log.w(TAG, "Watchdog: stuck for ${elapsed}ms, resetting")
                resetSpeakingState()
                processNextInQueue()
            }
        }
    }

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
            Log.e(TAG, "TTS init failed (attempt ${initRetryCount + 1})")
            retryInit()
            return
        }

        val result = tts?.setLanguage(Locale.KOREAN)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "Korean not supported (attempt ${initRetryCount + 1})")
            retryInit()
            return
        }

        initRetryCount = 0
        isInitialized = true
        setOptimalVoice()
        Log.d(TAG, "TTS initialized successfully")
        processPendingQueue()
    }

    private fun retryInit() {
        if (initRetryCount < MAX_INIT_RETRIES) {
            initRetryCount++
            mainHandler.postDelayed({
                tts?.shutdown()
                tts = TextToSpeech(context, this)
                setupTTSListener()
            }, INIT_RETRY_DELAY_MS)
        } else {
            Log.e(TAG, "TTS init failed after $MAX_INIT_RETRIES attempts")
        }
    }

    private fun setOptimalVoice() {
        val voices = tts?.voices ?: return
        val koreanVoices = voices.filter { it.locale.language == "ko" }

        val selectedVoice: Voice? =
            koreanVoices.find { it.name.contains("Google", ignoreCase = true) }
                ?: koreanVoices.find {
                    it.name.contains("female", ignoreCase = true) ||
                    it.name.contains("여", ignoreCase = true)
                }
                ?: koreanVoices.firstOrNull()

        selectedVoice?.let {
            tts?.voice = it
            Log.d(TAG, "Selected voice: ${it.name}")
        }
    }

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS completed: $utteranceId")
                resetSpeakingState()
                processNextInQueue()
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
                resetSpeakingState()
                processNextInQueue()
            }
        })
    }

    fun speakSimple(message: String, amount: String, suffix: String? = null) {
        val baseMessage = "$message. ${formatAmount(amount)} 확인"
        val fullMessage = if (suffix != null) "$baseMessage. $suffix" else baseMessage
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val volumePercent = prefs.getInt("volume_percent", 100)
        val speechRate = prefs.getFloat("speech_rate", 1.0f)

        if (!isInitialized) {
            pendingQueue.offer(SpeechItem(fullMessage, speechRate, volumePercent))
            return
        }
        speechQueue.offer(SpeechItem(fullMessage, speechRate, volumePercent))
        if (!isSpeaking) processNextInQueue()
    }

    private fun processPendingQueue() {
        while (pendingQueue.isNotEmpty()) {
            val item = pendingQueue.poll() ?: break
            speechQueue.offer(item)
        }
        if (!isSpeaking) processNextInQueue()
    }

    private fun processNextInQueue() {
        if (speechQueue.isEmpty() || isSpeaking) return

        val item = speechQueue.poll() ?: return
        isSpeaking = true
        speakingStartTime = System.currentTimeMillis()
        mainHandler.postDelayed(speakingWatchdog, WATCHDOG_TIMEOUT_MS)
        speakNow(item)
    }

    private fun speakNow(item: SpeechItem) {
        val currentTts = tts
        if (currentTts == null) {
            Log.e(TAG, "TTS engine is null, skipping")
            resetSpeakingState()
            processNextInQueue()
            return
        }

        val randomPitch = 0.8f + (Random.nextFloat() * 0.2f)
        currentTts.setSpeechRate(item.speechRate)
        currentTts.setPitch(randomPitch)

        val btConnected = bluetoothAudioManager.isBluetoothAudioConnected()
        val streamType = if (btConnected) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM

        setMaxVolume(item.volumePercent, btConnected)
        requestAudioFocus(btConnected)
        applyAudioAttributes(currentTts, btConnected)

        val ttsVolume = if (btConnected) {
            (item.volumePercent / 100f).coerceIn(0.05f, 1.0f)
        } else {
            1.0f
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
        }

        val result = currentTts.speak(item.message, TextToSpeech.QUEUE_FLUSH, params, item.utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS speak ERROR: ${item.message}")
            resetSpeakingState()
            processNextInQueue()
            return
        }

        vibrateWithIntensity(item.volumePercent)
    }

    private fun applyAudioAttributes(currentTts: TextToSpeech, btConnected: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(
                        if (btConnected) AudioAttributes.USAGE_MEDIA
                        else AudioAttributes.USAGE_ALARM
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                currentTts.setAudioAttributes(attrs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying audio attributes", e)
        }
    }

    private fun resetSpeakingState() {
        mainHandler.removeCallbacks(speakingWatchdog)
        restoreVolume()
        releaseAudioFocus()
        isSpeaking = false
        speakingStartTime = 0
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

    private fun setMaxVolume(percent: Int, btConnected: Boolean) {
        try {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            if (btConnected) {
                val originalMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                prefs.edit()
                    .putInt("temp_original_music_vol", originalMusic)
                    .apply()
                val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            } else {
                val originalAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val originalRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                prefs.edit()
                    .putInt("temp_original_alarm_vol", originalAlarm)
                    .putInt("temp_original_ring_vol", originalRing)
                    .apply()

                val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxAlarm * percent) / 100, 0)
                val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxRing * percent) / 100, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    private fun restoreVolume() {
        try {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val originalMusic = prefs.getInt("temp_original_music_vol", -1)
            val originalAlarm = prefs.getInt("temp_original_alarm_vol", -1)
            val originalRing = prefs.getInt("temp_original_ring_vol", -1)
            if (originalMusic != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusic, 0)
            }
            if (originalAlarm != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarm, 0)
            }
            if (originalRing != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, originalRing, 0)
            }
            prefs.edit()
                .remove("temp_original_music_vol")
                .remove("temp_original_alarm_vol")
                .remove("temp_original_ring_vol")
                .apply()
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

    private fun requestAudioFocus(btConnected: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val usage = if (btConnected) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM
                val attrs = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = focusReq
                audioManager.requestAudioFocus(focusReq)
            } else {
                val streamType = if (btConnected) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, streamType, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
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

    fun shutdown() {
        mainHandler.removeCallbacks(speakingWatchdog)
        releaseAudioFocus()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isSpeaking = false
        synchronized(Companion) {
            INSTANCE = null
        }
    }
}
