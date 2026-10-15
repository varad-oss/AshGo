package com.varad.unstoppableash.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.json.Json
import java.io.IOException

/** Tolerates fields added or removed between app versions. */
internal val LenientJson = Json { ignoreUnknownKeys = true }

/** A read IO error surfaces as "nothing stored" for display; writes still refuse to clobber unreadable data. */
internal fun Flow<Preferences>.safeData(): Flow<Preferences> = catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
