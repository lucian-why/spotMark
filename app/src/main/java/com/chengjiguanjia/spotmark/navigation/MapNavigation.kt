package com.chengjiguanjia.spotmark.navigation

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.location.LocationPoint
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val AMAP_PACKAGE = "com.autonavi.minimap"
private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

fun buildAmapRouteIntent(spot: SavedSpot, origin: LocationPoint?): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse(buildAmapRouteUri(spot, origin))).apply {
        setPackage(AMAP_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun buildAmapRouteUri(spot: SavedSpot, origin: LocationPoint?): String {
    val dlat = String.format(Locale.US, "%.7f", spot.latitude)
    val dlon = String.format(Locale.US, "%.7f", spot.longitude)
    val label = uriEncode(spot.title)
    val originParams = origin?.let {
        val slat = String.format(Locale.US, "%.7f", it.latitude)
        val slon = String.format(Locale.US, "%.7f", it.longitude)
        "&sname=${uriEncode("Current location")}&slat=$slat&slon=$slon"
    } ?: ""
    return "androidamap://route?sourceApplication=SpotMark$originParams&dname=$label&dlat=$dlat&dlon=$dlon&dev=1&t=2"
}

fun buildGoogleRouteIntent(spot: SavedSpot): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse(buildGoogleRouteUri(spot))).apply {
        setPackage(GOOGLE_MAPS_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun buildGoogleRouteUri(spot: SavedSpot): String {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    return "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking"
}

fun buildMapFallbackIntent(spot: SavedSpot): Intent {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    val label = Uri.encode(spot.title)
    return Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$lat,$lng($label)")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun uriEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

fun openNavigation(context: Context, spot: SavedSpot, origin: LocationPoint? = null): Boolean {
    val candidates = listOf(
        buildAmapRouteIntent(spot, origin),
        buildGoogleRouteIntent(spot),
        buildMapFallbackIntent(spot),
    )
    return candidates.any { intent -> context.tryStartActivity(intent) }
}

private fun Context.tryStartActivity(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
