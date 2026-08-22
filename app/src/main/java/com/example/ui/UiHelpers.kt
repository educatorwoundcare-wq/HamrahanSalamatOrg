package com.example.ui

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog

fun Double.formatPrice(currency: String = "ریال"): String {
    val formatter = DecimalFormat("#,###")
    return "${formatter.format(this)} $currency"
}

object JalaliCalendar {
    class YearMonthDay(val year: Int, val month: Int, val day: Int) {
        override fun toString(): String = String.format("%04d/%02d/%02d", year, month, day)
    }

    fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): YearMonthDay {
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        val gy2 = gYear - 1600
        val gm2 = gMonth - 1
        val gd2 = gDay - 1

        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) {
            gDayNo += gDaysInMonth[i + 1]
        }
        if (gm2 > 1 && ((gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd2

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        for (i in 0..11) {
            var days = jDaysInMonth[i + 1]
            if (i == 11 && ((jy + 1) % 33 in intArrayOf(1, 5, 9, 13, 17, 22, 26, 30))) {
                days = 30
            }
            if (jDayNo >= days) {
                jDayNo -= days
            } else {
                jm = i + 1
                break
            }
        }
        val jd = jDayNo + 1
        return YearMonthDay(jy, jm, jd)
    }

    fun toJdn(year: Int, month: Int, day: Int): Int {
        return (1461 * (year + 4800 + (month - 14) / 12)) / 4 +
               (367 * (month - 2 - 12 * ((month - 14) / 12))) / 12 -
               (3 * ((year + 4900 + (month - 14) / 12) / 100)) / 4 +
               day - 32075
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): YearMonthDay {
        val r = jy - 979
        val jDaysInMonth = intArrayOf(0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jDayNo = 365 * r + (r / 33) * 8 + ((r % 33) + 3) / 4
        for (i in 0 until jm - 1) {
            jDayNo += jDaysInMonth[i + 1]
        }
        jDayNo += jd - 1
        val gDayNo = jDayNo + 79

        var gy2 = 1600 + 400 * (gDayNo / 146097)
        var gDayNo2 = gDayNo % 146097
        var leap = true
        if (gDayNo2 >= 366) {
            gDayNo2--
            val div = gDayNo2 / 36524
            gy2 += div * 100
            gDayNo2 %= 36524
            if (gDayNo2 >= 365) {
                gDayNo2++
                leap = false
            } else {
                leap = false
            }
            val div2 = gDayNo2 / 1461
            gy2 += div2 * 4
            gDayNo2 %= 1461
            if (gDayNo2 >= 366) {
                gDayNo2--
                val div3 = gDayNo2 / 365
                gy2 += div3
                gDayNo2 %= 365
                leap = false
            } else {
                leap = true
            }
        } else {
            leap = true
        }
        var gm2 = 0
        val gDaysInMonth = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        for (i in 0..11) {
            val days = gDaysInMonth[i + 1]
            if (gDayNo2 >= days) {
                gDayNo2 -= days
            } else {
                gm2 = i + 1
                break
            }
        }
        val gd2 = gDayNo2 + 1
        return YearMonthDay(gy2, gm2, gd2)
    }
}

fun jalaliToTimestamp(jy: Int, jm: Int, jd: Int): Long {
    val gDate = JalaliCalendar.jalaliToGregorian(jy, jm, jd)
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, gDate.year)
    cal.set(Calendar.MONTH, gDate.month - 1)
    cal.set(Calendar.DAY_OF_MONTH, gDate.day)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun Long.getStartOfJalaliMonth(): Long {
    val jalali = this.toJalaliDate()
    return jalaliToTimestamp(jalali.year, jalali.month, 1)
}

fun Long.getStartOfJalaliSeason(): Long {
    val jalali = this.toJalaliDate()
    val targetMonth = ((jalali.month - 1) / 3) * 3 + 1
    return jalaliToTimestamp(jalali.year, targetMonth, 1)
}

fun Long.getStartOfJalaliYear(): Long {
    val jalali = this.toJalaliDate()
    return jalaliToTimestamp(jalali.year, 1, 1)
}

fun Long.toJalaliDate(): JalaliCalendar.YearMonthDay {
    val cal = Calendar.getInstance()
    cal.timeInMillis = this
    return JalaliCalendar.gregorianToJalali(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

fun String.toPersianDigits(): String {
    var result = this
    val farsi = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
    for (i in 0..9) {
        result = result.replace(i.toString(), farsi[i])
    }
    return result
}

val persianMonthNames = arrayOf(
    "فروردین", "اردیبهشت", "خرداد",
    "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر",
    "دی", "بهمن", "اسفند"
)

fun getPersianWeekdayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        Calendar.SATURDAY -> "شنبه"
        Calendar.SUNDAY -> "یکشنبه"
        Calendar.MONDAY -> "دوشنبه"
        Calendar.TUESDAY -> "سه‌شنبه"
        Calendar.WEDNESDAY -> "چهارشنبه"
        Calendar.THURSDAY -> "پنجشنبه"
        Calendar.FRIDAY -> "جمعه"
        else -> ""
    }
}

fun Long.formatPersianDate(): String {
    if (this <= 0) return "-"
    return this.toJalaliDate().toString().toPersianDigits()
}

fun Long.toDateString(): String = this.formatPersianDate()

fun Long.formatPersianDateTime(): String {
    if (this <= 0) return "-"
    val jalali = this.toJalaliDate()
    val cal = Calendar.getInstance()
    cal.timeInMillis = this
    val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    return "${jalali.toString()} $timeStr".toPersianDigits()
}

fun Long.formatRelativePersianDate(): String {
    if (this <= 0) return "-"
    val now = System.currentTimeMillis()
    val calNow = Calendar.getInstance()
    calNow.timeInMillis = now
    val calThen = Calendar.getInstance()
    calThen.timeInMillis = this

    val jdnNow = JalaliCalendar.toJdn(calNow.get(Calendar.YEAR), calNow.get(Calendar.MONTH) + 1, calNow.get(Calendar.DAY_OF_MONTH))
    val jdnThen = JalaliCalendar.toJdn(calThen.get(Calendar.YEAR), calThen.get(Calendar.MONTH) + 1, calThen.get(Calendar.DAY_OF_MONTH))
    val diffDays = jdnNow - jdnThen

    return when (diffDays) {
        0 -> "امروز"
        1 -> "دیروز"
        -1 -> "فردا"
        in 2..7 -> "$diffDays روز پیش".toPersianDigits()
        in -7..-2 -> "${-diffDays} روز بعد".toPersianDigits()
        else -> this.formatPersianDate()
    }
}

fun Long.formatDate(): String {
    return this.formatPersianDate()
}

fun Long.formatDateTime(): String {
    return this.formatPersianDateTime()
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    com.example.ui.components.EmptyState(
        icon = icon,
        message = message,
        modifier = modifier,
        description = description
    )
}

@Composable
fun HamrahanLogo(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(size)
    ) {
        val w = this.size.width
        val h = this.size.height

        // 1. The Shield (Outer shelter curves)
        val shieldPath = Path().apply {
            moveTo(w * 0.5f, h * 0.05f) // Top center
            cubicTo(w * 0.85f, h * 0.05f, w * 0.95f, h * 0.35f, w * 0.9f, h * 0.65f) // Right curve
            cubicTo(w * 0.85f, h * 0.85f, w * 0.65f, h * 0.95f, w * 0.5f, h * 0.98f) // Bottom right
            cubicTo(w * 0.35f, h * 0.95f, w * 0.15f, h * 0.85f, w * 0.1f, h * 0.65f) // Bottom left
            cubicTo(w * 0.05f, h * 0.35f, w * 0.15f, h * 0.05f, w * 0.5f, h * 0.05f) // Left curve to top center
            close()
        }
        drawPath(
            path = shieldPath,
            color = primaryColor.copy(alpha = 0.12f)
        )
        drawPath(
            path = shieldPath,
            color = primaryColor,
            style = Stroke(width = w * 0.04f)
        )

        // 2. The Caring Hand (Stylized inner crescent)
        val handPath = Path().apply {
            moveTo(w * 0.3f, h * 0.65f)
            cubicTo(w * 0.32f, h * 0.8f, w * 0.68f, h * 0.8f, w * 0.7f, h * 0.65f)
            cubicTo(w * 0.72f, h * 0.5f, w * 0.55f, h * 0.45f, w * 0.5f, h * 0.45f)
            cubicTo(w * 0.45f, h * 0.45f, w * 0.28f, h * 0.5f, w * 0.3f, h * 0.65f)
            close()
        }
        drawPath(
            path = handPath,
            color = secondaryColor.copy(alpha = 0.15f)
        )
        drawPath(
            path = handPath,
            color = secondaryColor,
            style = Stroke(width = w * 0.03f)
        )

        // 3. The Leaf & Pulse (Central organic heartbeat/vitality marker)
        val leafPath = Path().apply {
            moveTo(w * 0.5f, h * 0.25f) // Leaf tip
            cubicTo(w * 0.65f, h * 0.32f, w * 0.65f, h * 0.52f, w * 0.5f, h * 0.62f) // Right leaf curve
            cubicTo(w * 0.35f, h * 0.52f, w * 0.35f, h * 0.32f, w * 0.5f, h * 0.25f) // Left leaf curve
            close()
        }
        drawPath(
            path = leafPath,
            color = secondaryColor
        )

        // Heartbeat spike across the center of the leaf
        val pulsePath = Path().apply {
            moveTo(w * 0.5f, h * 0.25f)
            lineTo(w * 0.5f, h * 0.4f)
            lineTo(w * 0.45f, h * 0.43f)
            lineTo(w * 0.55f, h * 0.48f)
            lineTo(w * 0.5f, h * 0.51f)
            lineTo(w * 0.5f, h * 0.62f)
        }
        drawPath(
            path = pulsePath,
            color = Color.White,
            style = Stroke(width = w * 0.03f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
    }
}

@Composable
fun PersianDatePickerDialog(
    initialTimestamp: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val jalali = initialTimestamp.toJalaliDate()
    var selectedYear by remember { mutableStateOf(jalali.year) }
    var selectedMonth by remember { mutableStateOf(jalali.month) }
    var selectedDay by remember { mutableStateOf(jalali.day) }

    val years = (1400..1415).toList()
    val months = (1..12).toList()

    val maxDays = when (selectedMonth) {
        in 1..6 -> 31
        in 7..11 -> 30
        12 -> {
            val rem = (selectedYear - 474) % 2820
            val r = (rem + 38) * 31 % 128
            if (r < 31) 30 else 29
        }
        else -> 30
    }

    if (selectedDay > maxDays) {
        selectedDay = maxDays
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("persian_date_picker_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "انتخاب تاریخ جلالی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val formattedPreview = "${selectedDay} ${persianMonthNames[selectedMonth - 1]} ${selectedYear}".toPersianDigits()
                    Text(
                        text = formattedPreview,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Year Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("سال", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var yearExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { yearExpanded = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(selectedYear.toString().toPersianDigits())
                            }
                            DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                                years.forEach { y ->
                                    DropdownMenuItem(
                                        text = { Text(y.toString().toPersianDigits()) },
                                        onClick = {
                                            selectedYear = y
                                            yearExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Month Selector
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("ماه", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var monthExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { monthExpanded = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(persianMonthNames[selectedMonth - 1])
                            }
                            DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                                months.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(persianMonthNames[m - 1]) },
                                        onClick = {
                                            selectedMonth = m
                                            monthExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Day Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("روز", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var dayExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { dayExpanded = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(selectedDay.toString().toPersianDigits())
                            }
                            DropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                                (1..maxDays).forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text(d.toString().toPersianDigits()) },
                                        onClick = {
                                            selectedDay = d
                                            dayExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val timestamp = jalaliToTimestamp(selectedYear, selectedMonth, selectedDay)
                            onConfirm(timestamp)
                        }
                    ) {
                        Text("تایید")
                    }
                }
            }
        }
    }
}

@Composable
fun PersianTimePickerDialog(
    initialTimestamp: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val cal = Calendar.getInstance()
    cal.timeInMillis = initialTimestamp
    var selectedHour by remember { mutableStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(cal.get(Calendar.MINUTE)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("persian_time_picker_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "انتخاب ساعت و دقیقه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val formattedPreview = String.format("%02d:%02d", selectedHour, selectedMinute).toPersianDigits()
                    Text(
                        text = formattedPreview,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hour selector
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ساعت", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "افزایش ساعت"
                                )
                            }
                            Text(
                                text = String.format("%02d", selectedHour).toPersianDigits(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { selectedHour = if (selectedHour == 0) 23 else selectedHour - 1 }) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "کاهش ساعت"
                                )
                            }
                        }
                    }

                    // Minute selector
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("دقیقه", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "افزایش دقیقه"
                                )
                            }
                            Text(
                                text = String.format("%02d", selectedMinute).toPersianDigits(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { selectedMinute = if (selectedMinute < 5) 55 else selectedMinute - 5 }) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "کاهش دقیقه"
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val newCal = Calendar.getInstance()
                        newCal.timeInMillis = initialTimestamp
                        newCal.set(Calendar.HOUR_OF_DAY, selectedHour)
                        newCal.set(Calendar.MINUTE, selectedMinute)
                        onConfirm(newCal.timeInMillis)
                    }) {
                        Text("تایید")
                    }
                }
            }
        }
    }
}




