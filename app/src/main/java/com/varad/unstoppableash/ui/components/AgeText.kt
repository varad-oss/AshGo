package com.varad.unstoppableash.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.time.AgeUnit
import com.varad.unstoppableash.domain.time.formatAge
import java.util.Date

/** "4 min ago" / "just now" — visible form. */
@Composable
fun shortAgeAgo(ageMs: Long): String {
    val age = formatAge(ageMs)
    val unit = when (age.unit) {
        AgeUnit.JUST_NOW -> return stringResource(R.string.age_just_now)
        AgeUnit.MINUTES -> stringResource(R.string.age_short_minutes, age.value)
        AgeUnit.HOURS -> stringResource(R.string.age_short_hours, age.value)
        AgeUnit.DAYS -> stringResource(R.string.age_short_days, age.value)
    }
    return stringResource(R.string.age_ago, unit)
}

/** "4 minutes ago" / "just now" — spoken form. */
@Composable
fun longAgeAgo(ageMs: Long): String {
    val age = formatAge(ageMs)
    val unit = when (age.unit) {
        AgeUnit.JUST_NOW -> return stringResource(R.string.age_just_now)
        AgeUnit.MINUTES -> pluralStringResource(R.plurals.age_long_minutes, age.value, age.value)
        AgeUnit.HOURS -> pluralStringResource(R.plurals.age_long_hours, age.value, age.value)
        AgeUnit.DAYS -> pluralStringResource(R.plurals.age_long_days, age.value, age.value)
    }
    return stringResource(R.string.age_ago, unit)
}

/** Wall-clock time in the user's 12/24 h preference, e.g. "6:40 PM". */
@Composable
fun timeOfDay(epochMs: Long): String =
    android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(Date(epochMs))
