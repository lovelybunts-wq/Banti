package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
