package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalStudyTheme

/**
 * Tactile 3D sculpted card with subtle bottom extruded lip and soft top sheen,
 * giving the requested "3D print / eye-friendly dark" aesthetic without glare.
 */
@Composable
fun Tactile3DCard(
    modifier: Modifier = Modifier,
    accentColor: Color = LocalStudyTheme.current.primary,
    cornerRadius: Dp = 20.dp,
    lipDepth: Dp = 5.dp,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalStudyTheme.current
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }

    val baseModifier = modifier
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .padding(bottom = lipDepth)
        .drawBehind {
            val lipPx = lipDepth.toPx()
            val radiusPx = cornerRadius.toPx()
            // Deep 3D extruded base lip underneath the card
            drawRoundRect(
                color = theme.cardShadowLip,
                topLeft = Offset(0f, lipPx),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            )
            // Subtle accent rim along the bottom edge
            drawRoundRect(
                color = accentColor.copy(alpha = 0.25f),
                topLeft = Offset(2f, lipPx * 0.6f),
                size = Size(size.width - 4f, size.height),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            )
        }
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    theme.surfaceElevated,
                    theme.surface,
                )
            )
        )
        .border(
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        theme.cardBorderHighlight,
                        theme.outline.copy(alpha = 0.5f)
                    )
                )
            ),
            shape = shape
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = accentColor.copy(alpha = 0.35f)),
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Column(
        modifier = baseModifier.padding(contentPadding),
        content = content
    )
}

@Composable
fun Tactile3DBadge(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
fun TactileIconOrb(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = null,
) {
    val theme = LocalStudyTheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.28f),
                        tint.copy(alpha = 0.10f),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.45f),
                shape = RoundedCornerShape(15.dp)
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}
