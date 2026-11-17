package com.example.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object SubjectVisuals {
    fun iconFor(iconKey: String): ImageVector = when (iconKey.lowercase()) {
        "history" -> Icons.Rounded.HistoryEdu
        "polity" -> Icons.Rounded.AccountBalance
        "geography" -> Icons.Rounded.Public
        "english" -> Icons.Rounded.Translate
        "maths" -> Icons.Rounded.Calculate
        "science" -> Icons.Rounded.Biotech
        "reasoning" -> Icons.Rounded.Psychology
        "book" -> Icons.AutoMirrored.Rounded.MenuBook
        else -> Icons.Rounded.AutoStories
    }

    fun colorFor(colorHex: String, fallback: Color): Color {
        return try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {
            fallback
        }
    }

    val presetPalette = listOf(
        "#F6AD55",
        "#4FD1C5",
        "#68D391",
        "#90CDF4",
        "#818CF8",
        "#F472B6",
        "#FBBF24",
    )

    val presetIconKeys = listOf(
        "history" to "History",
        "polity" to "Polity",
        "geography" to "Geography",
        "english" to "English",
        "maths" to "Maths",
        "science" to "Science",
        "reasoning" to "Reasoning",
        "book" to "General",
    )
}
