package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.HamrahanScreen
import com.example.ui.HamrahanLogo
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.EnterpriseTypographyStyles
import com.example.ui.theme.LocalEnterpriseColors
import com.example.ui.toPersianDigits
import kotlinx.coroutines.launch

/**
 * Enterprise Navigation Categories for organizing navigation items cleanly
 */
enum class NavigationCategory(val title: String, val icon: ImageVector) {
    DASHBOARD("داشبورد", Icons.Default.Dashboard),
    OPERATIONS("عملیات و پذیرش", Icons.Default.MedicalServices),
    FINANCE("امور مالی و حسابداری", Icons.Default.AccountBalance),
    MANAGEMENT("مدیریت و منابع", Icons.Default.Badge),
    ADMINISTRATION("تنظیمات و مرکز", Icons.Default.Settings)
}

data class NavigationGroup(
    val category: NavigationCategory,
    val screens: List<HamrahanScreen>
)

val EnterpriseNavigationGroups = listOf(
    NavigationGroup(
        category = NavigationCategory.DASHBOARD,
        screens = listOf(HamrahanScreen.DASHBOARD)
    ),
    NavigationGroup(
        category = NavigationCategory.OPERATIONS,
        screens = listOf(
            HamrahanScreen.PATIENTS,
            HamrahanScreen.SERVICES,
            HamrahanScreen.REGISTRATION
        )
    ),
    NavigationGroup(
        category = NavigationCategory.FINANCE,
        screens = listOf(
            HamrahanScreen.ACCOUNTING,
            HamrahanScreen.EXPENSES,
            HamrahanScreen.COMMISSIONS,
            HamrahanScreen.REPORTS
        )
    ),
    NavigationGroup(
        category = NavigationCategory.MANAGEMENT,
        screens = listOf(
            HamrahanScreen.EMPLOYEES,
            HamrahanScreen.SEARCH
        )
    ),
    NavigationGroup(
        category = NavigationCategory.ADMINISTRATION,
        screens = listOf(
            HamrahanScreen.PROFILE,
            HamrahanScreen.SETTINGS
        )
    )
)

/**
 * Enterprise Application Shell supporting Compact (<600dp) and Medium/Expanded (>=600dp) layouts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterpriseScaffold(
    currentScreen: HamrahanScreen,
    onScreenSelected: (HamrahanScreen) -> Unit,
    companyName: String,
    isOnline: Boolean = true,
    syncing: Boolean,
    lastSyncTime: Long,
    userRole: String,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        if (isExpanded) {
            // Medium/Expanded Layout: Permanent / Navigation Rail Shell
            Row(modifier = Modifier.fillMaxSize()) {
                EnterpriseNavigationRail(
                    currentScreen = currentScreen,
                    onScreenSelected = onScreenSelected,
                    companyName = companyName,
                    userRole = userRole
                )

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Scaffold(
                    topBar = {
                        EnterpriseTopAppBar(
                            currentScreen = currentScreen,
                            companyName = companyName,
                            isOnline = isOnline,
                            syncing = syncing,
                            lastSyncTime = lastSyncTime,
                            userRole = userRole,
                            showMenuButton = false,
                            onMenuClick = {},
                            onSyncClick = onSyncClick
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        content(innerPadding)
                    }
                }
            }
        } else {
            // Compact Layout: Modal Drawer + Bottom Navigation Bar
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    EnterpriseDrawerSheet(
                        currentScreen = currentScreen,
                        onScreenSelected = { screen ->
                            onScreenSelected(screen)
                            coroutineScope.launch { drawerState.close() }
                        },
                        companyName = companyName,
                        userRole = userRole
                    )
                },
                modifier = Modifier.testTag("app_navigation_drawer")
            ) {
                Scaffold(
                    topBar = {
                        EnterpriseTopAppBar(
                            currentScreen = currentScreen,
                            companyName = companyName,
                            isOnline = isOnline,
                            syncing = syncing,
                            lastSyncTime = lastSyncTime,
                            userRole = userRole,
                            showMenuButton = true,
                            onMenuClick = { coroutineScope.launch { drawerState.open() } },
                            onSyncClick = onSyncClick
                        )
                    },
                    bottomBar = {
                        EnterpriseBottomNavigationBar(
                            currentScreen = currentScreen,
                            onScreenSelected = onScreenSelected,
                            onMoreClick = { coroutineScope.launch { drawerState.open() } }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        content(innerPadding)
                    }
                }
            }
        }
    }
}

/**
 * Top App Bar with sync status badge and enterprise styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterpriseTopAppBar(
    currentScreen: HamrahanScreen,
    companyName: String,
    isOnline: Boolean = true,
    syncing: Boolean,
    lastSyncTime: Long,
    userRole: String,
    showMenuButton: Boolean,
    onMenuClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.Elevation.low,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing.l, vertical = DesignTokens.Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.m)
                ) {
                    if (showMenuButton) {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "منو",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column {
                        Text(
                            text = currentScreen.title,
                            style = EnterpriseTypographyStyles.screenTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (companyName.isNotBlank()) "سامانه همراهان سلامت • $companyName" else "سامانه همراهان سلامت",
                            style = EnterpriseTypographyStyles.supportingText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s)
                ) {
                    // Sync Status Indicator Badge
                    EnterpriseSyncStatusBadge(
                        isOnline = isOnline,
                        syncing = syncing,
                        lastSyncTime = lastSyncTime,
                        onClick = onSyncClick
                    )

                    // User Avatar / Role Box
                    Surface(
                        shape = RoundedCornerShape(DesignTokens.Radius.m),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "نقش کاربر",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = userRole.ifBlank { "کاربر" },
                                style = EnterpriseTypographyStyles.label,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sync Status Indicator Badge
 */
