package com.varad.unstoppableash.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.varad.unstoppableash.R
import com.varad.unstoppableash.core.result.FailureReason

/** A plain-language explanation for every failure the UI can show. */
@Composable
fun failureMessage(reason: FailureReason): String = stringResource(
    when (reason) {
        FailureReason.OFFLINE -> R.string.error_offline
        FailureReason.PERMISSION_DENIED, FailureReason.NOT_PAIRED -> R.string.error_denied
        FailureReason.RATE_LIMITED -> R.string.error_rate_limited
        FailureReason.INVALID -> R.string.error_invalid
        else -> R.string.error_generic
    },
)
