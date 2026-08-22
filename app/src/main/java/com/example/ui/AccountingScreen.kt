package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Cashbox
import com.example.data.FinancialTransaction
import com.example.data.JournalEntry
import com.example.data.FinancialEditHistory
import com.example.ui.components.EnterpriseCard
import com.example.ui.components.KPICard
import com.example.ui.components.StatusBadge
import com.example.ui.components.EnterpriseStatusType
import com.example.ui.components.SectionHeader
import com.example.ui.components.SearchToolbar
import com.example.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingScreen(viewModel: HamrahanViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val cashboxes by viewModel.cashboxes.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Transactions, 1: Cashboxes, 2: Maintenance
    if (!isDeveloperMode && selectedTab == 2) {
        selectedTab = 0
    }
    var showAddTxDialog by remember { mutableStateOf(false) }
    var showAddCashboxDialog by remember { mutableStateOf(false) }

    // Search, Filter & Sort States
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("همه") } // "همه", "درآمد", "هزینه", "ابطال شده"
    var selectedSortOrder by remember { mutableStateOf("جدیدترین") } // "جدیدترین", "قدیمی‌ترین", "بیشترین مبلغ", "کمترین مبلغ"
    var selectedTransaction by remember { mutableStateOf<FinancialTransaction?>(null) }

    val currentDeepLink by viewModel.currentDeepLink.collectAsState()
    LaunchedEffect(currentDeepLink) {
        val link = currentDeepLink
        if (link != null && (link.screen == "accounting" || link.screen == "accountingScreen") && link.tab == "add") {
            showAddTxDialog = true
            viewModel.clearDeepLink()
        }
    }

    // Financial KPI Summary Metrics (Presentation layer calculations only)
    val totalIncome = remember(transactions) {
        transactions.filter { it.isCleared && it.type == "درآمد" }.sumOf { it.amount }
    }
    val totalExpense = remember(transactions) {
        transactions.filter { it.isCleared && it.type == "هزینه" }.sumOf { it.amount }
    }
    val netBalance = remember(totalIncome, totalExpense) {
        totalIncome - totalExpense
    }
    val totalCashboxBalance = remember(cashboxes) {
        cashboxes.sumOf { it.balance }
    }

    // Filtered and Sorted Transactions
    val filteredTransactions = remember(transactions, searchQuery, selectedTypeFilter, selectedSortOrder) {
        transactions.filter { tx ->
            val matchesSearch = searchQuery.isBlank() ||
                    tx.description.contains(searchQuery, ignoreCase = true) ||
                    tx.category.contains(searchQuery, ignoreCase = true) ||
                    tx.paymentMethod.contains(searchQuery, ignoreCase = true) ||
                    tx.origin.contains(searchQuery, ignoreCase = true) ||
                    tx.referenceId?.toString()?.contains(searchQuery) == true

            val matchesType = when (selectedTypeFilter) {
                "درآمد" -> tx.type == "درآمد" && tx.isCleared
                "هزینه" -> tx.type == "هزینه" && tx.isCleared
                "ابطال شده" -> !tx.isCleared
                else -> true
            }
            matchesSearch && matchesType
        }.let { list ->
            when (selectedSortOrder) {
                "قدیمی‌ترین" -> list.sortedBy { it.date }
                "بیشترین مبلغ" -> list.sortedByDescending { it.amount }
                "کمترین مبلغ" -> list.sortedBy { it.amount }
                else -> list.sortedByDescending { it.date }
            }
        }
    }

    // Auto-select first transaction for workspace on wide screen if null
    LaunchedEffect(filteredTransactions) {
        if (selectedTransaction == null || filteredTransactions.none { it.id == selectedTransaction?.id }) {
            selectedTransaction = filteredTransactions.firstOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("میز کار مالی و دفتر کل حسابداری", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("مدیریت تراکنش‌ها، اسناد مالی و صندوق‌های مرکز", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (selectedTab == 0 || selectedTab == 1) {
                        Button(
                            onClick = { showAddTxDialog = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("add_transaction_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ثبت تراکنش جدید", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else if (selectedTab == 2) {
                        Button(
                            onClick = { showAddCashboxDialog = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("add_cashbox_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ایجاد حساب/صندوق", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            )
        },
        modifier = Modifier.testTag("accounting_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. Financial KPI Summary Bar ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KPICard(
                    title = "کل درآمدها",
                    value = totalIncome.formatPrice(currency),
                    icon = Icons.Default.TrendingUp,
                    iconTint = Color(0xFF15803D),
                    iconContainerColor = Color(0xFFDCFCE7),
                    modifier = Modifier.weight(1f)
                )

                KPICard(
                    title = "کل هزینه‌ها",
                    value = totalExpense.formatPrice(currency),
                    icon = Icons.Default.TrendingDown,
                    iconTint = Color(0xFFB91C1C),
                    iconContainerColor = Color(0xFFFEE2E2),
                    modifier = Modifier.weight(1f)
                )

                KPICard(
                    title = "تراز کل دفتر",
                    value = netBalance.formatPrice(currency),
                    icon = Icons.Default.AccountBalanceWallet,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                )

                KPICard(
                    title = "موجودی صندوق‌ها",
                    value = totalCashboxBalance.formatPrice(currency),
                    icon = Icons.Default.AccountBalance,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }

            // --- 2. Tab Row Selector ---
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("دفتر کل و اسناد دوبل", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("ریز تراکنش‌های مالی", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("حساب‌های بانکی و صندوق‌ها", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) }
                )
                if (isDeveloperMode) {
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("نگهداری و خودترمیمی", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Build, contentDescription = null) }
                    )
                }
            }

            // --- 3. Main Content Body based on Selected Tab ---
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isExpandedWidth = maxWidth >= 600.dp

                if (selectedTab == 0) {
                    // --- DOUBLE ENTRY GENERAL LEDGER TAB ---
                    JournalLedgerView(viewModel = viewModel)
                } else if (selectedTab == 1) {
                    // --- TRANSACTIONS TAB ---
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Search & Filter Toolbar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchToolbar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = "جستجو در شرح، بابت، دسته، روش پرداخت و شماره مرجع...",
                                modifier = Modifier.weight(1f)
                            )

                            // Sort Order Selector
                            var showSortMenu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedIconButton(
                                    onClick = { showSortMenu = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Sort, contentDescription = "مرتب‌سازی")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    val sortOptions = listOf("جدیدترین", "قدیمی‌ترین", "بیشترین مبلغ", "کمترین مبلغ")
                                    sortOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt) },
                                            onClick = { selectedSortOrder = opt; showSortMenu = false }
                                        )
                                    }
                                }
                            }
                        }

                        // Filter Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val filterList = listOf("همه", "درآمد", "هزینه", "ابطال شده")
                            filterList.forEach { filterName ->
                                FilterChip(
                                    selected = selectedTypeFilter == filterName,
                                    onClick = { selectedTypeFilter = filterName },
                                    label = { Text(filterName, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }

                        // Split Screen Master-Detail layout for wide screens vs Single Column for Mobile
                        if (isExpandedWidth) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Master Transaction List (Width = 360dp)
                                Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                                    if (filteredTransactions.isEmpty()) {
                                        EmptyState(
                                            icon = Icons.Default.ReceiptLong,
                                            message = "هیچ تراکنشی یافت نشد",
                                            description = "با تغییر فیلترها یا عبارت جستجو تراکنش‌های بیشتری را بررسی کنید."
                                        )
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(filteredTransactions, key = { tx -> tx.id }) { tx ->
                                                val isSelected = selectedTransaction?.id == tx.id
                                                TransactionMasterItem(
                                                    tx = tx,
                                                    currency = currency,
                                                    isSelected = isSelected,
                                                    onClick = { selectedTransaction = tx }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Detail Workspace Panel (Remaining Width)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    selectedTransaction?.let { tx ->
                                        TransactionDetailWorkspace(
                                            tx = tx,
                                            currency = currency,
                                            cashboxes = cashboxes,
                                            onVoid = { viewModel.updateFinancialTransaction(tx.copy(isCleared = false)) },
                                            onRestore = { viewModel.updateFinancialTransaction(tx.copy(isCleared = true)) },
                                            onDelete = {
                                                viewModel.deleteFinancialTransaction(tx)
                                                selectedTransaction = null
                                            }
                                        )
                                    } ?: run {
                                        EmptyState(
                                            icon = Icons.Default.Info,
                                            message = "یک تراکنش را برای مشاهده جزئیات انتخاب کنید",
                                            description = "از لیست سمت راست روی هر تراکنش مالی کلیک کنید تا شناسنامه و سابقه کامل آن نمایش داده شود."
                                        )
                                    }
                                }
                            }
                        } else {
                            // Compact Screen Layout (Single Column)
                            if (filteredTransactions.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Default.ReceiptLong,
                                    message = "هیچ تراکنشی یافت نشد",
                                    description = "با تغییر فیلترها یا عبارت جستجو تراکنش‌های بیشتری را بررسی کنید."
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filteredTransactions, key = { tx -> tx.id }) { tx ->
                                        TransactionItem(
                                            tx = tx,
                                            currency = currency,
                                            onVoid = { viewModel.updateFinancialTransaction(tx.copy(isCleared = false)) },
                                            onRestore = { viewModel.updateFinancialTransaction(tx.copy(isCleared = true)) },
                                            onDelete = { viewModel.deleteFinancialTransaction(tx) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTab == 2) {
                    // --- CASHBOXES TAB ---
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(
                            title = "حساب‌های بانکی و صندوق‌های مرکز",
                            subtitle = "مدیریت موجودی نقدینگی و درگاه‌های واریز/برداشت",
                            icon = Icons.Default.AccountBalance,
                            badgeCount = cashboxes.size
                        )

                        if (cashboxes.isEmpty()) {
                            EmptyState(
                                icon = Icons.Default.AccountBalance,
                                message = "هیچ صندوق یا حساب بانکی تعریف نشده است",
                                description = "برای تفکیک واریزی‌ها و پرداختی‌های روزانه، یک صندوق یا حساب جدید اضافه کنید."
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(cashboxes, key = { cb -> cb.id }) { cb ->
                                    CashboxItem(
                                        cb = cb,
                                        currency = currency,
                                        onDelete = { viewModel.deleteCashbox(cb) }
                                    )
                                }
                            }
                        }
                    }
                } else if (selectedTab == 3 && isDeveloperMode) {
                    // --- MAINTENANCE TOOLS TAB ---
                    MaintenanceToolsView(viewModel = viewModel)
                }
            }
        }
    }

    // --- Add Transaction Dialog ---
    if (showAddTxDialog) {
        AddTransactionDialog(
            cashboxes = cashboxes,
            onDismiss = { showAddTxDialog = false },
            onSave = { type, category, amount, desc, method, cbId, origin, reason, creator, time ->
                viewModel.addFinancialTransaction(type, category, amount, desc, method, cbId, origin, reason, creator, time)
                showAddTxDialog = false
            }
        )
    }

    // --- Add Cashbox Dialog ---
    if (showAddCashboxDialog) {
        AddCashboxDialog(
            onDismiss = { showAddCashboxDialog = false },
            onSave = { name, type, num, bal ->
                val newCb = Cashbox(name = name, type = type, accountNumber = num, balance = bal)
                viewModel.saveCashbox(newCb)
                showAddCashboxDialog = false
            }
        )
    }
}

/**
 * Compact item in Master List for Expanded Workspace
 */
@Composable
fun TransactionMasterItem(
    tx: FinancialTransaction,
    currency: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isIncome = tx.type == "درآمد"
    val isVoided = !tx.isCleared

    EnterpriseCard(
        onClick = onClick,
        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else if (isVoided) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isVoided) Color.LightGray.copy(alpha = 0.2f)
                            else if (isIncome) Color(0xFFDCFCE7)
                            else Color(0xFFFEE2E2)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVoided) Icons.Default.Block else if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isVoided) Color.Gray else if (isIncome) Color(0xFF15803D) else Color(0xFFB91C1C),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.description,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isVoided) Color.Gray else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${tx.category} • ${tx.date.formatDate()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isIncome) "+" else "-") + tx.amount.formatPrice(currency),
                    color = if (isVoided) Color.Gray else if (isIncome) Color(0xFF10B981) else Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                StatusBadge(
                    text = if (isVoided) "ابطال شده" else "تایید شده",
                    statusType = if (isVoided) EnterpriseStatusType.ERROR else EnterpriseStatusType.ACTIVE
                )
            }
        }
    }
}

