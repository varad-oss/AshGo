package com.varad.unstoppableash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.varad.unstoppableash.R

/** Nunito (SIL OFL 1.1, bundled — never fetched at runtime). One variable file serves every weight. */
val Nunito: FontFamily = FontFamily(
    listOf(400, 600, 700, 800).map { w ->
        Font(
            R.font.nunito_variable,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private fun style(size: TextUnit, line: TextUnit, weight: Int) =
    TextStyle(fontFamily = Nunito, fontSize = size, lineHeight = line, fontWeight = FontWeight(weight))

/** REDESIGN_SPEC §3. Slots the spec doesn't name keep Material sizes but switch to Nunito. */
val OrbitTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Nunito),
        displayMedium = base.displayMedium.copy(fontFamily = Nunito),
        displaySmall = style(32.sp, 40.sp, 800),
        headlineLarge = base.headlineLarge.copy(fontFamily = Nunito),
        headlineMedium = base.headlineMedium.copy(fontFamily = Nunito),
        headlineSmall = style(26.sp, 32.sp, 700),
        titleLarge = style(22.sp, 28.sp, 700),
        titleMedium = style(18.sp, 24.sp, 700),
        titleSmall = base.titleSmall.copy(fontFamily = Nunito),
        bodyLarge = style(16.sp, 24.sp, 400),
        bodyMedium = style(14.sp, 20.sp, 400),
        bodySmall = style(12.sp, 16.sp, 400),
        labelLarge = style(14.sp, 20.sp, 600),
        labelMedium = base.labelMedium.copy(fontFamily = Nunito),
        labelSmall = style(12.sp, 16.sp, 400),
    )
}

/** SOS screens: heavier and larger, never decorated. */
val SosTextStyle: TextStyle = style(28.sp, 34.sp, 800)
