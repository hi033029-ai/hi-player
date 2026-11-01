package com.example.ui.screens

import com.example.ui.theme.hiContainerBorder

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.AppThemeMode
import kotlin.math.roundToInt

enum class OnboardingStep {
    SPLASH_LOGO,
    WELCOME_NOTE,
    CHOOSE_THEME,
    PERMISSIONS,
    TEXT_SIZE
}

@Composable
fun SplashScreen(
    isFirstLaunch: Boolean,
    onFinish: (selectedTheme: AppThemeMode, textSizeSp: Int) -> Unit,
    onSkip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by rememberSaveable { mutableStateOf(OnboardingStep.SPLASH_LOGO) }
    var selectedTheme by rememberSaveable { mutableStateOf<AppThemeMode>(AppThemeMode.SYSTEM) }
    var textSizeSp by rememberSaveable { mutableStateOf(14) }

    var finishing by rememberSaveable { mutableStateOf(false) }
    fun finishOnce() {
        if (!finishing) {
            finishing = true
            onFinish(selectedTheme, textSizeSp)
        }
    }
    BackHandler(enabled = currentStep != OnboardingStep.SPLASH_LOGO && currentStep != OnboardingStep.WELCOME_NOTE) {
        currentStep = when (currentStep) {
            OnboardingStep.CHOOSE_THEME -> OnboardingStep.WELCOME_NOTE
            OnboardingStep.PERMISSIONS -> OnboardingStep.CHOOSE_THEME
            else -> OnboardingStep.PERMISSIONS
        }
    }

    // Only persisted settings gate entry. Branding must not impose an artificial wait.
    LaunchedEffect(Unit) {
        if (currentStep != OnboardingStep.SPLASH_LOGO) return@LaunchedEffect
        if (isFirstLaunch) currentStep = OnboardingStep.WELCOME_NOTE else finishOnce()
    }

    com.example.ui.theme.HiPlayerTheme(themeMode = selectedTheme) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            com.example.ui.theme.LocalHiPalette.current.background,
                            com.example.ui.theme.LocalHiPalette.current.background
                        )
                    )
                )
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    OnboardingStep.SPLASH_LOGO -> {
                        SplashLogoView(
                            scale = 1f,
                            alpha = 1f
                        )
                    }

                    OnboardingStep.WELCOME_NOTE -> {
                        WelcomeNoteView(
                            onContinue = {
                                currentStep = OnboardingStep.CHOOSE_THEME
                            },
                            onSkip = {
                                if (!finishing) { finishing = true; onSkip() }
                            }
                        )
                    }

                    OnboardingStep.CHOOSE_THEME -> {
                        ChooseThemeView(
                            selectedTheme = selectedTheme,
                            onThemeSelected = { selectedTheme = it },
                            onBack = { currentStep = OnboardingStep.WELCOME_NOTE },
                            onProceed = {
                                currentStep = OnboardingStep.PERMISSIONS
                            }
                        )
                    }

                    OnboardingStep.PERMISSIONS -> {
                        PermissionsSetupView(
                            selectedTheme = selectedTheme,
                            onComplete = {
                                currentStep = OnboardingStep.TEXT_SIZE
                            },
                            onSkip = { finishOnce() },
                        )
                    }

                    OnboardingStep.TEXT_SIZE -> {
                        TextSizeSetupView(
                            selectedTheme = selectedTheme,
                            textSizeSp = textSizeSp,
                            onTextSizeChanged = { textSizeSp = it },
                            onBack = { currentStep = OnboardingStep.PERMISSIONS },
                            onComplete = { finishOnce() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSizeSetupView(
    selectedTheme: AppThemeMode,
    textSizeSp: Int,
    onTextSizeChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val accent = com.example.ui.theme.getPaletteForTheme(selectedTheme, androidx.compose.foundation.isSystemInDarkTheme()).primary
    var footerHeight by remember { mutableStateOf(180.dp) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .padding(bottom = footerHeight + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            com.example.ui.components.HiPlayerLogoBadge(size = 58.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Choose Text Size", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.LocalHiPalette.current.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Set the size used throughout Hi Player. You can change it later in Settings.",
                fontSize = 13.sp,
                color = com.example.ui.theme.LocalHiPalette.current.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.LocalHiPalette.current.surface),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Hi Player preview", fontSize = textSizeSp.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Your library, player controls, folders, and settings will use this size.", fontSize = textSizeSp.sp, color = com.example.ui.theme.LocalHiPalette.current.textSecondary)
                    Slider(
                        value = textSizeSp.toFloat(),
                        onValueChange = { onTextSizeChanged(it.roundToInt()) },
                        valueRange = 12f..24f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_text_size_slider")
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Small", fontSize = 11.sp, color = com.example.ui.theme.LocalHiPalette.current.textSecondary)
                        Text("${textSizeSp}sp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                        Text("Large", fontSize = 11.sp, color = com.example.ui.theme.LocalHiPalette.current.textSecondary)
                    }
                }
            }
        }
        OnboardingActions(
            onHeightChanged = { footerHeight = it },
            primaryLabel = "Enter Hi Player", onPrimary = onComplete, accent = accent,
            secondaryLabel = "Back", onSecondary = onBack, primaryTag = "onboarding_finish_button",
        )
    }
}

@Composable
private fun SplashLogoView(
    scale: Float,
    alpha: Float
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale)
            .alpha(alpha)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Soft background glow
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                com.example.ui.theme.LocalHiPalette.current.primary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )

            com.example.ui.components.HiPlayerLogoBadge(
                size = 82.dp,
                shape = RoundedCornerShape(percent = 38)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Hi Player",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = com.example.ui.theme.LocalHiPalette.current.textPrimary,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "4K UHD Blu-ray & Hi-Fi Audio",
            fontSize = 12.sp,
            color = com.example.ui.theme.LocalHiPalette.current.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun WelcomeNoteView(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    var footerHeight by remember { mutableStateOf(180.dp) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = footerHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header with Logo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Color(0xFF004080).copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFF7E9EB8), RoundedCornerShape(18.dp))
                    .background(Color(0xFFB5D8EB)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = com.example.ui.components.rememberHiPlayerLogoPainter(),
                    contentDescription = "Hi Player Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Welcome to Hi Player",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.LocalHiPalette.current.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your videos, music, browser and files — together, with controls that make them yours.",
                fontSize = 14.sp,
                color = com.example.ui.theme.LocalHiPalette.current.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Feature Highlights
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureRow(Icons.Default.Tv, "Video, HDR & picture controls",
                    "Play local videos and streams, resume watching, choose subtitles and adjust picture presets. Format and HDR support depend on your device.")
                FeatureRow(Icons.Default.Headphones, "Music & background listening",
                    "Browse songs, albums and folders, tune the equalizer, and listen with playback controls in notifications.")
                FeatureRow(Icons.Default.Security, "Built-in browser",
                    "Browse with tabs, ad and tracker filtering, supported-page skipping, downloads and Play in Hi Player. Website compatibility varies.")
                FeatureRow(Icons.Default.Folder, "Files & personal connections",
                    "Organise files, use the recycle bin and archive tools, and connect supported personal drives or media servers.")
                FeatureRow(Icons.Default.Palette, "Make it yours",
                    "Choose themes, text size, library views, sorting and playback settings. Helpful tips appear when you use each feature.")
                FeatureRow(Icons.Default.CheckCircle, "Start your way",
                    "Media access is optional. Skip setup to explore, then allow access from the library whenever you're ready.")
            }
        }

        }
        OnboardingActions(
            onHeightChanged = { footerHeight = it },
            primaryLabel = "Continue", onPrimary = onContinue, accent = com.example.ui.theme.LocalHiPalette.current.primary,
            secondaryLabel = "Skip setup", onSecondary = onSkip, primaryTag = "welcome_continue_button",
            secondaryTag = "welcome_skip_button",
        )
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.LocalHiPalette.current.surfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
                    .background(com.example.ui.theme.LocalHiPalette.current.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = com.example.ui.theme.LocalHiPalette.current.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = com.example.ui.theme.LocalHiPalette.current.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = com.example.ui.theme.LocalHiPalette.current.textSecondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun ChooseThemeView(
    selectedTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
    onProceed: () -> Unit
) {
    val selectedPalette = com.example.ui.theme.getPaletteForTheme(selectedTheme, androidx.compose.foundation.isSystemInDarkTheme())

    var footerHeight by remember { mutableStateOf(180.dp) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .padding(bottom = footerHeight + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp)).hiContainerBorder(RoundedCornerShape(16.dp))
                    .background(selectedPalette.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = com.example.ui.components.rememberHiPlayerLogoPainter(),
                    contentDescription = "Hi Player Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Choose Your Theme",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.LocalHiPalette.current.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Select your preferred theme. Set it as default for the whole app.",
                fontSize = 10.sp,
                color = com.example.ui.theme.LocalHiPalette.current.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Themes in Horizontal Form
            com.example.ui.components.HorizontalThemeSelector(
                selectedTheme = selectedTheme,
                onThemeSelected = onThemeSelected,
                showCustom = false,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Currently Selected Theme Detail Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.LocalHiPalette.current.surfaceElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(selectedPalette.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = if (selectedPalette.isDark) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = selectedTheme.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = com.example.ui.theme.LocalHiPalette.current.textPrimary
                        )
                        Text(
                            text = selectedTheme.description,
                            fontSize = 10.sp,
                            color = com.example.ui.theme.LocalHiPalette.current.textSecondary
                        )
                    }
                }
            }
        }

        }
        OnboardingActions(
            onHeightChanged = { footerHeight = it },
            primaryLabel = "Apply & Continue", onPrimary = onProceed, accent = selectedPalette.primary,
            secondaryLabel = "Back", onSecondary = onBack, primaryTag = "theme_proceed_button",
            contentColor = if (selectedPalette.isDark) Color.Black else Color.White,
        )
    }
}

