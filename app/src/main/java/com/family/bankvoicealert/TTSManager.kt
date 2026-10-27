package com.family.bankvoicealert

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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
import androidx.core.content.ContextCompat
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

class TTSManager private constructor(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TTSManager"
        // 합성(파일 생성) + 재생이 모두 끝나야 하므로 여유 있게 잡는다
        private const val WATCHDOG_TIMEOUT_MS = 15_000L
        private const val MAX_INIT_RETRIES = 3
        private const val INIT_RETRY_DELAY_MS = 2_000L
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        private const val SAMSUNG_TTS_PACKAGE = "com.samsung.SMT"
        private const val SYNTH_FILE_PREFIX = "tts_"
        // 엔진 연결이 끊긴 것을 발견해 다시 연결한 뒤, 같은 항목을 다시 읽어 보는 최대 횟수 (무한 반복 방지)
        private const val MAX_RECOVERY_ATTEMPTS = 1
        // 초기화를 시작한 뒤 이 시간 안에는 ensureReady 가 엔진을 또 만들지 않는다
        private const val INIT_PENDING_TIMEOUT_MS = 15_000L
        // 엔진 앱이 업데이트된 뒤 새 엔진이 자리 잡을 시간을 조금 준 다음 다시 연결한다
        private const val ENGINE_UPDATE_REINIT_DELAY_MS = 3_000L

        /** 사용자가 고른 목소리(엔진의 Voice 이름)를 저장하는 설정 키 */
        const val PREF_VOICE_NAME = "voice_name"
        /** 화면에 보여주는 목소리 버튼의 최대 개수 (목소리1 ~ 목소리5) */
        const val MAX_VOICE_OPTIONS = 5
        /** 목소리 버튼을 눌렀을 때 들려주는 샘플 (테스트 재생과 같은 문장) */
        private const val PREVIEW_MESSAGE = "띵동"
        private const val PREVIEW_AMOUNT = "10000"

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
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // 현재 엔진에서 고를 수 있는 한국어 목소리 (초기화 후 채워짐, 최대 MAX_VOICE_OPTIONS개, 0번이 기본 목소리)
    @Volatile private var voiceOptions: List<Voice> = emptyList()
    // 엔진에 마지막으로 적용한 목소리 이름 (같은 목소리를 다시 setVoice 하지 않기 위함)
    @Volatile private var appliedVoiceName: String? = null

    // 파일로 합성 중인 항목 (utteranceId -> 항목). 합성이 끝나면 우리 프로세스에서 직접 재생한다.
    private val synthesizing = ConcurrentHashMap<String, SpeechItem>()
    private var mediaPlayer: MediaPlayer? = null
    private var playingFile: File? = null

    private var tts: TextToSpeech? = null
    private var audioFocusRequest: Any? = null
    @Volatile private var isInitialized = false
    @Volatile private var isSpeaking = false
    @Volatile private var speakingStartTime = 0L
    // 지금 읽는 중인 항목 (엔진이 멈추거나 연결이 끊겼을 때 같은 항목을 다시 시도하기 위해 기억한다)
    @Volatile private var currentItem: SpeechItem? = null
    // 엔진 객체를 마지막으로 만든 시각 (0이면 진행 중인 초기화가 없음)
    @Volatile private var initStartedAt = 0L
    private var initRetryRunnable: Runnable? = null
    private var engineUpdateReceiver: BroadcastReceiver? = null
    private var initRetryCount = 0
    @Volatile private var triedGoogleFallback = false
    @Volatile private var koreanUnavailable = false
    @Volatile private var koreanNeedsData = false
    private var notifiedUnavailable = false

    /**
     * 자동 엔진 폴백(구글 TTS 전환)으로도 한국어 음성을 쓸 수 없을 때 한 번만 호출된다.
     * needsData=true 면 한국어 음성 데이터만 없는 상태, false 면 한국어 지원 엔진 자체가 없는 상태.
     * 콜백 등록 시점에 이미 사용 불가가 확정돼 있으면 즉시 통지한다.
     */
    var onKoreanUnavailable: ((needsData: Boolean) -> Unit)? = null
        set(value) {
            field = value
            if (value != null && koreanUnavailable && !notifiedUnavailable) {
                notifiedUnavailable = true
                val needsData = koreanNeedsData
                mainHandler.post { value.invoke(needsData) }
            }
        }

    /**
     * 고를 수 있는 목소리 목록이 바뀌었을 때(엔진 초기화·전환 완료, 한국어 사용 불가 확정) 메인 스레드에서 호출된다.
     * 등록 시점에 이미 초기화가 끝나 있으면 즉시 한 번 통지한다.
     */
    var onVoicesChanged: (() -> Unit)? = null
        set(value) {
            field = value
            if (value != null && isInitialized) {
                mainHandler.post { value.invoke() }
            }
        }

    private val speakingWatchdog = Runnable {
        if (isSpeaking && speakingStartTime > 0) {
            val elapsed = System.currentTimeMillis() - speakingStartTime
            if (elapsed >= WATCHDOG_TIMEOUT_MS) {
                Log.w(TAG, "Watchdog: stuck for ${elapsed}ms, resetting")
                val stuck = currentItem
                if (stuck != null && stuck.recoveryAttempts < MAX_RECOVERY_ATTEMPTS) {
                    // 엔진이 응답하지 않는다(업데이트·강제 종료 뒤 남은 죽은 연결 등): 엔진을 새로 만들고 한 번 더 읽는다
                    recoverEngine(stuck, "watchdog timeout after ${elapsed}ms")
                } else {
                    synthesizing.clear()
                    stopPlayback()
                    resetSpeakingState()
                    processNextInQueue()
                }
            }
        }
    }

    data class SpeechItem(
        val message: String,
        val speechRate: Float,
        val volumePercent: Int,
        val voiceName: String? = null,
        val isPreview: Boolean = false,
        val utteranceId: String = UUID.randomUUID().toString(),
        /** 엔진 연결을 다시 만든 뒤 이 항목을 다시 시도한 횟수 */
        val recoveryAttempts: Int = 0
    )

    /** 화면에서 고를 수 있는 목소리 하나. index 는 0부터, label 은 "목소리1"처럼 사람이 보는 이름 */
    data class VoiceOption(val index: Int, val name: String) {
        val label: String get() = "목소리${index + 1}"
    }

    init {
        cleanupSynthFiles()
        createEngine()
        registerEngineUpdateReceiver()
    }

    /** 엔진 객체를 새로 만든다 (engine 이 null 이면 기기 기본 엔진). 초기화 결과는 onInit 으로 온다 */
    private fun createEngine(engine: String? = null) {
        initStartedAt = System.currentTimeMillis()
        tts = if (engine != null) TextToSpeech(context, this, engine) else TextToSpeech(context, this)
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
            Log.e(TAG, "Korean unavailable on engine ${currentEngineName()} (result=$result)")
            // 기본 엔진(예: 삼성 SMT)이 한국어를 못 하면, 설치돼 있는 구글 TTS 엔진으로 한 번 전환해 본다.
            if (!triedGoogleFallback && currentEngineName() != GOOGLE_TTS_PACKAGE && isGoogleTtsInstalled()) {
                triedGoogleFallback = true
                switchToGoogleEngine()
                return
            }
            // 폴백할 구글 엔진이 없거나, 구글로도 실패 → 한국어 사용 불가 확정 후 1회 통지
            koreanNeedsData = (result == TextToSpeech.LANG_MISSING_DATA)
            koreanUnavailable = true
            initStartedAt = 0L
            voiceOptions = emptyList()
            notifyVoicesChanged()
            notifyKoreanUnavailable()
            return
        }

        initRetryCount = 0
        initStartedAt = 0L
        isInitialized = true
        koreanUnavailable = false
        appliedVoiceName = null
        refreshVoiceOptions()
        applyVoice(selectedVoiceName())
        Log.d(TAG, "TTS initialized successfully on ${currentEngineName()}")
        notifyVoicesChanged()
        processPendingQueue()
    }

    private fun retryInit() {
        if (initRetryCount < MAX_INIT_RETRIES) {
            initRetryCount++
            val retry = Runnable {
                initRetryRunnable = null
                try {
                    tts?.shutdown()
                } catch (e: Exception) {
                    Log.e(TAG, "Error shutting down failed TTS", e)
                }
                createEngine()
            }
            initRetryRunnable = retry
            mainHandler.postDelayed(retry, INIT_RETRY_DELAY_MS)
        } else {
            Log.e(TAG, "TTS init failed after $MAX_INIT_RETRIES attempts")
            // 다음 ensureReady(앱 실행·입금) 때 처음부터 다시 시도할 수 있게 한다
            initStartedAt = 0L
        }
    }

    private fun currentEngineName(): String? =
        try { tts?.defaultEngine } catch (e: Exception) { null }

    private fun isGoogleTtsInstalled(): Boolean {
        return try {
            tts?.engines?.any { it.name == GOOGLE_TTS_PACKAGE } == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query installed TTS engines", e)
            false
        }
    }

    private fun switchToGoogleEngine() {
        mainHandler.post {
            Log.d(TAG, "Switching TTS engine to Google ($GOOGLE_TTS_PACKAGE)")
            try {
                tts?.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down before engine switch", e)
            }
            createEngine(GOOGLE_TTS_PACKAGE)
        }
    }

    private fun notifyKoreanUnavailable() {
        val callback = onKoreanUnavailable
        if (callback == null || notifiedUnavailable) return
        notifiedUnavailable = true
        val needsData = koreanNeedsData
        mainHandler.post { callback.invoke(needsData) }
    }

    // ===== 목소리 선택 =====

    /**
     * 현재 엔진이 가진 한국어 목소리 중 실제로 쓸 수 있는 것만 골라 번호를 매긴다.
     * - 아직 내려받지 않은 목소리(notInstalled)는 뺀다.
     * - 구글 TTS가 "ko-KR-language"처럼 내놓는 '언어 기본' 항목은 실제 목소리가 아니라 기본 목소리와 소리가 같으므로 뺀다.
     * - 인터넷이 필요한 목소리는 뺀다(입금 알림은 오프라인에서도 울려야 함). 단, 기기 안 목소리가 하나도 없으면 그대로 보여준다.
     * - 이름순으로 정렬해 실행할 때마다 번호가 바뀌지 않게 하고, 앱이 원래 기본으로 쓰던 목소리를 1번에 둔다.
     * - 최대 MAX_VOICE_OPTIONS개까지만 보여준다. 엔진(삼성·구글)과 설치된 음성 데이터에 따라 개수가 다르다.
     */
    private fun refreshVoiceOptions() {
        val allVoices: Set<Voice> = try {
            tts?.voices ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query voices", e)
            emptySet()
        }
        val korean = allVoices.filter { voice ->
            try {
                voice.locale?.language == "ko" &&
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            } catch (e: Exception) {
                false
            }
        }
        val realVoices = korean.filter { !it.name.endsWith("-language", ignoreCase = true) }
        val candidates = if (realVoices.isNotEmpty()) realVoices else korean
        val onDevice = candidates.filter { !it.isNetworkConnectionRequired }
        val usable = (if (onDevice.isNotEmpty()) onDevice else candidates).sortedBy { it.name }
        val defaultVoice = pickDefaultVoice(usable)
        val ordered = if (defaultVoice != null) {
            listOf(defaultVoice) + usable.filter { it.name != defaultVoice.name }
        } else {
            usable
        }
        voiceOptions = ordered.take(MAX_VOICE_OPTIONS)
        Log.d(TAG, "Korean voices on ${currentEngineName()}: " +
            korean.map { "${it.name}(network=${it.isNetworkConnectionRequired})" } +
            " -> options=${voiceOptions.map { it.name }}")
    }

    /** 예전 버전부터 쓰던 기본 목소리 규칙: 이름에 Google → 여성 표시 → 첫 번째 */
    private fun pickDefaultVoice(voices: List<Voice>): Voice? =
        voices.find { it.name.contains("Google", ignoreCase = true) }
            ?: voices.find {
                it.name.contains("female", ignoreCase = true) ||
                    it.name.contains("여", ignoreCase = true)
            }
            ?: voices.firstOrNull()

    private fun selectedVoiceName(): String? = prefs.getString(PREF_VOICE_NAME, null)

    /**
     * voiceName 목소리를 엔진에 적용한다. 이름이 없거나 이 엔진에 없는 이름이면 1번(기본) 목소리를 쓴다.
     * 재생 직전마다 호출되므로, 화면에서 바꾼 선택이 다음 알림부터 바로 반영된다.
     */
    private fun applyVoice(voiceName: String?) {
        val options = voiceOptions
        if (options.isEmpty()) return
        val target = options.firstOrNull { it.name == voiceName } ?: options.first()
        if (appliedVoiceName == target.name) return
        val currentTts = tts ?: return
        try {
            val result = currentTts.setVoice(target)
            if (result == TextToSpeech.SUCCESS) {
                appliedVoiceName = target.name
                Log.d(TAG, "Selected voice: ${target.name}")
            } else {
                Log.w(TAG, "setVoice(${target.name}) failed: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setVoice(${target.name}) threw", e)
        }
    }

    private fun notifyVoicesChanged() {
        val callback = onVoicesChanged ?: return
        mainHandler.post { callback.invoke() }
    }

    /** 현재 엔진에서 고를 수 있는 한국어 목소리 목록 (초기화 전이거나 한국어 목소리가 없으면 빈 목록) */
    fun getVoiceOptions(): List<VoiceOption> =
        voiceOptions.mapIndexed { index, voice -> VoiceOption(index, voice.name) }

    /** 저장된 목소리의 번호(0부터). 저장값이 없거나 이 엔진에 없는 이름이면 0(기본 목소리) */
    fun getSelectedVoiceIndex(): Int {
        val saved = selectedVoiceName() ?: return 0
        return voiceOptions.indexOfFirst { it.name == saved }.coerceAtLeast(0)
    }

    /**
     * index 번 목소리를 선택해 저장하고, 그 목소리로 샘플("띵동. 1만원 확인")을 바로 들려준다.
     * 아직 재생을 시작하지 않은 이전 샘플은 버려서, 버튼을 연달아 눌러도 마지막 선택만 들리게 한다.
     */
    fun selectVoice(index: Int) {
        val voice = voiceOptions.getOrNull(index) ?: return
        prefs.edit().putString(PREF_VOICE_NAME, voice.name).apply()
        speechQueue.removeIf { it.isPreview }
        pendingQueue.removeIf { it.isPreview }
        val message = "$PREVIEW_MESSAGE. ${formatAmount(PREVIEW_AMOUNT)} 확인"
        enqueue(SpeechItem(message, currentSpeechRate(), currentVolumePercent(), voiceName = voice.name, isPreview = true))
    }

    private fun currentSpeechRate(): Float = prefs.getFloat("speech_rate", 1.0f)

    private fun currentVolumePercent(): Int = prefs.getInt("volume_percent", 100)

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                val item = utteranceId?.let { synthesizing.remove(it) }
                if (item != null) {
                    // 파일 합성 완료 → 우리 프로세스에서 재생
                    Log.d(TAG, "TTS synthesized to file: $utteranceId")
                    mainHandler.post { playSynthesizedFile(item) }
                    return
                }
                Log.d(TAG, "TTS completed: $utteranceId")
                resetSpeakingState()
                processNextInQueue()
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
                val item = utteranceId?.let { synthesizing.remove(it) }
                if (item != null) {
                    // 파일 합성이 실패하면 엔진이 직접 말하는 기존 방식으로 한 번 더 시도한다
                    synthFileFor(item).delete()
                    mainHandler.post { speakDirectly(item) }
                    return
                }
                mainHandler.post {
                    val failed = currentItem
                    if (failed != null && failed.utteranceId == utteranceId &&
                        failed.recoveryAttempts < MAX_RECOVERY_ATTEMPTS && !isEngineConnected()
                    ) {
                        // 엔진이 직접 말하는 길까지 실패했고 연결도 끊겨 있다: 다시 연결해 한 번 더 읽는다
                        recoverEngine(failed, "utterance error while disconnected")
                    } else {
                        resetSpeakingState()
                        processNextInQueue()
                    }
                }
            }
        })
    }

    fun speakSimple(message: String, amount: String, suffix: String? = null) {
        val baseMessage = "$message. ${formatAmount(amount)} 확인"
        val fullMessage = if (suffix != null) "$baseMessage. $suffix" else baseMessage
        enqueue(SpeechItem(fullMessage, currentSpeechRate(), currentVolumePercent(), voiceName = selectedVoiceName()))
    }

    private fun enqueue(item: SpeechItem) {
        if (!isInitialized) {
            pendingQueue.offer(item)
            ensureReady()
            return
        }
        speechQueue.offer(item)
        if (!isSpeaking) processNextInQueue()
    }

    /**
     * 엔진이 쓸 수 있는 상태인지 확인하고, 아니면 다시 연결한다.
     * - 초기화가 안 됐거나 실패했으면 새로 초기화한다 (이미 초기화가 진행 중이면 기다린다).
     * - 초기화는 됐지만 연결이 끊겨 있으면(엔진 앱 업데이트·강제 종료 뒤) 엔진을 새로 만든다.
     *   예전에는 이 상태를 알아채지 못해, 알림은 와도 휴대폰을 다시 켤 때까지 소리가 나지 않았다.
     */
    fun ensureReady() {
        mainHandler.post {
            if (!isInitialized) {
                val pendingFor = System.currentTimeMillis() - initStartedAt
                if (initStartedAt > 0 && pendingFor < INIT_PENDING_TIMEOUT_MS) {
                    Log.d(TAG, "ensureReady: init already in progress (${pendingFor}ms)")
                    return@post
                }
                reinitEngine("not initialized")
            } else if (!isEngineConnected()) {
                reinitEngine("engine connection lost")
            }
        }
    }

    private fun processPendingQueue() {
        while (pendingQueue.isNotEmpty()) {
            val item = pendingQueue.poll() ?: break
            speechQueue.offer(item)
        }
        if (!isSpeaking) processNextInQueue()
    }

    private fun processNextInQueue() {
        if (!isInitialized || speechQueue.isEmpty() || isSpeaking) return

        val item = speechQueue.poll() ?: return
        isSpeaking = true
        currentItem = item
        speakingStartTime = System.currentTimeMillis()
        mainHandler.postDelayed(speakingWatchdog, WATCHDOG_TIMEOUT_MS)
        speakNow(item)
    }

    /**
     * Android 17의 백그라운드 오디오 강화 규칙 때문에, 소리를 실제로 내는 TTS 엔진 프로세스(구글·삼성 TTS)는
     * 백그라운드로 취급되어 재생이 조용히 음소거된다. 그래서 엔진에게는 음성을 파일로만 만들게 하고
     * (synthesizeToFile), 재생은 포그라운드 서비스와 팝업 화면을 가진 우리 앱이 직접 한다.
     * 파일 합성을 지원하지 않거나 실패하면 엔진이 직접 말하는 기존 방식으로 되돌아간다.
     */
    private fun speakNow(item: SpeechItem) {
        val currentTts = tts
        if (currentTts == null) {
            Log.e(TAG, "TTS engine is null, skipping")
            resetSpeakingState()
            processNextInQueue()
            return
        }

        // 엔진 앱(구글·삼성 TTS)이 업데이트되거나 강제 종료되면 연결이 끊긴 채 남고, 그 뒤의 synthesizeToFile/speak 는
        // 소리 없이 ERROR 만 돌려준다. 읽기 전에 연결을 확인하고, 끊겨 있으면 엔진을 새로 만든 뒤 이 항목부터 이어서 읽는다.
        if (!isEngineConnected(currentTts)) {
            recoverEngine(item, "engine disconnected before synthesis")
            return
        }

        val randomPitch = 0.8f + (Random.nextFloat() * 0.2f)
        currentTts.setSpeechRate(item.speechRate)
        currentTts.setPitch(randomPitch)
        applyVoice(item.voiceName)

        val file = synthFileFor(item)
        file.delete()
        synthesizing[item.utteranceId] = item
        val result = try {
            currentTts.synthesizeToFile(item.message, Bundle(), file, item.utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "synthesizeToFile threw", e)
            TextToSpeech.ERROR
        }
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "synthesizeToFile failed (result=$result), falling back to direct speak")
            synthesizing.remove(item.utteranceId)
            speakDirectly(item)
        }
    }

    /** 합성된 파일을 우리 프로세스의 MediaPlayer로 재생한다 (메인 스레드에서 호출) */
    private fun playSynthesizedFile(item: SpeechItem) {
        val file = synthFileFor(item)
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Synthesized file missing, falling back to direct speak")
            speakDirectly(item)
            return
        }

        val btConnected = bluetoothAudioManager.isBluetoothAudioConnected()
        setMaxVolume(item.volumePercent, btConnected)
        requestAudioFocus(btConnected)

        val playbackVolume = if (btConnected) {
            (item.volumePercent / 100f).coerceIn(0.05f, 1.0f)
        } else {
            1.0f
        }

        stopPlayback()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (btConnected) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setVolume(playbackVolume, playbackVolume)
            player.setOnCompletionListener { finishPlayback() }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                finishPlayback()
                true
            }
            player.prepare()
            mediaPlayer = player
            playingFile = file
            player.start()
            vibrateWithIntensity(item.volumePercent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play synthesized file, falling back to direct speak", e)
            try { player.release() } catch (ignored: Exception) {}
            mediaPlayer = null
            playingFile = null
            file.delete()
            speakDirectly(item)
        }
    }

    /** 재생이 끝났거나 실패했을 때: 플레이어 정리 → 상태 복구 → 다음 항목 */
    private fun finishPlayback() {
        stopPlayback()
        resetSpeakingState()
        processNextInQueue()
    }

    private fun stopPlayback() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (ignored: Exception) {}
            try { player.release() } catch (ignored: Exception) {}
        }
        mediaPlayer = null
        playingFile?.delete()
        playingFile = null
    }

    /** 기존 방식: TTS 엔진이 직접 소리를 낸다 (파일 합성이 안 될 때만 사용) */
    private fun speakDirectly(item: SpeechItem) {
        val currentTts = tts
        if (currentTts == null) {
            resetSpeakingState()
            processNextInQueue()
            return
        }

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
            if (item.recoveryAttempts < MAX_RECOVERY_ATTEMPTS && !isEngineConnected(currentTts)) {
                recoverEngine(item, "speak returned ERROR while disconnected")
                return
            }
            resetSpeakingState()
            processNextInQueue()
            return
        }

        vibrateWithIntensity(item.volumePercent)
    }

    private fun synthFileFor(item: SpeechItem): File =
        File(context.cacheDir, "$SYNTH_FILE_PREFIX${item.utteranceId}.wav")

    /** 비정상 종료 등으로 남은 합성 파일 정리 */
    private fun cleanupSynthFiles() {
        try {
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith(SYNTH_FILE_PREFIX) && it.name.endsWith(".wav") }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up synth files", e)
        }
    }

    private fun applyAudioAttributes(currentTts: TextToSpeech, btConnected: Boolean) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(
                    if (btConnected) AudioAttributes.USAGE_MEDIA
                    else AudioAttributes.USAGE_ALARM
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            currentTts.setAudioAttributes(attrs)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying audio attributes", e)
        }
    }

    private fun resetSpeakingState() {
        mainHandler.removeCallbacks(speakingWatchdog)
        restoreVolume()
        releaseAudioFocus()
        isSpeaking = false
        currentItem = null
        speakingStartTime = 0
    }

    // ===== 엔진 연결 복구 =====

    /**
     * 엔진 프로세스와의 연결이 살아 있는지 확인한다.
     * 엔진 앱이 업데이트되거나 강제 종료되면 프레임워크의 TextToSpeech 는 연결을 다시 맺지 않고(onBindingDied 를
     * 처리하지 않음), 이후 모든 호출이 오류값만 돌려준다. isLanguageAvailable 은 연결이 끊겼을 때
     * LANG_NOT_SUPPORTED(-2)를 돌려주므로 가벼운 연결 확인용으로 쓴다 (초기화 때 한국어 지원은 이미 확인했다).
     */
    private fun isEngineConnected(currentTts: TextToSpeech? = tts): Boolean {
        val engine = currentTts ?: return false
        return try {
            engine.isLanguageAvailable(Locale.KOREAN) >= 0
        } catch (e: Exception) {
            Log.e(TAG, "isLanguageAvailable threw", e)
            false
        }
    }

    /**
     * 읽는 도중 엔진이 끊기거나 멈춘 것을 발견했을 때: 지금 항목을 (재시도 한도 안에서) 대기열 맨 앞에 되돌리고,
     * 아직 안 읽은 항목도 대기열로 옮긴 뒤 엔진을 새로 만든다. 초기화가 끝나면 onInit 이 대기열을 이어서 읽는다.
     */
    private fun recoverEngine(item: SpeechItem, reason: String) {
        mainHandler.post {
            // 이미 다른 경로(감시 타이머·오류 콜백)가 같은 항목을 복구했으면 중복 처리하지 않는다
            if (currentItem?.utteranceId != item.utteranceId) return@post
            Log.w(TAG, "Recovering TTS engine ($reason)")
            synthesizing.clear()
            stopPlayback()
            resetSpeakingState()
            if (item.recoveryAttempts < MAX_RECOVERY_ATTEMPTS) {
                synthFileFor(item).delete()
                pendingQueue.offer(
                    item.copy(
                        recoveryAttempts = item.recoveryAttempts + 1,
                        utteranceId = UUID.randomUUID().toString()
                    )
                )
            } else {
                Log.e(TAG, "Dropping item after ${item.recoveryAttempts} engine recoveries: ${item.message}")
            }
            while (true) {
                val next = speechQueue.poll() ?: break
                pendingQueue.offer(next)
            }
            reinitEngine(reason)
        }
    }

    /** 기존 엔진 객체를 버리고 새로 만든다. 초기화 결과(onInit)가 오면 대기열을 이어서 읽는다 */
    private fun reinitEngine(reason: String) {
        Log.w(TAG, "Re-creating TTS engine connection: $reason")
        initRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        initRetryRunnable = null
        isInitialized = false
        initRetryCount = 0
        triedGoogleFallback = false
        koreanUnavailable = false
        appliedVoiceName = null
        val stale = tts
        tts = null
        try {
            stale?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down stale TTS", e)
        }
        createEngine()
    }

    /**
     * 엔진 앱(구글 음성 서비스·삼성 TTS)이 업데이트되면 기존 연결은 영구히 끊긴다. 업데이트를 감지하면 잠시 뒤
     * 연결을 새로 만들어, 다음 입금 때 재연결 때문에 소리가 늦어지지 않게 한다.
     */
    private fun registerEngineUpdateReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_PACKAGE_REPLACED) return
                val pkg = intent.data?.schemeSpecificPart ?: return
                if (!isTtsEnginePackage(pkg)) return
                Log.w(TAG, "TTS engine package replaced: $pkg")
                mainHandler.postDelayed(
                    { reinitEngine("engine package replaced: $pkg") },
                    ENGINE_UPDATE_REINIT_DELAY_MS
                )
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REPLACED).apply { addDataScheme("package") }
        try {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            engineUpdateReceiver = receiver
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register engine update receiver", e)
        }
    }

    private fun unregisterEngineUpdateReceiver() {
        val receiver = engineUpdateReceiver ?: return
        engineUpdateReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // 이미 해제된 경우
        }
    }

    private fun isTtsEnginePackage(pkg: String): Boolean {
        if (pkg == GOOGLE_TTS_PACKAGE || pkg == SAMSUNG_TTS_PACKAGE) return true
        return try {
            tts?.engines?.any { it.name == pkg } == true
        } catch (e: Exception) {
            false
        }
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
                prefs.edit()
                    .putInt("temp_original_alarm_vol", originalAlarm)
                    .apply()

                val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxAlarm * percent) / 100, 0)
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
            if (originalMusic != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusic, 0)
            }
            if (originalAlarm != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarm, 0)
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
        initRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        initRetryRunnable = null
        unregisterEngineUpdateReceiver()
        synthesizing.clear()
        stopPlayback()
        releaseAudioFocus()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isSpeaking = false
        currentItem = null
        initStartedAt = 0L
        voiceOptions = emptyList()
        appliedVoiceName = null
        synchronized(Companion) {
            INSTANCE = null
        }
    }

    /**
     * 소리가 안 나는 원인이 될 수 있는 항목만 골라 (제목, 설명) 목록으로 돌려준다.
     * 재생 직전에 알람/미디어 볼륨을 직접 올리므로, 시스템 볼륨이 낮은 것 자체는 문제로 보지 않는다.
     * 거래 내역 등 민감정보는 다루지 않는다.
     */
    fun getSoundIssues(): List<Pair<String, String>> {
        val issues = mutableListOf<Pair<String, String>>()

        // 재시도를 모두 소진하고도 초기화가 안 된 경우만 실패로 본다(초기화 중 오탐 방지)
        if (!isInitialized && !koreanUnavailable && initRetryCount >= MAX_INIT_RETRIES) {
            issues.add("음성 기능이 준비되지 않았어요" to "휴대폰을 다시 켠 뒤에도 소리가 없으면 알려주세요.")
        }

        // 초기화는 됐지만 엔진 앱 업데이트 등으로 연결이 끊긴 상태: 바로 다시 연결을 시도하고 사용자에게도 알린다
        if (isInitialized && !isEngineConnected()) {
            issues.add(
                "음성 기능 연결이 끊어졌어요" to
                    "휴대폰의 음성 앱이 업데이트되면 생길 수 있어요. 지금 다시 연결하고 있으니 잠시 후 테스트 재생을 눌러 주세요."
            )
            ensureReady()
        }

        if (koreanUnavailable) {
            val detail = if (koreanNeedsData) {
                "한국어 음성 데이터가 없어요. 음성 설정에서 한국어를 설치해 주세요."
            } else {
                "한국어를 읽을 수 있는 음성이 없어요. 'Google 음성 서비스' 설치가 필요해요."
            }
            issues.add("한국어 음성을 쓸 수 없어요" to detail)
        }

        val volumePercent = try {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("volume_percent", 100)
        } catch (e: Exception) {
            100
        }
        if (volumePercent <= 10) {
            issues.add("앱 소리 크기가 ${volumePercent}%예요" to "화면의 소리 막대를 올려야 목소리가 들려요.")
        }

        val dnd = try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.currentInterruptionFilter
        } catch (e: Exception) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        when (dnd) {
            NotificationManager.INTERRUPTION_FILTER_NONE ->
                issues.add("방해금지(무음) 모드가 켜져 있어요" to "모든 소리가 막혀 있어요. 방해금지를 꺼주세요.")
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_ALARMS ->
                issues.add("방해금지 모드가 켜져 있어요" to "설정에 따라 목소리가 안 들릴 수 있어요.")
        }

        val btConnected = try {
            bluetoothAudioManager.isBluetoothAudioConnected()
        } catch (e: Exception) {
            false
        }
        if (btConnected) {
            issues.add("블루투스로 소리가 나가고 있어요" to "이어폰·스피커 연결을 끊으면 휴대폰에서 소리가 나요.")
        }

        return issues
    }
}
