package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.example.ui.navigation.*

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: HamrahanViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get the Repository and Use Cases from manual DI AppContainer
        val appContainer = (application as HamrahanApplication).container
        val repository = appContainer.repository
        val registerServiceUseCase = appContainer.registerServiceAndGenerateLedgerUseCase
        val settleCommissionUseCase = appContainer.settleEmployeeCommissionUseCase
        val supabaseAuthRepository = appContainer.supabaseAuthRepository

        // Create ViewModel using Factory
        viewModel = ViewModelProvider(
            this,
            HamrahanViewModelFactory(repository, registerServiceUseCase, settleCommissionUseCase, supabaseAuthRepository)
        )[HamrahanViewModel::class.java]

        // Handle initial deep link if any
        intent?.getStringExtra("ROUTE_TARGET")?.let { target ->
            viewModel.handleDeepLink(target)
        }

        val prefs = getSharedPreferences("crash_prefs", android.content.Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        if (lastCrash != null) {
            prefs.edit().remove("last_crash").apply()
        }

        setContent {
            var crashToShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(lastCrash) }
            if (crashToShow != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { crashToShow = null },
                    confirmButton = {
                        androidx.compose.material3.Button(onClick = { crashToShow = null }) {
                            androidx.compose.material3.Text("Close")
                        }
                    },
                    title = { androidx.compose.material3.Text("Crash Detected") },
                    text = {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = androidx.compose.ui.Modifier.heightIn(max = 400.dp)) {
                            item {
                                androidx.compose.material3.Text(
                                    text = crashToShow ?: "",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                )
            }

            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val companyName by viewModel.companyName.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode) {
                // Force Right-to-Left layout for Farsi localization
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HamrahanAppContent(viewModel = viewModel, companyName = companyName)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("ROUTE_TARGET")?.let { target ->
            viewModel.handleDeepLink(target)
        }
    }
}

enum class HamrahanScreen(val title: String, val icon: ImageVector, val route: String) {
    DASHBOARD("داشبورد مدیریتی", Icons.Default.Dashboard, "com.example.ui.navigation.Dashboard"),
    PATIENTS("پرونده بیماران", Icons.Default.People, "com.example.ui.navigation.PatientList"),
    EMPLOYEES("مدیریت همکاران", Icons.Default.Badge, "com.example.ui.navigation.PersonnelList"),
    SERVICES("کاتالوگ خدمات", Icons.Default.MedicalServices, "com.example.ui.navigation.ServiceCatalog"),
    REGISTRATION("ثبت خدمت جدید", Icons.Default.PostAdd, "com.example.ui.navigation.ServiceRegistration"),
    ACCOUNTING("دفتر کل مالی", Icons.Default.AccountBalance, "com.example.ui.navigation.FinancialLedgers"),
    EXPENSES("مدیریت هزینه‌ها", Icons.Default.TrendingDown, "com.example.ui.navigation.Expenses"),
    COMMISSIONS("سیستم کارمزد", Icons.Default.Payments, "com.example.ui.navigation.Commissions"),
    REPORTS("گزارشات سیستم", Icons.Default.Summarize, "com.example.ui.navigation.Reports"),
    SEARCH("جستجوی سراسری", Icons.Default.Search, "com.example.ui.navigation.Search"),
    SETTINGS("تنظیمات سیستم", Icons.Default.Settings, "com.example.ui.navigation.Settings"),
    PROFILE("شناسنامه مرکز", Icons.Default.Business, "com.example.ui.navigation.CompanyProfile")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HamrahanAppContent(
    viewModel: HamrahanViewModel,
    companyName: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceStatus by viewModel.activeDeviceStatus.collectAsState()
    val companyIsSetup by viewModel.companyIsSetup.collectAsState()
    val hasBeenApproved by viewModel.hasBeenApproved.collectAsState()
    val userRole by viewModel.currentUserRole.collectAsState()

    val isMotherAccount = userRole == "Mother Account"
    val effectiveDeviceStatus = if (isMotherAccount) "Active" else deviceStatus
    val effectiveApproved = if (isMotherAccount) true else hasBeenApproved

    val syncing by viewModel.syncing.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    LaunchedEffect(syncing) {
        if (!syncing) {
            viewModel.runAlertDiagnostics(context)
        }
    }

    if (companyIsSetup) {
        if (effectiveDeviceStatus == "Revoked") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("revoked_device_alert_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "دسترسی دستگاه لغو گردید",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "بر اساس سیاست‌های امنیتی و تصمیم سرپرست مرکز (Mother Account)، دسترسی این دستگاه به دیتابیس لغو شده است.\nجهت فعال‌سازی مجدد یا پیگیری، با مدیریت ارشد مرکز تماس حاصل فرمایید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
            return
        }

        if (effectiveDeviceStatus == "Pending" && !effectiveApproved) {
            val companyName by viewModel.companyNameState.collectAsState()
            val lastSyncTime by viewModel.lastSyncTime.collectAsState()
            val deviceId by viewModel.activeDeviceId.collectAsState()
            val deviceName by viewModel.activeDeviceName.collectAsState()

            LaunchedEffect(deviceId) {
                android.util.Log.i(
                    "PAIRING_RUNTIME",
                    "[PAIRING_RUNTIME] [UI_REQUEST_STATUS] deviceId=$deviceId status=Pending rendered=true"
                )
            }

            val formattedSyncTime = if (lastSyncTime > 0) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = lastSyncTime }
                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val minute = cal.get(java.util.Calendar.MINUTE)
                val second = cal.get(java.util.Calendar.SECOND)
                String.format("%02d:%02d:%02d", hour, minute, second)
            } else {
                "در حال همگام‌سازی اولیه..."
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("pending_device_alert_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A), // Green color for successful connection
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "درخواست اتصال ارسال شد",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF16A34A)
                        )
                        Text(
                            text = "درخواست اتصال شما برای دفتر ارسال شده است.\nلطفاً منتظر تأیید مدیر دفتر بمانید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Meta details
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("نام مرکز:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(companyName.ifEmpty { "در حال دریافت اطلاعات مرکز..." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("شناسه درخواست (ID):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(deviceId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("نام دستگاه:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(deviceName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("وضعیت فعلی:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFEAB308), CircleShape))
                                    Text("در انتظار تأیید مدیر دفتر", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("آخرین همگام‌سازی:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(formattedSyncTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.triggerSync() },
                            modifier = Modifier.fillMaxWidth().testTag("refresh_status_btn")
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("بررسی مجدد وضعیت دسترسی")
                        }
                    }
                }
            }
            return
        }

        if (effectiveDeviceStatus == "Rejected") {
            val companyName by viewModel.companyNameState.collectAsState()
            val deviceId by viewModel.activeDeviceId.collectAsState()
            val deviceName by viewModel.activeDeviceName.collectAsState()

            LaunchedEffect(deviceId) {
                android.util.Log.i(
                    "PAIRING_RUNTIME",
                    "[PAIRING_RUNTIME] [UI_REQUEST_STATUS] deviceId=$deviceId status=Rejected rendered=true"
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("rejected_device_alert_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "درخواست اتصال رد شد",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "مدیر دفتر درخواست اتصال این دستگاه را تأیید نکرد.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("نام مرکز:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(companyName.ifEmpty { "در حال دریافت اطلاعات مرکز..." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("شناسه دستگاه (ID):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(deviceId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("نام دستگاه:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(deviceName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("وضعیت فعلی:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                    Text("رد شده", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.resetDeviceJoinState() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("retry_join_btn")
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تلاش مجدد برای اتصال")
                        }
                    }
                }
            }
            return
        }
    }

    LaunchedEffect(viewModel.permissionError) {
        viewModel.permissionError.collect { errorMsg ->
            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val activeCompanyId by viewModel.companyId.collectAsState()

    var dismissedDeviceIds by remember { mutableStateOf(setOf<String>()) }
    var eventPairingDevice by remember { mutableStateOf<com.example.data.ConnectedDevice?>(null) }

    val isMotherOrAdmin = effectiveDeviceStatus == "Active" &&
        (userRole == "Mother Account" || userRole == "Admin" || userRole == "GM" || userRole == "General Manager")

    val pendingDevices = remember(connectedDevices, activeCompanyId) {
        connectedDevices.filter { device ->
            device.status.equals("Pending", ignoreCase = true) &&
            (activeCompanyId.isBlank() || device.companyId.isBlank() || device.companyId == activeCompanyId)
        }
    }

    LaunchedEffect(isMotherOrAdmin) {
        if (isMotherOrAdmin) {
            viewModel.pairingApprovalEvents.collect { device ->
                android.util.Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Event received in UI deviceId=${device.deviceId}")
                eventPairingDevice = device
                dismissedDeviceIds = dismissedDeviceIds - device.deviceId
            }
        }
    }

    val activePendingDevice = remember(pendingDevices, eventPairingDevice, dismissedDeviceIds, isMotherOrAdmin) {
        if (!isMotherOrAdmin) null
        else {
            val eventDev = eventPairingDevice?.takeIf { ev ->
                pendingDevices.any { it.deviceId == ev.deviceId } && ev.deviceId !in dismissedDeviceIds
            }
            eventDev ?: pendingDevices.firstOrNull { it.deviceId !in dismissedDeviceIds }
        }
    }

    LaunchedEffect(connectedDevices, pendingDevices, activePendingDevice, isMotherOrAdmin) {
        android.util.Log.d("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [UI_STATE] connectedDevices=${connectedDevices.size} pendingDevices=${pendingDevices.map { it.deviceId }} activePendingDevice=${activePendingDevice?.deviceId} isMotherOrAdmin=$isMotherOrAdmin")
    }

    activePendingDevice?.let { device ->
        LaunchedEffect(device.deviceId) {
            android.util.Log.d("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [DIALOG_RENDER] Showing PairingApprovalDialog for deviceId=${device.deviceId} deviceName=${device.deviceName}")
        }
        PairingApprovalDialog(
            device = device,
            onApprove = {
                android.util.Log.d("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [APPROVAL_CLICKED] Approve clicked deviceId=${device.deviceId}")
                android.util.Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Approve clicked deviceId=${device.deviceId}")
                viewModel.approveDeviceAccess(device.deviceId)
                if (eventPairingDevice?.deviceId == device.deviceId) {
                    eventPairingDevice = null
                }
            },
            onReject = {
                android.util.Log.d("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [REJECT_CLICKED] Reject clicked deviceId=${device.deviceId}")
                android.util.Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Reject clicked deviceId=${device.deviceId}")
                viewModel.rejectDeviceAccess(device.deviceId)
                if (eventPairingDevice?.deviceId == device.deviceId) {
                    eventPairingDevice = null
                }
            },
            onDismiss = {
                android.util.Log.d("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [DIALOG_DISMISSED] Dialog dismissed deviceId=${device.deviceId}")
                android.util.Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Dialog dismissed deviceId=${device.deviceId}")
                dismissedDeviceIds = dismissedDeviceIds + device.deviceId
                if (eventPairingDevice?.deviceId == device.deviceId) {
                    eventPairingDevice = null
                }
            }
        )
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val currentScreen = remember(currentRoute) {
        HamrahanScreen.values().find {
            currentRoute != null && currentRoute.contains(it.route.substringAfterLast("."))
        } ?: HamrahanScreen.DASHBOARD
    }

    // Observe and perform dynamic deep-link routing on Alert Notification clicks
    val navigateTarget by viewModel.navigateToScreen.collectAsState()
    LaunchedEffect(navigateTarget) {
        navigateTarget?.let { target ->
            val mapped = when (target.lowercase().trim()) {
                "dashboard" -> Dashboard
                "patient", "patients" -> PatientList
                "employee", "employees" -> PersonnelList
                "service", "services" -> ServiceCatalog
                "register", "registration" -> ServiceRegistration
                "accounting" -> FinancialLedgers
                "expense", "expenses" -> Expenses
                "commission", "commissions" -> Commissions
                "report", "reports" -> Reports
                "search" -> Search
                "settings" -> Settings
                "profile", "companyprofile" -> CompanyProfile
                else -> null
            }
            if (mapped != null) {
                navController.navigate(mapped) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(Dashboard) { saveState = true }
                }
            }
            viewModel.clearNavigateToScreen()
        }
    }

    com.example.ui.components.EnterpriseScaffold(
        currentScreen = currentScreen,
        onScreenSelected = { screen ->
            val routeObj: Any = when (screen) {
                HamrahanScreen.DASHBOARD -> Dashboard
                HamrahanScreen.PATIENTS -> PatientList
                HamrahanScreen.EMPLOYEES -> PersonnelList
                HamrahanScreen.SERVICES -> ServiceCatalog
                HamrahanScreen.REGISTRATION -> ServiceRegistration
                HamrahanScreen.ACCOUNTING -> FinancialLedgers
                HamrahanScreen.EXPENSES -> Expenses
                HamrahanScreen.COMMISSIONS -> Commissions
                HamrahanScreen.REPORTS -> Reports
                HamrahanScreen.SEARCH -> Search
                HamrahanScreen.SETTINGS -> Settings
                HamrahanScreen.PROFILE -> CompanyProfile
            }
            navController.navigate(routeObj) {
                launchSingleTop = true
                restoreState = true
                popUpTo(Dashboard) { saveState = true }
            }
        },
        companyName = companyName,
        isOnline = isOnline,
        syncing = syncing,
        lastSyncTime = lastSyncTime,
        userRole = userRole,
        onSyncClick = { viewModel.triggerSync() }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            MainNavGraph(navController = navController, viewModel = viewModel)
        }
    }
}

@Composable
fun MainNavGraph(navController: androidx.navigation.NavHostController, viewModel: HamrahanViewModel) {
    NavHost(
        navController = navController,
        startDestination = Dashboard
    ) {
        composable<Dashboard> {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToRegister = { 
                    navController.navigate(ServiceRegistration) 
                }
            )
        }
        composable<PatientList> { PatientScreen(viewModel = viewModel) }
        composable<PersonnelList> { EmployeeScreen(viewModel = viewModel) }
        composable<ServiceCatalog> { ServiceScreen(viewModel = viewModel) }
        composable<ServiceRegistration> { RegistrationScreen(viewModel = viewModel) }
        composable<FinancialLedgers> { AccountingScreen(viewModel = viewModel) }
        composable<Expenses> { ExpensesScreen(viewModel = viewModel) }
        composable<Commissions> { CommissionScreen(viewModel = viewModel) }
        composable<Reports> { ReportScreen(viewModel = viewModel) }
        composable<Search> { SearchScreen(viewModel = viewModel) }
        composable<Settings> { SettingsScreen(viewModel = viewModel) }
        composable<CompanyProfile> { CompanyProfileScreen(viewModel = viewModel) }
        
        composable<SyncManagement> {
            // Placeholder for sync management screen if needed
        }
        composable<EmployeeLedger> { backStackEntry ->
            val route = backStackEntry.toRoute<EmployeeLedger>()
            // Example of passing args to a theoretical destination
            // EmployeeLedgerScreen(viewModel = viewModel, employeeId = route.employeeId)
        }
        composable<ServiceRegistrationDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceRegistrationDetail>()
            // ServiceRegistrationDetailScreen(viewModel = viewModel, registrationId = route.registrationId)
        }
    }
}

@Composable
fun ProtectedScreenGuard(
    screenTitle: String,
    onNavigateToProfile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .testTag("protected_screen_guard_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(36.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                Text(
                    text = "دسترسی به بخش $screenTitle محدود شده است",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "این بخش حاوی اطلاعات حساس و خصوصی مرکز همکاران سلامت می‌باشد. جهت مشاهده اطلاعات و ثبت داده‌ها، ابتدا باید مرکز خود را راه‌اندازی کنید یا درخواست اتصال به یک مرکز ثبت‌شده را ارسال نمایید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onNavigateToProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("guard_go_to_profile_btn")
                ) {
                    Icon(Icons.Default.Business, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("راه‌اندازی شناسنامه مرکز / عضویت")
                }
            }
        }
    }
}

@Composable
fun PairingApprovalDialog(
    device: com.example.data.ConnectedDevice,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(device.deviceId) {
        android.util.Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Dialog displayed deviceId=${device.deviceId}")
    }

    AlertDialog(
        onDismissRequest = {
            if (!isProcessing) {
                android.util.Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Dialog dismissed deviceId=${device.deviceId}")
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "درخواست اتصال دستگاه جدید",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "یک دستگاه جدید درخواست اتصال به دفتر شما را دارد:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("نام دستگاه:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text(device.deviceName.ifBlank { "نامشخص" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("نوع دستگاه:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text(device.deviceType.ifBlank { "نامشخص" }, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("نقش درخواستی:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text(
                                text = when (device.requestedRole) {
                                    "Staff" -> "کارمند / پرسنل"
                                    "Admin" -> "مدیر سیستم"
                                    else -> device.requestedRole.ifBlank { "پرسنل" }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isProcessing,
                onClick = {
                    if (!isProcessing) {
                        isProcessing = true
                        onApprove()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("تأیید دسترسی", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                enabled = !isProcessing,
                onClick = {
                    if (!isProcessing) {
                        isProcessing = true
                        onReject()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("رد درخواست", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
