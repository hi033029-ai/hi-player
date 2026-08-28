package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoItem
import com.example.player.DecoderTelemetry
import com.example.ui.theme.HiAccentAmber
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiSurfaceElevated
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary

@Composable
fun VideoInfoDialog(
    video: VideoItem?,
    telemetry: DecoderTelemetry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HiSurfaceDark,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = HiPrimaryCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "4K Remux Engine Telemetry",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = HiTextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = video?.title ?: "Current Stream",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HiPrimaryCyan,
                    maxLines = 2
                )

                HorizontalDivider(color = Color(0x33FFFFFF), thickness = 1.dp)

                TelemetryRow(label = "Video Codec", value = telemetry.codecName)
                TelemetryRow(label = "Resolution", value = telemetry.resolution)
                TelemetryRow(label = "Frame Rate", value = String.format("%.2f fps", telemetry.fps))
                TelemetryRow(label = "Bitrate", value = String.format("%.1f Mbps", telemetry.bitrateMbps))
                TelemetryRow(label = "Color Space / HDR", value = telemetry.colorSpace)
                TelemetryRow(label = "Audio Stream", value = telemetry.audioFormat)
                TelemetryRow(
                    label = "Dropped Frames",
                    value = "${telemetry.droppedFrames} (0.00%)",
                    valueColor = if (telemetry.droppedFrames == 0) Color(0xFF00E676) else HiAccentAmber
                )
                TelemetryRow(label = "Hardware Decoder", value = "MediaCodec Direct Surface", valueColor = Color(0xFF00E676))

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HiSurfaceElevated)
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ultra Buffer Cache Active: 128MB Remux Mode",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = HiPrimaryCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    valueColor: Color = HiTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = HiTextSecondary
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
