package com.varad.unstoppableash.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.theme.Orbit

/** Always labelled (never placeholder-only); optional counter enforces [maxLength]. */
@Composable
fun OrbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    error: String? = null,
) {
    val c = Orbit.colors
    OutlinedTextField(
        value = value,
        onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
        isError = error != null,
        shape = MaterialTheme.shapes.small,
        supportingText = when {
            error != null -> { { Text(error) } }
            maxLength != null -> { { Text(stringResource(R.string.text_field_counter, value.length, maxLength)) } }
            else -> null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.primary,
            focusedLabelColor = c.primary,
            cursorColor = c.primary,
            errorBorderColor = c.sosText,
            errorLabelColor = c.sosText,
            errorSupportingTextColor = c.sosText,
            focusedContainerColor = c.surfaceWarm,
            unfocusedContainerColor = c.surfaceWarm,
        ),
    )
}
