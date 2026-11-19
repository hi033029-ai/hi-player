package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppThemeMode
import com.example.ui.theme.HiCardShape
import com.example.ui.theme.HiThemePalette
import com.example.ui.theme.LocalHiPalette
import com.example.ui.theme.contrastContentColor
import com.example.ui.theme.getPaletteForTheme

@Composable
fun HorizontalThemeSelector(
    selectedTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    customPalette: HiThemePalette? = null,
    showCustom: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
) {
    val systemDark = isSystemInDarkTheme()
    val themes = AppThemeMode.entries.filter { showCustom || it != AppThemeMode.CUSTOM }
    LazyRow(
        modifier = modifier.fillMaxWidth().selectableGroup().testTag("horizontal_theme_selector"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(themes, key = { it.id }) { theme ->
            RoundColorThemeItem(
                theme = theme,
                palette = if (theme == AppThemeMode.CUSTOM && customPalette != null) customPalette
                    else getPaletteForTheme(theme, systemDark),
                isSelected = selectedTheme == theme,
                onSelect = { onThemeSelected(theme) },
            )
        }
    }
}

@Composable
fun RoundColorThemeItem(
    theme: AppThemeMode,
    palette: HiThemePalette,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = LocalHiPalette.current
    Column(
        modifier = modifier.width(116.dp).clip(HiCardShape)
            .background(if (isSelected) current.selectionSurface else current.surface)
            .border(if (isSelected) 1.5.dp else 0.5.dp,
                if (isSelected) current.primary else current.surfaceBorder, HiCardShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(8.dp).testTag("theme_card_${theme.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(11.dp))
                .background(Brush.verticalGradient(palette.backgroundGradient ?: listOf(palette.background, palette.background))),
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(10.dp).background(palette.primary, CircleShape))
                    Box(Modifier.size(width = 32.dp, height = 4.dp).background(palette.textPrimary, CircleShape))
                }
                repeat(2) {
                    Row(
                        Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(5.dp)).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(Modifier.size(13.dp).background(palette.selectionSurface, RoundedCornerShape(3.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.fillMaxWidth(0.85f).height(3.dp).background(palette.textPrimary, CircleShape))
                            Box(Modifier.fillMaxWidth(0.55f).height(2.dp).background(palette.textSecondary, CircleShape))
                        }
                    }
                }
            }
            if (isSelected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).background(current.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, null, tint = contrastContentColor(current.primary, current.surface), modifier = Modifier.size(13.dp))
                }
            }
        }
        Text(
            theme.displayName,
            color = current.textPrimary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
        )
    }
}
