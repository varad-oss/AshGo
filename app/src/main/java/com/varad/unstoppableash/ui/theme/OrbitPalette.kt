package com.varad.unstoppableash.ui.theme

/**
 * Raw ARGB values for REDESIGN_SPEC §2, kept Compose-free so ContrastTest can
 * verify every text/background pair on the JVM.
 */
internal object OrbitPalette {
    object Light {
        const val background = 0xFFFFF7F5
        const val surface = 0xFFFFFFFF
        const val surfaceWarm = 0xFFFFFBFA
        const val primary = 0xFF8B405F
        const val onPrimary = 0xFFFFFFFF
        const val text = 0xFF382637
        const val textSecondary = 0xFF6E5A6C
        const val blush = 0xFFF6DDE8
        const val lavender = 0xFFEADFF8
        const val goodBg = 0xFFDFF1E4
        const val goodInk = 0xFF2E6B45
        const val warnBg = 0xFFFCEBCF
        const val warnInk = 0xFF8A5300
        const val sos = 0xFFB3261E
        const val onSos = 0xFFFFFFFF
        const val sosText = 0xFFB3261E
        const val outline = 0x1A382637 // text at 10 %
    }

    object Dark {
        const val background = 0xFF1E151D
        const val surface = 0xFF2A2029
        const val surfaceWarm = 0xFF32262F
        const val primary = 0xFFF2B3CB
        const val onPrimary = 0xFF3A1426
        const val text = 0xFFF3E8F1
        const val textSecondary = 0xFFCBB8C8
        const val blush = 0xFF4A2B3B
        const val lavender = 0xFF3B2F4D
        const val goodBg = 0xFF1F3527
        const val goodInk = 0xFF9FD3B0
        const val warnBg = 0xFF3D2E14
        const val warnInk = 0xFFF4C27A
        const val sos = 0xFFD32F2F
        const val onSos = 0xFFFFFFFF
        const val sosText = 0xFFFFB4AB
        const val outline = 0x1FF3E8F1 // text at 12 %
    }
}
