package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.BrandColors
import com.example.ui.theme.LocalStudyTheme
import java.io.File

@Composable
fun LearnTalentLogo(
    customLogoFile: File?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 13.dp,
) {
    val theme = LocalStudyTheme.current
    val shape = RoundedCornerShape(cornerRadius)
    val mod = modifier
        .size(size)
        .clip(shape)
        .border(1.5.dp, theme.goldAccent.copy(alpha = 0.85f), shape)

    if (customLogoFile != null && customLogoFile.exists()) {
        AsyncImage(
            model = customLogoFile,
            contentDescription = "Learn Talent LT Logo",
            contentScale = ContentScale.Crop,
            modifier = mod
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.img_lt_logo),
            contentDescription = "Learn Talent LT Logo",
            contentScale = ContentScale.Crop,
            modifier = mod
        )
    }
}

@Composable
fun LearnTalentBrandHeader(
    customLogoFile: File?,
    subtitle: String = "FLT – KEY OF SUCCESS",
    modifier: Modifier = Modifier,
    logoSize: Dp = 44.dp,
    lightTextOnDarkBg: Boolean = false,
) {
    val theme = LocalStudyTheme.current
    val titleColor = if (lightTextOnDarkBg) BrandColors.PureWhite else theme.primary
    val subtitleColor = if (lightTextOnDarkBg) Color(0xFFD6E6FF) else theme.onSurfaceVariant
    val pillBg = if (lightTextOnDarkBg) BrandColors.PremiumGold.copy(alpha = 0.22f) else theme.goldAccent.copy(alpha = 0.20f)
    val pillFg = if (lightTextOnDarkBg) BrandColors.PremiumGold else if (theme.isLight) BrandColors.DeepNavy else theme.goldAccent

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LearnTalentLogo(
            customLogoFile = customLogoFile,
            size = logoSize,
            cornerRadius = 12.dp
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LEARN TALENT",
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("top_brand_title")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = pillBg,
                    border = BorderStroke(0.75.dp, pillFg.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "FLT – KEY OF SUCCESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = pillFg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (subtitle.isNotBlank() && subtitle != "FLT – KEY OF SUCCESS" && subtitle != "by Bunts") {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StudentAvatar(
    photoUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val theme = LocalStudyTheme.current
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "L"
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "$displayName profile photo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, theme.goldAccent, CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(theme.primaryContainer)
                .border(1.5.dp, theme.goldAccent.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleSmall,
                color = theme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
