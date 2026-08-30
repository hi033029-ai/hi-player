package com.example.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(OnboardingStep.SPLASH_LOGO) }
    var selectedTheme by remember { mutableStateOf<AppThemeMode>(AppThemeMode.WARM_SUNSET_LIGHT) }
    var textSizeSp by remember { mutableStateOf(14) }

    // Smooth In & Out Animation for Logo
    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Logo Entrance (In)
        logoAlpha.animateTo(1f, animationSpec = tween(500, easing = LinearOutSlowInEasing))
        logoScale.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))

        if (isFirstLaunch) {
            delay(900)
            currentStep = OnboardingStep.WELCOME_NOTE
        } else {
            delay(1100)
            // Logo Exit (Out)
            logoAlpha.animateTo(0f, animationSpec = tween(400, easing = FastOutSlowInEasing))
            logoScale.animateTo(1.12f, animationSpec = tween(400, easing = FastOutSlowInEasing))
            onFinish(selectedTheme, textSizeSp)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8FAFC)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            },
            label = "onboarding_step"
        ) { step ->
            when (step) {
                OnboardingStep.SPLASH_LOGO -> {
                    SplashLogoView(
                        scale = logoScale.value,
                        alpha = logoAlpha.value
                    )
                }

                OnboardingStep.WELCOME_NOTE -> {
                    WelcomeNoteView(
                        onContinue = {
                            currentStep = OnboardingStep.CHOOSE_THEME
                        }
                    )
                }

                OnboardingStep.CHOOSE_THEME -> {
                    ChooseThemeView(
                        selectedTheme = selectedTheme,
                        onThemeSelected = { selectedTheme = it },
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
                        }
                    )
                }

                OnboardingStep.TEXT_SIZE -> {
                    TextSizeSetupView(
                        selectedTheme = selectedTheme,
                        textSizeSp = textSizeSp,
                        onTextSizeChanged = { textSizeSp = it },
                        onComplete = { onFinish(selectedTheme, textSizeSp) }
                    )
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
    onComplete: () -> Unit
) {
    val accent = com.example.ui.theme.getPaletteForTheme(selectedTheme).primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Memory, contentDescription = null, tint = accent, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Choose Text Size", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0C2133))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Set the size used throughout Hi Player. You can change it later in Settings.",
            fontSize = 13.sp,
            color = Color(0xFF2C4A60),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hi Player preview", fontSize = textSizeSp.sp, fontWeight = FontWeight.Bold, color = accent)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Your library, player controls, folders, and settings will use this size.", fontSize = textSizeSp.sp, color = Color(0xFF2C4A60))
                Slider(
                    value = textSizeSp.toFloat(),
                    onValueChange = { onTextSizeChanged(it.roundToInt()) },
                    valueRange = 12f..24f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_text_size_slider")
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Small", fontSize = 11.sp, color = Color(0xFF2C4A60))
                    Text("${textSizeSp}sp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    Text("Large", fontSize = 11.sp, color = Color(0xFF2C4A60))
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onComplete,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text("Enter Hi Player", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
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
                                Color(0xFF0056B3).copy(alpha = 0.25f),
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
            color = Color(0xFF0C2133),
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "4K UHD Blu-ray & Hi-Fi Audio",
            fontSize = 12.sp,
            color = Color(0xFF0056B3),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun WelcomeNoteView(
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
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
                    painter = painterResource(id = R.drawable.hi_player_logo),
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
                color = Color(0xFF0C2133),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Designed for high-bitrate 4K HDR playback, lossless audio, and clean file management.",
                fontSize = 10.sp,
                color = Color(0xFF2C4A60),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Feature Highlights
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureRow(
                    icon = Icons.Default.Tv,
                    title = "Optimized 4K HDR & Wide Color",
                    description = "True-to-life colors with hardware direct rendering (BT.2020 / HDR10)."
                )

                FeatureRow(
                    icon = Icons.Default.Memory,
                    title = "HW+, HW & SW Acceleration",
                    description = "Flexible multi-mode decoders for stutter-free 60fps video playback."
                )

                FeatureRow(
                    icon = Icons.Default.Headphones,
                    title = "Hi-Fi Audio & Night Mode",
                    description = "Background listening, dialogue booster, and equalizer support."
                )

                FeatureRow(
                    icon = Icons.Default.Folder,
                    title = "Universal File Explorer",
                    description = "Clean media browser with instant subfolder navigation."
                )

                FeatureRow(
                    icon = Icons.Default.Security,
                    title = "Archives, APKs & Stream URLs",
                    description = "Choose archive destinations, install APKs directly, and play valid video URLs."
                )

                FeatureRow(
                    icon = Icons.Default.CheckCircle,
                    title = "Continue Watching & Favorites",
                    description = "Resume progress and keep library modes, views, sorting, and preferences between launches."
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("welcome_continue_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0056B3),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Continue",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFC7E3F2)),
        border = null,
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0056B3).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF0056B3),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = Color(0xFF0C2133)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = Color(0xFF2C4A60),
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ChooseThemeView(
    selectedTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    onProceed: () -> Unit
) {
    val selectedPalette = com.example.ui.theme.getPaletteForTheme(selectedTheme)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(selectedPalette.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = selectedPalette.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Choose Your Theme",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0C2133),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Select your preferred theme. Set it as default for the whole app.",
                fontSize = 10.sp,
                color = Color(0xFF2C4A60),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Themes in Horizontal Form
            com.example.ui.components.HorizontalThemeSelector(
                selectedTheme = selectedTheme,
                onThemeSelected = onThemeSelected,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Currently Selected Theme Detail Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFC7E3F2)),
                border = null,
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
                            color = Color(0xFF0C2133)
                        )
                        Text(
                            text = selectedTheme.description,
                            fontSize = 10.sp,
                            color = Color(0xFF2C4A60)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("theme_proceed_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = selectedPalette.primary,
                contentColor = if (selectedPalette.isDark) Color.Black else Color.White
            )
        ) {
            Text(
                text = "Apply & Continue",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
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
            containerColor = if (isSelected) Color(0xFFD6ECF7) else Color(0xFFC7E3F2)
        ),
        border = null,
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
                        color = Color(0xFF0C2133)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = Color(0xFF2C4A60)
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color(0xFF0056B3),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionsSetupView(
    selectedTheme: AppThemeMode,
    onComplete: () -> Unit
) {
    val context = LocalContext.current

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
        onComplete()
    }

    val accentColor = com.example.ui.theme.getPaletteForTheme(selectedTheme).primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
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
                text = "Permissions Required",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0C2133),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "To scan your videos, audio, and provide media controls, please grant storage and notification access.",
                fontSize = 10.sp,
                color = Color(0xFF2C4A60),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
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

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val hasAll = permissionsToRequest.all {
                    androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (hasAll) {
                    onComplete()
                } else {
                    try {
                        launcher.launch(permissionsToRequest)
                    } catch (e: Exception) {
                        onComplete()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("grant_permissions_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Give Permission",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFC7E3F2)),
        border = null,
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
                    .clip(RoundedCornerShape(8.dp))
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
                    fontSize = 11.5.sp,
                    color = Color(0xFF0C2133)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = Color(0xFF2C4A60),
                    lineHeight = 13.sp
                )
            }
        }
    }
}
