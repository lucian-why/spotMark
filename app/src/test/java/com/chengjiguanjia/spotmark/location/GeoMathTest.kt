package com.chengjiguanjia.spotmark.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun samePointHasZeroDistance() {
        val metrics = metricsToTarget(
            from = LocationPoint(34.7472, 113.6249),
            to = LocationPoint(34.7472, 113.6249),
        )

        assertEquals(0f, metrics.distanceMeters, 0.01f)
    }

    @Test
    fun eastPointHasAboutEastBearing() {
        val metrics = metricsToTarget(
            from = LocationPoint(0.0, 0.0),
            to = LocationPoint(0.0, 1.0),
        )

        assertTrue(metrics.distanceMeters > 100_000f)
        assertEquals(90f, metrics.bearingDegrees, 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLatitudeThrows() {
        metricsToTarget(
            from = LocationPoint(91.0, 0.0),
            to = LocationPoint(0.0, 0.0),
        )
    }

    @Test
    fun arrowRotationUsesRelativeHeading() {
        assertEquals(270f, arrowRotationDegrees(targetBearing = 90f, deviceHeading = 180f), 0.01f)
    }
}
