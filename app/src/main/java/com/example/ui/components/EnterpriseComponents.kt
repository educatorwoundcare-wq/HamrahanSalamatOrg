package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ripple
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.EnterpriseTypographyStyles
import com.example.ui.theme.LocalEnterpriseColors
import com.example.ui.toPersianDigits

/**
 * Micro-Interaction Helper:
 * Spring Press Animation (scale down to 0.98f) + Haptic Feedback
 */
@Composable
fun Modifier.bounceClick(
    onClick: (() -> Unit)? = null,
    scaleDown: Float = 0.98f
): Modifier {
    if (onClick == null) return this
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceClickScale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        )
}

/**
 * 1. EnterpriseCard
 * Standard container card with M3 surface container, elevation, border, radius, and spring ripple feedback.
 */
@Composable
fun EnterpriseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    elevation: Dp = DesignTokens.Elevation.low,
    cornerRadius: Dp = DesignTokens.Radius.l,
    contentPadding: PaddingValues = PaddingValues(DesignTokens.Spacing.l),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

/**
 * 2. KPICard
 * Enterprise metric display card featuring title, bold value, trend badge, and icon box.
 */
@Composable
fun KPICard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    trendText: String? = null,
    isPositiveTrend: Boolean? = true,
    sparklineData: List<Float>? = null,
    onClick: (() -> Unit)? = null
) {
    val enterpriseColors = LocalEnterpriseColors.current

    EnterpriseCard(
        modifier = modifier.heightIn(min = DesignTokens.ComponentHeight.kpiCardMin),
        onClick = onClick,
        contentPadding = PaddingValues(DesignTokens.Spacing.l)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = EnterpriseTypographyStyles.supportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.xs))

                Text(
                    text = value.toPersianDigits(),
                    style = EnterpriseTypographyStyles.kpiValue,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (sparklineData != null && sparklineData.size >= 2) {
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.s))
                    SparklineChart(
                        data = sparklineData,
                        lineColor = iconTint,
                        modifier = Modifier.height(28.dp).fillMaxWidth(0.8f)
                    )
                }

                if (trendText != null || subtitle != null) {
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.s))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)
                    ) {
                        if (trendText != null && isPositiveTrend != null) {
                            val badgeBg = if (isPositiveTrend) enterpriseColors.success.copy(alpha = 0.12f) else enterpriseColors.error.copy(alpha = 0.12f)
                            val badgeFg = if (isPositiveTrend) enterpriseColors.success else enterpriseColors.error
                            val trendIcon = if (isPositiveTrend) Icons.Default.TrendingUp else Icons.Default.TrendingDown

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(DesignTokens.Radius.s))
                                    .background(badgeBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = trendIcon,
                                        contentDescription = null,
                                        tint = badgeFg,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = trendText.toPersianDigits(),
                                        style = EnterpriseTypographyStyles.supportingText,
                                        color = badgeFg,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (subtitle != null) {
                            Text(
                                text = subtitle.toPersianDigits(),
                                style = EnterpriseTypographyStyles.supportingText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(DesignTokens.Radius.m))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 3. StatusBadge
 * Enterprise pill badge for entity statuses (Active, Pending, Syncing, Error, Success, etc.)
 */
enum class EnterpriseStatusType {
    ACTIVE,
    PENDING,
    COMPLETED,
    WARNING,
    ERROR,
    SYNCING,
    NEUTRAL
}

@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    statusType: EnterpriseStatusType = EnterpriseStatusType.NEUTRAL,
    icon: ImageVector? = null,
    customColor: Color? = null
) {
    val enterpriseColors = LocalEnterpriseColors.current

    val (bgColor, fgColor) = when {
        customColor != null -> customColor.copy(alpha = 0.12f) to customColor
        statusType == EnterpriseStatusType.ACTIVE -> enterpriseColors.success.copy(alpha = 0.12f) to enterpriseColors.success
        statusType == EnterpriseStatusType.COMPLETED -> enterpriseColors.success.copy(alpha = 0.12f) to enterpriseColors.success
        statusType == EnterpriseStatusType.PENDING -> enterpriseColors.warning.copy(alpha = 0.12f) to enterpriseColors.warning
        statusType == EnterpriseStatusType.WARNING -> enterpriseColors.warning.copy(alpha = 0.12f) to enterpriseColors.warning
        statusType == EnterpriseStatusType.ERROR -> enterpriseColors.error.copy(alpha = 0.12f) to enterpriseColors.error
        statusType == EnterpriseStatusType.SYNCING -> enterpriseColors.syncSyncing.copy(alpha = 0.12f) to enterpriseColors.syncSyncing
        else -> enterpriseColors.financialNeutral.copy(alpha = 0.12f) to enterpriseColors.financialNeutral
    }

    Surface(
        modifier = modifier.heightIn(max = DesignTokens.ComponentHeight.badge),
        shape = RoundedCornerShape(DesignTokens.Radius.full),
        color = bgColor,
        contentColor = fgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(12.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(fgColor)
                )
            }
            Text(
                text = text.toPersianDigits(),
                style = EnterpriseTypographyStyles.label,
                fontWeight = FontWeight.Medium,
                color = fgColor
            )
        }
    }
}

