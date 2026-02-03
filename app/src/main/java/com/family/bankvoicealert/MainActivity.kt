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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
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
    private lateinit var permissionButton: Button
    private lateinit var backgroundSwitch: Switch
    private lateinit var backgroundText: TextView
    private lateinit var batteryOptimizationButton: Button
    private lateinit var salesSummaryButton: Button
    private lateinit var popupToggleButton: Button
    private lateinit var depositDataManager: DepositDataManager

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
        depositDataManager = DepositDataManager.getInstance(this)

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
        // 알림 권한은 SMS 권한 결과 후 체이닝하여 요청 (동시 요청 시 무시되는 문제 방지)
        // SMS 권한이 이미 허용된 경우 바로 알림 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission()
        }

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
        permissionButton = findViewById(R.id.permissionButton)
        backgroundSwitch = findViewById(R.id.backgroundSwitch)
        backgroundText = findViewById(R.id.backgroundText)
        batteryOptimizationButton = findViewById(R.id.batteryOptimizationButton)
        salesSummaryButton = findViewById(R.id.salesSummaryButton)
        popupToggleButton = findViewById(R.id.popupToggleButton)
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

        // 팝업 알림 상태 로드
        updatePopupToggleButton(prefs.getBoolean("popup_alert_enabled", true))
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

        salesSummaryButton.setOnClickListener {
            showSalesSummaryDialog()
        }

        popupToggleButton.setOnClickListener {
            val current = prefs.getBoolean("popup_alert_enabled", true)
            val newState = !current
            prefs.edit().putBoolean("popup_alert_enabled", newState).apply()
            updatePopupToggleButton(newState)
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
            statusText.text = "1번 준비 끝!"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusText.text = "1번, 아래 버튼 누르기"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    private fun testVoiceAlert() {
        // 고정값: 입금확인 1만원
        ttsManager.speakDeposit("테스트", "10000")

        // 팝업 알림이 켜져 있을 때만 표시
        if (prefs.getBoolean("popup_alert_enabled", true)) {
            val intent = Intent(this, DepositAlertActivity::class.java).apply {
                putExtra(DepositAlertActivity.EXTRA_AMOUNT, "10,000원")
            }
            startActivity(intent)
        }
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
        // 버튼 상태에 따라 텍스트 변경 (1번 앱카드와 동일한 스타일)
        if (enabled) {
            backgroundText.text = "2번 준비 끝!"
            backgroundText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            backgroundText.text = "2번, 아래 버튼 누르기"
            backgroundText.setTextColor(getColor(android.R.color.holo_red_dark))
        }

        // 스위치 상태만 변경
        if (enabled != backgroundSwitch.isChecked) {
            backgroundSwitch.isChecked = enabled
        }
    }

    private fun updatePopupToggleButton(enabled: Boolean) {
        if (enabled) {
            popupToggleButton.text = "팝업 알림 켜짐"
            popupToggleButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
        } else {
            popupToggleButton.text = "팝업 알림 꺼짐"
            popupToggleButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
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
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    // 이전에 거부한 적 있음 - 설명 후 다시 요청
                    AlertDialog.Builder(this)
                        .setTitle("알림 권한 필요")
                        .setMessage("입금 알림을 받으려면 알림 권한이 필요합니다.\n권한을 허용해 주세요.")
                        .setPositiveButton("허용") { _, _ ->
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                101
                            )
                        }
                        .setNegativeButton("취소", null)
                        .show()
                } else {
                    // 처음 요청 또는 "다시 묻지 않기" 선택됨
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        101
                    )
                }
            }
        }
    }

    private fun showNotificationPermissionGuide() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AlertDialog.Builder(this)
                .setTitle("알림 권한이 필요합니다")
                .setMessage("입금 감지 시 알림 팝업을 표시하려면 알림 권한이 필요합니다.\n\n설정에서 알림 권한을 허용해 주세요.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("나중에", null)
                .show()
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
                // SMS 권한 처리 후 알림 권한 요청
                requestNotificationPermission()
            }
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "알림 권한 허용됨", Toast.LENGTH_SHORT).show()
                } else {
                    // 권한 거부 시 설정으로 안내
                    showNotificationPermissionGuide()
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
            prefs.edit().apply {
                // 기존 설정 제거하고 새 설정으로 전환
                remove("is_first_run")
                // 기존 사용자 설정 유지 (가이드를 다시 표시하지 않음)
                putBoolean("dont_show_guide", !isFirstRun)
                apply()
            }
        }
    }

    private fun checkFirstRun() {
        // 사용자가 "다시 보지 않기"를 체크했는지 확인
        val dontShowAgain = prefs.getBoolean("dont_show_guide", false)

        // 이미 권한이 설정되어 있으면 가이드 표시하지 않음 (업데이트 후에도 권한 유지)
        if (isNotificationServiceEnabled()) {
            // 권한이 이미 설정되어 있으면 dont_show_guide를 true로 설정
            if (!dontShowAgain) {
                prefs.edit().putBoolean("dont_show_guide", true).apply()
            }
            return
        }

        // "다시 보지 않기"를 체크하지 않았으면 가이드 표시
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

            // 매출 집계 설명창 표시
            showSalesSummaryGuide()

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

            // 매출 집계 설명창 표시
            showSalesSummaryGuide()
        }

        dialog.show()

        // 다이얼로그 배경 투명 설정 및 화면 중앙 배치
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setGravity(Gravity.CENTER)
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

    private fun showSalesSummaryGuide() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sales_summary_guide, null)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirm)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnConfirm.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // 다이얼로그 배경 투명 설정 및 화면 가운데 표시
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setGravity(Gravity.CENTER)
        }
    }

    private fun showSalesSummaryDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sales_summary, null)

        val btnPrevMonth = dialogView.findViewById<Button>(R.id.btnPrevMonth)
        val btnNextMonth = dialogView.findViewById<Button>(R.id.btnNextMonth)
        val tvCurrentMonth = dialogView.findViewById<TextView>(R.id.tvCurrentMonth)
        val calendarGrid = dialogView.findViewById<GridLayout>(R.id.calendarGrid)
        val tvSelectedDate = dialogView.findViewById<TextView>(R.id.tvSelectedDate)
        val depositListContainer = dialogView.findViewById<LinearLayout>(R.id.depositListContainer)
        val tvDailyTotal = dialogView.findViewById<TextView>(R.id.tvDailyTotal)
        val tvMonthlyTotal = dialogView.findViewById<TextView>(R.id.tvMonthlyTotal)
        val btnBack = dialogView.findViewById<Button>(R.id.btnBack)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 현재 날짜
        val calendar = java.util.Calendar.getInstance()
        var currentYear = calendar.get(java.util.Calendar.YEAR)
        var currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
        var selectedDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val todayYear = currentYear
        val todayMonth = currentMonth
        val todayDay = selectedDay

        // 람다 변수 선언 (상호 참조를 위해)
        lateinit var updateCalendar: () -> Unit
        lateinit var updateDepositList: () -> Unit

        // 입금 내역 업데이트 함수
        updateDepositList = {
            val dateStr = String.format("%04d-%02d-%02d", currentYear, currentMonth, selectedDay)
            val deposits = depositDataManager.getDepositsForDate(dateStr)
            val dailyTotal = depositDataManager.getDailySummary(dateStr)

            tvSelectedDate.text = "${currentYear}년 ${currentMonth}월 ${selectedDay}일"
            tvDailyTotal.text = depositDataManager.formatAmount(dailyTotal)

            depositListContainer.removeAllViews()

            if (deposits.isEmpty()) {
                val emptyText = TextView(this)
                emptyText.text = "입금 내역이 없습니다"
                emptyText.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                emptyText.gravity = android.view.Gravity.CENTER
                emptyText.setPadding(0, 32, 0, 32)
                depositListContainer.addView(emptyText)
            } else {
                for (deposit in deposits) {
                    val itemView = LinearLayout(this)
                    itemView.orientation = LinearLayout.HORIZONTAL
                    itemView.setPadding(8, 12, 8, 12)
                    itemView.gravity = android.view.Gravity.CENTER_VERTICAL

                    val timeText = TextView(this)
                    timeText.text = deposit.time
                    timeText.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                    timeText.textSize = 12f
                    timeText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.25f)

                    val senderText = TextView(this)
                    senderText.text = deposit.sender
                    senderText.setTextColor(resources.getColor(android.R.color.white, theme))
                    senderText.textSize = 14f
                    senderText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f)

                    val amountText = TextView(this)
                    amountText.text = depositDataManager.formatAmount(deposit.amount)
                    amountText.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
                    amountText.textSize = 14f
                    amountText.setTypeface(null, android.graphics.Typeface.BOLD)
                    amountText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)

                    val deleteBtn = Button(this)
                    deleteBtn.text = "X"
                    deleteBtn.textSize = 12f
                    deleteBtn.setTextColor(resources.getColor(android.R.color.white, theme))
                    deleteBtn.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark, theme))
                    deleteBtn.layoutParams = LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.WRAP_CONTENT)
                    deleteBtn.setPadding(8, 4, 8, 4)

                    val depositId = deposit.id
                    deleteBtn.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("삭제 확인")
                            .setMessage("이 입금 기록을 삭제하시겠습니까?\n${deposit.time} - ${depositDataManager.formatAmount(deposit.amount)}")
                            .setPositiveButton("삭제") { _, _ ->
                                depositDataManager.deleteDeposit(depositId)
                                updateDepositList()
                                updateCalendar()
                            }
                            .setNegativeButton("취소", null)
                            .show()
                    }

                    itemView.addView(timeText)
                    itemView.addView(senderText)
                    itemView.addView(amountText)
                    itemView.addView(deleteBtn)

                    depositListContainer.addView(itemView)

                    // 구분선
                    val divider = View(this)
                    divider.setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme))
                    divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    depositListContainer.addView(divider)
                }
            }
        }

        // 달력 업데이트 함수
        updateCalendar = {
            tvCurrentMonth.text = "${currentYear}년 ${currentMonth}월"
            calendarGrid.removeAllViews()

            // 해당 월의 첫 날과 마지막 날 계산
            val cal = java.util.Calendar.getInstance()
            cal.set(currentYear, currentMonth - 1, 1)
            val firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
            val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

            // 월별 매출 데이터 가져오기
            val monthlySummary = depositDataManager.getMonthlySummary(currentYear, currentMonth)

            // 월 매출 합계 계산 및 표시
            val monthlyTotal = monthlySummary.values.sum()
            tvMonthlyTotal.text = depositDataManager.formatAmount(monthlyTotal)

            // 빈 칸 추가
            for (i in 0 until firstDayOfWeek) {
                val emptyView = TextView(this)
                emptyView.layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                calendarGrid.addView(emptyView)
            }

            // 날짜 추가
            for (day in 1..maxDay) {
                val dayView = TextView(this)
                dayView.textSize = 14f
                dayView.gravity = android.view.Gravity.CENTER
                dayView.setPadding(4, 12, 4, 12)

                val dayOfWeek = (firstDayOfWeek + day - 1) % 7
                dayView.setTextColor(when (dayOfWeek) {
                    0 -> resources.getColor(android.R.color.holo_red_light, theme) // 일요일
                    6 -> resources.getColor(android.R.color.holo_blue_light, theme) // 토요일
                    else -> resources.getColor(android.R.color.white, theme)
                })

                // 매출이 있는 날 표시
                if (monthlySummary.containsKey(day)) {
                    dayView.text = "$day\n●"
                    dayView.setTextColor(resources.getColor(android.R.color.holo_orange_light, theme))
                } else {
                    dayView.text = day.toString()
                }

                // 선택된 날짜 배경색 (연한 음영)
                if (day == selectedDay) {
                    dayView.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark, theme))
                    dayView.alpha = 0.7f
                } else {
                    dayView.setBackgroundColor(0)
                    dayView.alpha = 1.0f
                }

                // 오늘 날짜 표시
                if (currentYear == todayYear && currentMonth == todayMonth && day == todayDay) {
                    dayView.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
                    dayView.setTypeface(null, android.graphics.Typeface.BOLD)
                }

                dayView.layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }

                val currentDay = day
                dayView.setOnClickListener {
                    selectedDay = currentDay
                    updateCalendar()
                    updateDepositList()
                }

                calendarGrid.addView(dayView)
            }
        }

        // 이전 달 버튼
        btnPrevMonth.setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentMonth = 12
                currentYear--
            }
            selectedDay = 1
            updateCalendar()
            updateDepositList()
        }

        // 다음 달 버튼
        btnNextMonth.setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentMonth = 1
                currentYear++
            }
            selectedDay = 1
            updateCalendar()
            updateDepositList()
        }

        // 뒤로가기 버튼
        btnBack.setOnClickListener {
            dialog.dismiss()
        }

        // 초기화
        updateCalendar()
        updateDepositList()

        dialog.show()

        // 다이얼로그 크기 설정 (상단 고정, 하단 광고 공간 확보)
        dialog.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            window.setGravity(Gravity.TOP)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout(
                displayMetrics.widthPixels,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }
}