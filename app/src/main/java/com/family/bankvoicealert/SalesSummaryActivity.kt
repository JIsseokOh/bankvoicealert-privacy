package com.family.bankvoicealert

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SalesSummaryActivity : AppCompatActivity() {

    private lateinit var depositDataManager: DepositDataManager
    private lateinit var calendarGrid: GridLayout
    private lateinit var monthYearText: TextView
    private lateinit var monthlyTotalText: TextView
    private lateinit var selectedDateText: TextView
    private lateinit var dailyTotalText: TextView
    private lateinit var depositListContainer: LinearLayout
    private lateinit var noDataText: TextView

    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var selectedDate: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    override fun attachBaseContext(newBase: android.content.Context) {
        // 시스템 폰트 스케일을 1.0으로 고정하여 텍스트 크기 변경 방지
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = 1.0f
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_summary)

        depositDataManager = DepositDataManager(this)

        // 뷰 초기화
        initViews()

        // 현재 날짜로 초기화
        val calendar = Calendar.getInstance()
        currentYear = calendar.get(Calendar.YEAR)
        currentMonth = calendar.get(Calendar.MONTH) + 1

        // 달력 표시
        updateCalendar()

        // 오늘 날짜 선택
        val today = dateFormat.format(Date())
        selectDate(today)
    }

    private fun initViews() {
        calendarGrid = findViewById(R.id.calendarGrid)
        monthYearText = findViewById(R.id.monthYearText)
        monthlyTotalText = findViewById(R.id.monthlyTotalText)
        selectedDateText = findViewById(R.id.selectedDateText)
        dailyTotalText = findViewById(R.id.dailyTotalText)
        depositListContainer = findViewById(R.id.depositListContainer)
        noDataText = findViewById(R.id.noDataText)

        // 뒤로가기 버튼
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 이전 월 버튼
        findViewById<Button>(R.id.prevMonthButton).setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentMonth = 12
                currentYear--
            }
            updateCalendar()
        }

        // 다음 월 버튼
        findViewById<Button>(R.id.nextMonthButton).setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentMonth = 1
                currentYear++
            }
            updateCalendar()
        }
    }

    private fun updateCalendar() {
        // 월/년 텍스트 업데이트
        monthYearText.text = "${currentYear}년 ${currentMonth}월"

        // 월별 총 매출 업데이트
        val monthlyTotal = depositDataManager.getTotalSalesForMonth(currentYear, currentMonth)
        monthlyTotalText.text = "월 매출 합계: ${numberFormat.format(monthlyTotal)}원"

        // 달력 그리드 초기화
        calendarGrid.removeAllViews()

        val calendar = Calendar.getInstance()
        calendar.set(currentYear, currentMonth - 1, 1)

        // 첫 날의 요일 (0=일요일, 1=월요일, ...)
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1

        // 이번 달의 마지막 날
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 입금이 있는 날짜들
        val datesWithDeposits = depositDataManager.getDatesWithDeposits(currentYear, currentMonth)

        // 빈 칸 추가 (첫 주)
        for (i in 0 until firstDayOfWeek) {
            val emptyView = View(this)
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = 100
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            emptyView.layoutParams = params
            calendarGrid.addView(emptyView)
        }

        // 날짜 버튼 추가
        for (day in 1..maxDay) {
            val button = Button(this)
            button.text = day.toString()
            button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))

            // 입금이 있는 날짜는 다른 색상으로 표시
            if (datesWithDeposits.contains(day)) {
                button.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                button.setTypeface(null, android.graphics.Typeface.BOLD)
            }

            // 요일에 따른 색상 설정
            val dayOfWeek = (firstDayOfWeek + day - 1) % 7
            when (dayOfWeek) {
                0 -> button.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light)) // 일요일
                6 -> button.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light)) // 토요일
            }

            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = 100
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(4, 4, 4, 4)
            button.layoutParams = params
            button.gravity = Gravity.CENTER

            // 클릭 리스너
            val dateString = String.format("%04d-%02d-%02d", currentYear, currentMonth, day)
            button.setOnClickListener {
                selectDate(dateString)
            }

            calendarGrid.addView(button)
        }
    }

    private fun selectDate(dateString: String) {
        selectedDate = dateString

        // 선택한 날짜 표시
        try {
            val date = dateFormat.parse(dateString)
            selectedDateText.text = displayDateFormat.format(date)
        } catch (e: Exception) {
            selectedDateText.text = dateString
        }

        // 당일 매출 계산
        val dailyTotal = depositDataManager.getTotalSalesForDate(dateString)
        dailyTotalText.text = "당일 매출: ${numberFormat.format(dailyTotal)}원"

        // 입금 목록 표시
        showDeposits(dateString)
    }

    private fun showDeposits(dateString: String) {
        depositListContainer.removeAllViews()

        val deposits = depositDataManager.getDepositsForDate(dateString)

        if (deposits.isEmpty()) {
            noDataText.visibility = View.VISIBLE
            noDataText.text = "입금 내역이 없습니다"
            depositListContainer.addView(noDataText)
        } else {
            noDataText.visibility = View.GONE

            deposits.forEach { deposit ->
                val itemView = LinearLayout(this)
                itemView.orientation = LinearLayout.HORIZONTAL
                itemView.setPadding(16, 12, 16, 12)

                // 입금자명
                val nameText = TextView(this)
                nameText.text = deposit.depositorName
                nameText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                nameText.textSize = 16f
                nameText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                // 금액
                val amountText = TextView(this)
                val amount = try {
                    deposit.amount.replace(",", "").replace("원", "").trim().toLong()
                    numberFormat.format(amount) + "원"
                } catch (e: Exception) {
                    deposit.amount
                }
                amountText.text = amount
                amountText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                amountText.textSize = 16f
                amountText.setTypeface(null, android.graphics.Typeface.BOLD)

                // 시간
                val timeText = TextView(this)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timeText.text = timeFormat.format(Date(deposit.timestamp))
                timeText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                timeText.textSize = 14f
                timeText.setPadding(16, 0, 0, 0)

                itemView.addView(nameText)
                itemView.addView(amountText)
                itemView.addView(timeText)

                // 구분선
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                )
                divider.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))

                depositListContainer.addView(itemView)
                depositListContainer.addView(divider)
            }
        }
    }
}