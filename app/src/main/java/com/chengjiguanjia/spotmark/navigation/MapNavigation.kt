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
private const val BAIDU_MAP_PACKAGE = "com.baidu.BaiduMap"
private const val TENCENT_MAP_PACKAGE = "com.tencent.map"
private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
private const val PREFS_NAME = "spotmark_navigation"
private const val KEY_PROVIDER = "provider"

enum class MapProvider(val id: String) {
    Amap("amap"),
    Baidu("baidu"),
    Tencent("tencent"),
    Google("google"),
    System("system"),
}

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

fun buildBaiduRouteIntent(spot: SavedSpot): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse(buildBaiduRouteUri(spot))).apply {
        setPackage(BAIDU_MAP_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun buildBaiduRouteUri(spot: SavedSpot): String {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    val label = uriEncode(spot.title)
    return "baidumap://map/direction?destination=latlng:$lat,$lng|name:$label&mode=walking&coord_type=wgs84"
}

fun buildTencentRouteIntent(spot: SavedSpot): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse(buildTencentRouteUri(spot))).apply {
        setPackage(TENCENT_MAP_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun buildTencentRouteUri(spot: SavedSpot): String {
    val lat = String.format(Locale.US, "%.7f", spot.latitude)
    val lng = String.format(Locale.US, "%.7f", spot.longitude)
    val label = uriEncode(spot.title)
    return "qqmap://map/routeplan?type=walk&tocoord=$lat,$lng&to=$label&referer=SpotMark"
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
        buildBaiduRouteIntent(spot),
        buildTencentRouteIntent(spot),
        buildGoogleRouteIntent(spot),
        buildMapFallbackIntent(spot),
    )
    return candidates.any { intent -> context.tryStartActivity(intent) }
}

fun openNavigation(context: Context, spot: SavedSpot, origin: LocationPoint?, provider: MapProvider): Boolean {
    val intent = when (provider) {
        MapProvider.Amap -> buildAmapRouteIntent(spot, origin)
        MapProvider.Baidu -> buildBaiduRouteIntent(spot)
        MapProvider.Tencent -> buildTencentRouteIntent(spot)
        MapProvider.Google -> buildGoogleRouteIntent(spot)
        MapProvider.System -> buildMapFallbackIntent(spot)
    }
    return context.tryStartActivity(intent)
}

fun openNavigationWithPreference(context: Context, spot: SavedSpot, origin: LocationPoint? = null): Boolean {
    val provider = getRememberedMapProvider(context)
    if (provider != null) {
        return openNavigation(context, spot, origin, provider) || openNavigation(context, spot, origin)
    }
    return MapChoiceActivity.start(context, spot, origin)
}

fun getRememberedMapProvider(context: Context): MapProvider? {
    val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PROVIDER, null)
    return MapProvider.entries.firstOrNull { it.id == id }
}

fun rememberMapProvider(context: Context, provider: MapProvider) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_PROVIDER, provider.id)
        .apply()
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
