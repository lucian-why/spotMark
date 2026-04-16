package com.chengjiguanjia.spotmark.navigation

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import com.chengjiguanjia.spotmark.domain.SavedSpot
import java.util.Locale

private const val AMAP_PACKAGE = "com.autonavi.minimap"
private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

fun buildAmapNavigationIntent(spot: SavedSpot): Intent {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    val label = Uri.encode(spot.title)
    val uri = Uri.parse(
        "androidamap://navi?sourceApplication=SpotMark&poiname=$label&lat=$lat&lon=$lng&dev=1&style=2",
    )
    return Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage(AMAP_PACKAGE)
    }
}

fun buildGoogleNavigationIntent(spot: SavedSpot): Intent {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    val uri = Uri.parse("google.navigation:q=$lat,$lng")
    return Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage(GOOGLE_MAPS_PACKAGE)
    }
}

fun buildMapFallbackIntent(spot: SavedSpot): Intent {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    val label = Uri.encode(spot.title)
    return Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$lat,$lng($label)"))
}

fun openNavigation(context: Context, spot: SavedSpot): Boolean {
    val candidates = listOf(
        buildAmapNavigationIntent(spot),
        buildGoogleNavigationIntent(spot),
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