/**
 * Detailed Transaction Workspace Panel for Wide / Tablet Screens
 */
@Composable
fun TransactionDetailWorkspace(
    tx: FinancialTransaction,
    currency: String,
    cashboxes: List<Cashbox>,
    onVoid: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncome = tx.type == "درآمد"
    val isVoided = !tx.isCleared

    EnterpriseCard(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Workspace Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    title = "شناسنامه سند مالی #${tx.id}",
                    subtitle = "جزئیات حسابداری و کد پیگیری سیستم",
                    icon = Icons.Default.ReceiptLong
                )
                StatusBadge(
                    text = if (isVoided) "ابطال شده" else "ثبت نهایی / معتبر",
                    statusType = if (isVoided) EnterpriseStatusType.ERROR else EnterpriseStatusType.ACTIVE
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Primary Amount & Type Visualizer Hero Card
            EnterpriseCard(
                containerColor = if (isVoided) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else if (isIncome) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                borderColor = if (isVoided) Color.LightGray else if (isIncome) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                contentPadding = PaddingValues(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isIncome) "مبلغ واریزی (درآمد)" else "مبلغ پرداختی (هزینه)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = (if (isIncome) "+" else "-") + tx.amount.formatPrice(currency),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isVoided) Color.Gray else if (isIncome) Color(0xFF15803D) else Color(0xFFB91C1C)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isIncome) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = if (isIncome) Color(0xFF15803D) else Color(0xFFB91C1C),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Data Table Grid
            Text(
                text = "اطلاعات ثبت و حسابداری",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            EnterpriseCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailDataRow(label = "شرح سند / موضوع", value = tx.description)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailDataRow(label = "دسته‌بندی مالی", value = tx.category)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailDataRow(label = "تاریخ ثبت تراکنش", value = tx.date.formatDate())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailDataRow(label = "منبع تراکنش (Origin)", value = tx.origin)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailDataRow(label = "روش پرداخت / جابجایی", value = tx.paymentMethod)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailDataRow(label = "شناسه مرجع (Reference ID)", value = tx.referenceId?.toString() ?: "ثبت دستی / بدون مرجع")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailDataRow(label = "ثبت کننده تراکنش", value = tx.creatorName ?: "سیستم")
                    if (!tx.manualReason.isNullOrBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        DetailDataRow(label = "علت ثبت / اصلاح", value = tx.manualReason)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isVoided) {
                    Button(
                        onClick = onRestore,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("برگرداندن و تایید سند", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onVoid,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ابطال سند مالی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حذف تراکنش", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun DetailDataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun TransactionItem(
    tx: FinancialTransaction,
    currency: String,
    onVoid: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isIncome = tx.type == "درآمد"
    val isVoided = !tx.isCleared

    EnterpriseCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        containerColor = if (isVoided) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
        borderColor = if (isVoided) Color.LightGray.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isVoided) Color.LightGray.copy(alpha = 0.2f)
                                else if (isIncome) Color(0xFFDCFCE7)
                                else Color(0xFFFEE2E2)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVoided) Icons.Default.Block else if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = if (isVoided) Color.Gray else if (isIncome) Color(0xFF15803D) else Color(0xFFB91C1C),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = tx.description,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isVoided) Color.Gray else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (isVoided) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tx.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = tx.date.formatDate(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isVoided) {
                                StatusBadge(text = "ابطال شده", statusType = EnterpriseStatusType.ERROR)
                            }
                        }
                    }
                }

                Text(
                    text = (if (isIncome) "+" else "-") + tx.amount.formatPrice(currency),
                    color = if (isVoided) Color.Gray else if (isIncome) Color(0xFF10B981) else Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (isVoided) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isVoided) {
                        Button(
                            onClick = onRestore,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("برگرداندن سند", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onVoid,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("ابطال سند مالی", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("حذف تراکنش", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CashboxItem(
    cb: Cashbox,
    currency: String,
    onDelete: () -> Unit
) {
    EnterpriseCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column {
                    Text(
                        text = cb.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (cb.accountNumber.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "شماره حساب: ${cb.accountNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = cb.balance.formatPrice(currency),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف حساب",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    cashboxes: List<Cashbox>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, Int?, String, String, String, Long) -> Unit
) {
    var type by remember { mutableStateOf("درآمد") } // "درآمد" / "هزینه"
    var origin by remember { mutableStateOf("Manual Entry") } // "Service", "Expense", "Salary", "Manual Entry", "Adjustment"
    var category by remember { mutableStateOf("سایر") }
    var amountString by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("بانکی / آنلاین") }
    var selectedCashbox by remember { mutableStateOf<Cashbox?>(cashboxes.firstOrNull()) }
    
    // New fields required for Manual Entry / Adjustment
    var reason by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("مدیر سیستم") }
    var referenceIdString by remember { mutableStateOf("") }
    
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ثبت تراکنش حسابداری دستی",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نوع تراکنش:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(16.dp))
                    FilterChip(
                        selected = type == "درآمد",
                        onClick = { type = "درآمد"; category = "سایر درآمدها" },
                        label = { Text("درآمد (واریزی)") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = type == "هزینه",
                        onClick = { type = "هزینه"; category = "اجاره دفتر" },
                        label = { Text("هزینه (برداشت)") }
                    )
                }

                // Origin selector (labeling of transaction origin)
                Text("منبع تراکنش (Origin):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val origins = listOf(
                        "Manual Entry" to "ثبت دستی",
                        "Adjustment" to "اصلاحی",
                        "Service" to "خدمات سلامت (Service)",
                        "Expense" to "هزینه (Expense)",
                        "Salary" to "حقوق همکار (Salary)"
                    )
                    origins.forEach { (orgKey, orgLabel) ->
                        FilterChip(
                            selected = origin == orgKey,
                            onClick = { 
                                origin = orgKey 
                                if (orgKey == "Manual Entry" && reason.isBlank()) {
                                    reason = "ثبت دستی تراکنش"
                                } else if (orgKey == "Adjustment" && reason.isBlank()) {
                                    reason = "تراکنش اصلاحی"
                                }
                            },
                            label = { Text(orgLabel, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }

                // Warn when creating manual transactions without reference
                val refId = referenceIdString.toIntOrNull()
                val isManualOrAdjustment = origin == "Manual Entry" || origin == "Adjustment"
                if (isManualOrAdjustment && refId == null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "هشدار: ایجاد تراکنش دستی بدون شناسه سند مرجع فاقد ردیابی کامل است و به عنوان «تراکنش بدون مرجع» اسکن خواهد شد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Required inputs based on manual/adjustment
                if (isManualOrAdjustment) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("علت ثبت دستی/اصلاحی (اجباری)") },
                        isError = hasError && reason.isBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("tx_reason_input")
                    )

                    OutlinedTextField(
                        value = creator,
                        onValueChange = { creator = it },
                        label = { Text("ثبت کننده تراکنش (اجباری)") },
                        isError = hasError && creator.isBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("tx_creator_input")
                    )
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("دسته بندی (مانند: اجاره، آب و برق، ملزومات)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountString,
                    onValueChange = { amountString = it },
                    label = { Text("مبلغ تراکنش") },
                    isError = hasError && amountString.isBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("tx_amount_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات تراکنش (بابت چه موضوعی - اجباری)") },
                    isError = hasError && description.isBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("tx_desc_input")
                )

                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = { paymentMethod = it },
                    label = { Text("روش جابجایی پول (کارت به کارت، حواله...)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = referenceIdString,
                    onValueChange = { referenceIdString = it },
                    label = { Text(if (isManualOrAdjustment) "شناسه سند مرجع (اختیاری)" else "شناسه سند مرجع (برای این منبع اجباری است)") },
                    isError = hasError && !isManualOrAdjustment && refId == null,
                    modifier = Modifier.fillMaxWidth().testTag("tx_ref_id_input")
                )

                // Cashbox connection
                if (cashboxes.isNotEmpty()) {
                    Column {
                        Text("تاثیر بر موجودی صندوق / بانک:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        ScrollableTabRow(
                            selectedTabIndex = cashboxes.indexOf(selectedCashbox).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            divider = {}
                        ) {
                            cashboxes.forEach { cb ->
                                Tab(
                                    selected = selectedCashbox == cb,
                                    onClick = { selectedCashbox = cb },
                                    text = { Text(cb.name) }
                                )
                            }
                        }
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            errorMessage = ""
                            val amt = amountString.toDoubleOrNull() ?: 0.0
                            val finalRefId = referenceIdString.toIntOrNull()
                            
                            if (amountString.isBlank() || description.isBlank()) {
                                hasError = true
                                errorMessage = "مبلغ و توضیحات تراکنش نمی‌توانند خالی باشند."
                            } else if (isManualOrAdjustment && (reason.isBlank() || creator.isBlank())) {
                                hasError = true
                                errorMessage = "برای تراکنش‌های دستی/اصلاحی، علت ثبت و نام ثبت‌کننده الزامی است."
                            } else if (!isManualOrAdjustment && finalRefId == null) {
                                hasError = true
                                errorMessage = "برای تراکنش‌های غیردستی، وارد کردن شناسه سند مرجع عددی معتبر الزامی است."
                            } else {
                                onSave(
                                    type,
                                    category,
                                    amt,
                                    description,
                                    paymentMethod,
                                    selectedCashbox?.id,
                                    origin,
                                    if (isManualOrAdjustment) reason else "",
                                    if (isManualOrAdjustment) creator else "سیستم",
                                    System.currentTimeMillis()
                                )
                            }
                        },
                        modifier = Modifier.testTag("save_transaction_confirm_button")
                    ) {
                        Text("ذخیره تراکنش")
                    }
                }
            }
        }
    }
}

@Composable
fun AddCashboxDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("حساب بانکی") } // "صندوق" / "حساب بانکی"
    var accountNumber by remember { mutableStateOf("") }
    var initialBalanceString by remember { mutableStateOf("") }

    var hasError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تعریف صندوق یا حساب بانکی جدید",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام حساب یا صندوق (مثلا: کارت ملی شرکت)") },
                    isError = hasError && name.isBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("cashbox_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نوع:")
                    Spacer(modifier = Modifier.width(16.dp))
                    FilterChip(
                        selected = type == "صندوق",
                        onClick = { type = "صندوق" },
                        label = { Text("صندوق فیزیکی دفتر") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = type == "حساب بانکی",
                        onClick = { type = "حساب بانکی" },
                        label = { Text("حساب بانکی دیجیتال") }
                    )
                }

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text("شماره حساب یا شماره کارت") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = initialBalanceString,
                    onValueChange = { initialBalanceString = it },
                    label = { Text("موجودی اولیه") },
                    isError = hasError && initialBalanceString.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || initialBalanceString.isBlank()) {
                                hasError = true
                            } else {
                                onSave(
                                    name, type, accountNumber,
                                    initialBalanceString.toDoubleOrNull() ?: 0.0
                                )
                            }
                        },
                        modifier = Modifier.testTag("save_cashbox_confirm_button")
                    ) {
                        Text("ایجاد حساب")
                    }
                }
            }
        }
    }
}


