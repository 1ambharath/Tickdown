package com.example.tickdown.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tickdown.theme.TickdownCompletedGreen
import com.example.tickdown.theme.TickdownOverdueRed
import com.example.tickdown.theme.TickdownPausedAmber

/**
 * Minimalist container card with restrained 1.5dp border outline and rounded corners.
 * Reflects the neo-industrial / Nothing aesthetics in the design artifact.
 */
@Composable
fun TickdownCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    cornerRadius: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .border(BorderStroke(1.5.dp, borderColor), shape)
            .background(backgroundColor, shape)
            .clickable(onClick = onClick)
    } else {
        modifier
            .clip(shape)
            .border(BorderStroke(1.5.dp, borderColor), shape)
            .background(backgroundColor, shape)
    }

    Box(modifier = cardModifier) {
        content()
    }
}

/**
 * Restrained pill badge matching the aesthetic (e.g. `1 / 5 ACTIVE`, `v1.0.0`, `DAYS LEFT`).
 */
@Composable
fun TickdownBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.secondary,
    textColor: Color = MaterialTheme.colorScheme.onSecondary,
    borderColor: Color? = null
) {
    val shape = RoundedCornerShape(100.dp)
    val badgeModifier = if (borderColor != null) {
        modifier
            .clip(shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .background(backgroundColor, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    } else {
        modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    }

    Box(
        modifier = badgeModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            letterSpacing = 0.8.sp
        )
    }
}

/**
 * Status indicator badge for ACTIVE, PAUSED, OVERDUE, or COMPLETED.
 */
@Composable
fun TickdownStatusBadge(
    status: String,
    isOverdue: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when {
        isOverdue -> Triple(TickdownOverdueRed, Color.White, "OVERDUE")
        status == "ACTIVE" -> Triple(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, "ACTIVE")
        status == "PAUSED" -> Triple(TickdownPausedAmber, Color.White, "PAUSED")
        status == "COMPLETED" -> Triple(TickdownCompletedGreen, Color.White, "DONE")
        else -> Triple(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, status)
    }

    TickdownBadge(
        text = label,
        modifier = modifier,
        backgroundColor = bgColor,
        textColor = textColor
    )
}

/**
 * Prominent but minimal action button (e.g. `+ ADD`, `COMPLETE`).
 */
@Composable
fun TickdownButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    accentColor: Color? = null
) {
    val shape = RoundedCornerShape(100.dp)
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        accentColor != null -> accentColor
        isPrimary -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        accentColor != null -> Color.White
        isPrimary -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (!isPrimary && enabled) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    } else null

    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = shape,
        color = bg,
        contentColor = contentColor,
        border = border
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Circular slot action button matching design artifact (e.g. `+` button in Slot circle).
 */
@Composable
fun TickdownCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
