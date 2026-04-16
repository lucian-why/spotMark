package com.chengjiguanjia.spotmark.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chengjiguanjia.spotmark.domain.SavedSpot
import java.util.Locale

fun buildNavigationIntent(spot: SavedSpot): Intent {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    val uri = Uri.parse("google.navigation:q=$lat,$lng")
    return Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
}

fun buildMapFallbackIntent(spot: SavedSpot): Intent {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    val label = Uri.encode(spot.title)
    return Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$lat,$lng($label)"))
}

fun openNavigation(context: Context, spot: SavedSpot): Boolean {
    val googleMapsIntent = buildNavigationIntent(spot)
    val fallbackIntent = buildMapFallbackIntent(spot)
    return when {
        googleMapsIntent.resolveActivity(context.packageManager) != null -> {
            context.startActivity(googleMapsIntent)
            true
        }

        fallbackIntent.resolveActivity(context.packageManager) != null -> {
            context.startActivity(fallbackIntent)
            true
        }

        else -> false
    }
}
