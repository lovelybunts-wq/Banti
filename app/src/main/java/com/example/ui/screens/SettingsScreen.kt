package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalFileStorage
import com.example.data.remote.DriveAuthOutcome
import com.example.data.remote.GoogleDriveBackupManager
import com.example.data.remote.GoogleDriveProfile
import com.example.security.BiometricLockHelper
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.LearnTalentLogo
import com.example.ui.components.StudentAvatar
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme
import com.example.ui.theme.StudyThemeOption
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingDrivePurpose { BACKUP, RESTORE, SIGN_IN }

@Composable
fun SettingsScreen(
    localSettings: LocalAppSettings,
    isOfflineSession: Boolean,
    signedInEmail: String?,
    signedInName: String?,
    signedInPhotoUrl: String?,
    uidLabel: String,
    isAdminUser: Boolean,
    isDriveBusy: Boolean,
    customLogoFile: File?,
    driveBackupManager: GoogleDriveBackupManager,
    onToggleLightDark: () -> Unit,
    onSelectTheme: (String) -> Unit,
    onToggleBiometricLock: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onToggleCloudSync: (Boolean) -> Unit,
    onOpenAdminPanel: () -> Unit,
    onSaveWhatsAppNumber: (String) -> Unit,
    onOpenWhatsAppRequest: () -> Unit,
    onPickCustomLogo: (Uri) -> Unit,
    onResetCustomLogo: () -> Unit,
    onDriveBackupWithToken: (String) -> Unit,
    onDriveRestoreWithToken: (String) -> Unit,
    onBuildBackupJson: () -> String,
    onRestoreBackupJson: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onSignedOut: () -> Unit,
    onSignedInFromOffline: () -> Unit,
    onDriveSignInSuccess: (profile: GoogleDriveProfile, accessToken: String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val isOnline = remember { LocalFileStorage.isOnline(context) }
    val biometricSupported = remember { BiometricLockHelper.canAuthenticate(context) }

    var adminPhoneInput by remember(localSettings.adminWhatsAppNumber) {
        mutableStateOf(localSettings.adminWhatsAppNumber)
    }
    var pendingDrivePurpose by remember { mutableStateOf(PendingDrivePurpose.BACKUP) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickCustomLogo(uri)
    }

    val localExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val json = onBuildBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                onShowMessage("लोकल बैकअप (.json) सफलतापूर्वक सेव हो गया!")
            }.onFailure { onShowMessage(it.message ?: "Export failed") }
        }
    }

    val localImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Empty file")
                onRestoreBackupJson(json)
            }.onFailure { onShowMessage(it.message ?: "Import failed") }
        }
    }

    fun completeDriveSignInFromSettings(token: String) {
        scope.launch {
            driveBackupManager.fetchGoogleUserProfile(token)
                .onSuccess { profile -> onDriveSignInSuccess(profile, token) }
                .onFailure {
                    onDriveSignInSuccess(GoogleDriveProfile("drive_${token.hashCode().toUInt()}", "", "Google User"), token)
                }
        }
    }

    val driveAuthResolutionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            val token = driveBackupManager.extractTokenFromIntent(activity, result.data)
            if (!token.isNullOrBlank()) {
                when (pendingDrivePurpose) {
                    PendingDrivePurpose.RESTORE -> onDriveRestoreWithToken(token)
                    PendingDrivePurpose.BACKUP -> onDriveBackupWithToken(token)
                    PendingDrivePurpose.SIGN_IN -> completeDriveSignInFromSettings(token)
                }
            } else {
                onShowMessage("Google Drive अनुमति नहीं मिल सकी।")
            }
        }
    }

    fun launchDriveAction(purpose: PendingDrivePurpose) {
        if (activity == null) {
            onShowMessage("Activity unavailable for Google Drive login.")
            return
        }
        pendingDrivePurpose = purpose
        scope.launch {
            when (val outcome = driveBackupManager.requestAccountAndDriveAuthorization(activity)) {
                is DriveAuthOutcome.Authorized -> when (purpose) {
                    PendingDrivePurpose.RESTORE -> onDriveRestoreWithToken(outcome.accessToken)
                    PendingDrivePurpose.BACKUP -> onDriveBackupWithToken(outcome.accessToken)
                    PendingDrivePurpose.SIGN_IN -> completeDriveSignInFromSettings(outcome.accessToken)
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    driveAuthResolutionLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
                }
                is DriveAuthOutcome.Error -> onShowMessage("${outcome.message} (आप .json बैकअप भी कर सकते हैं)")
            }
        }
    }

    Scaffold(containerColor = theme.background) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        LearnTalentBrandHeader(customLogoFile = customLogoFile, subtitle = "थीम, बैकअप, प्रोफाइल और सुरक्षा")
                        OutlinedButton(onClick = onToggleLightDark, shape = RoundedCornerShape(12.dp)) {
                            Icon(if (theme.isLight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (theme.isLight) "Dark" else "Light", color = theme.primary)
                        }
                    }
                }

                // 1. Account Card with Student Photo & Role
                item {
                    Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StudentAvatar(photoUrl = signedInPhotoUrl, displayName = signedInName ?: "Student", size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (!isOfflineSession) (signedInName?.ifBlank { null } ?: "Google User") else "Offline Local Mode",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onSurface
                                    )
                                    Tactile3DBadge(
                                        text = if (isAdminUser) "ADMIN" else if (isOfflineSession) "OFFLINE" else "STUDENT",
                                        color = if (isAdminUser) theme.tertiary else theme.primary
                                    )
                                }
                                Text(
                                    text = if (!isOfflineSession) (signedInEmail?.ifBlank { null } ?: "Cloud Sync Active") else "डेटा फोन में सुरक्षित है। क्लाउड सिंक के लिए Google लॉगिन करें।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.onSurfaceVariant
                                )
                                Text("ID: ${uidLabel.take(14)}...", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (isOfflineSession) {
                            Button(
                                onClick = {
                                    if (GoogleAuthHelper.isFirebaseInitialized(context)) {
                                        GoogleAuthHelper.onGoogleSignInClicked(
                                            context = context, credentialManager = credentialManager,
                                            onAuthSuccess = onSignedInFromOffline,
                                            onAuthError = { launchDriveAction(PendingDrivePurpose.SIGN_IN) }, scope = scope
                                        )
                                    } else launchDriveAction(PendingDrivePurpose.SIGN_IN)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("settings_google_signin_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google & Sync Cloud")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { GoogleAuthHelper.signOut(credentialManager, scope, onSignedOut) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("sign_out_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = theme.error)
                                Spacer(Modifier.width(8.dp))
                                Text("लॉगिन स्क्रीन पर जाएं", color = theme.error)
                            }
                        } else {
                            Button(
                                onClick = { GoogleAuthHelper.signOut(credentialManager, scope, onSignedOut) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.error.copy(alpha = 0.15f), contentColor = theme.error),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("sign_out_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("${stringResource(R.string.sign_out)} (लॉगआउट करें)")
                            }
                        }
                    }
                }

                // 2. Admin Panel Shortcut (Admin only)
                if (isAdminUser) {
                    item {
                        Tactile3DCard(accentColor = theme.tertiary, onClick = onOpenAdminPanel, modifier = Modifier.fillMaxWidth(), testTag = "settings_open_admin_btn") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    TactileIconOrb(icon = Icons.Rounded.AdminPanelSettings, tint = theme.tertiary, size = 44.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Open Admin Panel", style = MaterialTheme.typography.titleMedium, color = theme.tertiary)
                                        Text("Upload Shared PDF/HTML, Analytics, Student Results & Requests", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                    }
                                }
                                Tactile3DBadge(text = "MANAGE", color = theme.tertiary)
                            }
                        }
                    }
                }

                // 3. Google Drive Sync & JSON Export
                item {
                    Tactile3DCard(accentColor = theme.success, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                TactileIconOrb(icon = if (isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, tint = if (isOnline) theme.success else theme.secondary, size = 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Google Drive Sync ('Learn Talent' Folder)", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                    Text("नोट्स, PDF व डायरी आपके Drive में सेव होंगे", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                }
                            }
                            Switch(checked = localSettings.cloudSyncEnabled, onCheckedChange = onToggleCloudSync)
                        }
                        if (localSettings.lastDriveBackupMillis > 0L) {
                            Spacer(Modifier.height(8.dp))
                            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(localSettings.lastDriveBackupMillis))
                            Tactile3DBadge(text = "Last Drive Backup: $dateStr", color = theme.success)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { launchDriveAction(PendingDrivePurpose.BACKUP) }, enabled = !isDriveBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onPrimary),
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("drive_backup_btn")
                            ) {
                                if (isDriveBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = theme.onPrimary, strokeWidth = 2.dp)
                                else { Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Drive Backup") }
                            }
                            OutlinedButton(
                                onClick = { launchDriveAction(PendingDrivePurpose.RESTORE) }, enabled = !isDriveBusy,
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("drive_restore_btn")
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Drive Restore", color = theme.onSurface)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { localExportLauncher.launch("learn_talent_backup.json") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("Export .json", color = theme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { localImportLauncher.launch(arrayOf("*/*")) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("Import .json", color = theme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 4. WhatsApp Support Request (Number strictly hidden from non-admins)
                item {
                    Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TactileIconOrb(icon = Icons.AutoMirrored.Rounded.Send, tint = theme.secondary, size = 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("एडमिन सपोर्ट (Notes / Test Request)", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                Text("विषय, टॉपिक और सब-टॉपिक लिखकर सीधे एडमिन को मैसेज भेजें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (isAdminUser) {
                            OutlinedTextField(
                                value = adminPhoneInput, onValueChange = { adminPhoneInput = it },
                                label = { Text("Admin WhatsApp Number (+91...)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("settings_whatsapp_input")
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { onSaveWhatsAppNumber(adminPhoneInput) },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.secondary, contentColor = theme.onSecondary),
                                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Save Number")
                                }
                                OutlinedButton(onClick = onOpenWhatsAppRequest, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("settings_whatsapp_request_btn")) {
                                    Text("Test Request", color = theme.onSurface)
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = theme.success, modifier = Modifier.size(18.dp))
                                Text("एडमिन मोबाइल नंबर गोपनीय एवं सुरक्षित है।", style = MaterialTheme.typography.bodySmall, color = theme.success)
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onOpenWhatsAppRequest,
                                colors = ButtonDefaults.buttonColors(containerColor = theme.secondary, contentColor = theme.onSecondary),
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("settings_whatsapp_request_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp)); Text("एडमिन से टेस्ट / नोट्स मांगें")
                            }
                        }
                    }
                }

                // 5. Custom IMG Logo
                item {
                    Tactile3DCard(accentColor = theme.tertiary, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LearnTalentLogo(customLogoFile = customLogoFile, size = 46.dp, cornerRadius = 12.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("App IMG Logo", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                Text("कस्टम लोगो चुनें या डिफ़ॉल्ट लोगो इस्तेमाल करें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("pick_custom_logo_btn")
                            ) {
                                Icon(Icons.Rounded.Image, contentDescription = null, tint = theme.tertiary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Change IMG Logo", color = theme.onSurface)
                            }
                            if (customLogoFile != null) {
                                OutlinedButton(onClick = onResetCustomLogo, shape = RoundedCornerShape(12.dp)) { Text("Reset", color = theme.onSurfaceVariant) }
                            }
                        }
                    }
                }

                // 6. Theme Selector (Light & Dark eye-friendly presets)
                item {
                    Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TactileIconOrb(icon = Icons.Rounded.Palette, tint = theme.primary, size = 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.theme_selector_title), style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                Text("आंखों के अनुकूल Light और Dark थीम चुनें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudyThemeOption.entries.forEach { option ->
                                val selected = option.id == localSettings.themeOption.id
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(option.surfaceElevated)
                                        .border(if (selected) 2.dp else 1.dp, if (selected) option.primary else option.outline, RoundedCornerShape(14.dp))
                                        .clickable { onSelectTheme(option.id) }.padding(12.dp).testTag("theme_option_${option.id}"),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(option.primary).border(2.dp, option.secondary, CircleShape))
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(option.displayName, style = MaterialTheme.typography.titleSmall, color = option.onSurface)
                                            Text(option.subtitleHi, style = MaterialTheme.typography.bodySmall, color = option.onSurfaceVariant)
                                        }
                                    }
                                    if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = option.primary)
                                }
                            }
                        }
                    }
                }

                // 7. Biometric Lock
                item {
                    Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                TactileIconOrb(icon = Icons.Rounded.Fingerprint, tint = theme.secondary, size = 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.biometric_lock_label), style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                    Text(if (biometricSupported) "फिंगरप्रिंट / पिन से नोट्स सुरक्षित रखें" else "डिवाइस पर बायोमेट्रिक/PIN सेट करें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                }
                            }
                            Switch(checked = localSettings.biometricLockEnabled, onCheckedChange = onToggleBiometricLock, modifier = Modifier.testTag("biometric_lock_switch"))
                        }
                        if (localSettings.biometricLockEnabled) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = onLockNow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("lock_now_btn")) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Lock Vault Now", color = theme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}
