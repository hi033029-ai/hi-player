package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppPlayerSettings
import com.example.ui.theme.LocalHiPalette

private data class ThemeSwatch(val name: String, val hex: String, val color: Color)

private val themeSwatches = listOf(
    ThemeSwatch("White", "#FFFFFF", Color.White),
    ThemeSwatch("Black", "#000000", Color.Black),
    ThemeSwatch("Slate", "#0F172A", Color(0xFF0F172A)),
    ThemeSwatch("Ice", "#F8FAFC", Color(0xFFF8FAFC)),
    ThemeSwatch("Blue", "#2563EB", Color(0xFF2563EB)),
    ThemeSwatch("Cyan", "#00E5FF", Color(0xFF00E5FF)),
    ThemeSwatch("Red", "#E10600", Color(0xFFE10600)),
    ThemeSwatch("Amber", "#F59E0B", Color(0xFFF59E0B)),
    ThemeSwatch("Green", "#10B981", Color(0xFF10B981)),
    ThemeSwatch("Purple", "#8B5CF6", Color(0xFF8B5CF6)),
    ThemeSwatch("Pink", "#EC4899", Color(0xFFEC4899)),
)

@Composable
fun ThemeCustomizationPanel(
    settings: AppPlayerSettings,
    onChange: (
        textHex: String,
        backgroundHex: String,
        primaryHex: String,
        surfaceHex: String,
        textOpacity: Int,
        backgroundOpacity: Int,
        primaryOpacity: Int,
        surfaceOpacity: Int,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHiPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("CUSTOMIZE COLORS", color = palette.primary, fontSize = 11.sp)
        ThemeColorRole(
            title = "Text",
            selectedHex = settings.customThemeTextHex,
            opacity = settings.customThemeTextOpacity,
            onColor = { hex -> onChange(hex, settings.customThemeBackgroundHex, settings.customThemePrimaryHex, settings.customThemeSurfaceHex, settings.customThemeTextOpacity, settings.customThemeBackgroundOpacity, settings.customThemePrimaryOpacity, settings.customThemeSurfaceOpacity) },
            onOpacity = { value -> onChange(settings.customThemeTextHex, settings.customThemeBackgroundHex, settings.customThemePrimaryHex, settings.customThemeSurfaceHex, value, settings.customThemeBackgroundOpacity, settings.customThemePrimaryOpacity, settings.customThemeSurfaceOpacity) },
        )
        ThemeColorRole(
            title = "Background",
            selectedHex = settings.customThemeBackgroundHex,
            opacity = settings.customThemeBackgroundOpacity,
            onColor = { hex -> onChange(settings.customThemeTextHex, hex, settings.customThemePrimaryHex, settings.customThemeSurfaceHex, settings.customThemeTextOpacity, settings.customThemeBackgroundOpacity, settings.customThemePrimaryOpacity, settings.customThemeSurfaceOpacity) },
            onOpacity = { value -> onChange(settings.customThemeTextHex, settings.customThemeBackgroundHex, settings.customThemePrimaryHex, settings.customThemeSurfaceHex, settings.customThemeTextOpacity, value, settings.customThemePrimaryOpacity, settings.customThemeSurfaceOpacity) },
        )
        ThemeColorRole(
            title = "Accent",
            selectedHex = settings.customThemePrimaryHex,
            opacity = settings.customThemePrimaryOpacity,
            onColor = { hex -> onChange(settings.customThemeTextHex, settings.customThemeBackgroundHex, hex, settings.customThemeSurfaceHex, settings.customThemeTextOpacity, settings.customThemeBackgroundOpacity, settings.customThemePrimaryOpacity, settings.customThemeSurfaceOpacity) },
            onOpacity = { value -> onChange(settings.customThemeTextHex, settings.customThemeBackgroundHex, settings.customThemePrimaryHex, settings.customThemeSurfaceHex, settings.customThemeTextOpacity, settings.customThemeBackgroundOpacity, value, settings.customThemeSurfaceOpacity) },
        )
        ThemeColorRole(
            title = "Surface",
            selectedHex = settings.customThemeSurfaceHex,
            opacity = settings.customThemeSurfaceOpacity,
            onColor = { hex -> onChange(settings.customThemeTextHex, settings.customThemeBackgroundHex, settings.customThemePrimaryHex, hex, settings.customThemeTextOpacity, settings.customThemeBackgroundOpacity, settings.customThemePrimaryOpacity, settings.customThemeSurfaceOpacity) },
            onOpacity = { value -> onChange(settings.customThemeTextHex, settings.customThemeBackgroundHex, settings.customThemePrimaryHex, settings.customThemeSurfaceHex, settings.customThemeTextOpacity, settings.customThemeBackgroundOpacity, settings.customThemePrimaryOpacity, value) },
        )
    }
}

@Composable
private fun ThemeColorRole(
    title: String,
    selectedHex: String,
    opacity: Int,
    onColor: (String) -> Unit,
    onOpacity: (Int) -> Unit,
) {
    val palette = LocalHiPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = palette.textPrimary, fontSize = 13.sp)
            Text("$selectedHex  ${opacity}%", color = palette.textSecondary, fontSize = 10.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            themeSwatches.forEach { swatch ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(swatch.color, CircleShape)
                            .border(
                                width = if (swatch.hex.equals(selectedHex, ignoreCase = true)) 3.dp else 1.dp,
                                color = if (swatch.hex.equals(selectedHex, ignoreCase = true)) palette.primary else palette.surfaceBorder,
                                shape = CircleShape,
                            )
                            .clickable { onColor(swatch.hex) },
                    )
                    Text(swatch.name, color = palette.textSecondary, fontSize = 8.sp)
                }
            }
        }
        Slider(
            value = opacity.toFloat(),
            onValueChange = { onOpacity(it.toInt()) },
            valueRange = 10f..100f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun themeColorSwatches(): List<String> = themeSwatches.map { it.hex }
