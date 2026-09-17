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
    ThemeSwatch("White", "#FFFFFF", Color.White), ThemeSwatch("Black", "#000000", Color.Black),
    ThemeSwatch("Slate", "#0F172A", Color(0xFF0F172A)), ThemeSwatch("Ice", "#F8FAFC", Color(0xFFF8FAFC)),
    ThemeSwatch("Blue", "#2563EB", Color(0xFF2563EB)), ThemeSwatch("Cyan", "#00E5FF", Color(0xFF00E5FF)),
    ThemeSwatch("Red", "#E10600", Color(0xFFE10600)), ThemeSwatch("Amber", "#F59E0B", Color(0xFFF59E0B)),
    ThemeSwatch("Green", "#10B981", Color(0xFF10B981)), ThemeSwatch("Purple", "#8B5CF6", Color(0xFF8B5CF6)),
    ThemeSwatch("Pink", "#EC4899", Color(0xFFEC4899)),
)

@Composable
fun ThemeCustomizationPanel(
    settings: AppPlayerSettings,
    onChange: (String, String, String, String, Int, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = settings
    val palette = LocalHiPalette.current
    Column(
        modifier = modifier.fillMaxWidth().background(palette.surface.copy(alpha = 0.72f), RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("CUSTOMIZE COLORS", color = palette.primary, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeColorRole(Modifier.weight(1f), "Text", s.customThemeTextHex, s.customThemeTextOpacity,
                { v -> onChange(v, s.customThemeBackgroundHex, s.customThemePrimaryHex, s.customThemeSurfaceHex, s.customThemeTextOpacity, s.customThemeBackgroundOpacity, s.customThemePrimaryOpacity, s.customThemeSurfaceOpacity) },
                { v -> onChange(s.customThemeTextHex, s.customThemeBackgroundHex, s.customThemePrimaryHex, s.customThemeSurfaceHex, v, s.customThemeBackgroundOpacity, s.customThemePrimaryOpacity, s.customThemeSurfaceOpacity) })
            ThemeColorRole(Modifier.weight(1f), "Background", s.customThemeBackgroundHex, s.customThemeBackgroundOpacity,
                { v -> onChange(s.customThemeTextHex, v, s.customThemePrimaryHex, s.customThemeSurfaceHex, s.customThemeTextOpacity, s.customThemeBackgroundOpacity, s.customThemePrimaryOpacity, s.customThemeSurfaceOpacity) },
                { v -> onChange(s.customThemeTextHex, s.customThemeBackgroundHex, s.customThemePrimaryHex, s.customThemeSurfaceHex, s.customThemeTextOpacity, v, s.customThemePrimaryOpacity, s.customThemeSurfaceOpacity) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeColorRole(Modifier.weight(1f), "Accent", s.customThemePrimaryHex, s.customThemePrimaryOpacity,
                { v -> onChange(s.customThemeTextHex, s.customThemeBackgroundHex, v, s.customThemeSurfaceHex, s.customThemeTextOpacity, s.customThemeBackgroundOpacity, s.customThemePrimaryOpacity, s.customThemeSurfaceOpacity) },
                { v -> onChange(s.customThemeTextHex, s.customThemeBackgroundHex, s.customThemePrimaryHex, s.customThemeSurfaceHex, s.customThemeTextOpacity, s.customThemeBackgroundOpacity, v, s.customThemeSurfaceOpacity) })
            ThemeColorRole(Modifier.weight(1f), "Surface", s.customThemeSurfaceHex, s.customThemeSurfaceOpacity,
                { v -> onChange(s.customThemeTextHex, s.customThemeBackgroundHex, s.customThemePrimaryHex, v, s.customThemeTextOpacity, s.customThemeBackgroundOpacity, s.customThemePrimaryOpacity, s.customThemeSurfaceOpacity) },
                { v -> onChange(s.customThemeTextHex, s.customThemeBackgroundHex, s.customThemePrimaryHex, s.customThemeSurfaceHex, s.customThemeTextOpacity, s.customThemeBackgroundOpacity, s.customThemePrimaryOpacity, v) })
        }
    }
}

@Composable
private fun ThemeColorRole(
    modifier: Modifier,
    title: String,
    selectedHex: String,
    opacity: Int,
    onColor: (String) -> Unit,
    onOpacity: (Int) -> Unit,
) {
    val palette = LocalHiPalette.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = palette.textPrimary, fontSize = 11.sp)
            Text("${opacity}%", color = palette.textSecondary, fontSize = 9.sp)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            themeSwatches.forEach { swatch ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(22.dp).background(swatch.color, CircleShape).border(
                            if (swatch.hex.equals(selectedHex, true)) 2.dp else 1.dp,
                            if (swatch.hex.equals(selectedHex, true)) palette.primary else palette.surfaceBorder,
                            CircleShape,
                        ).clickable { onColor(swatch.hex) },
                    )
                    Text(swatch.name, color = palette.textSecondary, fontSize = 7.sp)
                }
            }
        }
        Slider(value = opacity.toFloat(), onValueChange = { onOpacity(it.toInt()) }, valueRange = 10f..100f, modifier = Modifier.fillMaxWidth())
    }
}

fun themeColorSwatches(): List<String> = themeSwatches.map { it.hex }
