package com.chengjiguanjia.spotmark.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

fun requestPinSpotMarkWidget(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val manager = context.getSystemService(AppWidgetManager::class.java)
    val provider = ComponentName(context, SpotMarkWidgetReceiver::class.java)
    if (!manager.isRequestPinAppWidgetSupported) return false
    val callback = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetPinResultReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return manager.requestPinAppWidget(provider, null, callback)
}
