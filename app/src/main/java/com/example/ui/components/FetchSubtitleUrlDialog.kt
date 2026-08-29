package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HiPrimaryCyan
import com.example.ui.theme.HiSurfaceDark
import com.example.ui.theme.HiTextPrimary
import com.example.ui.theme.HiTextSecondary

@Composable
fun FetchSubtitleUrlDialog(
    onFetchUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HiSurfaceDark,
        title = {
            Text(
                text = "Fetch Subtitle from Remote URL",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = HiTextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter HTTP/HTTPS URL of a remote .srt or .vtt subtitle file. Includes graceful fallback handling if fetching fails.",
                    fontSize = 12.sp,
                    color = HiTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com/subtitles/english.vtt", color = HiTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HiPrimaryCyan,
                        unfocusedBorderColor = Color(0x44FFFFFF),
                        focusedTextColor = HiTextPrimary,
                        unfocusedTextColor = HiTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subtitle_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onFetchUrl(urlText)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HiPrimaryCyan, contentColor = Color.Black),
                modifier = Modifier.testTag("fetch_subtitle_button")
            ) {
                Text("Fetch Subtitle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HiTextSecondary)
            }
        }
    )
}
