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

/**
 * Lets the user paste their free TMDB API key so the player can show a
 * public star rating in the controls overlay. Unlike the earlier OMDb
 * dialog, this one is actually persisted (DataStore, via
 * PlayerPreferencesRepository) so it only needs to be entered once.
 */
@Composable
fun TmdbKeyDialog(
    currentKey: String,
    onSaveKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyText by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HiSurfaceDark,
        title = {
            Text(
                text = "TMDB API Key for Ratings",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = HiTextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Paste a free TMDB API key to show a star rating next to the title while playing. Get one instantly at themoviedb.org/settings/api - it's saved on-device and used only to look up the title you're currently playing.",
                    fontSize = 12.sp,
                    color = HiTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    placeholder = { Text("e.g. 7a3b1f9e...", color = HiTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HiPrimaryCyan,
                        unfocusedBorderColor = Color(0x44FFFFFF),
                        focusedTextColor = HiTextPrimary,
                        unfocusedTextColor = HiTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tmdb_key_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveKey(keyText.trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HiPrimaryCyan, contentColor = Color.Black),
                modifier = Modifier.testTag("save_tmdb_key_button")
            ) {
                Text("Save Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HiTextSecondary)
            }
        }
    )
}
