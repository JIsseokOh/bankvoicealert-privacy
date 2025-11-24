package com.family.bankvoicealert

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class DepositRecord(
    val depositorName: String,
    val amount: String,
    val timestamp: Long,
    val dateString: String // YYYY-MM-DD format
)

class DepositDataManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("deposit_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 입금 기록 저장
    fun saveDeposit(depositorName: String, amount: String) {
        val currentTime = System.currentTimeMillis()
        val dateString = dateFormat.format(Date(currentTime))

        val deposit = DepositRecord(
            depositorName = depositorName,
            amount = amount,
            timestamp = currentTime,
            dateString = dateString
        )

        val deposits = getAllDeposits().toMutableList()
        deposits.add(deposit)

        // 저장
        val json = gson.toJson(deposits)
        prefs.edit().putString("deposits", json).apply()
    }

    // 모든 입금 기록 가져오기
    fun getAllDeposits(): List<DepositRecord> {
        val json = prefs.getString("deposits", "[]")
        val type = object : TypeToken<List<DepositRecord>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // 특정 날짜의 입금 기록 가져오기
    fun getDepositsForDate(dateString: String): List<DepositRecord> {
        return getAllDeposits().filter { it.dateString == dateString }
    }

    // 특정 월의 입금 기록 가져오기
    fun getDepositsForMonth(year: Int, month: Int): List<DepositRecord> {
        val monthString = String.format("%04d-%02d", year, month)
        return getAllDeposits().filter { it.dateString.startsWith(monthString) }
    }

    // 특정 날짜의 총 매출 계산
    fun getTotalSalesForDate(dateString: String): Long {
        return getDepositsForDate(dateString).sumOf {
            try {
                it.amount.replace(",", "").replace("원", "").trim().toLong()
            } catch (e: Exception) {
                0L
            }
        }
    }

    // 특정 월의 총 매출 계산
    fun getTotalSalesForMonth(year: Int, month: Int): Long {
        return getDepositsForMonth(year, month).sumOf {
            try {
                it.amount.replace(",", "").replace("원", "").trim().toLong()
            } catch (e: Exception) {
                0L
            }
        }
    }

    // 입금이 있는 날짜 목록 가져오기 (달력 표시용)
    fun getDatesWithDeposits(year: Int, month: Int): Set<Int> {
        val monthString = String.format("%04d-%02d", year, month)
        return getDepositsForMonth(year, month)
            .map { it.dateString }
            .filter { it.startsWith(monthString) }
            .map { it.substring(8, 10).toIntOrNull() ?: 0 }
            .filter { it > 0 }
            .toSet()
    }

    // 모든 데이터 삭제
    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}