@Composable
fun MaintenanceToolsView(viewModel: HamrahanViewModel) {
    val userRole by viewModel.currentUserRole.collectAsState()
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentOp by remember { mutableStateOf("") }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("maintenance_tools_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            "سامانه خودکار نگهداری و اصلاح داده‌های مالی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "جهت یکپارچه‌سازی کامل، رفع همزادها و اسناد یتیم بدون تأثیر بر اطلاعات پایه سیستم.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        item {
            Text("ابزارهای نگهداری سیستم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        // Action Buttons Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Action 1: Rebuild Financial Ledger
                MaintenanceActionRow(
                    title = "بازسازی کامل دفتر کل مالی",
                    description = "حذف کل اسناد موقت حسابداری و بازسازی مجدد بر اساس فاکتورهای خدمات و هزینه‌ها",
                    icon = Icons.Default.Build,
                    onClick = {
                        currentOp = "بازسازی کامل دفتر کل مالی"
                        viewModel.rebuildFinancialLedger { logs = it }
                    }
                )

                // Action 2: Recalculate Dashboard Totals
                MaintenanceActionRow(
                    title = "محاسبه مجدد شاخص‌های کلان",
                    description = "مجموع تراکنش‌های دریافتی و پرداختی را جهت برابری صددرصدی بورد کنترل بررسی می‌کند",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        currentOp = "محاسبه مجدد شاخص‌های کلان"
                        viewModel.recalculateDashboardTotals { logs = it }
                    }
                )

                // Action: Recalculate Cashbox Balances
                MaintenanceActionRow(
                    title = "محاسبه مجدد مانده صندوق‌ها",
                    description = "تطبیق و محاسبه مجدد مانده کل صندوق‌ها و حساب‌های بانکی بر اساس ریز تراکنش‌های تسویه‌شده",
                    icon = Icons.Default.AccountBalance,
                    onClick = {
                        currentOp = "محاسبه مجدد مانده صندوق‌ها"
                        viewModel.recalculateCashboxBalances { logs = it }
                    }
                )

                // Action 3: Remove Orphan Ledger Entries
                MaintenanceActionRow(
                    title = "حذف اسناد یتیم و بدون مرجع",
                    description = "تراکنش‌ها یا ردیف‌های حسابداری بدون مرجع فعال را بلافاصله شناسایی و پاکسازی می‌کند",
                    icon = Icons.Default.Delete,
                    onClick = {
                        currentOp = "حذف اسناد یتیم و بدون مرجع"
                        viewModel.removeOrphanLedgerEntries { logs = it }
                    }
                )

                // Action 4: Repair Broken References
                MaintenanceActionRow(
                    title = "اصلاح پیوندها و روابط مخدوش",
                    description = "کلیدهای خارجی مرتبط با بیماران، خدمات و پرسنل را بررسی و ثبت‌های مخدوش را اصلاح می‌کند",
                    icon = Icons.Default.Warning,
                    onClick = {
                        currentOp = "اصلاح پیوندها و روابط مخدوش"
                        viewModel.repairBrokenReferences { logs = it }
                    }
                )

                // Action 5: Validate Financial Integrity (Validate and Repair)
                MaintenanceActionRow(
                    title = "اعتبارسنجی و خودترمیمی",
                    description = "پایش ساختاری دیتابیس مالی، حذف تکراری‌های همزاد به صورت آنی و خودکار",
                    icon = Icons.Default.CheckCircle,
                    onClick = {
                        currentOp = "اعتبارسنجی و خودترمیمی"
                        viewModel.validateAndRepairFinancialIntegrity { logs = it }
                    }
                )

                // Action: Scan for Financial Orphans & Inconsistencies (Financial Integrity Tools)
                MaintenanceActionRow(
                    title = "اسکن پایش یکپارچگی مالی",
                    description = "پایش مستقل تراکنش‌های بدون مرجع، مرجع‌های نامعتبر، تراکنش‌های همزاد تکراری و ثبت گزارش جزئیات",
                    icon = Icons.Default.Warning,
                    onClick = {
                        currentOp = "گزارش پایش یکپارچگی مالی"
                        viewModel.scanFinancialIntegrityIssues { logs = it }
                    }
                )

                // Action 6: Refresh Financial Indexes
                MaintenanceActionRow(
                    title = "بروزرسانی نمایه‌های پایگاه داده",
                    description = "ترتیب درخت اندیس تراکنش‌های مالی جهت تسریع لود گزارش‌های حسابداری و عملکردی",
                    icon = Icons.Default.Info,
                    onClick = {
                        currentOp = "بروزرسانی نمایه‌های پایگاه داده"
                        viewModel.refreshFinancialIndexes { logs = it }
                    }
                )

                // Action 7: Clear Ledger (Administrator Only)
                MaintenanceActionRow(
                    title = "پاکسازی اسناد دفتر کل (مخصوص مدیریت)",
                    description = "پاکسازی کامل و ریشه‌ای ژورنال‌های حسابداری بدون اثر روی ثبت خدمات و هزینه‌ها",
                    icon = Icons.Default.Delete,
                    isDanger = true,
                    onClick = {
                        if (userRole == "Admin" || userRole == "Mother Account" || userRole == "GM") {
                            showConfirmClearDialog = true
                        } else {
                            logs = listOf("❌ خطای عدم دسترسی: اجرای این عملیات صرفاً مخصوص مدیر ارشد سیستم (Administrator) می‌باشد.")
                        }
                    }
                )
            }
        }

        // Operation Logs Display Panel
        if (currentOp.isNotEmpty() || logs.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "گزارش نهایی عملیات: $currentOp",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { logs = emptyList(); currentOp = "" }) {
                                Text("پاکسازی", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (logs.isEmpty()) {
                            Text("درحال اجرای عملیات نگهداری...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            logs.forEach { log ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(log, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("تایید عملیات حساس پاکسازی دفتر کل") },
            text = { Text("آیا مطمئن هستید که می‌خواهید تمام اسناد دفتر کل را پاکسازی کنید؟ پس از این کار، دفتر کل فوراً بازسازی خواهد شد. این کار داده‌های پایه را تغییر نمی‌دهد.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showConfirmClearDialog = false
                        currentOp = "پاکسازی و بازسازی خودکار دفتر کل"
                        viewModel.clearLedger { clearLogs ->
                            // Automatically rebuild after clearing as per specifications
                            viewModel.rebuildFinancialLedger { rebuildLogs ->
                                logs = clearLogs + rebuildLogs
                            }
                        }
                    }
                ) {
                    Text("پاکسازی و بازسازی", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun MaintenanceActionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val cardColor = if (isDanger) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    val outlineColor = if (isDanger) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
    val iconColor = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, outlineColor),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isDanger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

// --- DOUBLE-ENTRY ACCOUNTING LEDGER ENGINE & AUDIT UI ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalLedgerView(viewModel: HamrahanViewModel) {
    val journalEntries by viewModel.journalEntries.collectAsState()
    val editHistories by viewModel.editHistories.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedAccountFilter by remember { mutableStateOf("همه حساب‌ها") }
    var selectedDateRange by remember { mutableStateOf("همه زمان‌ها") }

    var selectedEntryForAdjustment by remember { mutableStateOf<JournalEntry?>(null) }
    var selectedEntryForReversing by remember { mutableStateOf<JournalEntry?>(null) }
    var selectedEntryForHistory by remember { mutableStateOf<JournalEntry?>(null) }

    // Chart of Accounts filter list dynamically built from entries
    val accountOptions = remember(journalEntries) {
        val accountsFromEntries = journalEntries.flatMap { listOf(it.debitAccount, it.creditAccount) }.toSet()
        val defaults = listOf(
            "همه حساب‌ها",
            "صندوق اصلی (دارایی)",
            "حساب بانکی (دارایی)",
            "درآمد خدمات سلامت (درآمد)",
            "هزینه‌های جاری (هزینه)",
            "حقوق/کارمزد همکاران"
        )
        (defaults + accountsFromEntries).distinct()
    }

    // Chronological Running Balance calculation
    val entriesWithRunningBalance = remember(journalEntries, selectedAccountFilter, selectedDateRange, searchQuery) {
        val chronologicalList = journalEntries.sortedBy { it.date }
        var currentBalance = 0.0

        val listWithBalance = chronologicalList.map { entry ->
            val isDebitMatched = selectedAccountFilter == "همه حساب‌ها" || entry.debitAccount == selectedAccountFilter
            val isCreditMatched = selectedAccountFilter == "همه حساب‌ها" || entry.creditAccount == selectedAccountFilter

            val delta = when {
                selectedAccountFilter == "همه حساب‌ها" -> entry.amount
                isDebitMatched -> entry.amount
                isCreditMatched -> -entry.amount
                else -> 0.0
            }
            currentBalance += delta
            entry to currentBalance
        }

        listWithBalance.filter { (entry, _) ->
            val matchesSearch = searchQuery.isBlank() ||
                    entry.documentNumber.contains(searchQuery, ignoreCase = true) ||
                    entry.debitAccount.contains(searchQuery, ignoreCase = true) ||
                    entry.creditAccount.contains(searchQuery, ignoreCase = true) ||
                    entry.reference.contains(searchQuery, ignoreCase = true)

            val now = System.currentTimeMillis()
            val matchesDate = when (selectedDateRange) {
                "امروز" -> entry.date >= now - 86400000L
                "۷ روز اخیر" -> entry.date >= now - 7 * 86400000L
                "۳۰ روز اخیر" -> entry.date >= now - 30 * 86400000L
                else -> true
            }

            val matchesAccount = selectedAccountFilter == "همه حساب‌ها" ||
                    entry.debitAccount == selectedAccountFilter ||
                    entry.creditAccount == selectedAccountFilter

            matchesSearch && matchesDate && matchesAccount
        }.reversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("journal_ledger_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toolbar: Search Box
        SearchToolbar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "جستجو در شماره سند، حساب بدهکار/بستانکار، بابت...",
            modifier = Modifier.fillMaxWidth()
        )

        // Filters Row: Account Picker & Date Range Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Account Picker Dropdown
            var showAccountMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showAccountMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedAccountFilter,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = showAccountMenu,
                    onDismissRequest = { showAccountMenu = false }
                ) {
                    accountOptions.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                selectedAccountFilter = acc
                                showAccountMenu = false
                            }
                        )
                    }
                }
            }

            // Date Range Picker Dropdown
            var showDateMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(0.8f)) {
                OutlinedButton(
                    onClick = { showDateMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedDateRange,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = showDateMenu,
                    onDismissRequest = { showDateMenu = false }
                ) {
                    listOf("همه زمان‌ها", "امروز", "۷ روز اخیر", "۳۰ روز اخیر").forEach { range ->
                        DropdownMenuItem(
                            text = { Text(range, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                selectedDateRange = range
                                showDateMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Journal Entries Table / List
        if (entriesWithRunningBalance.isEmpty()) {
            EmptyState(
                icon = Icons.Default.ReceiptLong,
                message = "هیچ سند حسابداری با شرایط انتخابی یافت نشد",
                description = "با تغییر فیلتر حساب یا بازه زمانی، اسناد دفتر کل را بررسی نمایید."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(entriesWithRunningBalance, key = { it.first.id }) { (entry, runningBal) ->
                    JournalEntryRowCard(
                        entry = entry,
                        runningBalance = runningBal,
                        currency = currency,
                        onAdjustmentClick = { selectedEntryForAdjustment = entry },
                        onReversingClick = { selectedEntryForReversing = entry },
                        onHistoryClick = { selectedEntryForHistory = entry }
                    )
                }
            }
        }
    }

    // Dialogs
    selectedEntryForAdjustment?.let { origEntry ->
        AdjustmentEntryDialog(
            originalEntry = origEntry,
            currency = currency,
            onDismiss = { selectedEntryForAdjustment = null },
            onConfirm = { newAmount, reason ->
                viewModel.issueAdjustmentEntry(origEntry.id, newAmount, reason) {
                    selectedEntryForAdjustment = null
                }
            }
        )
    }

    selectedEntryForReversing?.let { origEntry ->
        ReversingEntryDialog(
            originalEntry = origEntry,
            currency = currency,
            onDismiss = { selectedEntryForReversing = null },
            onConfirm = { reason ->
                viewModel.issueReversingEntry(origEntry.id, reason) {
                    selectedEntryForReversing = null
                }
            }
        )
    }

    selectedEntryForHistory?.let { origEntry ->
        JournalEditHistoryDialog(
            originalEntry = origEntry,
            editHistories = editHistories.filter { it.entityType == "JournalEntry" && it.entityId == origEntry.id },
            currency = currency,
            onDismiss = { selectedEntryForHistory = null }
        )
    }
}

@Composable
fun JournalEntryRowCard(
    entry: JournalEntry,
    runningBalance: Double,
    currency: String,
    onAdjustmentClick: () -> Unit,
    onReversingClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val isReversing = entry.documentNumber.startsWith("REV-")
    val isAdjustment = entry.documentNumber.startsWith("ADJ-")

    val badgeText = when {
        isReversing -> "سند ابطال (معکوس)"
        isAdjustment -> "سند اصلاحی"
        else -> "سند اولیه"
    }

    val badgeType = when {
        isReversing -> EnterpriseStatusType.ERROR
        isAdjustment -> EnterpriseStatusType.PENDING
        else -> EnterpriseStatusType.COMPLETED
    }

    EnterpriseCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("journal_entry_${entry.id}"),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top Header: Doc number, Status Badge, Date, Action Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "سند ${entry.documentNumber}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    StatusBadge(text = badgeText, statusType = badgeType)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.date.toDateString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "عملیات سند")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("صدور سند اصلاحی") },
                                onClick = { showMenu = false; onAdjustmentClick() }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("ابطال سند (معکوس)") },
                                onClick = { showMenu = false; onReversingClick() }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("مشاهده تاریخچه تغییرات") },
                                onClick = { showMenu = false; onHistoryClick() }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Double Entry Accounts (Debit / Credit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Debit Account (بدهکار)
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("بدهکار (حساب)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        Text(entry.debitAccount, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Credit Account (بستانکار)
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("بستانکار (حساب)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(entry.creditAccount, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Reference description
            Text(
                text = "بابت: ${entry.reference}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Amount & Chronological Running Balance (مانده لحظه‌ای)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("مانده لحظه‌ای:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = runningBalance.formatPrice(currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("مبلغ سند:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = entry.amount.formatPrice(currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isReversing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun AdjustmentEntryDialog(
    originalEntry: JournalEntry,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (newAmount: Double, reason: String) -> Unit
) {
    var newAmountText by remember { mutableStateOf(originalEntry.amount.toInt().toString()) }
    var reasonText by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    val newAmount = newAmountText.toDoubleOrNull() ?: 0.0
    val delta = newAmount - originalEntry.amount

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("صدور سند اصلاحی (تعدیل)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "تنظیم مابه‌التفاوت سند شماره ${originalEntry.documentNumber} بدون حذف سوابق قبلی",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("مبلغ فعلی ثبت‌شده:", style = MaterialTheme.typography.bodySmall)
                        Text(originalEntry.amount.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = newAmountText,
                    onValueChange = { newAmountText = it; hasError = false },
                    label = { Text("مبلغ اصلاحی جدید ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = hasError && newAmount <= 0
                )

                if (delta != 0.0) {
                    Text(
                        text = "مابه‌التفاوت محاسباتی: ${delta.formatPrice(currency)} " + if (delta > 0) "(افزایشی)" else "(کاهشی)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it; hasError = false },
                    label = { Text("علت صدور سند اصلاحی") },
                    placeholder = { Text("مثلاً: خطای اپراتور در ثبت اولیه، تخفیف مصوب مدیریت...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = hasError && reasonText.isBlank()
                )

                if (hasError) {
                    Text("لطفاً مبلغ معتبر و علت اصلاح را وارد نمایید.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newAmount <= 0 || reasonText.isBlank()) {
                                hasError = true
                            } else {
                                onConfirm(newAmount, reasonText)
                            }
                        }
                    ) {
                        Text("صدور سند اصلاحی")
                    }
                }
            }
        }
    }
}

@Composable
fun ReversingEntryDialog(
    originalEntry: JournalEntry,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var reasonText by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("صدور سند ابطال (معکوس)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(
                    "با تأیید این عملیات، سند معکوس جهت صفر کردن اثر مالی سند ${originalEntry.documentNumber} صادر می‌گردد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("جابجایی حساب‌ها در سند ابطال:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("بدهکار جدید: ${originalEntry.creditAccount}", style = MaterialTheme.typography.bodySmall)
                        Text("بستانکار جدید: ${originalEntry.debitAccount}", style = MaterialTheme.typography.bodySmall)
                        Text("مبلغ ابطال: ${originalEntry.amount.formatPrice(currency)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it; hasError = false },
                    label = { Text("علت ابطال / لغو سند") },
                    placeholder = { Text("مثلاً: انصراف بیمار، لغو خدمت، فاکتور اشتباه...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = hasError
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (reasonText.isBlank()) {
                                hasError = true
                            } else {
                                onConfirm(reasonText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("تأیید و صدور سند ابطال")
                    }
                }
            }
        }
    }
}

@Composable
fun JournalEditHistoryDialog(
    originalEntry: JournalEntry,
    editHistories: List<FinancialEditHistory>,
    currency: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تاریخچه تغییرات سند ${originalEntry.documentNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                if (editHistories.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.History,
                        message = "سابقه تغییری یافت نشد",
                        description = "تاکنون هیچ سند تعدیل یا اصلاحیه مالی برای این سند ثبت نشده است."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(editHistories) { history ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${history.editedBy} (${history.userRole})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text(history.timestamp.toDateString(), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text("تغییر مبلغ: ${history.previousValue} ➔ ${history.newValue}", style = MaterialTheme.typography.bodySmall)
                                    Text("مابه‌التفاوت: ${history.differenceAmount.formatPrice(currency)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text("علت: ${history.reason}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
