package com.example.ui.components

import com.example.ui.theme.hiContainerBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary
import com.example.viewmodel.SubtitleStyleConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleCustomizationBottomSheet(
    config: SubtitleStyleConfig,
    onUpdateStyle: (SubtitleStyleConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var fontSize by remember { mutableFloatStateOf(config.fontSizeSp.toFloat()) }
    var fontFamily by remember { mutableStateOf(config.fontFamily) }
    var textColorHex by remember { mutableLongStateOf(config.textColorHex) }
    var bgOpacity by remember { mutableFloatStateOf(config.bgOpacity) }
    var verticalOffset by remember { mutableFloatStateOf(config.verticalOffsetDp.toFloat()) }
    var workingConfig by remember { mutableStateOf(config) }

    fun updateStyle(next: SubtitleStyleConfig) {
        workingConfig = next
        onUpdateStyle(next)
    }

    ModalBottomSheet(

        modifier = androidx.compose.ui.Modifier.hiContainerBorder(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HiSurfaceDark,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Subtitle Customization",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HiTextPrimary
            )
            Text(
                text = "Adjust font, colors, background opacity and vertical alignment",
                fontSize = 12.sp,
                color = HiTextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // LIVE PREVIEW BOX
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Sample Subtitle Line [00:01:23]",
                        color = Color(textColorHex),
                        fontSize = fontSize.sp,
                        fontFamily = when (fontFamily) {
                            "Monospace" -> FontFamily.Monospace
                            "Serif" -> FontFamily.Serif
                            "Cursive" -> FontFamily.Cursive
                            else -> FontFamily.SansSerif
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. FONT SIZE SLIDER
            Text("Font Size (${fontSize.toInt()} sp)", fontSize = 13.sp, color = HiTextPrimary, fontWeight = FontWeight.Bold)
            Slider(
                value = fontSize,
                onValueChange = {
                    fontSize = it
                    updateStyle(workingConfig.copy(fontSizeSp = it.toInt()))
                },
                valueRange = 14f..36f,
                colors = SliderDefaults.colors(thumbColor = HiPrimaryCyan, activeTrackColor = HiPrimaryCyan)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. FONT FAMILY CHOOSER
            Text("Font Family", fontSize = 13.sp, color = HiTextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("SansSerif", "Monospace", "Serif", "Cursive").forEach { family ->
                    val isSelected = fontFamily == family
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                fontFamily = family
                                updateStyle(workingConfig.copy(fontFamily = family))
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = family,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) HiPrimaryCyan else HiTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. TEXT COLOR SWATCHES
            Text("Text Color", fontSize = 13.sp, color = HiTextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    0xFFFFFFFF to "White",
                    0xFFFACC15 to "Yellow",
                    0xFF00E5FF to "Cyan",
                    0xFF4ADE80 to "Green",
                    0xFFF472B6 to "Pink"
                ).forEach { (colorVal, name) ->
                    val isSelected = textColorHex == colorVal
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(colorVal))
                            .border(if (isSelected) 3.dp else 0.dp, if (isSelected) HiPrimaryCyan else Color.Transparent, CircleShape)
                            .clickable {
                                textColorHex = colorVal
                                updateStyle(workingConfig.copy(textColorHex = colorVal))
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. BACKGROUND OPACITY SLIDER
            Text("Background Opacity (${(bgOpacity * 100).toInt()}%)", fontSize = 13.sp, color = HiTextPrimary, fontWeight = FontWeight.Bold)
            Slider(
                value = bgOpacity,
                onValueChange = {
                    bgOpacity = it
                    updateStyle(workingConfig.copy(bgOpacity = it))
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = HiPrimaryCyan, activeTrackColor = HiPrimaryCyan)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. VERTICAL POSITION OFFSET SLIDER
            Text("Vertical Offset (${verticalOffset.toInt()} dp)", fontSize = 13.sp, color = HiTextPrimary, fontWeight = FontWeight.Bold)
            Slider(
                value = verticalOffset,
                onValueChange = {
                    verticalOffset = it
                    updateStyle(workingConfig.copy(verticalOffsetDp = it.toInt()))
                },
                valueRange = 0f..60f,
                colors = SliderDefaults.colors(thumbColor = HiPrimaryCyan, activeTrackColor = HiPrimaryCyan)
            )
        }
    }
}
