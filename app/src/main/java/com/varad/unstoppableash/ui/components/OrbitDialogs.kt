package com.varad.unstoppableash.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.varad.unstoppableash.ui.theme.Orbit

/** Confirmation dialog; [destructive] paints the confirm action in the SOS/destructive red. */
@Composable
fun OrbitDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val c = Orbit.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            if (destructive) {
                androidx.compose.material3.TextButton(
                    onClick = onConfirm,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = c.sosText),
                ) { Text(confirmLabel) }
            } else {
                OrbitButton(confirmLabel, onConfirm, style = OrbitButtonStyle.Quiet)
            }
        },
        dismissButton = { OrbitButton(dismissLabel, onDismiss, style = OrbitButtonStyle.Quiet) },
        shape = MaterialTheme.shapes.large,
        containerColor = c.surfaceWarm,
        titleContentColor = c.text,
        textContentColor = c.text,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = Orbit.colors.surfaceWarm,
        contentColor = Orbit.colors.text,
        content = content,
    )
}
