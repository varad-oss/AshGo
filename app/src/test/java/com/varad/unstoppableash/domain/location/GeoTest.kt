package com.varad.unstoppableash.domain.location

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test fun identicalPointsAreZero() = assertEquals(0.0, distanceMeters(18.5204, 73.8567, 18.5204, 73.8567), 0.0)
    @Test fun knownCityDistance() = assertEquals(1_767.4, distanceMeters(18.5204, 73.8567, 18.5314, 73.8446), 1.0)
    @Test fun oneDegreeAtEquator() = assertEquals(111_195.1, distanceMeters(0.0, 0.0, 0.0, 1.0), 1.0)
}
