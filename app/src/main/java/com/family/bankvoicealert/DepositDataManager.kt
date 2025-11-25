package com.family.bankvoicealert

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DepositDataManager private constructor(context: Context) {

    companion object {
        private const val TAG = "DepositDataManager"
        private const val PREFS_NAME = "deposit_data"
        private const val KEY_DEPOSITS = "deposits"

        @Volatile
        private var INSTANCE: DepositDataManager? = null

        fun getInstance(context: Context): DepositDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DepositDataManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

    data class DepositRecord(
        val id: String,
        val date: String,
        val time: String,
        val amount: Long,
        val sender: String,
        val timestamp: Long
    )

    /**
     * 입금 기록 추가 (테스트 알림이 아닌 실제 입금만)
     */
    fun addDeposit(amount: String, sender: String = "") {
        try {
            val now = System.currentTimeMillis()
            val cleanAmount = amount.replace(",", "").replace("원", "").trim()
            val amountLong = cleanAmount.toLongOrNull() ?: return

            val record = DepositRecord(
                id = UUID.randomUUID().toString(),
                date = dateFormat.format(Date(now)),
                time = timeFormat.format(Date(now)),
                amount = amountLong,
                sender = sender.ifEmpty { "알수없음" },
                timestamp = now
            )

            val deposits = getAllDepositsJson()
            val newDeposit = JSONObject().apply {
                put("id", record.id)
                put("date", record.date)
                put("time", record.time)
                put("amount", record.amount)
                put("sender", record.sender)
                put("timestamp", record.timestamp)
            }
            deposits.put(newDeposit)

            prefs.edit().putString(KEY_DEPOSITS, deposits.toString()).apply()
            Log.d(TAG, "Deposit added: ${record.amount}원 from ${record.sender}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding deposit", e)
        }
    }

    /**
     * 특정 날짜의 입금 기록 조회
     */
    fun getDepositsForDate(date: String): List<DepositRecord> {
        val deposits = mutableListOf<DepositRecord>()
        try {
            val allDeposits = getAllDepositsJson()
            for (i in 0 until allDeposits.length()) {
                val obj = allDeposits.getJSONObject(i)
                if (obj.getString("date") == date) {
                    deposits.add(
                        DepositRecord(
                            id = obj.getString("id"),
                            date = obj.getString("date"),
                            time = obj.getString("time"),
                            amount = obj.getLong("amount"),
                            sender = obj.optString("sender", "알수없음"),
                            timestamp = obj.getLong("timestamp")
                        )
                    )
                }
            }
            // 시간순 정렬 (최신이 위로)
            deposits.sortByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting deposits for date", e)
        }
        return deposits
    }

    /**
     * 특정 날짜의 매출 합계
     */
    fun getDailySummary(date: String): Long {
        return getDepositsForDate(date).sumOf { it.amount }
    }

    /**
     * 특정 월의 일별 매출 맵 (달력 표시용)
     */
    fun getMonthlySummary(year: Int, month: Int): Map<Int, Long> {
        val summary = mutableMapOf<Int, Long>()
        try {
            val allDeposits = getAllDepositsJson()
            val targetPrefix = String.format("%04d-%02d", year, month)

            for (i in 0 until allDeposits.length()) {
                val obj = allDeposits.getJSONObject(i)
                val date = obj.getString("date")
                if (date.startsWith(targetPrefix)) {
                    val day = date.substring(8, 10).toInt()
                    val amount = obj.getLong("amount")
                    summary[day] = (summary[day] ?: 0L) + amount
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly summary", e)
        }
        return summary
    }

    /**
     * 특정 입금 기록 삭제
     */
    fun deleteDeposit(id: String): Boolean {
        try {
            val allDeposits = getAllDepositsJson()
            val newDeposits = JSONArray()
            var deleted = false

            for (i in 0 until allDeposits.length()) {
                val obj = allDeposits.getJSONObject(i)
                if (obj.getString("id") != id) {
                    newDeposits.put(obj)
                } else {
                    deleted = true
                    Log.d(TAG, "Deposit deleted: $id")
                }
            }

            if (deleted) {
                prefs.edit().putString(KEY_DEPOSITS, newDeposits.toString()).apply()
            }
            return deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting deposit", e)
            return false
        }
    }

    /**
     * 모든 입금 기록 가져오기 (JSON)
     */
    private fun getAllDepositsJson(): JSONArray {
        return try {
            val json = prefs.getString(KEY_DEPOSITS, "[]") ?: "[]"
            JSONArray(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing deposits JSON", e)
            JSONArray()
        }
    }

    /**
     * 금액 포맷팅 (1,000원 형식)
     */
    fun formatAmount(amount: Long): String {
        return String.format("%,d원", amount)
    }
}
