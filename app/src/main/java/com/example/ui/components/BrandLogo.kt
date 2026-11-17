package com.example.ui.components

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.LocalStudyTheme
import java.io.File

@Composable
fun LearnTalentLogo(
    customLogoFile: File?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 12.dp,
) {
    val theme = LocalStudyTheme.current
    val shape = RoundedCornerShape(cornerRadius)
    val mod = modifier
        .size(size)
        .clip(shape)
        .border(1.5.dp, theme.primary.copy(alpha = 0.6f), shape)

    if (customLogoFile != null && customLogoFile.exists()) {
        AsyncImage(
            model = customLogoFile,
            contentDescription = "Learn Talent Logo",
            contentScale = ContentScale.Crop,
            modifier = mod
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.img_app_icon_fg),
            contentDescription = "Learn Talent Logo",
            contentScale = ContentScale.Crop,
            modifier = mod
        )
    }
}

@Composable
fun LearnTalentBrandHeader(
    customLogoFile: File?,
    subtitle: String = "by Bunts",
    modifier: Modifier = Modifier,
    logoSize: Dp = 42.dp,
) {
    val theme = LocalStudyTheme.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LearnTalentLogo(
            customLogoFile = customLogoFile,
            size = logoSize,
            cornerRadius = 11.dp
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LEARN TALENT",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("top_brand_title")
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = theme.secondary.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = "by Bunts",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.secondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (subtitle.isNotBlank() && subtitle != "by Bunts") {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceVariant,
                    maxLines = 1
                )
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
                .border(1.5.dp, theme.primary, CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(theme.primaryContainer)
                .border(1.5.dp, theme.primary.copy(alpha = 0.6f), CircleShape),
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
