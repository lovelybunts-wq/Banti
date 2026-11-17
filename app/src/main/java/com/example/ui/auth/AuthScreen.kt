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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.auth.GoogleSessionUser
import com.example.auth.GoogleSignInResult
import com.example.data.local.UserPreferencesManager
import com.example.data.remote.GoogleDriveBackupManager
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.BrandColors
import com.example.ui.theme.LocalStudyTheme
import com.example.ui.theme.NotoSansDevanagariFontFamily
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onSignedIn: (GoogleSessionUser, accessToken: String?) -> Unit,
    onContinueOffline: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val driveManager = remember { GoogleDriveBackupManager() }
    val prefs = remember { UserPreferencesManager(context.applicationContext) }
    val theme = LocalStudyTheme.current

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requestedAdminLogin by remember { mutableStateOf(false) }
    var adminDeniedPendingSession by remember { mutableStateOf<Pair<GoogleSessionUser, String?>?>(null) }

    fun handleResolvedSignIn(session: GoogleSessionUser, token: String?) {
        isLoading = false
        val verifiedAdmin = GoogleAuthHelper.isAdminEmail(session.email, session.emailVerified)
        if (requestedAdminLogin && !verifiedAdmin) {
            requestedAdminLogin = false
            adminDeniedPendingSession = session to token
        } else {
            requestedAdminLogin = false
            onSignedIn(session, token)
        }
    }

    val fallbackSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (activity == null) {
            isLoading = false
            errorMessage = "Activity unavailable for Google login."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            when (
                val res = GoogleAuthHelper.completeFallbackSignInFromIntent(
                    activity = activity,
                    resultCode = result.resultCode,
                    data = result.data,
                    driveManager = driveManager,
                    prefs = prefs
                )
            ) {
                is GoogleSignInResult.Success -> handleResolvedSignIn(res.session, res.accessToken)
                is GoogleSignInResult.Error -> {
                    isLoading = false
                    requestedAdminLogin = false
                    errorMessage = res.message
                }
                is GoogleSignInResult.NeedsConsentUi,
                is GoogleSignInResult.NeedsFallbackSignInUi -> isLoading = false
            }
        }
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (activity == null) {
            isLoading = false
            errorMessage = "Activity unavailable for Google login."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            when (
                val res = GoogleAuthHelper.completeSignInFromIntent(
                    activity = activity,
                    resultCode = result.resultCode,
                    data = result.data,
                    driveManager = driveManager,
                    prefs = prefs
                )
            ) {
                is GoogleSignInResult.Success -> handleResolvedSignIn(res.session, res.accessToken)
                is GoogleSignInResult.NeedsFallbackSignInUi -> {
                    fallbackSignInLauncher.launch(res.signInIntent)
                }
                is GoogleSignInResult.Error -> {
                    isLoading = false
                    requestedAdminLogin = false
                    errorMessage = res.message
                }
                is GoogleSignInResult.NeedsConsentUi -> isLoading = false
            }
        }
    }

    fun triggerGoogleSignIn(asAdminAttempt: Boolean) {
        if (activity == null) {
            errorMessage = "Google Sign-In के लिए Activity उपलब्ध नहीं है।"
            return
        }
        requestedAdminLogin = asAdminAttempt
        errorMessage = null
        isLoading = true
        scope.launch {
            when (val res = GoogleAuthHelper.startGoogleSignIn(activity, driveManager, prefs)) {
                is GoogleSignInResult.Success -> handleResolvedSignIn(res.session, res.accessToken)
                is GoogleSignInResult.NeedsConsentUi -> {
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(res.intentSender).build())
                }
                is GoogleSignInResult.NeedsFallbackSignInUi -> {
                    fallbackSignInLauncher.launch(res.signInIntent)
                }
                is GoogleSignInResult.Error -> {
                    isLoading = false
                    requestedAdminLogin = false
                    errorMessage = res.message
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BrandColors.DeepNavy,
                        Color(0xFF0A2B5E),
                        BrandColors.RoyalBlue,
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Brand Identity Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LearnTalentBrandHeader(
                    customLogoFile = null,
                    subtitle = "",
                    logoSize = 48.dp,
                    lightTextOnDarkBg = true,
                )
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = BrandColors.PureWhite.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, BrandColors.SkyBlue.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "v2.0 EdTech",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandColors.SkyBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Main Hero Headline
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = BrandColors.PureWhite)) {
                            append("LEARN SMARTER.\n")
                        }
                        withStyle(SpanStyle(color = BrandColors.PremiumGold)) {
                            append("ACHIEVE BIGGER.")
                        }
                    },
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 31.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    modifier = Modifier.testTag("welcome_hero_headline")
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Your Complete Learning & Examination Platform.",
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandColors.SkyBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "अपनी परीक्षा की तैयारी को दें एक नई दिशा। Mock Tests, Study Materials और Performance Analysis के साथ आगे बढ़ें।",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = NotoSansDevanagariFontFamily,
                        lineHeight = 21.sp
                    ),
                    color = Color(0xFFDCEBFF)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 3D Educational Illustration Showcase Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0A224B),
                border = BorderStroke(1.2.dp, BrandColors.GoldBorder()),
                shadowElevation = 10.dp,
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_welcome_hero),
                        contentDescription = "Learn Talent 3D books, online CBT exam laptop, globe, and stationery illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = BrandColors.DeepNavy.copy(alpha = 0.86f),
                        border = BorderStroke(1.dp, BrandColors.SkyBlue.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "HTML CBT Tests • NCERT PDFs • Cloud Drive",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.PureWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF450A0A),
                    border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFEE2E2)
                        )
                    }
                }
            }

            // Primary Continue with Google CTA
            Button(
                onClick = { triggerGoogleSignIn(asAdminAttempt = false) },
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColors.PureWhite,
                    contentColor = BrandColors.DeepNavy,
                    disabledContainerColor = BrandColors.PureWhite.copy(alpha = 0.7f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = BrandColors.RoyalBlue,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Signing in with Google...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BrandColors.RoyalBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandColors.RoyalBlue
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Dual Role Entry Cards (Student Login & Admin Login)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoleEntryCard(
                    modifier = Modifier.weight(1f),
                    title = "STUDENT LOGIN",
                    subtitle = "Study & Practice",
                    icon = Icons.Rounded.School,
                    accent = BrandColors.SkyBlue,
                    testTag = "student_login_card",
                    enabled = !isLoading,
                    onClick = { triggerGoogleSignIn(asAdminAttempt = false) }
                )
                RoleEntryCard(
                    modifier = Modifier.weight(1f),
                    title = "ADMIN LOGIN",
                    subtitle = "Manage & Upload",
                    icon = Icons.Rounded.AdminPanelSettings,
                    accent = BrandColors.PremiumGold,
                    testTag = "admin_login_card",
                    enabled = !isLoading,
                    onClick = { triggerGoogleSignIn(asAdminAttempt = true) }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Subtle Offline Mode Fallback
            TextButton(
                onClick = {
                    errorMessage = null
                    scope.launch {
                        prefs.setOfflineModeAccepted(true)
                        onContinueOffline()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.testTag("continue_offline_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = Color(0xFFBBD6FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Continue Offline • Sync to Google Drive Later",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD6E7FF)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Bottom Trust Badges
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BrandColors.PureWhite.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, BrandColors.PureWhite.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrustBadgeItem(Icons.Rounded.VerifiedUser, "Secure Login", BrandColors.PremiumGold)
                    TrustBadgeItem(Icons.Rounded.CloudSync, "Multi-Device Sync", BrandColors.SkyBlue)
                    TrustBadgeItem(Icons.Rounded.Bolt, "Fast & Reliable", Color(0xFF4ADE80))
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }

    val deniedInfo = adminDeniedPendingSession
    if (deniedInfo != null) {
        val (session, token) = deniedInfo
        AlertDialog(
            onDismissRequest = {
                adminDeniedPendingSession = null
                onSignedIn(session, token)
            },
            containerColor = theme.surfaceElevated,
            title = {
                Text(
                    text = "Admin Access Restricted",
                    color = theme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "आपने ${session.email} से लॉगिन किया है। Admin Panel केवल अधिकृत Super Admin (${UserPreferencesManager.ADMIN_EMAIL}) के लिए सुरक्षित है।\n\nआपका खाता Student मोड में सुरक्षित रूप से शुरू हो रहा है।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminDeniedPendingSession = null
                        onSignedIn(session, token)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.RoyalBlue, contentColor = BrandColors.PureWhite)
                ) {
                    Text("Continue as Student")
                }
            }
        )
    }
}

private fun BrandColors.GoldBorder() = PremiumGold.copy(alpha = 0.55f)

@Composable
private fun RoleEntryCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Tactile3DCard(
        modifier = modifier,
        accentColor = accent,
        cornerRadius = 18.dp,
        lipDepth = 4.dp,
        onClick = if (enabled) onClick else null,
        testTag = testTag,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TactileIconOrb(icon = icon, tint = accent, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalStudyTheme.current.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalStudyTheme.current.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrustBadgeItem(
    icon: ImageVector,
    label: String,
    tint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFEAF2FF),
            textAlign = TextAlign.Center
        )
    }
}
