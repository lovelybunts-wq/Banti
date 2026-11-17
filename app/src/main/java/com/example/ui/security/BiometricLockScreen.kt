package com.example.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.security.BiometricLockHelper
import com.example.ui.components.LearnTalentLogo
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.LocalStudyTheme
import java.io.File

@Composable
fun BiometricLockScreen(
    customLogoFile: File? = null,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val theme = LocalStudyTheme.current
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val title = stringResource(R.string.biometric_prompt_title)
    val subtitle = stringResource(R.string.biometric_prompt_subtitle)

    fun triggerPrompt() {
        if (activity == null || !BiometricLockHelper.canAuthenticate(context)) {
            onUnlocked()
            return
        }
        errorMsg = null
        BiometricLockHelper.authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = onUnlocked,
            onError = { msg -> errorMsg = msg }
        )
    }

    LaunchedEffect(Unit) {
        triggerPrompt()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Tactile3DCard(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            accentColor = theme.primary
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LearnTalentLogo(
                    customLogoFile = customLogoFile,
                    size = 72.dp,
                    cornerRadius = 18.dp
                )
                Text(
                    text = stringResource(R.string.brand_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.primary
                )
                Text(
                    text = "आपका स्टडी डेटा बायोमेट्रिक लॉक से सुरक्षित है",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = { triggerPrompt() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.unlock_with_biometric_btn))
                }
            }
        }
    }
}
