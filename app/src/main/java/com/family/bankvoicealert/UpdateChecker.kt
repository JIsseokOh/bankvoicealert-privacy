package com.family.bankvoicealert

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UpdateChecker"
        // GitHub Pages에 호스팅될 버전 정보 JSON 파일 URL
        private const val VERSION_CHECK_URL = "https://jisseokoh.github.io/bankvoicealert-privacy/version.json"
        private const val GOOGLE_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.family.bankvoicealert"

        // 업데이트 필요 여부 확인용 키
        const val PREF_NEEDS_UPDATE = "needs_update"
        const val PREF_UPDATE_TTS_MESSAGE = "update_tts_message"
    }

    data class VersionInfo(
        val latestVersionCode: Int,
        val latestVersionName: String,
        val minimumVersionCode: Int,  // 이 버전 이하는 강제 업데이트
        val updateMessage: String,
        val forceUpdate: Boolean,
        val playStoreUrl: String = GOOGLE_PLAY_STORE_URL
    )

    fun checkForUpdate(onUpdateNeeded: (VersionInfo) -> Unit, onError: (Exception) -> Unit) {
        thread {
            try {
                val versionInfo = fetchVersionInfo()
                val currentVersionCode = getCurrentVersionCode()
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

                Log.d(TAG, "Current version: $currentVersionCode, Latest: ${versionInfo.latestVersionCode}, Minimum: ${versionInfo.minimumVersionCode}")

                // 현재 버전이 최소 요구 버전보다 낮으면 강제 업데이트
                if (currentVersionCode < versionInfo.minimumVersionCode) {
                    // 업데이트 필요 상태 저장 (TTS 알림용)
                    prefs.edit()
                        .putBoolean(PREF_NEEDS_UPDATE, true)
                        .putString(PREF_UPDATE_TTS_MESSAGE, "띵동을 업데이트해주세요!")
                        .apply()

                    val forceUpdateInfo = versionInfo.copy(forceUpdate = true)
                    Handler(Looper.getMainLooper()).post {
                        onUpdateNeeded(forceUpdateInfo)
                    }
                } else if (currentVersionCode < versionInfo.latestVersionCode) {
                    // 선택적 업데이트 - TTS 알림 없이 다이얼로그만 표시
                    prefs.edit()
                        .putBoolean(PREF_NEEDS_UPDATE, false)
                        .remove(PREF_UPDATE_TTS_MESSAGE)
                        .apply()

                    // 선택적 업데이트
                    val optionalUpdateInfo = versionInfo.copy(forceUpdate = false)
                    Handler(Looper.getMainLooper()).post {
                        onUpdateNeeded(optionalUpdateInfo)
                    }
                } else {
                    // 최신 버전이면 업데이트 불필요 상태로 변경
                    prefs.edit()
                        .putBoolean(PREF_NEEDS_UPDATE, false)
                        .remove(PREF_UPDATE_TTS_MESSAGE)
                        .apply()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking for update", e)
                Handler(Looper.getMainLooper()).post {
                    onError(e)
                }
            }
        }
    }

    private fun fetchVersionInfo(): VersionInfo {
        // 캐시 버스팅을 위해 타임스탬프를 쿼리 파라미터로 추가
        val urlWithCacheBuster = "$VERSION_CHECK_URL?t=${System.currentTimeMillis()}"
        val url = URL(urlWithCacheBuster)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        // 캐시 방지 헤더 추가
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
        connection.setRequestProperty("Pragma", "no-cache")
        connection.useCaches = false

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                // JSON 파싱
                val json = JSONObject(response.toString())
                return VersionInfo(
                    latestVersionCode = json.getInt("latestVersionCode"),
                    latestVersionName = json.getString("latestVersionName"),
                    minimumVersionCode = json.getInt("minimumVersionCode"),
                    updateMessage = json.optString("updateMessage", "새로운 버전이 출시되었습니다."),
                    forceUpdate = false,  // 이 값은 버전 비교 후 결정
                    playStoreUrl = json.optString("playStoreUrl", GOOGLE_PLAY_STORE_URL)
                )
            } else {
                throw Exception("HTTP error code: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting version code", e)
            0
        }
    }

    fun showUpdateDialog(activity: Activity, versionInfo: VersionInfo) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("업데이트 알림")
            .setMessage(versionInfo.updateMessage)
            .setCancelable(!versionInfo.forceUpdate)  // 강제 업데이트면 취소 불가
            .setPositiveButton("업데이트") { _, _ ->
                // Play Store로 이동
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(versionInfo.playStoreUrl)
                    setPackage("com.android.vending")  // Play Store 앱으로 직접 열기
                }

                try {
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    // Play Store 앱이 없으면 브라우저로 열기
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(versionInfo.playStoreUrl))
                    activity.startActivity(browserIntent)
                }

                // 강제 업데이트면 앱 종료
                if (versionInfo.forceUpdate) {
                    activity.finishAffinity()
                }
            }

        // 강제 업데이트가 아니면 "나중에" 버튼 추가
        if (!versionInfo.forceUpdate) {
            dialog.setNegativeButton("나중에") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
        }

        val alertDialog = dialog.create()

        // 강제 업데이트면 뒤로가기 버튼도 비활성화
        if (versionInfo.forceUpdate) {
            alertDialog.setOnCancelListener {
                // 취소 시에도 앱 종료
                activity.finishAffinity()
            }
        }

        alertDialog.show()
    }
}