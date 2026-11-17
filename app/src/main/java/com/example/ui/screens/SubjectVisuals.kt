package com.example.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.School
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
        "computer" -> Icons.Rounded.Computer
        "current_affairs" -> Icons.Rounded.Newspaper
        "mp_gk" -> Icons.Rounded.LocationCity
        "general_studies" -> Icons.Rounded.School
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
        "#2456A4",
        "#35BDB5",
        "#D97706",
        "#0D9488",
        "#16A34A",
        "#2563EB",
        "#7C3AED",
        "#DB2777",
        "#0284C7",
        "#EA580C",
    )

    val presetIconKeys = listOf(
        "history" to "History",
        "polity" to "Polity",
        "geography" to "Geography",
        "english" to "English",
        "maths" to "Maths",
        "science" to "Science",
        "reasoning" to "Reasoning",
        "computer" to "Computer",
        "current_affairs" to "Current Affairs",
        "mp_gk" to "MP GK",
        "general_studies" to "General Studies",
        "book" to "Other",
    )
}
