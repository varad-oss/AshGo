package com.varad.unstoppableash.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.time.AgeUnit
import com.varad.unstoppableash.domain.time.formatAge
import android.text.format.DateUtils
import java.time.ZoneId

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

/** "6:40 PM" today, "Fri 6:40 PM" this week, "Oct 3, 6:40 PM" otherwise — never implies today when it isn't. */
@Composable
fun lastSeenText(epochMs: Long): String {
    val context = LocalContext.current
    val flags = when (lastSeenFormat(epochMs, System.currentTimeMillis(), ZoneId.systemDefault())) {
        LastSeenFormat.TIME_ONLY -> DateUtils.FORMAT_SHOW_TIME
        LastSeenFormat.WEEKDAY_TIME -> DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        LastSeenFormat.DATE_TIME -> DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    }
    return DateUtils.formatDateTime(context, epochMs, flags)
}