@Composable
fun EnterpriseSyncStatusBadge(
    isOnline: Boolean = true,
    syncing: Boolean,
    lastSyncTime: Long,
    onClick: () -> Unit
) {
    val enterpriseColors = LocalEnterpriseColors.current

    val (badgeBg, badgeFg, label) = when {
        !isOnline -> Triple(
            enterpriseColors.syncPending.copy(alpha = 0.12f),
            enterpriseColors.syncPending,
            "آفلاین"
        )
        syncing -> Triple(
            enterpriseColors.syncSyncing.copy(alpha = 0.12f),
            enterpriseColors.syncSyncing,
            "همگام‌سازی..."
        )
        lastSyncTime > 0 -> Triple(
            enterpriseColors.syncSynced.copy(alpha = 0.12f),
            enterpriseColors.syncSynced,
            "همگام‌سازی فعال"
        )
        else -> Triple(
            enterpriseColors.syncSynced.copy(alpha = 0.12f),
            enterpriseColors.syncSynced,
            "آنلاین"
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(DesignTokens.Radius.full),
        color = badgeBg,
        border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.3f)),
        modifier = Modifier
            .height(36.dp)
            .testTag("sync_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = badgeFg,
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(badgeFg)
                )
            }

            Text(
                text = label,
                style = EnterpriseTypographyStyles.label,
                color = badgeFg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Compact Bottom Navigation Bar
 */
@Composable
fun EnterpriseBottomNavigationBar(
    currentScreen: HamrahanScreen,
    onScreenSelected: (HamrahanScreen) -> Unit,
    onMoreClick: () -> Unit
) {
    val primaryScreens = listOf(
        HamrahanScreen.DASHBOARD,
        HamrahanScreen.PATIENTS,
        HamrahanScreen.REGISTRATION,
        HamrahanScreen.ACCOUNTING
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.Elevation.medium,
        modifier = Modifier.testTag("app_bottom_navigation_bar")
    ) {
        primaryScreens.forEach { screen ->
            val isSelected = currentScreen == screen
            NavigationBarItem(
                selected = isSelected,
                onClick = { onScreenSelected(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title
                    )
                },
                label = {
                    Text(
                        text = screen.title.split(" ").first(),
                        style = EnterpriseTypographyStyles.supportingText,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // "More" button to trigger full navigation drawer
        NavigationBarItem(
            selected = currentScreen !in primaryScreens,
            onClick = onMoreClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "سایر بخش‌ها"
                )
            },
            label = {
                Text(
                    text = "سایر",
                    style = EnterpriseTypographyStyles.supportingText,
                    fontWeight = FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/**
 * Drawer Sheet categorized by Enterprise Navigation Groups
 */
@Composable
fun EnterpriseDrawerSheet(
    currentScreen: HamrahanScreen,
    onScreenSelected: (HamrahanScreen) -> Unit,
    companyName: String,
    userRole: String
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        // Drawer Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(DesignTokens.Spacing.xl)
        ) {
            Column {
                HamrahanLogo(
                    size = 48.dp,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    secondaryColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = DesignTokens.Spacing.s)
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.s))
                Text(
                    text = "سامانه همراهان سلامت",
                    style = EnterpriseTypographyStyles.sectionHeader,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (companyName.isNotBlank()) companyName else "مدیریت جامع خدمات پرستاری",
                    style = EnterpriseTypographyStyles.supportingText,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(DesignTokens.Spacing.m))

        // Categorized Menu Items
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.m)
        ) {
            EnterpriseNavigationGroups.forEach { group ->
                Text(
                    text = group.category.title,
                    style = EnterpriseTypographyStyles.supportingText,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = DesignTokens.Spacing.m, vertical = DesignTokens.Spacing.xs)
                )

                group.screens.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = EnterpriseTypographyStyles.bodyText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = isSelected,
                        onClick = { onScreenSelected(screen) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("nav_item_${screen.name.lowercase()}"),
                        shape = RoundedCornerShape(DesignTokens.Radius.m),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = DesignTokens.Spacing.xs)
                )
            }
        }
    }
}

/**
 * Enterprise Navigation Rail for Expanded/Tablet Layouts
 */
@Composable
fun EnterpriseNavigationRail(
    currentScreen: HamrahanScreen,
    onScreenSelected: (HamrahanScreen) -> Unit,
    companyName: String,
    userRole: String
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = DesignTokens.Spacing.l)
            ) {
                HamrahanLogo(
                    size = 40.dp,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    secondaryColor = MaterialTheme.colorScheme.secondary
                )
            }
        },
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)
        ) {
            EnterpriseNavigationGroups.forEach { group ->
                group.screens.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { onScreenSelected(screen) },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title.split(" ").first(),
                                style = EnterpriseTypographyStyles.supportingText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
