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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.data.local.LocalAppSettings
import com.example.security.BiometricLockHelper
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.StudentAvatar
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme
import com.example.ui.theme.StudyThemeOption

@Composable
fun SettingsScreen(
    localSettings: LocalAppSettings,
    signedInEmail: String?,
    signedInName: String,
    signedInPhotoUrl: String?,
    uidLabel: String,
    isAdminUser: Boolean,
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
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val biometricSupported = remember { BiometricLockHelper.canAuthenticate(context) }

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
                        LearnTalentBrandHeader(customLogoFile = null, subtitle = "प्रोफाइल, थीम और सपोर्ट")
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

                // 1. Logged-in Google Profile Card + Logout ONLY
                item {
                    Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StudentAvatar(photoUrl = signedInPhotoUrl, displayName = signedInName, size = 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = signedInName.ifBlank { "Student" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onSurface
                                    )
                                    Tactile3DBadge(
                                        text = if (isAdminUser) "ADMIN" else "ONLINE",
                                        color = if (isAdminUser) theme.tertiary else theme.success
                                    )
                                }
                                Text(
                                    text = signedInEmail?.takeIf { it.isNotBlank() } ?: "Google Cloud Sync Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.onSurfaceVariant
                                )
                                Text(
                                    text = "ID: ${uidLabel.take(16)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { GoogleAuthHelper.signOut(credentialManager, scope, onSignedOut) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.error.copy(alpha = 0.14f),
                                contentColor = theme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sign_out_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${stringResource(R.string.sign_out)} (लॉगआउट करें)")
                        }
                    }
                }

                // 2. Support Card (Clean single Support button without exposing Admin phone or config)
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

                // 3. Admin Panel Shortcut (strictly visible to Admin only)
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
                                        Text("Upload Shared PDF/HTML, Analytics, Student Results & Requests", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                    }
                                }
                                Tactile3DBadge(text = "MANAGE", color = theme.tertiary)
                            }
                        }
                    }
                }

                // 4. Eye-Friendly Theme Selector
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

                // 5. Biometric App Lock
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
