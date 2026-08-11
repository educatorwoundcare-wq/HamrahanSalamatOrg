package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Expense
import com.example.data.FixedExpenseTemplate
import com.example.ui.HamrahanViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: HamrahanViewModel,
    modifier: Modifier = Modifier
) {
    val userRole by viewModel.currentUserRole.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val templates by viewModel.fixedExpenseTemplates.collectAsState()
    val categories by viewModel.expenseCategories.collectAsState()

    val categorySuggestionsDismissed by viewModel.categorySuggestionsDismissed.collectAsState()
    val templateSuggestionsDismissed by viewModel.templateSuggestionsDismissed.collectAsState()

    // Access restriction check: Admin, Manager, Financial Manager, Mother Account, GM, Internal Manager, Accountant can access
    val hasAccess = remember(userRole) {
        userRole == "Admin" || userRole == "Manager" || userRole == "Financial Manager" ||
        userRole == "Mother Account" || userRole == "GM" || userRole == "Internal Manager" || userRole == "Accountant"
    }

    if (!hasAccess) {
        AccessDeniedView(userRole = userRole)
        return
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("گزارشات و نمودارها", "لیست هزینه‌ها", "الگوهای هزینه‌های ثابت")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Tab Row Selector ---
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .testTag("expense_tabs"),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("expense_tab_$index"),
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        // --- SUGGESTION BANNERS (User-Driven Suggestion Engines) ---
        if (categories.isEmpty() && !categorySuggestionsDismissed) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("category_suggestion_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "پیشنهاد راه‌اندازی دسته‌بندی‌های مالی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        "هیچ دسته‌بندی مالی تعریف نشده است. مایلید دسته‌بندی‌های پیشنهادی ما (هزینه ثابت، حقوق پرسنل، اجاره مرکز، ملزومات، جاری روزانه) را به عنوان پیشنهاد ثبت کنید؟ شما کنترل ۱۰۰٪ روی ویرایش و حذف تک‌تک آنها دارید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.updateSystemSetting("category_suggestions_dismissed", "true") }
                        ) {
                            Text("رد کردن", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.saveExpenseCategory(com.example.data.ExpenseCategory(name = "هزینه ثابت"))
                                viewModel.saveExpenseCategory(com.example.data.ExpenseCategory(name = "حقوق و پرسنلی"))
                                viewModel.saveExpenseCategory(com.example.data.ExpenseCategory(name = "اجاره و ساختمان"))
                                viewModel.saveExpenseCategory(com.example.data.ExpenseCategory(name = "تجهیزات و ملزومات"))
                                viewModel.saveExpenseCategory(com.example.data.ExpenseCategory(name = "هزینه‌های جاری روزانه"))
                                viewModel.saveExpenseCategory(com.example.data.ExpenseCategory(name = "سایر هزینه‌ها"))
                                viewModel.updateSystemSetting("category_suggestions_dismissed", "true")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer, contentColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text("ایجاد دسته‌های پیشنهادی")
                        }
                    }
                }
            }
        }

        if (templates.isEmpty() && !templateSuggestionsDismissed) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("template_suggestion_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "پیشنهاد الگوهای هزینه‌های ثابت ماهانه",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        "هیچ الگوی هزینه ثابتی وجود ندارد. مایلید الگوهای پیشنهادی (حقوق ماهیانه منشی، اجاره دفتر، پشتیبانی سایت و اینترنت) را برای ایجاد خودکار در ابتدای هر ماه ثبت کنیم؟ شما می‌توانید بعداً هرکدام را ویرایش یا حذف کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.updateSystemSetting("template_suggestions_dismissed", "true") }
                        ) {
                            Text("رد کردن", color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.saveFixedExpenseTemplate(
                                    FixedExpenseTemplate(
                                        title = "حقوق ماهیانه منشی",
                                        category = "حقوق و پرسنلی",
                                        monthlyAmount = 25000000.0,
                                        paymentDay = 29,
                                        isActive = true
                                    )
                                )
                                viewModel.saveFixedExpenseTemplate(
                                    FixedExpenseTemplate(
                                        title = "اجاره ماهیانه دفتر مرکز",
                                        category = "اجاره و ساختمان",
                                        monthlyAmount = 50000000.0,
                                        paymentDay = 1,
                                        isActive = true
                                    )
                                )
                                viewModel.saveFixedExpenseTemplate(
                                    FixedExpenseTemplate(
                                        title = "پشتیبانی سایت و اینترنت",
                                        category = "هزینه‌های جاری روزانه",
                                        monthlyAmount = 5000000.0,
                                        paymentDay = 5,
                                        isActive = true
                                    )
                                )
                                viewModel.updateSystemSetting("template_suggestions_dismissed", "true")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSecondaryContainer, contentColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text("ایجاد الگوهای پیشنهادی")
                        }
                    }
                }
            }
        }

        // --- Active Tab Screen ---
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> FinancialDashboardTab(viewModel = viewModel, expenses = expenses, templates = templates)
                1 -> ExpensesListTab(
                    viewModel = viewModel,
                    expenses = expenses,
                    categoriesList = categories.map { it.name }
                )
                2 -> FixedExpenseTemplatesTab(viewModel = viewModel, templates = templates)
            }
        }
    }
}