/**
 * 4. SectionHeader
 * Reusable header with title, subtitle/counter badge, leading icon, and action button.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    badgeCount: Int? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.Spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(DesignTokens.Radius.s))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s)
                ) {
                    Text(
                        text = title,
                        style = EnterpriseTypographyStyles.sectionHeader,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (badgeCount != null) {
                        Surface(
                            shape = RoundedCornerShape(DesignTokens.Radius.full),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = badgeCount.toString().toPersianDigits(),
                                style = EnterpriseTypographyStyles.supportingText,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = EnterpriseTypographyStyles.supportingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (actionText != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.s, vertical = 0.dp)
            ) {
                Text(
                    text = actionText,
                    style = EnterpriseTypographyStyles.label,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 5. SearchToolbar
 * Enterprise search input toolbar with search icon, clear query button, and filter badge.
 */
@Composable
fun SearchToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "جستجو...",
    activeFilterCount: Int = 0,
    onFilterClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = DesignTokens.ComponentHeight.input)
                .testTag("search_toolbar_input"),
            placeholder = {
                Text(
                    text = placeholder,
                    style = EnterpriseTypographyStyles.bodyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "جستجو",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "پاک کردن",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.Radius.m),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        if (onFilterClick != null) {
            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(activeFilterCount.toString().toPersianDigits())
                        }
                    }
                }
            ) {
                OutlinedIconButton(
                    onClick = onFilterClick,
                    modifier = Modifier.size(DesignTokens.ComponentHeight.input),
                    shape = RoundedCornerShape(DesignTokens.Radius.m),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = "فیلترها",
                        tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 6. EmptyState & EmptyStateView
 * Standard empty state placeholder components with brand pulse badge & guidance.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    EmptyStateView(
        title = message,
        modifier = modifier,
        description = description,
        icon = icon,
        actionText = actionText,
        onActionClick = onActionClick
    )
}

@Composable
fun EmptyStateView(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Default.Inbox,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (description != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(20.dp))
                ActionButton(
                    text = actionText,
                    onClick = onActionClick,
                    variant = EnterpriseButtonVariant.PRIMARY,
                    icon = Icons.Default.Add
                )
            }
        }
    }
}

/**
 * 7. LoadingSkeleton & Domain Skeletons
 * Animated shimmer skeleton composables.
 */
@Composable
fun shimmerBrush(targetValue: Float = 1000f): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation.value - 200f, translateAnimation.value - 200f),
        end = Offset(translateAnimation.value, translateAnimation.value)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush())
    )
}

@Composable
fun CardSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 100.dp
) {
    ShimmerBox(modifier = modifier.fillMaxWidth().height(height), cornerRadius = DesignTokens.Radius.l)
}

@Composable
fun KPIGridSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.m)
    ) {
        CardSkeleton(modifier = Modifier.weight(1f), height = 110.dp)
        CardSkeleton(modifier = Modifier.weight(1f), height = 110.dp)
    }
}

@Composable
fun PatientCardSkeleton(modifier: Modifier = Modifier) {
    EnterpriseCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(modifier = Modifier.size(48.dp), cornerRadius = 24.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(18.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
            }
            ShimmerBox(modifier = Modifier.width(60.dp).height(24.dp), cornerRadius = 12.dp)
        }
    }
}

