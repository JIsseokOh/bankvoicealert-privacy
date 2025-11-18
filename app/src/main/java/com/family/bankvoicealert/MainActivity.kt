package com.family.bankvoicealert

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager
    private lateinit var ttsManager: TTSManager
    private lateinit var adManager: AdManager
    private lateinit var updateChecker: UpdateChecker
    
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeText: TextView
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedText: TextView
    private lateinit var statusText: TextView
    private lateinit var serviceSwitch: Switch
    private lateinit var testButton: Button
    private lateinit var helpButton: Button
    private lateinit var supportButton: Button
    private lateinit var privacyButton: Button
    private lateinit var permissionButton: Button
    private lateinit var backgroundSwitch: Switch
    private lateinit var backgroundText: TextView
    private lateinit var batteryOptimizationButton: Button
    // Removed debugLogText

    override fun attachBaseContext(newBase: Context) {
        // 시스템 폰트 스케일을 1.0으로 고정하여 텍스트 크기 변경 방지
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = 1.0f  // 시스템 폰트 스케일 무시
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        // 기존 사용자 마이그레이션: is_first_run -> dont_show_guide
        migratePreferences()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ttsManager = TTSManager.getInstance(this)
        adManager = AdManager(this)
        updateChecker = UpdateChecker(this)

        initViews()
        loadSettings()
        checkPermissions()
        setupListeners()

        // 앱 시작 시 버전 체크
        checkForAppUpdate()

        // 배너 광고 로드
        val adContainer = findViewById<LinearLayout>(R.id.adContainer)
        adManager.loadBannerAd(adContainer)
        requestSmsPermission()
        requestNotificationPermission()

        // 뒤로가기 버튼 처리
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })

        // 첫 실행 체크 및 가이드 표시
        checkFirstRun()

        // 배터리 최적화가 제외되지 않았으면 자동으로 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
            requestBatteryOptimizationExemption()
        }
    }
    
    private fun initViews() {
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeText = findViewById(R.id.volumeText)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        speedText = findViewById(R.id.speedText)
        statusText = findViewById(R.id.statusText)
        serviceSwitch = findViewById(R.id.serviceSwitch)
        testButton = findViewById(R.id.testButton)
        helpButton = findViewById(R.id.helpButton)
        supportButton = findViewById(R.id.supportButton)
        privacyButton = findViewById(R.id.privacyButton)
        permissionButton = findViewById(R.id.permissionButton)
        backgroundSwitch = findViewById(R.id.backgroundSwitch)
        backgroundText = findViewById(R.id.backgroundText)
        batteryOptimizationButton = findViewById(R.id.batteryOptimizationButton)
        // Remove debug log text view
        // debugLogText = findViewById(R.id.debugLogText)
    }
    
    private fun loadSettings() {
        val volume = prefs.getInt("volume_percent", 100)
        val speed = prefs.getFloat("speech_rate", 1.0f)
        
        volumeSeekBar.progress = volume
        volumeText.text = "$volume%"
        
        speedSeekBar.progress = ((speed - 0.5f) * 20).toInt()
        speedText.text = "${String.format("%.1f", speed)}x"
        
        serviceSwitch.isChecked = isNotificationServiceEnabled()
        
        // 백그라운드 실행 상태 로드
        val backgroundEnabled = prefs.getBoolean("background_enabled", false)
        backgroundSwitch.isChecked = backgroundEnabled
        updateBackgroundStatus(backgroundEnabled)

        // 배터리 최적화 상태에 따라 버튼 텍스트 업데이트
        updateBatteryOptimizationButton()
    }
    
    private fun setupListeners() {
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeText.text = "$progress%"
                prefs.edit().putInt("volume_percent", progress).apply()
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress / 20f)
                speedText.text = "${String.format("%.1f", speed)}x"
                prefs.edit().putFloat("speech_rate", speed).apply()
                ttsManager.setSpeed(speed)
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isNotificationServiceEnabled()) {
                    requestNotificationAccess()
                    serviceSwitch.isChecked = false
                } else {
                    // 서비스가 활성화되어 있으면 상태 업데이트
                    updateStatus(true)
                }
            } else {
                // 팝업 없이 바로 비활성화
                updateStatus(false)
            }
        }
        
        testButton.setOnClickListener {
            testVoiceAlert()
        }

        helpButton.setOnClickListener {
            // 사용방법 다시 보기 - 처음 실행 안내창을 다시 표시
            showFirstRunGuide()
        }

        supportButton.setOnClickListener {
            // 카카오톡 오픈채팅으로 연결
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.kakao.com/o/sxro90Th"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "카카오톡 오픈채팅을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        privacyButton.setOnClickListener {
            // 개인정보처리방침 페이지로 연결
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jisseokoh.github.io/bankvoicealert-privacy/"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "웹 브라우저를 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        permissionButton.setOnClickListener {
            openNotificationSettings()
        }
        
        // 백그라운드 실행 스위치
        backgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startBackgroundService()
            } else {
                stopBackgroundService()
            }
        }

        // 배터리 최적화 버튼
        batteryOptimizationButton.setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }
    
    private fun checkPermissions() {
        val isEnabled = isNotificationServiceEnabled()
        updateStatus(isEnabled)
        serviceSwitch.isChecked = isEnabled
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }
    
    private fun requestNotificationAccess() {
        AlertDialog.Builder(this)
            .setTitle("알림 접근 권한 필요")
            .setMessage("은행 입금 알림을 감지하려면 알림 접근 권한이 필요합니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                openNotificationSettings()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
    
    private fun showDisableServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("서비스 중지")
            .setMessage("알림 감지 서비스를 중지하시겠습니까?")
            .setPositiveButton("중지") { _, _ ->
                // 서비스는 시스템이 관리하므로 스위치 상태만 변경
                serviceSwitch.isChecked = false
                updateStatus(false)
            }
            .setNegativeButton("취소") { _, _ ->
                serviceSwitch.isChecked = true
            }
            .show()
    }
    
    private fun updateStatus(enabled: Boolean) {
        if (enabled) {
            statusText.text = "활성화"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusText.text = "비활성화"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    private fun testVoiceAlert() {
        // 고정값: 입금확인 1만원
        ttsManager.speakDeposit("테스트", "10000")
        Toast.makeText(this, "입금확인 1만원 테스트 재생", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        adManager.resumeBannerAd()
        checkPermissions()

        // 배터리 최적화 상태 업데이트
        updateBatteryOptimizationButton()
        updateBackgroundStatus(backgroundSwitch.isChecked)

        // 백그라운드 서비스 상태는 약간의 지연 후 확인
        // 이렇게 하면 앱이 완전히 포그라운드로 전환된 후에 서비스를 시작
        Handler(Looper.getMainLooper()).postDelayed({
            checkBackgroundServiceStatus()
        }, 500)
    }

    private fun checkBackgroundServiceStatus() {
        try {
            // ForegroundService 실행 상태 확인
            val isRunning = isServiceRunning(ForegroundService::class.java)
            val savedState = prefs.getBoolean("background_enabled", false)

            // 저장된 설정이 true인데 서비스가 실행 중이지 않으면 서비스 시작
            if (savedState && !isRunning) {
                // Android 12 이상에서는 백그라운드에서 포그라운드 서비스 시작 제한이 있음
                // 앱이 포그라운드에 있을 때만 서비스 시작 시도
                try {
                    // 서비스를 시작하되, 실패하면 설정을 false로 변경
                    ForegroundService.startService(this)
                    backgroundSwitch.isChecked = true
                    updateBackgroundStatus(true)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to start background service", e)
                    // 서비스 시작 실패 시 설정을 비활성화
                    prefs.edit().putBoolean("background_enabled", false).apply()
                    backgroundSwitch.isChecked = false
                    updateBackgroundStatus(false)

                    // 사용자에게 수동으로 활성화하도록 안내
                    Toast.makeText(this,
                        "백그라운드 서비스 시작 실패. 앱이 열린 상태에서 다시 활성화해 주세요.",
                        Toast.LENGTH_LONG).show()
                }
            } else if (!savedState && isRunning) {
                // 설정은 false인데 서비스가 실행 중이면 서비스 중지
                try {
                    ForegroundService.stopService(this)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to stop service", e)
                }
                backgroundSwitch.isChecked = false
                updateBackgroundStatus(false)
            } else {
                // 상태가 일치하면 UI만 업데이트
                backgroundSwitch.isChecked = savedState
                updateBackgroundStatus(savedState)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking background service status", e)
            // 오류 발생 시 안전하게 설정을 비활성화
            backgroundSwitch.isChecked = false
            updateBackgroundStatus(false)
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    // Removed loadDebugLog function
    
    private fun startBackgroundService() {
        // 백그라운드 서비스 시작 (모든 Android 버전에서 허용)
        ForegroundService.startService(this)
        prefs.edit().putBoolean("background_enabled", true).apply()
        updateBackgroundStatus(true)
        Toast.makeText(this, "백그라운드 실행 활성화", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopBackgroundService() {
        try {
            ForegroundService.stopService(this)

            // 상태 업데이트를 약간 지연
            Handler(Looper.getMainLooper()).postDelayed({
                prefs.edit().putBoolean("background_enabled", false).apply()
                updateBackgroundStatus(false)
                Toast.makeText(this, "백그라운드 실행 중지", Toast.LENGTH_SHORT).show()
            }, 100)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping service", e)
            // 오류가 발생해도 UI 상태는 업데이트
            prefs.edit().putBoolean("background_enabled", false).apply()
            updateBackgroundStatus(false)
            Toast.makeText(this, "백그라운드 실행 중지", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateBackgroundStatus(enabled: Boolean) {
        // 배터리 최적화 상태 확인
        val isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations()

        // 텍스트를 항상 동일하게 표시
        backgroundText.text = "아래 버튼 누르고 앱 나가면 끝!"
        backgroundText.setTextColor(getColor(android.R.color.holo_green_light))

        // 스위치 상태만 변경
        if (enabled != backgroundSwitch.isChecked) {
            backgroundSwitch.isChecked = enabled
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true // Android 6.0 미만에서는 배터리 최적화가 없음
    }

    private fun updateBatteryOptimizationButton() {
        if (isIgnoringBatteryOptimizations()) {
            batteryOptimizationButton.text = "✅ 무제한 모드 활성화됨"
            batteryOptimizationButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            batteryOptimizationButton.text = "⚡ 무제한 모드 설정"
            batteryOptimizationButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations()) {
                AlertDialog.Builder(this)
                    .setTitle("무제한 실행 모드")
                    .setMessage("배터리 최적화를 제외하면 24시간 제한 없이 계속 실행됩니다.")
                    .setPositiveButton("설정하기") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            // 일부 기기에서 직접 요청이 안 되는 경우 배터리 최적화 설정 페이지로 이동
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                startActivity(intent)
                            } catch (e2: Exception) {
                                Toast.makeText(this, "배터리 최적화 설정을 열 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            } else {
                // 이미 제외되어 있는 경우
                AlertDialog.Builder(this)
                    .setTitle("무제한 실행 모드 활성화됨")
                    .setMessage("이미 배터리 최적화가 제외되어 있습니다.\n24시간 제한 없이 계속 실행됩니다.")
                    .setPositiveButton("확인", null)
                    .show()
            }
        } else {
            Toast.makeText(this, "이 기기에서는 배터리 최적화 설정이 필요하지 않습니다", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) 
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
                100
            )
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            100 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "SMS 권한 허용됨", Toast.LENGTH_SHORT).show()
                }
            }
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "알림 권한 허용됨", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        adManager.pauseBannerAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        adManager.destroyBannerAd()
        // 싱글톤이므로 shutdown 호출하지 않음
        // ttsManager.shutdown()
    }


    private fun showExitDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_exit_wait, null)
        val countdownText = dialogView.findViewById<TextView>(R.id.countdownText)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val exitButton = dialogView.findViewById<Button>(R.id.exitButton)
        val nativeAdContainer = dialogView.findViewById<FrameLayout>(R.id.nativeAdContainer)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 네이티브 광고 로드
        adManager.loadNativeAd(nativeAdContainer)

        var remainingSeconds = 5
        val countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000).toInt() + 1

                // 카운트다운 텍스트 업데이트
                when (remainingSeconds) {
                    5 -> countdownText.text = "5초 후 알리미 시작"
                    4 -> countdownText.text = "4초 후 알리미 시작"
                    3 -> countdownText.text = "3초 후 알리미 시작"
                    2 -> countdownText.text = "2초 후 알리미 시작"
                    1 -> countdownText.text = "1초 후 알리미 시작"
                }

                // 메시지 업데이트
                when (remainingSeconds) {
                    5, 4, 3 -> messageText.text = "광고 수익으로 무료 서비스를 유지합니다"
                    2, 1 -> messageText.text = "대한민국 소상공인 파이팅!"
                }
            }

            override fun onFinish() {
                countdownText.text = "알리미 시작!"
                messageText.text = "엄마아빠, 할머니할아버지 오늘도 힘내세용"
                exitButton.isEnabled = true
                exitButton.setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
            }
        }

        cancelButton.setOnClickListener {
            countDownTimer.cancel()
            adManager.destroyNativeAd()
            dialog.dismiss()
        }

        exitButton.setOnClickListener {
            if (exitButton.isEnabled) {
                countDownTimer.cancel()
                adManager.destroyNativeAd()
                dialog.dismiss()
                finish()
            }
        }

        // 다이얼로그 취소 시(뒤로가기) 처리
        dialog.setOnCancelListener {
            countDownTimer.cancel()
            adManager.destroyNativeAd()
        }

        dialog.show()
        countDownTimer.start()
    }

    private fun migratePreferences() {
        // 기존 is_first_run 값이 있는지 확인
        if (prefs.contains("is_first_run")) {
            val isFirstRun = prefs.getBoolean("is_first_run", true)
            // is_first_run이 false면 사용자가 이미 가이드를 봤다는 의미
            // 하지만 이번 업데이트에서는 가이드를 다시 보여주기 위해 dont_show_guide를 false로 설정
            prefs.edit().apply {
                // 기존 설정 제거하고 새 설정으로 전환
                remove("is_first_run")
                putBoolean("dont_show_guide", false)  // 일단 모든 사용자에게 다시 표시
                apply()
            }
        }
    }

    private fun checkFirstRun() {
        // 사용자가 "다시 보지 않기"를 체크했는지 확인
        val dontShowAgain = prefs.getBoolean("dont_show_guide", false)

        // "다시 보지 않기"를 체크하지 않았으면 항상 가이드 표시
        if (!dontShowAgain) {
            showFirstRunGuide()
        }
    }

    private fun showFirstRunGuide() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_first_run_guide, null)
        val checkDontShowAgain = dialogView.findViewById<CheckBox>(R.id.checkDontShowAgain)
        val btnSetupNow = dialogView.findViewById<Button>(R.id.btnSetupNow)
        val btnLater = dialogView.findViewById<Button>(R.id.btnLater)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnSetupNow.setOnClickListener {
            // 다시 보지 않기 체크 시 저장
            if (checkDontShowAgain.isChecked) {
                prefs.edit().putBoolean("dont_show_guide", true).apply()
            } else {
                // 체크 해제 시 다음에도 표시되도록 설정
                prefs.edit().putBoolean("dont_show_guide", false).apply()
            }
            dialog.dismiss()

            // 권한 설정 화면으로 이동 (권한 설정 버튼과 동일한 동작)
            openNotificationSettings()
        }

        btnLater.setOnClickListener {
            // 다시 보지 않기 체크 시 저장
            if (checkDontShowAgain.isChecked) {
                prefs.edit().putBoolean("dont_show_guide", true).apply()
            } else {
                // 체크 해제 시 다음에도 표시되도록 설정
                prefs.edit().putBoolean("dont_show_guide", false).apply()
            }
            dialog.dismiss()
        }

        dialog.show()

        // 다이얼로그의 최대 높이를 화면 높이의 80%로 제한
        dialog.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val maxHeight = (displayMetrics.heightPixels * 0.8).toInt()

            window.attributes?.let { params ->
                params.height = maxHeight
                window.attributes = params
            }
        }
    }

    private fun checkForAppUpdate() {
        updateChecker.checkForUpdate(
            onUpdateNeeded = { versionInfo ->
                // 업데이트가 필요한 경우 다이얼로그 표시
                updateChecker.showUpdateDialog(this, versionInfo)
            },
            onError = { exception ->
                // 네트워크 오류 등의 경우 로그만 남기고 앱은 정상 실행
                Log.e("MainActivity", "Failed to check for updates", exception)
            }
        )
    }
}