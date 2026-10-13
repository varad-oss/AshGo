package com.varad.unstoppableash.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** small: chips and inputs · medium: cards · large: sheets and dialogs (REDESIGN_SPEC §4). */
val OrbitShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)
