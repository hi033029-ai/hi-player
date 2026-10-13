package com.example.ui.components

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHiPalette
import com.example.viewmodel.DiscoverViewModel

/**
 * Connects Google Drive through Android's Google account chooser followed by
 * the Google Drive document provider. Hi Player never receives a password or
 * broad Drive OAuth token; Android grants access only to the chosen folder.
 */
@Composable
fun GoogleDriveAccountCard(
    discoverViewModel: DiscoverViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalHiPalette.current
    val sources by discoverViewModel.sourceSettings.collectAsState()
    val connection = sources.googleDrive
    var selectedAccountName by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(GoogleDriveFolderContract()) { uri ->
        val accountName = selectedAccountName
        if (uri == null || accountName.isNullOrBlank()) {
            isConnecting = false
            return@rememberLauncherForActivityResult
        }
        discoverViewModel.saveGoogleDriveFolder(uri, accountName) { result ->
            isConnecting = false
            result.onFailure { error ->
                connectionError = error.message ?: "Google Drive folder could not be connected."
            }
        }
    }

    val accountPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode != Activity.RESULT_OK || accountName.isNullOrBlank()) {
            isConnecting = false
            return@rememberLauncherForActivityResult
        }
        selectedAccountName = accountName
        folderPicker.launch(Unit)
    }

    fun startGoogleDriveConnection() {
        connectionError = null
        isConnecting = true
        val chooseAccountIntent = AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            false,
            null,
            null,
            null,
            null
        )
        accountPicker.launch(chooseAccountIntent)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(palette.primary.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "G",
                        color = palette.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (connection?.isConfigured == true) "Google account connected" else "Sign in with Google",
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (connection?.isConfigured == true) {
                            connection.accountName
                        } else {
                            "Choose an account and one Drive video folder"
                        },
                        color = palette.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (connection?.isConfigured == true) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Google Drive connected",
                        tint = palette.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (connection?.isConfigured == true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Drive video files",
                            color = palette.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = connection.displayName,
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Change",
                        color = palette.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable(enabled = !isConnecting) { startGoogleDriveConnection() }
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Disconnect",
                    color = Color(0xFFD14343),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(enabled = !isConnecting) { discoverViewModel.removeGoogleDrive() }
                        .padding(vertical = 4.dp)
                )
            } else {
                Text(
                    text = "Next, Android opens Google Drive's folder picker. Select the exact folder that holds your videos; Hi Player can read only that folder and never receives your Google password.",
                    color = palette.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { startGoogleDriveConnection() },
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isConnecting) "Opening Google Drive…" else "Choose account & Drive folder",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            connectionError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    color = Color(0xFFD14343),
                    fontSize = 11.sp
                )
            }
        }
    }
}

private class GoogleDriveFolderContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putExtra(DocumentsContract.EXTRA_PROMPT, "Choose Google Drive video folder")
            // Ask DocumentsUI to open the Drive provider root, instead of its
            // generic Recent-files view. The URI is advisory on Android builds
            // that do not expose Drive through the system picker.
            if (context.packageManager.resolveContentProvider(GOOGLE_DRIVE_AUTHORITY, 0) != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, GOOGLE_DRIVE_ROOT_URI)
            }
        }
        // Drive registers an ACTION_OPEN_DOCUMENT_TREE handler on supported
        // devices. Prefer it for an actual Drive-first view; otherwise retain
        // the standard Android picker, which can still show the Drive provider.
        val driveIntent = Intent(intent).setPackage(GOOGLE_DRIVE_PACKAGE)
        return if (context.packageManager.resolveActivity(driveIntent, 0) != null) driveIntent else intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null

    private companion object {
        const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"
        const val GOOGLE_DRIVE_AUTHORITY = "com.google.android.apps.docs.storage"
        val GOOGLE_DRIVE_ROOT_URI: Uri = Uri.parse("content://$GOOGLE_DRIVE_AUTHORITY/root/")
    }
}
