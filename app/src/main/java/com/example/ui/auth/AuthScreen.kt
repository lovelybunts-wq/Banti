package com.example.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.data.remote.DriveAuthOutcome
import com.example.data.remote.GoogleDriveBackupManager
import com.example.data.remote.GoogleDriveProfile
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AuthScreen(
    customLogoFile: File?,
    driveBackupManager: GoogleDriveBackupManager = remember { GoogleDriveBackupManager() },
    onAuthSuccess: () -> Unit,
    onDriveSignInSuccess: (profile: GoogleDriveProfile, accessToken: String) -> Unit,
    onContinueOffline: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val theme = LocalStudyTheme.current

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun completeDriveSignIn(token: String) {
        scope.launch {
            driveBackupManager.fetchGoogleUserProfile(token)
                .onSuccess { profile ->
                    isLoading = false
                    onDriveSignInSuccess(profile, token)
                }
                .onFailure {
                    val fallbackProfile = GoogleDriveProfile(
                        id = "drive_${token.hashCode().toUInt()}",
                        email = "",
                        name = "Google User"
                    )
                    isLoading = false
                    onDriveSignInSuccess(fallbackProfile, token)
                }
        }
    }

    val driveResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            val token = driveBackupManager.extractTokenFromIntent(activity, result.data)
            if (!token.isNullOrBlank()) {
                completeDriveSignIn(token)
            } else {
                isLoading = false
                errorMessage = "Google खाता अनुमति नहीं मिल सकी। कृपया पुनः प्रयास करें।"
            }
        } else {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        GoogleAuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(theme.background, theme.surface)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LearnTalentBrandHeader(
                customLogoFile = customLogoFile,
                subtitle = "सीमित सोर्स • बार-बार रिवीजन • स्मार्ट सिलेक्शन",
                logoSize = 52.dp
            )

            Spacer(Modifier.height(18.dp))

            Tactile3DCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = theme.primary,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_study_hero_banner),
                    contentDescription = "Learn Talent study desk illustration",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureRow(
                    icon = Icons.Rounded.AutoStories,
                    title = "PDF, HTML, Image Notes & Revision Diary",
                    subtitle = "विषय, टॉपिक, सब-टॉपिक एवं सोर्स के नाम के साथ व्यवस्थित फोल्डर"
                )
                FeatureRow(
                    icon = Icons.Rounded.Quiz,
                    title = "HTML Mock Test & Practice MCQs",
                    subtitle = "सबमिट करते ही स्कोर और मार्क्स तुरंत सेव"
                )
                FeatureRow(
                    icon = Icons.Rounded.CloudDone,
                    title = "Google Drive Backup + Offline Vault",
                    subtitle = "बिना लॉगिन के ऑफलाइन उपयोग करें या Google से सिंक करें"
                )
            }

            Spacer(Modifier.height(20.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = theme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    if (GoogleAuthHelper.isFirebaseInitialized(context)) {
                        GoogleAuthHelper.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                if (activity != null) {
                                    scope.launch {
                                        when (val outcome = driveBackupManager.requestAccountAndDriveAuthorization(activity)) {
                                            is DriveAuthOutcome.Authorized -> completeDriveSignIn(outcome.accessToken)
                                            is DriveAuthOutcome.NeedsResolution -> {
                                                driveResolutionLauncher.launch(
                                                    IntentSenderRequest.Builder(outcome.intentSender).build()
                                                )
                                            }
                                            is DriveAuthOutcome.Error -> {
                                                isLoading = false
                                                errorMessage = "$msg\n(${outcome.message})"
                                            }
                                        }
                                    }
                                } else {
                                    isLoading = false
                                    errorMessage = msg
                                }
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    } else if (activity != null) {
                        scope.launch {
                            when (val outcome = driveBackupManager.requestAccountAndDriveAuthorization(activity)) {
                                is DriveAuthOutcome.Authorized -> completeDriveSignIn(outcome.accessToken)
                                is DriveAuthOutcome.NeedsResolution -> {
                                    driveResolutionLauncher.launch(
                                        IntentSenderRequest.Builder(outcome.intentSender).build()
                                    )
                                }
                                is DriveAuthOutcome.Error -> {
                                    isLoading = false
                                    errorMessage = "${outcome.message}\n(आप नीचे ऑफलाइन बटन से भी ऐप उपयोग कर सकते हैं)"
                                }
                            }
                        }
                    } else {
                        isLoading = false
                        errorMessage = "Activity unavailable for Google Sign-In"
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = theme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.sign_in_google),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onContinueOffline,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, theme.secondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("continue_offline_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.OfflinePin,
                    contentDescription = null,
                    tint = theme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Continue Offline (बिना लॉगिन के अंदर जाएं)",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.secondary
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val theme = LocalStudyTheme.current
    Tactile3DCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = theme.secondary,
        lipDepth = 3.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TactileIconOrb(icon = icon, tint = theme.primary, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceVariant
                )
            }
        }
    }
}
