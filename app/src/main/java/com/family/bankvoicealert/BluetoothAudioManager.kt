package com.family.bankvoicealert

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

class BluetoothAudioManager private constructor(context: Context) {

    companion object {
        private const val TAG = "BluetoothAudioManager"

        @Volatile
        private var INSTANCE: BluetoothAudioManager? = null

        fun getInstance(context: Context): BluetoothAudioManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothAudioManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isBluetoothAudioConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { isBluetoothOutputDevice(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth audio state", e)
            false
        }
    }

    private fun isBluetoothOutputDevice(info: AudioDeviceInfo): Boolean {
        return when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                info.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            )
        }
    }
}
