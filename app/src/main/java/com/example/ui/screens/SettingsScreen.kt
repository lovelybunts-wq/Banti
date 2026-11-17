package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalAppSettings
import com.example.security.BiometricLockHelper
import com.example.ui.DriveSyncUiState
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.StudentAvatar
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme
import com.example.ui.theme.StudyThemeOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    localSettings: LocalAppSettings,
    signedInEmail: String?,
    signedInName: String,
    signedInPhotoUrl: String?,
    uidLabel: String,
    isOfflineAccount: Boolean,
    isAdminUser: Boolean,
    isNetworkOnline: Boolean = true,
    pendingSyncCount: Int = 0,
    studentAttemptsCount: Int = 0,
    studentAveragePct: Double = 0.0,
    driveSyncState: DriveSyncUiState,
    onSignInWithGoogle: () -> Unit,
    onSwitchGoogleAccount: () -> Unit,
    onSyncNow: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onLoadSharedCatalogUrl: (String) -> Unit,
    onToggleLightDark: () -> Unit,
    onSelectTheme: (String) -> Unit,
    onToggleBiometricLock: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onOpenSupport: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current
    val biometricSupported = remember { BiometricLockHelper.canAuthenticate(context) }
    var sharedCatalogInput by rememberSaveable(localSettings.sharedCatalogUrl) {
        mutableStateOf(localSettings.sharedCatalogUrl)
    }
    val formattedLastBackup = remember(localSettings.lastDriveBackupMillis) {
        if (localSettings.lastDriveBackupMillis > 0L) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(Date(localSettings.lastDriveBackupMillis))
        } else null
    }

    Scaffold(containerColor = theme.background) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LearnTalentBrandHeader(customLogoFile = null, subtitle = "प्रोफाइल, Google Drive Sync और थीम")
                        OutlinedButton(onClick = onToggleLightDark, shape = RoundedCornerShape(12.dp)) {
                            Icon(
                                imageVector = if (theme.isLight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (theme.isLight) "Dark" else "Light", color = theme.primary)
                        }
                    }
                }

                // 1. Account & Google Drive Cloud Storage Card
                item {
                    Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StudentAvatar(photoUrl = signedInPhotoUrl, displayName = signedInName, size = 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = signedInName.ifBlank { "Student" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onSurface
                                    )
                                    Tactile3DBadge(
                                        text = when {
                                            isOfflineAccount -> "OFFLINE"
                                            isAdminUser -> "ADMIN"
                                            else -> "STUDENT"
                                        },
                                        color = when {
                                            isOfflineAccount -> theme.secondary
                                            isAdminUser -> theme.tertiary
                                            else -> theme.success
                                        }
                                    )
                                    Tactile3DBadge(
                                        text = if (isNetworkOnline) "ONLINE" else "OFFLINE",
                                        color = if (isNetworkOnline) theme.success else theme.secondary
                                    )
                                }
                                Text(
                                    text = signedInEmail?.takeIf { it.isNotBlank() }
                                        ?: "Offline Vault (डेटा इस डिवाइस पर सेव है)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.onSurfaceVariant
                                )
                                Text(
                                    text = "Vault ID: ${uidLabel.take(18)} • Tests: $studentAttemptsCount (Avg ${com.example.ui.MainViewModel.formatNum(studentAveragePct)}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        val (statusIcon, statusTint, statusLabel) = when (driveSyncState) {
                            is DriveSyncUiState.OfflineAccount -> Triple(
                                Icons.Rounded.CloudOff, theme.secondary,
                                "ऑफलाइन मोड: Gmail लॉगिन करके पूरा डेटा अपने Google Drive (LEARN TALENT/) में सेव करें।"
                            )
                            is DriveSyncUiState.Synced -> Triple(
                                Icons.Rounded.CloudDone, theme.success,
                                driveSyncState.detail
                            )
                            is DriveSyncUiState.Syncing -> Triple(
                                Icons.Rounded.CloudSync, theme.primary,
                                driveSyncState.message
                            )
                            is DriveSyncUiState.PendingOffline -> Triple(
                                Icons.Rounded.Sync, theme.tertiary,
                                driveSyncState.message
                            )
                            is DriveSyncUiState.NeedAuth -> Triple(
                                Icons.Rounded.AccountCircle, theme.tertiary,
                                driveSyncState.message
                            )
                            is DriveSyncUiState.Error -> Triple(
                                Icons.Rounded.CloudOff, theme.error,
                                driveSyncState.message
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusTint.copy(alpha = 0.08f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(statusIcon, contentDescription = null, tint = statusTint, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(statusLabel, style = MaterialTheme.typography.bodySmall, color = theme.onSurface)
                                if (formattedLastBackup != null) {
                                    Text(
                                        text = "Last Drive Sync: $formattedLastBackup",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = theme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (isOfflineAccount) {
                            Button(
                                onClick = onSignInWithGoogle,
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("settings_google_signin_btn")
                            ) {
                                Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google (Gmail व Drive जोड़ें)")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onSignedOut,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("sign_out_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("लॉगिन स्क्रीन पर जाएं (Return to Login)")
                            }
                        } else {
                            Text(
                                text = if (isAdminUser) {
                                    "Google Drive: LEARN TALENT/ADMIN/ (PUBLISHED TESTS • PDF NOTES • STUDY MATERIAL • SUBJECTS) & STUDENTS/"
                                } else {
                                    "Google Drive: LEARN TALENT/STUDENTS/ (STUDENT DATA • TEST RESULTS • PROGRESS REPORTS • PERSONAL UPLOADS)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.onSurfaceVariant
                            )
                            if (pendingSyncCount > 0) {
                                Spacer(Modifier.height(4.dp))
                                Tactile3DBadge(text = "$pendingSyncCount changes queued for sync", color = theme.tertiary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = onSyncNow,
                                    enabled = driveSyncState !is DriveSyncUiState.Syncing,
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("settings_sync_now_btn")
                                ) {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync Now")
                                }
                                OutlinedButton(
                                    onClick = onRestoreFromDrive,
                                    enabled = driveSyncState !is DriveSyncUiState.Syncing,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("settings_restore_drive_btn")
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Sync to Google Drive", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                    Text("ऑनलाइन होने पर बदलाव अपने-आप Drive पर सेव होंगे", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = localSettings.cloudSyncEnabled,
                                    onCheckedChange = onToggleAutoSync,
                                    modifier = Modifier.testTag("settings_auto_sync_switch")
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = onSwitchGoogleAccount,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("settings_switch_account_btn")
                                ) {
                                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Switch Account")
                                }
                                Button(
                                    onClick = onSignedOut,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.error.copy(alpha = 0.14f),
                                        contentColor = theme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("sign_out_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.sign_out))
                                }
                            }
                        }
                    }
                }

                // 2. Shared Admin Catalog Import Card (via Public Drive JSON link or File ID)
                item {
                    Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TactileIconOrb(icon = Icons.Rounded.Link, tint = theme.secondary, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Admin Study Catalog Sync", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                Text("Admin द्वारा शेयर किया गया Google Drive Catalog लिंक या File ID डालें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = sharedCatalogInput,
                            onValueChange = { sharedCatalogInput = it },
                            label = { Text("Drive Catalog Link / File ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_catalog_url_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onLoadSharedCatalogUrl(sharedCatalogInput) },
                            enabled = sharedCatalogInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.secondary, contentColor = theme.onPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("settings_sync_catalog_btn")
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh Study Catalog")
                        }
                    }
                }

                // 3. Support Card
                item {
                    Tactile3DCard(
                        accentColor = theme.secondary,
                        onClick = onOpenSupport,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "settings_support_btn"
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                TactileIconOrb(icon = Icons.AutoMirrored.Rounded.HelpOutline, tint = theme.secondary, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Support (सहायता / अनुरोध)", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                    Text("नए नोट्स, टेस्ट या सहायता के लिए संदेश भेजें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                }
                            }
                            Tactile3DBadge(text = "SUPPORT", color = theme.secondary)
                        }
                    }
                }

                // 4. Admin Panel Shortcut (strictly visible to Admin only)
                if (isAdminUser) {
                    item {
                        Tactile3DCard(
                            accentColor = theme.tertiary,
                            onClick = onOpenAdminPanel,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "settings_open_admin_btn"
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    TactileIconOrb(icon = Icons.Rounded.AdminPanelSettings, tint = theme.tertiary, size = 44.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Open Admin Panel", style = MaterialTheme.typography.titleMedium, color = theme.tertiary)
                                        Text("Upload Shared PDF/HTML, Publish Drive Catalog, Analytics & Requests", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                    }
                                }
                                Tactile3DBadge(text = "MANAGE", color = theme.tertiary)
                            }
                        }
                    }
                }

                // 5. Eye-Friendly Theme Selector
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
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

                // 6. Biometric App Lock
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
