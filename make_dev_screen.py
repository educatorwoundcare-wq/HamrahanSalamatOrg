import os

with open('dev_tools.txt', 'r') as f:
    dev_tools_content = f.read()

content = f"""package com.example.ui.dev

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
) {{
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    
    // States from Legacy Dev Tools
    val userRole by viewModel.currentUserRole.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    
    val moduleDonutChartStr by viewModel.getSystemSetting("module_donut_chart").collectAsState(initial = "true")
    val moduleLineChartStr by viewModel.getSystemSetting("module_line_chart").collectAsState(initial = "true")
    val moduleDonutChart = moduleDonutChartStr == "true"
    val moduleLineChart = moduleLineChartStr == "true"
    
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    
    val largeAdjustmentPercentage by viewModel.largeAdjustmentPercentage.collectAsState()
    val largeAdjustmentAmount by viewModel.largeAdjustmentAmount.collectAsState()
    var editingPct by remember {{ mutableStateOf("") }}
    var editingAmt by remember {{ mutableStateOf("") }}
    LaunchedEffect(largeAdjustmentPercentage) {{ editingPct = largeAdjustmentPercentage.toString() }}
    LaunchedEffect(largeAdjustmentAmount) {{ editingAmt = largeAdjustmentAmount.toLong().toString() }}
    
    val integrityReport by viewModel.financialIntegrityReport.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val editHistories by viewModel.editHistories.collectAsState()
    
    // --- Diagnostics States ---
    val syncSummary by viewModel.syncSummary.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    var showLegacyTools by remember {{ mutableStateOf(false) }}

    Scaffold(
        topBar = {{
            TopAppBar(
                title = {{ Text("مرکز کنترل توسعه‌دهنده", fontWeight = FontWeight.Bold) }},
                navigationIcon = {{
                    IconButton(onClick = {{ navController.popBackStack() }}) {{
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }}
                }},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }}
    ) {{ padding ->
        if (!isDeveloperMode) {{
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {{
                Text("Developer Mode is Disabled")
            }}
            return@Scaffold
        }}
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {{
            // =====================================
            // SECTION A — 🏥 وضعیت سلامت سیستم
            // =====================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {{
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {{
                    Text("وضعیت سلامت سیستم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider()
                    
                    StatusRow("اینترنت", if(isOnline) "🟢 متصل" else "🔴 قطع")
                    StatusRow("همگام‌سازی", if(syncSummary?.failed ?: 0 > 0) "🔴 خطا در صف" else if (syncSummary?.pending ?: 0 > 0) "🟡 نیازمند توجه" else "🟢 سالم")
                    StatusRow("عملیات در صف", "${{syncSummary?.pending ?: 0}}")
                    StatusRow("خطاهای اخیر", "${{syncSummary?.failed ?: 0}}")
                }}
            }}
            
            // =====================================
            // SECTION B — ⚠️ مرکز خطاها
            // =====================================
            if ((syncSummary?.failed ?: 0) > 0) {{
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {{
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {{
                        Text("⚠️ مرکز خطاها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha=0.3f))
                        syncSummary?.details?.filter {{ it.classification.name != "SUCCESS" }}?.take(3)?.forEach {{ detail ->
                            Text("🔴 ${{detail.entityType}} (${{detail.operationType}})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("علت: ${{detail.failureReasonDescription}}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                        }}
                    }}
                }}
            }}
            
            // =====================================
            // SECTION M — 📊 SYSTEM INFORMATION
            // =====================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {{
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {{
                    Text("اطلاعات سیستم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    StatusRow("نسخه نرم‌افزار", "۲.۴.۰ (Version 2.4.0)")
                    StatusRow("Architecture", "Offline First")
                }}
            }}
            
            // =====================================
            // SECTION L — 🧪 DEVELOPER TEST LAB (Simplified)
            // =====================================
            Button(
                onClick = {{ viewModel.syncEngine?.triggerSync() }},
                modifier = Modifier.fillMaxWidth()
            ) {{
                Text("🔄 اجرای اجباری همگام‌سازی (Force Sync)")
            }}

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {{ showLegacyTools = !showLegacyTools }},
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {{
                Text(if (showLegacyTools) "مخفی کردن ابزارهای قدیمی" else "⚙️ نمایش ابزارهای قدیمی توسعه‌دهنده")
            }}
            
            if (showLegacyTools) {{
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {{
{dev_tools_content}
                }}
            }}
        }}
    }}
}}

@Composable
fun StatusRow(label: String, value: String) {{
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {{
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }}
}}
"""

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(content)