// --- ACCESS DENIED FALLBACK VIEW ---
@Composable
fun AccessDeniedView(userRole: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("access_denied_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = "عدم دسترسی به ماژول مالی",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "نقش فعلی شما: «${getRoleFarsiName(userRole)}» می‌باشد.\nاین ماژول مدیریتی صرفاً برای نقش‌های مدیریت (Mother Account، Admin و General Manager) قابل دسترسی است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "جهت تست سیستم، می‌توانید از بخش «تنظیمات سیستم» نقش خود را به Mother Account، Admin یا GM تغییر دهید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun getRoleFarsiName(role: String): String {
    return when (role) {
        "Mother Account" -> "سرپرست مرکز (Mother Account)"
        "Admin" -> "مدیر ارشد (Admin)"
        "GM", "General Manager" -> "مدیر کل (General Manager)"
        else -> "مدیر کل (General Manager)"
    }
}

// ==========================================
// 1. FINANCIAL DASHBOARD & CHARTS TAB
// ==========================================
@Composable
fun FinancialDashboardTab(
    viewModel: HamrahanViewModel,
    expenses: List<Expense>,
    templates: List<FixedExpenseTemplate>
) {
    val moduleDonutChart by viewModel.moduleDonutChart.collectAsState()
    val moduleDailyAverage by viewModel.moduleDailyAverage.collectAsState()

    var filterRange by remember { mutableStateOf("month") } // today, week, month, season, year, custom
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    var startTimestamp by remember { mutableStateOf(getStartOfMonth(now)) }
    var endTimestamp by remember { mutableStateOf(now) }

    // Recompute timestamps based on range filter selection
    LaunchedEffect(filterRange) {
        when (filterRange) {
            "today" -> {
                startTimestamp = getStartOfDay(now)
                endTimestamp = now
            }
            "week" -> {
                startTimestamp = getStartOfWeek(now)
                endTimestamp = now
            }
            "month" -> {
                startTimestamp = getStartOfMonth(now)
                endTimestamp = now
            }
            "season" -> {
                startTimestamp = getStartOfSeason(now)
                endTimestamp = now
            }
            "year" -> {
                startTimestamp = getStartOfYear(now)
                endTimestamp = now
            }
            "custom" -> {
                showCustomDateDialog = true
            }
        }
    }

    val filteredExpenses = remember(expenses, startTimestamp, endTimestamp, filterRange) {
        expenses.filter { it.paymentDate in startTimestamp..endTimestamp }
    }

    val totalExpensesSum = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amount }
    }

    val fixedExpensesSum = remember(filteredExpenses, templates) {
        filteredExpenses.filter { exp ->
            templates.any { it.title == exp.title } || exp.category == "هزینه ثابت" || exp.category.contains("ثابت")
        }.sumOf { it.amount }
    }

    val variableExpensesSum = remember(totalExpensesSum, fixedExpensesSum) {
        totalExpensesSum - fixedExpensesSum
    }

    // Average daily expense
    val daysCount = remember(startTimestamp, endTimestamp) {
        val diffMs = endTimestamp - startTimestamp
        val computed = (diffMs / (24 * 60 * 60 * 1000L)) + 1
        if (computed <= 0) 1 else computed
    }
    val averageDailyExpense = remember(totalExpensesSum, daysCount) {
        totalExpensesSum / daysCount
    }

    // Expense by categories breakdown
    val categoryBreakdown = remember(filteredExpenses) {
        filteredExpenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val topCategory = remember(categoryBreakdown) {
        categoryBreakdown.firstOrNull()?.first ?: "بدون تراکنش"
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Filter Header ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("بازه زمانی گزارش مالی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        Triple("today", "امروز", "today_filter"),
                        Triple("week", "هفته", "week_filter"),
                        Triple("month", "ماه", "month_filter"),
                        Triple("season", "فصل", "season_filter"),
                        Triple("year", "سال", "year_filter"),
                        Triple("custom", "دلخواه", "custom_filter")
                    )

                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        userScrollEnabled = true,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filterOptions) { (range, label, tag) ->
                            val isSelected = filterRange == range
                            FilterChip(
                                selected = isSelected,
                                onClick = { filterRange = range },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }
                }

                // Show formatted Date Range
                Text(
                    text = "دوره: ${formatDate(startTimestamp)} الی ${formatDate(endTimestamp)} (${daysCount} روز)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- Metrics Row 1 (Grid of 2) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "مجموع هزینه‌ها",
                value = formatCurrency(totalExpensesSum),
                icon = Icons.Default.TrendingDown,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )

            if (moduleDailyAverage) {
                MetricCard(
                    title = "میانگین روزانه",
                    value = formatCurrency(averageDailyExpense),
                    icon = Icons.Default.Analytics,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Metrics Row 2 (Grid of 2) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "ثابت / متغیر",
                value = "${formatCurrency(fixedExpensesSum)} / ${formatCurrency(variableExpensesSum)}",
                icon = Icons.Default.PieChart,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "بیشترین هزینه در",
                value = topCategory,
                icon = Icons.Default.Category,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        // --- Chart 1: Category Breakdown (Pie/Donut and Bar Representation) ---
        if (moduleDonutChart) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("سهم دسته‌بندی هزینه‌ها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    if (filteredExpenses.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "داده‌ای برای نمایش در این بازه زمانی وجود ندارد",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        // Custom Donut Chart using Canvas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // The Circular Canvas Donut
                            Canvas(
                                modifier = Modifier
                                    .size(120.dp)
                                    .testTag("donut_chart")
                            ) {
                                var currentAngle = 0f
                                val strokeWidth = 18.dp.toPx()
                                val colors = listOf(
                                    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
                                    Color(0xFFFFB300), Color(0xFF8E24AA), Color(0xFF3949AB),
                                    Color(0xFF00ACC1), Color(0xFFD81B60), Color(0xFF757575)
                                )

                                categoryBreakdown.forEachIndexed { idx, pair ->
                                    val sweepAngle = (pair.second / totalExpensesSum * 360f).toFloat()
                                    drawArc(
                                        color = colors[idx % colors.size],
                                        startAngle = currentAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        size = Size(size.width, size.height),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    currentAngle += sweepAngle
                                }
                            }

                            // Legand breakdown
                            Column(
                                modifier = Modifier.padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val colors = listOf(
                                    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
                                    Color(0xFFFFB300), Color(0xFF8E24AA), Color(0xFF3949AB),
                                    Color(0xFF00ACC1), Color(0xFFD81B60), Color(0xFF757575)
                                )
                                categoryBreakdown.take(4).forEachIndexed { idx, (cat, value) ->
                                    val percent = (value / totalExpensesSum * 100).toInt()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(colors[idx % colors.size], shape = CircleShape)
                                        )
                                        Text(
                                            text = "$cat ($percent%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(140.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Linear bar breakdown
                        categoryBreakdown.forEachIndexed { idx, (cat, value) ->
                            val percent = (value / totalExpensesSum).toFloat()
                            val colors = listOf(
                                Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
                                Color(0xFFFFB300), Color(0xFF8E24AA), Color(0xFF3949AB),
                                Color(0xFF00ACC1), Color(0xFFD81B60), Color(0xFF757575)
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("${formatCurrency(value)} (${(percent * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                LinearProgressIndicator(
                                    progress = { percent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = colors[idx % colors.size],
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Custom Date Range Picker Dialog ---
    if (showCustomDateDialog) {
        Dialog(onDismissRequest = {
            showCustomDateDialog = false
            filterRange = "month" // Fallback
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                var daysInput by remember { mutableStateOf("30") }

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("تنظیم بازه زمانی سفارشی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("سیستم هزینه‌ها را در بازه معینی از روزهای گذشته نمایش خواهد داد:", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

                    OutlinedTextField(
                        value = daysInput,
                        onValueChange = { daysInput = it },
                        label = { Text("تعداد روزهای گذشته") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("custom_days_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showCustomDateDialog = false
                            filterRange = "month"
                        }) {
                            Text("انصراف")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val days = daysInput.toLongOrNull() ?: 30L
                            startTimestamp = now - (days * 24 * 60 * 60 * 1000L)
                            endTimestamp = now
                            showCustomDateDialog = false
                        }) {
                            Text("اعمال")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("metric_${title.replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.1f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// 2. REGISTER & MANAGE EXPENSES TAB
// ==========================================
@Composable
fun ExpensesListTab(
    viewModel: HamrahanViewModel,
    expenses: List<Expense>,
    categoriesList: List<String>
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("همه دسته‌ها") }
    var selectedMethodFilter by remember { mutableStateOf("همه روش‌ها") }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    val filteredExpenses = remember(expenses, searchQuery, selectedCategoryFilter, selectedMethodFilter) {
        expenses.filter { exp ->
            val matchesSearch = exp.title.contains(searchQuery, ignoreCase = true) ||
                    exp.description.contains(searchQuery, ignoreCase = true) ||
                    exp.submitterName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "همه دسته‌ها" || exp.category == selectedCategoryFilter
            val matchesMethod = selectedMethodFilter == "همه روش‌ها" || exp.paymentMethod == selectedMethodFilter
            matchesSearch && matchesCategory && matchesMethod
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Filters and Search Bar ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجوی عنوان هزینه، توضیحات، شخص ثبت کننده...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("expense_search"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Category Selector
                        var showCatDropdown by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedCard(
                                onClick = { showCatDropdown = true },
                                modifier = Modifier.fillMaxWidth().testTag("category_filter_box")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedCategoryFilter, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = showCatDropdown,
                                onDismissRequest = { showCatDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("همه دسته‌ها") },
                                    onClick = { selectedCategoryFilter = "همه دسته‌ها"; showCatDropdown = false }
                                )
                                categoriesList.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = { selectedCategoryFilter = cat; showCatDropdown = false }
                                    )
                                }
                            }
                        }

                        // Payment Method Selector
                        var showMethodDropdown by remember { mutableStateOf(false) }
                        val paymentMethods = listOf("همه روش‌ها", "نقدی", "کارت", "انتقال بانکی", "چک", "سایر")
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedCard(
                                onClick = { showMethodDropdown = true },
                                modifier = Modifier.fillMaxWidth().testTag("method_filter_box")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedMethodFilter, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = showMethodDropdown,
                                onDismissRequest = { showMethodDropdown = false }
                            ) {
                                paymentMethods.forEach { method ->
                                    DropdownMenuItem(
                                        text = { Text(method) },
                                        onClick = { selectedMethodFilter = method; showMethodDropdown = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- List of Expenses ---
            if (filteredExpenses.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Payments,
                    message = "هیچ هزینه‌ای منطبق با فیلترهای بالا یافت نشد.",
                    description = "برای ثبت هزینه جدید، روی دکمه + گوشه پایین کلیک کنید.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredExpenses, key = { it.id }) { expense ->
                        ExpenseItemRow(
                            expense = expense,
                            onEdit = { editingExpense = it; showAddDialog = true },
                            onDelete = { viewModel.deleteExpense(it) }
                        )
                    }
                }
            }
        }

        // --- Floating Action Button to Add Expense ---
        FloatingActionButton(
            onClick = { editingExpense = null; showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_expense_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "ثبت هزینه جدید")
        }
    }

    // --- Add/Edit Expense Dialog ---
    if (showAddDialog) {
        ExpenseFormDialog(
            viewModel = viewModel,
            expense = editingExpense,
            categories = categoriesList,
            onDismiss = { showAddDialog = false },
            onSave = { expense, reason, comment ->
                viewModel.saveExpense(expense, reason, comment)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ExpenseItemRow(
    expense: Expense,
    onEdit: (Expense) -> Unit,
    onDelete: (Expense) -> Unit
) {
    com.example.ui.components.EnterpriseCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_item_${expense.id}"),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (expense.category == "هزینه ثابت") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = expense.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${expense.category} • ${expense.paymentMethod}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formatCurrency(expense.amount),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (expense.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "پرداخت: ${formatDate(expense.paymentDate)} • ثبت: ${expense.submitterName.ifEmpty { "مدیر سیستم" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onEdit(expense) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("edit_expense_btn_${expense.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "ویرایش",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onDelete(expense) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_expense_btn_${expense.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- EXPENSE REGISTRATION & EDIT DIALOG FORM ---
@Composable
fun ExpenseFormDialog(
    viewModel: HamrahanViewModel,
    expense: Expense?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (Expense, String, String) -> Unit
) {
    val fieldActiveSubmitter by viewModel.fieldActiveSubmitter.collectAsState()
    val fieldActiveReceipt by viewModel.fieldActiveReceipt.collectAsState()
    val fieldActiveDescription by viewModel.fieldActiveDescription.collectAsState()
    val fieldActivePaymentMethod by viewModel.fieldActivePaymentMethod.collectAsState()

    var title by remember { mutableStateOf(expense?.title ?: "") }
    var amountString by remember { mutableStateOf(expense?.amount?.toLong()?.toString() ?: "") }
    var selectedCategory by remember { mutableStateOf(expense?.category ?: categories.firstOrNull() ?: "سایر هزینه‌ها") }
    var selectedMethod by remember { mutableStateOf(expense?.paymentMethod ?: "کارت") }
    var description by remember { mutableStateOf(expense?.description ?: "") }
    var submitter by remember { mutableStateOf(expense?.submitterName ?: "مدیر مالی") }
    var receiptPath by remember { mutableStateOf(expense?.receiptAttachmentPath ?: "") }

    var titleError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    val paymentMethods = listOf("کارت", "نقدی", "انتقال بانکی", "چک", "سایر")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (expense == null) "ثبت هزینه جدید مرکز" else "ویرایش سند هزینه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (categories.isEmpty()) {
                    Text(
                        "توجه: هیچ دسته‌بندی هزینه‌ای در پایگاه‌داده تعریف نشده است. لطفاً ابتدا از بخش تنظیمات دسته‌بندی ایجاد کنید.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    label = { Text("عنوان هزینه") },
                    isError = titleError,
                    modifier = Modifier.fillMaxWidth().testTag("form_title_input"),
                    singleLine = true
                )

                // Amount Input
                OutlinedTextField(
                    value = amountString,
                    onValueChange = { amountString = it; amountError = false },
                    label = { Text("مبلغ هزینه (ریال)") },
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("form_amount_input"),
                    singleLine = true
                )

                // Category Dropdown
                var catDropdownExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("دسته‌بندی هزینه") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { catDropdownExpanded = true }
                            .testTag("form_category_select")
                    )
                    DropdownMenu(
                        expanded = catDropdownExpanded,
                        onDismissRequest = { catDropdownExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    catDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payment Method Selector
                if (fieldActivePaymentMethod) {
                    Text("روش پرداخت:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            userScrollEnabled = true
                        ) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    paymentMethods.forEach { method ->
                                        val isSelected = selectedMethod == method
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedMethod = method },
                                            label = { Text(method, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Submitter Input
                if (fieldActiveSubmitter) {
                    OutlinedTextField(
                        value = submitter,
                        onValueChange = { submitter = it },
                        label = { Text("ثبت‌کننده / کارپرداز") },
                        modifier = Modifier.fillMaxWidth().testTag("form_submitter_input"),
                        singleLine = true
                    )
                }

                // Receipt Attachment Simulated Path Input
                if (fieldActiveReceipt) {
                    OutlinedTextField(
                        value = receiptPath,
                        onValueChange = { receiptPath = it },
                        label = { Text("آدرس تصویر رسید یا شماره سند بانکی") },
                        placeholder = { Text("مثلاً: سند شماره ۱۲۳۴۵") },
                        modifier = Modifier.fillMaxWidth().testTag("form_receipt_input"),
                        singleLine = true
                    )
                }

                // Description Input
                if (fieldActiveDescription) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("توضیحات تکمیلی") },
                        modifier = Modifier.fillMaxWidth().testTag("form_desc_input"),
                        maxLines = 3
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Manual Adjustment Reason & Comment (Conditional on edit and change of financial values)
                val isFinChanged = expense != null && (amountString.toDoubleOrNull() != expense?.amount || selectedCategory != expense?.category)
                var adjustmentReason by remember { mutableStateOf("اصلاح اشتباه ثبت اطلاعات") }
                var adjustmentComment by remember { mutableStateOf("") }
                
                if (isFinChanged) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "تعدیل مالی",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "تعدیل مالی شناسایی شد. علت را انتخاب کنید:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            // Preset reasons dropdown
                            val presetReasons = listOf(
                                "اصلاح اشتباه ثبت اطلاعات",
                                "توافق با بیمار",
                                "تخفیف مدیریتی",
                                "اصلاح تعرفه",
                                "تعدیل حسابداری",
                                "اصلاح مالکیت مصرفی",
                                "حذف رکورد تکراری",
                                "انطباق پرداخت",
                                "سایر"
                            )
                            var reasonExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = adjustmentReason,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("علت تعدیل مالی") },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { reasonExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = reasonExpanded,
                                    onDismissRequest = { reasonExpanded = false }
                                ) {
                                    presetReasons.forEach { reasonItem ->
                                        DropdownMenuItem(
                                            text = { Text(reasonItem) },
                                            onClick = {
                                                adjustmentReason = reasonItem
                                                reasonExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = adjustmentComment,
                                onValueChange = { adjustmentComment = it },
                                label = { Text("توضیحات اختیاری علت تعدیل") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
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
                            if (title.isBlank()) {
                                titleError = true
                                return@Button
                            }
                            val amount = amountString.toDoubleOrNull() ?: 0.0
                            if (amount <= 0) {
                                amountError = true
                                return@Button
                            }

                            val result = Expense(
                                id = expense?.id ?: 0,
                                title = title,
                                category = selectedCategory,
                                amount = amount,
                                registrationDate = expense?.registrationDate ?: System.currentTimeMillis(),
                                paymentDate = System.currentTimeMillis(),
                                paymentMethod = if (fieldActivePaymentMethod) selectedMethod else "کارت",
                                description = if (fieldActiveDescription) description else "",
                                submitterName = if (fieldActiveSubmitter) submitter else "سیستم",
                                receiptAttachmentPath = if (fieldActiveReceipt) receiptPath else ""
                            )
                            onSave(result, adjustmentReason, adjustmentComment)
                        },
                        modifier = Modifier.testTag("submit_expense_form_btn")
                    ) {
                        Text("ذخیره")
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. FIXED EXPENSES / TEMPLATES TAB
// ==========================================
@Composable
fun FixedExpenseTemplatesTab(
    viewModel: HamrahanViewModel,
    templates: List<FixedExpenseTemplate>
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<FixedExpenseTemplate?>(null) }
    var showManualSyncMessage by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Info and Actions Header ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("مدیریت الگوهای مخارج ثابت ماهانه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        text = "هزینه‌های ثابت (مانند اجاره دفتر، حقوق کارکنان دفتری و خدمات هاستینگ) به صورت خودکار با شروع هر ماه ایجاد شده و در دفتر کل و گزارشات مالی ثبت می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.checkAndGenerateFixedExpensesForCurrentMonth()
                                showManualSyncMessage = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("sync_fixed_expenses_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تولید دستی هزینه‌های ثابت این ماه")
                    }
                }
            }

            AnimatedVisibility(visible = showManualSyncMessage) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showManualSyncMessage = false }) {
                            Text("باشه")
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("عملیات بررسی و تولید هزینه‌های ثابت با موفقیت انجام شد.")
                }
            }

            // --- List of Templates ---
            if (templates.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هیچ الگوی هزینه ثابتی تعریف نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(templates, key = { it.id }) { template ->
                        TemplateItemRow(
                            template = template,
                            onEdit = { editingTemplate = it; showAddDialog = true },
                            onToggleActive = { active -> viewModel.saveFixedExpenseTemplate(template.copy(isActive = active)) },
                            onDelete = { viewModel.deleteFixedExpenseTemplate(it) }
                        )
                    }
                }
            }
        }

        // --- Add Template FAB ---
        FloatingActionButton(
            onClick = { editingTemplate = null; showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_template_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "افزودن الگوی جدید")
        }
    }

    // --- Add/Edit Template Dialog ---
    if (showAddDialog) {
        TemplateFormDialog(
            template = editingTemplate,
            onDismiss = { showAddDialog = false },
            onSave = { template ->
                viewModel.saveFixedExpenseTemplate(template)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TemplateItemRow(
    template: FixedExpenseTemplate,
    onEdit: (FixedExpenseTemplate) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: (FixedExpenseTemplate) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("template_item_${template.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "سررسید: بیست و پنجم ماه • وضعیت: ${if (template.isActive) "فعال" else "غیرفعال"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formatCurrency(template.monthlyAmount),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("وضعیت الگو:", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = template.isActive,
                        onCheckedChange = onToggleActive,
                        modifier = Modifier.scale(0.8f).testTag("template_switch_${template.id}")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onEdit(template) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("edit_template_btn_${template.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "ویرایش الگو",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onDelete(template) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_template_btn_${template.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الگو",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// Extension to scale layout elements
private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
            placeable.placeRelative(0, 0)
        }
    }
)

// --- TEMPLATE DIALOG FORM ---
@Composable
fun TemplateFormDialog(
    template: FixedExpenseTemplate?,
    onDismiss: () -> Unit,
    onSave: (FixedExpenseTemplate) -> Unit
) {
    var title by remember { mutableStateOf(template?.title ?: "") }
    var amountString by remember { mutableStateOf(template?.monthlyAmount?.toLong()?.toString() ?: "") }
    var paymentDayString by remember { mutableStateOf(template?.paymentDay?.toString() ?: "25") }

    var titleError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (template == null) "الگوی هزینه ثابت جدید" else "ویرایش الگوی هزینه ثابت",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    label = { Text("عنوان هزینه ثابت") },
                    isError = titleError,
                    modifier = Modifier.fillMaxWidth().testTag("template_title_input"),
                    singleLine = true
                )

                // Amount Input
                OutlinedTextField(
                    value = amountString,
                    onValueChange = { amountString = it; amountError = false },
                    label = { Text("مبلغ ماهانه (ریال)") },
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("template_amount_input"),
                    singleLine = true
                )

                // Day of Payment Input (1 to 31)
                OutlinedTextField(
                    value = paymentDayString,
                    onValueChange = { paymentDayString = it },
                    label = { Text("روز پرداخت در ماه (۱ الی ۳۱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("template_day_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                            if (title.isBlank()) {
                                titleError = true
                                return@Button
                            }
                            val amount = amountString.toDoubleOrNull() ?: 0.0
                            if (amount <= 0) {
                                amountError = true
                                return@Button
                            }

                            val day = paymentDayString.toIntOrNull() ?: 25

                            val result = FixedExpenseTemplate(
                                id = template?.id ?: 0,
                                title = title,
                                monthlyAmount = amount,
                                paymentDay = day,
                                isActive = template?.isActive ?: true
                            )
                            onSave(result)
                        },
                        modifier = Modifier.testTag("submit_template_btn")
                    ) {
                        Text("ذخیره الگو")
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. HELPER DATES AND FORMATTERS FUNCTIONS
// ==========================================

private fun formatCurrency(amount: Double): String {
    val df = DecimalFormat("#,###")
    return "${df.format(amount)} ریال"
}

private fun formatDate(timestamp: Long): String {
    return timestamp.formatDate()
}

private fun getStartOfDay(time: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = time
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun getStartOfWeek(time: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = time
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val daysToSubtract = when (dayOfWeek) {
        Calendar.SATURDAY -> 0
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 5
        Calendar.FRIDAY -> 6
        else -> 0
    }
    calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun getStartOfMonth(time: Long): Long {
    return time.getStartOfJalaliMonth()
}

private fun getStartOfSeason(time: Long): Long {
    return time.getStartOfJalaliSeason()
}

private fun getStartOfYear(time: Long): Long {
    return time.getStartOfJalaliYear()
}
