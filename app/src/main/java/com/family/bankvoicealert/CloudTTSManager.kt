package com.family.bankvoicealert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class CloudTTSManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudTTSManager"
        private const val GEMINI_TTS_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro-preview-tts:generateContent"
        private const val CACHE_DIR_NAME = "tts_cache"
        private const val MAX_CACHE_SIZE = 200
        private const val SAMPLE_RATE = 24000
        private const val VOICE_NAME = "Kore"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    private val speechQueue = ConcurrentLinkedQueue<CloudSpeechItem>()

    private var audioTrack: AudioTrack? = null
    private var isSpeaking = false
    private var audioFocusRequest: Any? = null
    @Volatile
    private var isPreGenerating = false

    data class CloudSpeechItem(
        val message: String,
        val volumePercent: Int
    )

    fun speak(message: String, volumePercent: Int) {
        speechQueue.offer(CloudSpeechItem(message, volumePercent))
        if (!isSpeaking) {
            processNextInQueue()
        }
    }

    private fun processNextInQueue() {
        if (speechQueue.isEmpty() || isSpeaking) return
        val item = speechQueue.poll() ?: return
        isSpeaking = true

        Thread {
            try {
                val pcmData = getCachedAudio(item.message) ?: synthesizeAndCache(item.message)
                if (pcmData != null) {
                    playPcmAudio(pcmData, item.volumePercent)
                } else {
                    Log.e(TAG, "Failed to get audio for: ${item.message}")
                    isSpeaking = false
                    processNextInQueue()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in cloud TTS", e)
                isSpeaking = false
                processNextInQueue()
            }
        }.start()
    }

    private fun getCachedAudio(text: String): ByteArray? {
        val cacheFile = File(cacheDir, getCacheKey(text))
        return if (cacheFile.exists()) {
            Log.d(TAG, "Cache hit: $text")
            cacheFile.readBytes()
        } else null
    }

    private fun synthesizeAndCache(text: String): ByteArray? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) {
            Log.e(TAG, "Gemini API key not configured")
            return null
        }

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", text)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", VOICE_NAME)
                        })
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("$GEMINI_TTS_URL?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini TTS API error: ${response.code} ${response.body?.string()}")
            return null
        }

        val json = JSONObject(response.body?.string() ?: return null)
        val candidates = json.optJSONArray("candidates") ?: return null
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        val inlineData = parts.getJSONObject(0).optJSONObject("inlineData") ?: return null
        val audioBase64 = inlineData.getString("data")

        val pcmData = Base64.decode(audioBase64, Base64.DEFAULT)

        // Cache to file
        val cacheFile = File(cacheDir, getCacheKey(text))
        FileOutputStream(cacheFile).use { it.write(pcmData) }
        Log.d(TAG, "Cached: $text -> ${cacheFile.name} (${pcmData.size} bytes)")

        trimCache()
        return pcmData
    }

    private fun playPcmAudio(pcmData: ByteArray, volumePercent: Int) {
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
                        restoreVolume()
                        releaseAudioFocus()
                        track?.release()
                        audioTrack = null
                        isSpeaking = false
                        processNextInQueue()
                    }
                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing PCM audio", e)
            restoreVolume()
            releaseAudioFocus()
            audioTrack?.release()
            audioTrack = null
            isSpeaking = false
            processNextInQueue()
        }
    }

    private fun getCacheKey(text: String): String {
        val hash = text.hashCode().toUInt().toString(16)
        return "gemini_tts_${hash}.pcm"
    }

    private fun trimCache() {
        val files = cacheDir.listFiles() ?: return
        if (files.size > MAX_CACHE_SIZE) {
            files.sortBy { it.lastModified() }
            val toDelete = files.size - MAX_CACHE_SIZE
            for (i in 0 until toDelete) {
                files[i].delete()
            }
        }
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
     * Pre-generate TTS audio for common deposit amounts (1,000 ~ 20,000 won in 1,000 increments).
     * Runs in background thread. Skips already-cached amounts.
     */
    fun preGenerateCommonAmounts() {
        if (isPreGenerating) {
            Log.d(TAG, "Pre-generation already in progress, skipping")
            return
        }

        Thread {
            isPreGenerating = true
            Log.d(TAG, "Starting pre-generation of common deposit amounts")
            var generated = 0
            var skipped = 0

            try {
                for (amount in 1000..20000 step 1000) {
                    val formattedAmount = formatAmountForSpeech(amount.toLong())
                    val message = "띵동. $formattedAmount"
                    val cacheFile = File(cacheDir, getCacheKey(message))

                    if (cacheFile.exists()) {
                        skipped++
                        continue
                    }

                    val pcmData = synthesizeAndCache(message)
                    if (pcmData != null) {
                        generated++
                        Log.d(TAG, "Pre-generated: $message ($generated/20)")
                    } else {
                        Log.e(TAG, "Failed to pre-generate: $message")
                    }

                    // Small delay to avoid API rate limiting
                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during pre-generation", e)
            } finally {
                isPreGenerating = false
                Log.d(TAG, "Pre-generation complete: $generated generated, $skipped already cached")
            }
        }.start()
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
        audioTrack?.release()
        audioTrack = null
        releaseAudioFocus()
    }
}
