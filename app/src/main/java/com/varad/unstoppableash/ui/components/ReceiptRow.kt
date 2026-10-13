package com.varad.unstoppableash.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing

enum class ReceiptStatus { QUEUED, PENDING, DONE, FAILED, UNKNOWN }

/** One honest delivery fact, e.g. "SMS to Mum: done at 22:41". Retry appears only for failures. */
@Composable
fun ReceiptRow(
    label: String,
    status: ReceiptStatus,
    time: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = Orbit.colors
    val statusWord = stringResource(
        when (status) {
            ReceiptStatus.QUEUED -> R.string.receipt_queued
            ReceiptStatus.PENDING -> R.string.receipt_pending
            ReceiptStatus.DONE -> R.string.receipt_done
            ReceiptStatus.FAILED -> R.string.receipt_failed
            ReceiptStatus.UNKNOWN -> R.string.receipt_unknown
        },
    )
    val sentence = if (time != null) stringResource(R.string.receipt_cd_time, label, statusWord, time)
    else stringResource(R.string.receipt_cd, label, statusWord)
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = sentence },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when (status) {
                    ReceiptStatus.QUEUED -> Icon(Icons.Rounded.Schedule, null, tint = c.textSecondary)
                    ReceiptStatus.PENDING -> LoadingOrbit()
                    ReceiptStatus.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = c.goodInk)
                    ReceiptStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = c.warnInk)
                    ReceiptStatus.UNKNOWN -> Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = c.textSecondary)
                }
            }
            Text(label, color = c.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (time != null) Text(time, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (status == ReceiptStatus.FAILED && onRetry != null) {
            OrbitButton(stringResource(R.string.action_retry), onRetry, style = OrbitButtonStyle.Quiet)
        }
    }
}
