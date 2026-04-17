package com.chengjiguanjia.spotmark.navigation

import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.location.LocationPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapNavigationTest {
    @Test
    fun amapUriOpensRoutePreviewWithOriginAndDestination() {
        val spot = sampleSpot()
        val origin = LocationPoint(latitude = 34.7472, longitude = 113.6249)

        val uri = buildAmapRouteUri(spot, origin)

        assertTrue(uri.startsWith("androidamap://route?"))
        assertFalse(uri.contains("androidamap://navi"))
        assertTrue(uri.contains("slat=34.7472000"))
        assertTrue(uri.contains("slon=113.6249000"))
        assertTrue(uri.contains("dlat=34.7601000"))
        assertTrue(uri.contains("dlon=113.6502000"))
        assertTrue(uri.contains("dname=Bike%20lock"))
    }

    @Test
    fun googleUriOpensRoutePlannerInsteadOfImmediateNavigation() {
        val uri = buildGoogleRouteUri(sampleSpot())

        assertTrue(uri.startsWith("https://www.google.com/maps/dir/"))
        assertFalse(uri.startsWith("google.navigation:"))
        assertTrue(uri.contains("destination=34.7601000,113.6502000"))
    }

    private fun sampleSpot(): SavedSpot =
        SavedSpot(
            id = 1,
            title = "Bike lock",
            note = "",
            latitude = 34.7601,
            longitude = 113.6502,
            accuracyMeters = null,
            createdAt = 0,
            updatedAt = 0,
            photoPaths = emptyList(),
        )
}
