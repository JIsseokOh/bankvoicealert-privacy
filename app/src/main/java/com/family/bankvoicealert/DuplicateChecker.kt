package com.family.bankvoicealert

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class DuplicateChecker private constructor() {

    companion object {
        private const val TAG = "DuplicateChecker"
        private const val DUPLICATE_WINDOW_MS = 2000L

        @Volatile
        private var INSTANCE: DuplicateChecker? = null

        fun getInstance(): DuplicateChecker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DuplicateChecker().also { INSTANCE = it }
            }
        }
    }

    private val recentDeposits = ConcurrentHashMap<String, DepositInfo>()

    data class DepositInfo(
        val amount: String,
        val sender: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun isDuplicate(amount: String, sender: String?): Boolean {
        cleanOldEntries()

        val key = generateKey(amount, sender)
        val existingInfo = recentDeposits[key]

        if (existingInfo != null) {
            val timeDiff = System.currentTimeMillis() - existingInfo.timestamp
            if (timeDiff < DUPLICATE_WINDOW_MS) {
                Log.d(TAG, "Duplicate deposit detected: $amount won, sender: $sender, time diff: ${timeDiff}ms")
                return true
            }
        }

        addDeposit(amount, sender)
        return false
    }

    private fun addDeposit(amount: String, sender: String?) {
        val key = generateKey(amount, sender)
        recentDeposits[key] = DepositInfo(amount, sender)
        Log.d(TAG, "Deposit info saved: $amount won, sender: $sender")
    }

    private fun generateKey(amount: String, sender: String?): String {
        val cleanAmount = amount.replace(",", "").trim()
        val cleanSender = sender?.trim() ?: "unknown"
        return "${cleanAmount}_${cleanSender}"
    }

    private fun cleanOldEntries() {
        val currentTime = System.currentTimeMillis()
        val iterator = recentDeposits.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value.timestamp > DUPLICATE_WINDOW_MS) {
                Log.d(TAG, "Old deposit info removed: ${entry.key}")
                iterator.remove()
            }
        }
    }

    fun clear() {
        recentDeposits.clear()
        Log.d(TAG, "All deposit info cleared")
    }
}
