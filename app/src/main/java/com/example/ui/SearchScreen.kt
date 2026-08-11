package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: HamrahanViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val results by viewModel.globalSearchResults.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("search_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Page Title ---
        Text(
            text = "جستجوی سراسری و پیشرفته",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // --- Search Field ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("کلمه کلیدی، تلفن، نام بیمار، نام همکار، شماره فاکتور...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("global_search_input"),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (searchQuery.isBlank()) {
            EmptyStateView(
                icon = Icons.Default.Search,
                message = "جهت جستجو، عبارت مورد نظر را وارد کنید.",
                description = "امکان جستجوی نام بیمار، تلفن، نام همکار، شماره فاکتور و خدمات وجود دارد.",
                modifier = Modifier.weight(1f)
            )
        } else if (results.patients.isEmpty() && results.employees.isEmpty() && results.services.isEmpty() && results.transactions.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Search,
                message = "هیچ نتیجه‌ای منطبق با جستجو یافت نشد.",
                description = "لطفاً املای کلمات را بررسی کنید یا عبارت دیگری را امتحان کنید.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Patients Matching ---
                if (results.patients.isNotEmpty()) {
                    item {
                        Text(
                            text = "پرونده بیماران (${results.patients.size})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(results.patients) { p ->
                        SearchItemCard(
                            title = p.fullName,
                            subtitle = "تلفن: ${p.phone} | نشانی: ${p.address}",
                            icon = Icons.Default.People,
                            label = "بیمار",
                            badgeColor = Color(0xFFDCFCE7),
                            badgeTextColor = Color(0xFF15803D),
                            onClick = { viewModel.handleDeepLink("patients?id=${p.id}") }
                        )
                    }
                }

                // --- Employees Matching ---
                if (results.employees.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "پرونده همکاران پرسنلی (${results.employees.size})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(results.employees) { emp ->
                        SearchItemCard(
                            title = emp.fullName,
                            subtitle = "ردیف: ${emp.profession} | شماره همراه: ${emp.phone}",
                            icon = Icons.Default.Badge,
                            label = "همکار",
                            badgeColor = Color(0xFFE0F2FE),
                            badgeTextColor = Color(0xFF0369A1),
                            onClick = { viewModel.handleDeepLink("employees?id=${emp.id}") }
                        )
                    }
                }

                // --- Services Matching ---
                if (results.services.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تعرفه خدمات (${results.services.size})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(results.services) { s ->
                        SearchItemCard(
                            title = s.name,
                            subtitle = "دسته: ${s.category} | تعرفه: ${s.sellingPrice.formatPrice(currency)}",
                            icon = Icons.Default.MedicalServices,
                            label = "خدمت",
                            badgeColor = Color(0xFFF3E8FF),
                            badgeTextColor = Color(0xFF7E22CE),
                            onClick = { viewModel.handleDeepLink("services?id=${s.id}") }
                        )
                    }
                }

                // --- Transactions Matching ---
                if (results.transactions.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تراکنش‌های مالی (${results.transactions.size})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(results.transactions) { tx ->
                        SearchItemCard(
                            title = tx.description,
                            subtitle = "${tx.type} | مبلغ: ${tx.amount.formatPrice(currency)} | تاریخ: ${tx.date.formatDate()}",
                            icon = Icons.Default.SyncAlt,
                            label = "تراکنش",
                            badgeColor = Color(0xFFFEF3C7),
                            badgeTextColor = Color(0xFFB45309),
                            onClick = { viewModel.handleDeepLink("accounting?id=${tx.id}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchItemCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeTextColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit
) {
    com.example.ui.components.EnterpriseCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    color = badgeTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
