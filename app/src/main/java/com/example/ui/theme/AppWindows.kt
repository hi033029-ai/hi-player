package com.example.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Separate Android windows install their own LocalDensity. Create the window at the
// device density so moving the slider preserves its identity, then restore the chosen
// font density inside the new composition. Both densities have the same dp scale.
@Composable
private fun WindowHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides (LocalUnscaledDensity.current ?: LocalDensity.current), content = content)
}

@Composable
private fun WindowContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides (LocalAppDensity.current ?: LocalDensity.current), content = content)
}

@Composable
fun AppDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {
    WindowHost { Dialog(onDismissRequest, properties) { WindowContent(content) } }
}

@Composable
fun AppDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    WindowHost {
        DropdownMenu(expanded, onDismissRequest, modifier) {
            WindowContent { content() }
        }
    }
}

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
) {
    WindowHost {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            confirmButton = { WindowContent(confirmButton) },
            dismissButton = dismissButton?.let { slot -> @Composable { WindowContent(slot) } },
            title = title?.let { slot -> @Composable { WindowContent(slot) } },
            text = text?.let { slot -> @Composable { WindowContent(slot) } },
            shape = shape,
            containerColor = containerColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    WindowHost {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState,
            sheetMaxWidth = sheetMaxWidth, containerColor = containerColor,
            contentColor = contentColor, tonalElevation = tonalElevation,
            dragHandle = dragHandle?.let { slot -> @Composable { WindowContent(slot) } },
        ) { WindowContent { content() } }
    }
}
