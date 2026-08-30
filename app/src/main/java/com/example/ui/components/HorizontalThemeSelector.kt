package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppThemeMode
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.getPaletteForTheme

/**
 * Clean Horizontal Theme Selector displaying combined round small colors with the theme name lower below.
 */
@Composable
fun HorizontalThemeSelector(
    selectedTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
) {
    val themes = AppThemeMode.entries

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("horizontal_theme_selector"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(themes) { _, theme ->
            val themePalette = getPaletteForTheme(theme)
            val isSelected = selectedTheme == theme

            RoundColorThemeItem(
                theme = theme,
                palette = themePalette,
                isSelected = isSelected,
                onSelect = { onThemeSelected(theme) }
            )
        }
    }
}

/**
 * Individual Theme Option:
 * Shows combined round small colors on top, with the theme name lower underneath.
 */
@Composable
fun RoundColorThemeItem(
    theme: AppThemeMode,
    palette: com.example.ui.theme.HiThemePalette,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentAppPalette = LocalHiPalette.current

    Column(
        modifier = modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag("theme_card_${theme.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Combined Round Small Colors (Circle with dual / combined colors preview)
        Box(
            modifier = Modifier
                .size(38.dp)
                .then(
                    if (isSelected) {
                        Modifier
                            .shadow(4.dp, CircleShape)
                            .border(2.5.dp, palette.primary, CircleShape)
                            .padding(3.dp)
                    } else {
                        Modifier
                            .border(1.dp, currentAppPalette.surfaceBorder, CircleShape)
                            .padding(2.dp)
                    }
                )
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Split dual-color representation of the theme (Background on Left / Accent on Right)
            Row(modifier = Modifier.fillMaxSize()) {
                // Background color half (or background gradient)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (palette.backgroundGradient != null) {
                                Modifier.background(Brush.verticalGradient(palette.backgroundGradient))
                            } else {
                                Modifier.background(palette.background)
                            }
                        )
                )
                // Accent primary color half
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(palette.primary)
                )
            }

            // Inner center dot with accent color or checkmark when selected
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) palette.primary else if (palette.isDark) Color(0xFF0F172A) else Color.White
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.White.copy(alpha = 0.8f) else palette.primary.copy(alpha = 0.6f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (palette.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(palette.primary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Lower Theme Name
        Text(
            text = theme.displayName,
            fontSize = 9.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) currentAppPalette.primary else currentAppPalette.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 12.sp
        )
    }
}