@Composable
private fun ThemeOptionCard(
    theme: AppThemeMode,
    title: String,
    subtitle: String,
    bgPreview: Color,
    accentPreview: Color,
    textPreview: Color,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("theme_card_${theme.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) com.example.ui.theme.LocalHiPalette.current.surface else com.example.ui.theme.LocalHiPalette.current.surfaceElevated
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgPreview)
                        .border(1.dp, Color(0x33000000), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(accentPreview)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = com.example.ui.theme.LocalHiPalette.current.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = com.example.ui.theme.LocalHiPalette.current.textSecondary
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = com.example.ui.theme.LocalHiPalette.current.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionsSetupView(
    selectedTheme: AppThemeMode,
    onComplete: () -> Unit,
    onSkip: () -> Unit = onComplete,
) {
    val context = LocalContext.current

    var requesting by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val permissionsToRequest = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        list.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        requesting = false
        onComplete() // Declining permissions does not block entry; allow later from the library.
    }

    val accentColor = com.example.ui.theme.getPaletteForTheme(selectedTheme, androidx.compose.foundation.isSystemInDarkTheme()).primary

    var footerHeight by remember { mutableStateOf(180.dp) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .padding(bottom = footerHeight + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Optional permissions",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.LocalHiPalette.current.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Allow media access to scan your library and notifications for playback controls. You can choose Not now and grant access later.",
                fontSize = 14.sp,
                color = com.example.ui.theme.LocalHiPalette.current.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            PermissionCard(
                icon = Icons.Default.Folder,
                title = "Storage & Media Access",
                description = "Scan and playback 4K video files and audio from your device.",
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Playback Notifications",
                description = "Background playback controls and status notifications.",
                accentColor = accentColor
            )
        }

        }
        OnboardingActions(
            onHeightChanged = { footerHeight = it },
            primaryLabel = if (requesting) "Opening permissions…" else "Allow permissions",
            onPrimary = {
                val hasAll = permissionsToRequest.all {
                    androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (hasAll) onComplete() else {
                    requesting = true
                    permissionMessage = null
                    try { launcher.launch(permissionsToRequest) }
                    catch (error: Exception) {
                        requesting = false
                        permissionMessage = com.example.ui.feedback.UserFacingErrors.report("permission_prompt", error,
                            "Couldn't open permissions. Try again, or choose Not now and allow access later in Settings.")
                    }
                }
            },
            accent = accentColor, primaryEnabled = !requesting,
            secondaryLabel = "Not now", onSecondary = onSkip, secondaryEnabled = !requesting,
            primaryTag = "grant_permissions_button", message = permissionMessage,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.LocalHiPalette.current.surfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LocalHiPalette.current.surfaceBorder),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp)).hiContainerBorder(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = com.example.ui.theme.LocalHiPalette.current.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = com.example.ui.theme.LocalHiPalette.current.textSecondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/** Shared bottom anchor and 48dp minimum actions; labels may grow for accessibility. */
@Composable
private fun BoxScope.OnboardingActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    accent: Color,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryTag: String,
    secondaryTag: String = "onboarding_secondary_button",
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    contentColor: Color? = null,
    message: String? = null,
    onHeightChanged: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { with(density) { onHeightChanged(it.height.toDp()) } }
            .background(com.example.ui.theme.LocalHiPalette.current.background).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        message?.let { Text(it, color = com.example.ui.theme.LocalHiPalette.current.textSecondary, fontSize = 14.sp) }
        TextButton(onClick = onSecondary, enabled = secondaryEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(secondaryTag)) {
            Text(secondaryLabel, color = com.example.ui.theme.LocalHiPalette.current.primary, fontSize = 14.sp)
        }
        Button(onClick = onPrimary, enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(primaryTag),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = contentColor ?: com.example.ui.theme.contrastContentColor(accent, com.example.ui.theme.LocalHiPalette.current.surface))) {
            Text(primaryLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
