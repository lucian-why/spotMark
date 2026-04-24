package com.chengjiguanjia.spotmark.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

fun requestPinSpotMarkWidget(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val manager = context.getSystemService(AppWidgetManager::class.java)
    val provider = ComponentName(context, SpotMarkWidgetReceiver::class.java)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return manager.requestPinAppWidget(provider, null, null)
}