@Composable
fun FinancialRowSkeleton(modifier: Modifier = Modifier) {
    EnterpriseCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(modifier = Modifier.width(120.dp).height(16.dp))
                ShimmerBox(modifier = Modifier.width(80.dp).height(12.dp))
            }
            ShimmerBox(modifier = Modifier.width(90.dp).height(20.dp))
        }
    }
}

@Composable
fun ServiceRowSkeleton(modifier: Modifier = Modifier) {
    EnterpriseCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(modifier = Modifier.size(40.dp), cornerRadius = 8.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(16.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp))
            }
            ShimmerBox(modifier = Modifier.width(70.dp).height(22.dp), cornerRadius = 11.dp)
        }
    }
}

/**
 * Native Canvas Sparkline Trend Line Component
 */
@Composable
fun SparklineChart(
    data: List<Float>,
    modifier: Modifier = Modifier.height(36.dp).width(80.dp),
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.15f),
    strokeWidth: Dp = 2.dp
) {
    if (data.size < 2) return

    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val minVal = data.minOrNull() ?: 0f
        val maxVal = data.maxOrNull() ?: 1f
        val range = if (maxVal - minVal == 0f) 1f else maxVal - minVal

        val points = data.mapIndexed { index, value ->
            val x = (index.toFloat() / (data.size - 1)) * width
            val y = height - (((value - minVal) / range) * (height - strokeWidthPx * 2) + strokeWidthPx)
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent)
            )
        )

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Adaptive Tablet & Large Screen Layout Helper (Master-Detail Pane in RTL)
 */
@Composable
fun AdaptiveTwoPane(
    masterContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tabletThresholdDp: Int = 600
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= tabletThresholdDp

    if (isTablet) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                detailContent()
            }
            Box(modifier = Modifier.weight(1.2f)) {
                masterContent()
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            masterContent()
        }
    }
}

/**
 * 8. ActionButton
 * Enterprise action button component with standardized size, shape, variants, loading state, and leading icon.
 */
enum class EnterpriseButtonVariant {
    PRIMARY,
    SECONDARY,
    OUTLINED,
    TEXT,
    DANGER
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: EnterpriseButtonVariant = EnterpriseButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val enterpriseColors = LocalEnterpriseColors.current

    val height = DesignTokens.ComponentHeight.button
    val shape = RoundedCornerShape(DesignTokens.Radius.m)

    when (variant) {
        EnterpriseButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                modifier = modifier.heightIn(min = height),
                enabled = enabled && !isLoading,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                ButtonInnerContent(text, icon, isLoading, MaterialTheme.colorScheme.onPrimary)
            }
        }
        EnterpriseButtonVariant.SECONDARY -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier.heightIn(min = height),
                enabled = enabled && !isLoading,
                shape = shape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                ButtonInnerContent(text, icon, isLoading, MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        EnterpriseButtonVariant.OUTLINED -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.heightIn(min = height),
                enabled = enabled && !isLoading,
                shape = shape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                ButtonInnerContent(text, icon, isLoading, MaterialTheme.colorScheme.primary)
            }
        }
        EnterpriseButtonVariant.TEXT -> {
            TextButton(
                onClick = onClick,
                modifier = modifier.heightIn(min = height),
                enabled = enabled && !isLoading,
                shape = shape
            ) {
                ButtonInnerContent(text, icon, isLoading, MaterialTheme.colorScheme.primary)
            }
        }
        EnterpriseButtonVariant.DANGER -> {
            Button(
                onClick = onClick,
                modifier = modifier.heightIn(min = height),
                enabled = enabled && !isLoading,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = enterpriseColors.error,
                    contentColor = Color.White
                )
            ) {
                ButtonInnerContent(text, icon, isLoading, Color.White)
            }
        }
    }
}

@Composable
private fun ButtonInnerContent(
    text: String,
    icon: ImageVector?,
    isLoading: Boolean,
    contentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = text,
            style = EnterpriseTypographyStyles.label,
            fontWeight = FontWeight.Bold
        )
    }
}
