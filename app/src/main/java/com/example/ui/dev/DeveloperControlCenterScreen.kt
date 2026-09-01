package com.example.ui.dev

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.HamrahanViewModel
import com.example.ui.formatDateTime
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperControlCenterScreen(
    viewModel: HamrahanViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        while(true) {
            if (viewModel.isDevSessionValid()) {
                viewModel.keepDevSessionAlive()
            } else {
                viewModel.notifyDevSessionExpired()
                navController.navigate(com.example.ui.navigation.DeveloperAuth) {
                    popUpTo(com.example.ui.navigation.DeveloperControlCenter) { inclusive = true }
                }
            }
            kotlinx.coroutines.delay(1000)
        }
    }
val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    
    // States from Legacy Dev Tools
    val userRole by viewModel.currentUserRole.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    
    
    
    val moduleDonutChart by viewModel.moduleDonutChart.collectAsState()
    
    
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val moduleDailyAverage by viewModel.moduleDailyAverage.collectAsState()
    val moduleFixedExpensesGenerator by viewModel.moduleFixedExpensesGenerator.collectAsState()
    val autoGenerateFixedExpenses by viewModel.autoGenerateFixedExpenses.collectAsState()
    val fieldActiveSubmitter by viewModel.fieldActiveSubmitter.collectAsState()
    val fieldActiveReceipt by viewModel.fieldActiveReceipt.collectAsState()
    val fieldActiveDescription by viewModel.fieldActiveDescription.collectAsState()
    val fieldActivePaymentMethod by viewModel.fieldActivePaymentMethod.collectAsState()
    val requireManagerApprovalLarge by viewModel.requireManagerApprovalLargeAdjustments.collectAsState()
    // val requireManagerApprovalLarge by viewModel.requireManagerApprovalLarge.collectAsState()

    
    val largeAdjustmentPercentage by viewModel.largeAdjustmentPercentage.collectAsState()
    val largeAdjustmentAmount by viewModel.largeAdjustmentAmount.collectAsState()
    var editingPct by remember { mutableStateOf("") }
    var editingAmt by remember { mutableStateOf("") }
    LaunchedEffect(largeAdjustmentPercentage) { editingPct = largeAdjustmentPercentage.toString() }
    LaunchedEffect(largeAdjustmentAmount) { editingAmt = largeAdjustmentAmount.toLong().toString() }
    
    val integrityReport by viewModel.integrityReport.collectAsState()
    val editHistories: List<com.example.data.FinancialEditHistory> by viewModel.editHistories.collectAsState()
    val auditLogs: List<com.example.data.AuditLog> by viewModel.auditLogs.collectAsState()
    
    
    
    // --- Diagnostics States ---
    val syncSummary by viewModel.syncSummary.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val diagnosticEvents by viewModel.diagnosticEvents.collectAsState()
    val pendingRequests by viewModel.livePendingDevices.collectAsState()
    val isMasterDevice by viewModel.isMasterDevice.collectAsState()

    var showLegacyTools by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مرکز کنترل توسعه‌دهنده", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    ) { padding ->
        if (!isDeveloperMode) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Developer Mode is Disabled")
            }
            return@Scaffold
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =====================================
            // SECTION A — 🏥 وضعیت سلامت سیستم
            // =====================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("وضعیت سلامت سیستم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider()
                    
                    StatusRow("اینترنت", if(isOnline) "🟢 متصل" else "🔴 قطع")
                    StatusRow("همگام‌سازی", if(syncSummary?.failed ?: 0 > 0) "🔴 خطا در صف" else if (syncSummary?.pending ?: 0 > 0) "🟡 نیازمند توجه" else "🟢 سالم")
                    StatusRow("عملیات در صف", "${syncSummary?.pending ?: 0}")
                    StatusRow("خطاهای اخیر", "${syncSummary?.failed ?: 0}")
                }
            }
            
            // =====================================
            // SECTION B — ⚠️ مرکز خطاها
            // =====================================
            if ((syncSummary?.failed ?: 0) > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("⚠️ مرکز خطاها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha=0.3f))
                        syncSummary?.details?.filter { it.classification.name != "SUCCESS" }?.take(3)?.forEach { detail ->
                            Text("🔴 ${detail.entityType} (${detail.operationType})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("علت: ${detail.failureReasonDescription}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
            
            
            // =====================================
            // SECTION J — 📋 SMART DIAGNOSTICS LOGS
            // =====================================
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min=200.dp, max=400.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("گزارش هوشمند سیستم (Diagnostics Log)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = {
                            val logs = diagnosticEvents.joinToString("\n") { "[${it.timestamp.formatDateTime()}] [${it.category}] ${it.summary} - ${it.details}" }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logs))
                            Toast.makeText(context, "گزارشات در حافظه کپی شد", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "کپی")
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(diagnosticEvents) { event ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${event.category} - ${event.level}", fontWeight = FontWeight.Bold, color = if(event.level == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                        Text(event.timestamp.formatDateTime(), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(event.summary, style = MaterialTheme.typography.bodySmall)
                                    if(event.details.isNotEmpty()) {
                                        Text(event.details, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =====================================
            // SECTION M — 📊 SYSTEM INFORMATION
            // =====================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اطلاعات سیستم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    StatusRow("نسخه نرم‌افزار", "۲.۴.۰ (Version 2.4.0)")
                    StatusRow("Architecture", "Offline First")
                }
            }
            
            // =====================================
            // SECTION L — 🧪 DEVELOPER TEST LAB (Simplified)
            // =====================================
            Button(
                onClick = { viewModel.triggerSync() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 اجرای اجباری همگام‌سازی (Force Sync)")
            }


            // --- PAIRING DEBUG & CONTROL ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مدیریت اتصال دستگاه‌ها (Pairing Debug)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2))) {
                        Text("PAIRING DEBUG | Master: ${isMasterDevice} | Pending: ${pendingRequests.size}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = Color.Black)
                    }

                    if (isMasterDevice && pendingRequests.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        com.example.ui.components.PairingRequestsSection(
                            pendingDevices = pendingRequests,
                            onApprove = { dev -> viewModel.approveDeviceAccess(dev.deviceId) },
                            onReject = { dev -> viewModel.rejectDeviceAccess(dev.deviceId) },
                            onRefresh = { viewModel.refreshPairingRequests() },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("No pending requests or not Master.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.refreshPairingRequests() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { showLegacyTools = !showLegacyTools },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(if (showLegacyTools) "مخفی کردن ابزارهای قدیمی" else "⚙️ نمایش ابزارهای قدیمی توسعه‌دهنده")
            }
            
            if (showLegacyTools) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // --- Simulated User Access Role Card ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("dev_role_simulator_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("شبیه‌سازی و کنترل سطح دسترسی کاربر", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "جهت بررسی و آزمون دقیق دسترسی‌های امنیتی سیستم (تست نیازمندی عدم دسترسی کاربران غیرمجاز)، می‌توانید نقش کاربری فعال خود را در زیر تغییر دهید:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 20.sp
                        )

                        val roles = listOf(
                            Pair("Mother Account", "سرپرست مرکز (Mother Account)"),
                            Pair("Admin", "مدیر ارشد (Admin)"),
                            Pair("GM", "مدیر کل (GM)")
                        )

                        roles.forEach { (roleKey, roleLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateCurrentUserRole(roleKey) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = userRole == roleKey,
                                    onClick = { viewModel.updateCurrentUserRole(roleKey) },
                                    modifier = Modifier.testTag("role_radio_$roleKey")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(roleLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // --- Theme Switcher inside Developer Options ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("dev_theme_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("پوسته تاریک (Dark Mode)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("تغییر ظاهر کلی نرم‌افزار به حالت بهینه شب", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { viewModel.toggleDarkMode(it) },
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }

                // --- Advanced ERP Configuration Card (User-Driven) ---
                var newCategoryName by remember { mutableStateOf("") }
                var selectedMergeSource by remember { mutableStateOf("") }
                var selectedMergeTarget by remember { mutableStateOf("") }
                var showMergeSourceDropdown by remember { mutableStateOf(false) }
                var showMergeTargetDropdown by remember { mutableStateOf(false) }
                var editingCategoryForRename by remember { mutableStateOf<com.example.data.ExpenseCategory?>(null) }
                var renameCategoryInput by remember { mutableStateOf("") }

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("advanced_erp_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("مدیریت و پیکربندی ماژول‌های ERP پیشرفته", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 1. Module Management
                        Text("ماژول‌ها و بخش‌های فعال در سیستم مالی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("نمودار سهم دسته‌بندی مخارج (Donut Chart)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("نمایش دایره‌ای توزیع انواع هزینه‌ها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = moduleDonutChart,
                                onCheckedChange = { viewModel.updateSystemSetting("module_donut_chart", it.toString()) },
                                modifier = Modifier.testTag("switch_donut_chart")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ماژول محاسبه میانگین روزانه مخارج", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("نمایش خودکار میانگین هزینه روز در گزارش", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = moduleDailyAverage,
                                onCheckedChange = { viewModel.updateSystemSetting("module_daily_average", it.toString()) },
                                modifier = Modifier.testTag("switch_daily_average")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("سیستم الگوهای هزینه‌های ثابت", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("پشتیبانی از تعاریف و تولید مخارج ثابت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = moduleFixedExpensesGenerator,
                                onCheckedChange = { viewModel.updateSystemSetting("module_fixed_expenses_generator", it.toString()) },
                                modifier = Modifier.testTag("switch_fixed_generator")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تولید خودکار هزینه‌های ثابت", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("اجرای خودکار الگوها در ابتدای هر ماه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoGenerateFixedExpenses,
                                onCheckedChange = { viewModel.updateSystemSetting("auto_generate_fixed_expenses", it.toString()) },
                                modifier = Modifier.testTag("switch_auto_fixed_expenses")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 2. Form Fields Customization
                        Text("فیلدهای اختیاری فرم ثبت هزینه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فیلد کارپرداز / شخص ثبت‌کننده", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = fieldActiveSubmitter,
                                onCheckedChange = { viewModel.updateSystemSetting("field_active_submitter", it.toString()) },
                                modifier = Modifier.testTag("switch_field_submitter")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فیلد تصویر سند / پیوست فاکتور", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = fieldActiveReceipt,
                                onCheckedChange = { viewModel.updateSystemSetting("field_active_receipt", it.toString()) },
                                modifier = Modifier.testTag("switch_field_receipt")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فیلد توضیحات تکمیلی", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = fieldActiveDescription,
                                onCheckedChange = { viewModel.updateSystemSetting("field_active_description", it.toString()) },
                                modifier = Modifier.testTag("switch_field_description")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فیلد روش پرداخت (کارت، نقد و...)", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = fieldActivePaymentMethod,
                                onCheckedChange = { viewModel.updateSystemSetting("field_active_payment_method", it.toString()) },
                                modifier = Modifier.testTag("switch_field_payment_method")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 3. Category Management
                        Text("مدیریت پویا و کامل دسته‌بندی‌های هزینه مرکز", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text("تغییر نام، حذف و یکپارچه‌سازی دسته‌بندی‌ها به طور مستقیم از دیتابیس:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // Add Category
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCategoryName,
                                onValueChange = { newCategoryName = it },
                                placeholder = { Text("نام دسته جدید را وارد کنید...") },
                                modifier = Modifier.weight(1f).testTag("add_category_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    if (newCategoryName.isNotBlank()) {
                                        viewModel.saveExpenseCategory(com.example.data.ExpenseCategory(name = newCategoryName.trim()))
                                        newCategoryName = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("add_category_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "افزودن")
                            }
                        }

                        // Existing categories
                        if (expenseCategories.isEmpty()) {
                            Text("هیچ دسته‌بندی هزینه‌ای در پایگاه‌داده وجود ندارد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                expenseCategories.forEach { category ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (editingCategoryForRename?.id == category.id) {
                                            OutlinedTextField(
                                                value = renameCategoryInput,
                                                onValueChange = { renameCategoryInput = it },
                                                modifier = Modifier.weight(1f).testTag("rename_cat_input_${category.id}"),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            Row {
                                                TextButton(onClick = {
                                                    if (renameCategoryInput.isNotBlank()) {
                                                        viewModel.saveExpenseCategory(category.copy(name = renameCategoryInput.trim()))
                                                        editingCategoryForRename = null
                                                    }
                                                }) {
                                                    Text("ذخیره")
                                                }
                                                TextButton(onClick = { editingCategoryForRename = null }) {
                                                    Text("لغو")
                                                }
                                            }
                                        } else {
                                            Text(category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        editingCategoryForRename = category
                                                        renameCategoryInput = category.name
                                                    },
                                                    modifier = Modifier.size(32.dp).testTag("edit_cat_btn_${category.id}")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "ویرایش نام", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteExpenseCategory(category) },
                                                    modifier = Modifier.size(32.dp).testTag("delete_cat_btn_${category.id}")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف دسته", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 4. Merge Categories Section (Advanced ERP)
                        Text("یکپارچه‌سازی و ادغام دسته‌ها (ERP)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Text("انتقال تمام هزینه‌ها و الگوهای متعلق به دسته مبدا به دسته مقصد، سپس حذف دسته مبدا:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Source category picker
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { showMergeSourceDropdown = true },
                                        modifier = Modifier.fillMaxWidth().testTag("merge_source_picker")
                                    ) {
                                        Text(if (selectedMergeSource.isEmpty()) "انتخاب دسته مبدا" else selectedMergeSource, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(
                                        expanded = showMergeSourceDropdown,
                                        onDismissRequest = { showMergeSourceDropdown = false }
                                    ) {
                                        expenseCategories.forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category.name) },
                                                onClick = {
                                                    selectedMergeSource = category.name
                                                    showMergeSourceDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Target category picker
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { showMergeTargetDropdown = true },
                                        modifier = Modifier.fillMaxWidth().testTag("merge_target_picker")
                                    ) {
                                        Text(if (selectedMergeTarget.isEmpty()) "انتخاب دسته مقصد" else selectedMergeTarget, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(
                                        expanded = showMergeTargetDropdown,
                                        onDismissRequest = { showMergeTargetDropdown = false }
                                    ) {
                                        expenseCategories.filter { it.name != selectedMergeSource }.forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category.name) },
                                                onClick = {
                                                    selectedMergeTarget = category.name
                                                    showMergeTargetDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (selectedMergeSource.isNotEmpty() && selectedMergeTarget.isNotEmpty()) {
                                        viewModel.mergeExpenseCategories(selectedMergeSource, selectedMergeTarget)
                                        selectedMergeSource = ""
                                        selectedMergeTarget = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().testTag("merge_categories_btn"),
                                enabled = selectedMergeSource.isNotEmpty() && selectedMergeTarget.isNotEmpty()
                            ) {
                                Text("ادغام و یکپارچه‌سازی دسته‌بندی‌ها")
                            }
                        }
                    }
                }

                // --- Large Adjustments & Audit Configuration Card ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("adjustment_config_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("تنظیمات آستانه تعدیلات کلان مالی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text(
                            text = "هنگام ویرایش اسناد مالی، در صورتی که تغییرات از این آستانه‌ها بیشتر باشد، رویداد به عنوان «تعدیل کلان» نشاندار شده و در بخش ممیزی به صورت ویژه ثبت خواهد شد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editingPct,
                                onValueChange = { editingPct = it },
                                label = { Text("آستانه درصد تغییر کلان (%)") },
                                modifier = Modifier.weight(1f).testTag("adjustment_pct_input"),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editingAmt,
                                onValueChange = { editingAmt = it },
                                label = { Text("آستانه مبلغ تغییر کلان ($defaultCurrency)") },
                                modifier = Modifier.weight(1f).testTag("adjustment_amt_input"),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الزام تایید مدیریت برای تعدیلات مالی کلان", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("جلوگیری از ثبت نهایی تغییرات بدون ثبت دلیل رسمی", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = requireManagerApprovalLarge,
                                onCheckedChange = { viewModel.updateSystemSetting("require_manager_approval_large", it.toString()) },
                                modifier = Modifier.testTag("require_manager_approval_large_switch")
                            )
                        }

                        Button(
                            onClick = {
                                val pct = editingPct.toDoubleOrNull() ?: 20.0
                                val amt = editingAmt.toDoubleOrNull() ?: 100000.0
                                viewModel.updateSystemSetting("large_adjustment_percentage", pct.toString())
                                viewModel.updateSystemSetting("large_adjustment_amount", amt.toString())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.align(Alignment.End).testTag("save_adjustment_thresholds_btn")
                        ) {
                            Text("ذخیره آستانه‌ها")
                        }
                    }
                }

                // --- Financial Integrity Monitor Card ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("financial_integrity_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("پایش یکپارچگی ساختاری سیستم مالی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text(
                            text = "بررسی خودکار تمامیت روابط دیتابیس شامل عدم تکرار شماره اسناد، هم‌پوشانی مالی دیتابیس، ارجاعات خارجی پرسنل/بیمار و حذف رکوردهای یتیم.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 18.sp
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (integrityReport.contains("⚠️")) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(1.dp, if (integrityReport.contains("⚠️")) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = integrityReport,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                lineHeight = 22.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.runIntegrityCheck() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth().testTag("run_integrity_check_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("اجرای پایش و بررسی پایداری تراکنش‌ها")
                        }
                    }
                }

                // --- Financial Adjustment Audit Log History Card ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("adjustment_audit_log_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("تاریخچه اصلاحات و تعدیلات مالی (Audit Trail)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text(
                            text = "رویدادنگاری تغییر مقادیر اصلی اسناد مالی به انضمام نقش کاربر و توجیه مالی ثبت شده:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        if (editHistories.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("هیچ تعدیل مالی در سیستم ثبت نشده است.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                editHistories.take(15).forEach { history ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "کد سند: ${history.entityType} #${history.entityId}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "توسط: ${history.editedBy} (${history.userRole})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                            Text(
                                                text = "علت انتخاب شده: ${history.reason}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error
                                            )

                                            if (history.comment.isNotEmpty()) {
                                                Text(
                                                    text = "توضیحات: ${history.comment}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("مقدار قبلی:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                    Text(history.previousValue, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("مقدار جدید:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                    Text(history.newValue, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "مابه‌التفاوت: " + String.format("%,.0f", history.differenceAmount) + " " + defaultCurrency,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                
                                                val dateStr = history.timestamp.formatDateTime()
                                                Text(
                                                    text = dateStr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

                }
            }
        }
    }

@Composable
fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }

}
