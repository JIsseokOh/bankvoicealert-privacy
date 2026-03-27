package com.family.bankvoicealert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CloudTTSManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudTTSManager"
        private const val CACHE_DIR_NAME = "tts_cache"
        private const val SAMPLE_RATE = 24000
        private const val PREFS_NAME = "cloud_tts_prefs"
        private const val KEY_CACHED_VERSION = "cached_version_code"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    private val speechQueue = ConcurrentLinkedQueue<CloudSpeechItem>()
    private val speechExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val loadingExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isSpeaking = false
    private var audioFocusRequest: Any? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile
    private var speakingStartTime = 0L
    private val speakingWatchdog = Runnable {
        if (isSpeaking && speakingStartTime > 0) {
            val elapsed = System.currentTimeMillis() - speakingStartTime
            if (elapsed > 10_000) {
                Log.w(TAG, "CloudTTS watchdog: stuck for ${elapsed}ms, resetting")
                restoreVolume()
                releaseAudioFocus()
                audioTrack?.release()
                audioTrack = null
                isSpeaking = false
                speakingStartTime = 0
                processNextInQueue()
            }
        }
    }
    @Volatile
    private var isPreGenerating = false
    @Volatile
    var isAssetsReady = false
        private set

    data class CloudSpeechItem(
        val message: String,
        val volumePercent: Int,
        val speechRate: Float = 1.0f
    )

    fun speak(message: String, volumePercent: Int, speechRate: Float = 1.0f) {
        speechQueue.offer(CloudSpeechItem(message, volumePercent, speechRate))
        if (!isSpeaking) {
            processNextInQueue()
        }
    }

    fun hasCachedAudio(text: String): Boolean {
        return File(cacheDir, getCacheKey(text)).exists()
    }

    private fun processNextInQueue() {
        if (speechQueue.isEmpty() || isSpeaking) return
        val item = speechQueue.poll() ?: return
        isSpeaking = true
        speakingStartTime = System.currentTimeMillis()
        mainHandler.postDelayed(speakingWatchdog, 10_000)

        speechExecutor.execute {
            try {
                val pcmData = getCachedAudio(item.message)
                if (pcmData != null) {
                    playPcmAudio(pcmData, item.volumePercent, item.speechRate)
                } else {
                    Log.w(TAG, "No cached audio for: ${item.message}")
                    mainHandler.removeCallbacks(speakingWatchdog)
                    isSpeaking = false
                    speakingStartTime = 0
                    processNextInQueue()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in cloud TTS", e)
                mainHandler.removeCallbacks(speakingWatchdog)
                isSpeaking = false
                speakingStartTime = 0
                processNextInQueue()
            }
        }
    }

    private fun getCachedAudio(text: String): ByteArray? {
        val cacheFile = File(cacheDir, getCacheKey(text))
        return if (cacheFile.exists()) {
            Log.d(TAG, "Cache hit: $text")
            cacheFile.readBytes()
        } else null
    }

    private fun playPcmAudio(pcmData: ByteArray, volumePercent: Int, speechRate: Float = 1.0f) {
        try {
            setVolume(volumePercent)
            requestAudioFocus()
            vibrateWithIntensity(volumePercent)

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack?.release()
            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                maxOf(bufferSize, pcmData.size),
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.apply {
                write(pcmData, 0, pcmData.size)
                setNotificationMarkerPosition(pcmData.size / 2) // 16-bit = 2 bytes per sample
                setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        mainHandler.removeCallbacks(speakingWatchdog)
                        restoreVolume()
                        releaseAudioFocus()
                        track?.release()
                        audioTrack = null
                        isSpeaking = false
                        speakingStartTime = 0
                        processNextInQueue()
                    }
                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })
                if (speechRate != 1.0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = PlaybackParams().setSpeed(speechRate)
                }
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing PCM audio", e)
            mainHandler.removeCallbacks(speakingWatchdog)
            restoreVolume()
            releaseAudioFocus()
            audioTrack?.release()
            audioTrack = null
            isSpeaking = false
            speakingStartTime = 0
            processNextInQueue()
        }
    }

    private fun getCacheKey(text: String): String {
        val hash = text.hashCode().toUInt().toString(16)
        return "gemini_tts_${hash}.pcm"
    }

    private fun setVolume(percent: Int) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolume = (maxVolume * percent) / 100
            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putInt("temp_original_volume_cloud", originalVolume).apply()
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
            val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxRingVolume * percent) / 100, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    private fun restoreVolume() {
        try {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val originalVolume = prefs.getInt("temp_original_volume_cloud", -1)
            if (originalVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                prefs.edit().remove("temp_original_volume_cloud").apply()
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
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
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

    /**
     * Copy pre-generated TTS audio from bundled assets to cache.
     * Assets cover 500 ~ 50,000 won in 500 increments (100 files).
     * Runs in background thread. Skips already-cached amounts.
     */
    fun loadPreGeneratedAssets() {
        if (isPreGenerating) {
            Log.d(TAG, "Asset loading already in progress, skipping")
            return
        }

        loadingExecutor.execute {
            isPreGenerating = true
            var copied = 0
            var skipped = 0

            try {
                val currentVersionCode = getCurrentVersionCode()
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val cachedVersion = prefs.getInt(KEY_CACHED_VERSION, -1)

                if (cachedVersion != currentVersionCode) {
                    clearCache()
                    Log.d(TAG, "Cache invalidated: version $cachedVersion -> $currentVersionCode")
                }

                for (amount in 500..50000 step 500) {
                    val formattedAmount = formatAmountForSpeech(amount.toLong())
                    val message = "띵동. $formattedAmount 확인"
                    val cacheFile = File(cacheDir, getCacheKey(message))

                    if (cacheFile.exists()) {
                        skipped++
                        continue
                    }

                    try {
                        context.assets.open("tts_pregenerated/$amount.pcm").use { input ->
                            FileOutputStream(cacheFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        copied++
                    } catch (e: Exception) {
                        // Asset not found for this amount, skip silently
                    }
                }

                prefs.edit().putInt(KEY_CACHED_VERSION, currentVersionCode).apply()
                isAssetsReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Error loading pre-generated assets", e)
            } finally {
                isPreGenerating = false
                Log.d(TAG, "Asset loading complete: $copied copied, $skipped already cached, ready=$isAssetsReady")
            }
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            -1
        }
    }

    private fun clearCache() {
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("gemini_tts_")) {
                file.delete()
            }
        }
    }

    private fun formatAmountForSpeech(amountLong: Long): String {
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

    fun shutdown() {
        mainHandler.removeCallbacks(speakingWatchdog)
        audioTrack?.release()
        audioTrack = null
        releaseAudioFocus()
        speechExecutor.shutdown()
        loadingExecutor.shutdown()
    }
}
