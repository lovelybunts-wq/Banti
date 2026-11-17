package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.McqQuestion
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McqPracticeScreen(
    subtopicName: String,
    mcqs: List<McqQuestion>,
    onBack: () -> Unit,
    onComplete: (correct: Int, total: Int) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("$subtopicName • MCQ Practice", style = MaterialTheme.typography.titleMedium, color = theme.primary)
                }
            )
        }
    ) { innerPadding ->
        if (mcqs.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
                Text("No practice MCQs added yet.", color = theme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val current = mcqs[currentIndex.coerceIn(0, mcqs.lastIndex)]
        val progress = (currentIndex + 1).toFloat() / mcqs.size.toFloat()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                color = theme.primary,
                trackColor = theme.surfaceElevated,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Question ${currentIndex + 1} of ${mcqs.size}",
                style = MaterialTheme.typography.labelMedium,
                color = theme.onSurfaceVariant
            )

            Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                Text(current.question, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
            }

            current.options.forEachIndexed { idx, optText ->
                val isSelected = selectedOption == idx
                val isCorrect = idx == current.correctIndex.toInt()
                val cardAccent = when {
                    selectedOption == null -> theme.outline
                    isCorrect -> theme.success
                    isSelected -> theme.error
                    else -> theme.outline
                }
                Tactile3DCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = cardAccent,
                    lipDepth = 3.dp,
                    onClick = {
                        if (selectedOption == null) {
                            selectedOption = idx
                            if (isCorrect) correctCount++
                        }
                    },
                    testTag = "mcq_option_$idx"
                ) {
                    Text(
                        text = "${('A' + idx)}. $optText",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedOption != null && isCorrect) theme.success else theme.onSurface
                    )
                }
            }

            if (selectedOption != null && !current.explanation.isNullOrBlank()) {
                Tactile3DCard(accentColor = theme.secondary, lipDepth = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Text("Explanation: ${current.explanation}", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (currentIndex < mcqs.lastIndex) {
                        currentIndex++
                        selectedOption = null
                    } else {
                        onComplete(correctCount, mcqs.size)
                    }
                },
                enabled = selectedOption != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("mcq_next_btn")
            ) {
                Text(if (currentIndex < mcqs.lastIndex) "Next Question" else "Finish & Save Score")
            }
        }
    }
}
