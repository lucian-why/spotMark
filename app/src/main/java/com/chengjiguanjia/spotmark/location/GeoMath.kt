package com.chengjiguanjia.spotmark.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
)

data class TargetMetrics(
    val distanceMeters: Float,
    val bearingDegrees: Float,
)

fun metricsToTarget(from: LocationPoint, to: LocationPoint): TargetMetrics {
    require(from.latitude in -90.0..90.0) { "from latitude is out of range" }
    require(to.latitude in -90.0..90.0) { "to latitude is out of range" }
    require(from.longitude in -180.0..180.0) { "from longitude is out of range" }
    require(to.longitude in -180.0..180.0) { "to longitude is out of range" }

    val fromLat = Math.toRadians(from.latitude)
    val toLat = Math.toRadians(to.latitude)
    val deltaLat = Math.toRadians(to.latitude - from.latitude)
    val deltaLng = Math.toRadians(to.longitude - from.longitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(fromLat) * cos(toLat) * sin(deltaLng / 2) * sin(deltaLng / 2)
    val distance = 6_371_000.0 * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

    val y = sin(deltaLng) * cos(toLat)
    val x = cos(fromLat) * sin(toLat) -
        sin(fromLat) * cos(toLat) * cos(deltaLng)
    val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    return TargetMetrics(distanceMeters = distance.toFloat(), bearingDegrees = bearing.toFloat())
}

fun arrowRotationDegrees(targetBearing: Float, deviceHeading: Float): Float =
    (targetBearing - deviceHeading + 360f) % 360f

fun formatDistance(meters: Float?): String {
    if (meters == null) return "--"
    return if (meters < 1000f) {
        "${meters.roundToInt()} m"
    } else {
        "%.1f km".format(meters / 1000f)
    }
}
