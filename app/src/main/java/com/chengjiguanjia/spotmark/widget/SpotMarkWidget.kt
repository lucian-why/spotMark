package com.chengjiguanjia.spotmark.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.chengjiguanjia.spotmark.R
import com.chengjiguanjia.spotmark.data.SavedSpotEntity
import com.chengjiguanjia.spotmark.data.SpotMarkDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

const val ACTION_NAVIGATE = "com.chengjiguanjia.spotmark.widget.ACTION_NAVIGATE"
const val ACTION_UPDATE_LOCATION = "com.chengjiguanjia.spotmark.widget.ACTION_UPDATE_LOCATION"
const val ACTION_CAPTURE_LOCATION = "com.chengjiguanjia.spotmark.widget.ACTION_CAPTURE_LOCATION"
const val EXTRA_SPOT_ID = "spot_id"
const val EXTRA_SPOT_TITLE = "spot_title"
const val EXTRA_SPOT_LAT = "spot_lat"
const val EXTRA_SPOT_LNG = "spot_lng"

fun updateAllWidgets(context: Context) {
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)
    val ids = manager.getAppWidgetIds(ComponentName(appContext, SpotMarkWidgetReceiver::class.java))
    CoroutineScope(Dispatchers.IO).launch {
        updateSpotMarkWidgets(appContext, manager, ids)
    }
}

suspend fun updateSpotMarkWidgets(
    context: Context,
    manager: AppWidgetManager,
    widgetIds: IntArray,
) {
    val spots = try {
        SpotMarkDatabase.get(context).savedSpotDao().observeSpots().first().take(3)
    } catch (_: Exception) {
        emptyList()
    }
    widgetIds.forEach { widgetId ->
        manager.updateAppWidget(widgetId, buildWidgetViews(context, spots))
    }
}

private fun buildWidgetViews(context: Context, spots: List<SavedSpotEntity>): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_layout)
    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
    views.setOnClickPendingIntent(R.id.widget_capture, capturePendingIntent(context))
    views.setViewVisibility(R.id.widget_empty, if (spots.isEmpty()) View.VISIBLE else View.GONE)

    val rows = listOf(
        WidgetRowViews(R.id.widget_row_1, R.id.widget_spot_title_1, R.id.widget_spot_time_1, R.id.widget_nav_1, R.id.widget_update_1),
        WidgetRowViews(R.id.widget_row_2, R.id.widget_spot_title_2, R.id.widget_spot_time_2, R.id.widget_nav_2, R.id.widget_update_2),
        WidgetRowViews(R.id.widget_row_3, R.id.widget_spot_title_3, R.id.widget_spot_time_3, R.id.widget_nav_3, R.id.widget_update_3),
    )

    rows.forEachIndexed { index, row ->
        val spot = spots.getOrNull(index)
        views.setViewVisibility(row.containerId, if (spot == null) View.GONE else View.VISIBLE)
        if (spot != null) {
            views.setTextViewText(row.titleId, spot.title)
            views.setTextViewText(row.timeId, formatTimeAgo(context, spot.updatedAt))
            views.setOnClickPendingIntent(row.navigateId, spotPendingIntent(context, spot, ACTION_NAVIGATE, index))
            views.setOnClickPendingIntent(row.updateId, spotPendingIntent(context, spot, ACTION_UPDATE_LOCATION, index))
        }
    }

    return views
}

private fun capturePendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, WidgetActionActivity::class.java).apply {
        action = ACTION_CAPTURE_LOCATION
        data = Uri.parse("spotmark://widget/$ACTION_CAPTURE_LOCATION")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return PendingIntent.getActivity(
        context,
        ACTION_CAPTURE_LOCATION.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun spotPendingIntent(
    context: Context,
    spot: SavedSpotEntity,
    action: String,
    index: Int,
): PendingIntent {
    val intent = Intent(context, WidgetActionReceiver::class.java).apply {
        this.action = action
        data = Uri.parse("spotmark://widget/$action/${spot.id}/$index")
        putExtra(EXTRA_SPOT_ID, spot.id)
        putExtra(EXTRA_SPOT_TITLE, spot.title)
        putExtra(EXTRA_SPOT_LAT, spot.latitude)
        putExtra(EXTRA_SPOT_LNG, spot.longitude)
    }
    val requestCode = abs((spot.id.toString() + action + index).hashCode())
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private data class WidgetRowViews(
    val containerId: Int,
    val titleId: Int,
    val timeId: Int,
    val navigateId: Int,
    val updateId: Int,
)

private fun formatTimeAgo(context: Context, epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    return when {
        diff < 60_000 -> context.getString(R.string.widget_just_now)
        diff < 3_600_000 -> context.getString(R.string.widget_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> context.getString(R.string.widget_hours_ago, diff / 3_600_000)
        diff < 604_800_000 -> context.getString(R.string.widget_days_ago, diff / 86_400_000)
        else -> {
            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            sdf.format(Date(epochMillis))
        }
    }
}
