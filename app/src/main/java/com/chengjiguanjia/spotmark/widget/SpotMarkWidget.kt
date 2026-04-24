package com.chengjiguanjia.spotmark.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.chengjiguanjia.spotmark.R
import com.chengjiguanjia.spotmark.data.SavedSpotEntity
import com.chengjiguanjia.spotmark.data.SpotMarkDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ACTION_NAVIGATE = "com.chengjiguanjia.spotmark.widget.ACTION_NAVIGATE"
const val ACTION_UPDATE_LOCATION = "com.chengjiguanjia.spotmark.widget.ACTION_UPDATE_LOCATION"
const val EXTRA_SPOT_ID = "spot_id"
const val EXTRA_SPOT_TITLE = "spot_title"
const val EXTRA_SPOT_LAT = "spot_lat"
const val EXTRA_SPOT_LNG = "spot_lng"

class SpotMarkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val spots = try {
            SpotMarkDatabase.get(context).savedSpotDao().observeSpots().first()
        } catch (_: Exception) {
            emptyList()
        }

        provideContent {
            GlanceTheme {
                SpotMarkWidgetContent(context, spots.take(3))
            }
        }
    }
}

suspend fun updateAllWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    val widget = SpotMarkWidget()
    val ids = manager.getGlanceIds(SpotMarkWidget::class.java)
    ids.forEach { widget.update(context, it) }
}

@Composable
fun SpotMarkWidgetContent(context: Context, spots: List<SavedSpotEntity>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .fillMaxWidth()
            .background(ColorProvider(0xE6FFFFFF.toInt()))
            .padding(12.dp),
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = ColorProvider(0xFF333333.toInt()),
            ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (spots.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(color = ColorProvider(0xFF999999.toInt())),
            )
        } else {
            spots.forEachIndexed { index, spot ->
                SpotRow(context, spot)
                if (index < spots.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun SpotRow(context: Context, spot: SavedSpotEntity) {
    val navIntent = Intent().apply {
        setClassName(context.packageName, "com.chengjiguanjia.spotmark.widget.WidgetActionReceiver")
        action = ACTION_NAVIGATE
        putExtra(EXTRA_SPOT_ID, spot.id)
        putExtra(EXTRA_SPOT_TITLE, spot.title)
        putExtra(EXTRA_SPOT_LAT, spot.latitude)
        putExtra(EXTRA_SPOT_LNG, spot.longitude)
    }
    val updateIntent = Intent().apply {
        setClassName(context.packageName, "com.chengjiguanjia.spotmark.widget.WidgetActionReceiver")
        action = ACTION_UPDATE_LOCATION
        putExtra(EXTRA_SPOT_ID, spot.id)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = spot.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(0xFF333333.toInt()),
                    ),
                )
                Text(
                    text = formatTimeAgo(context, spot.updatedAt),
                    style = TextStyle(color = ColorProvider(0xFF999999.toInt())),
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = context.getString(R.string.widget_navigate),
                style = TextStyle(color = ColorProvider(0xFF1976D2.toInt())),
                modifier = GlanceModifier.clickable(actionSendBroadcast(navIntent)),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = context.getString(R.string.widget_update),
                style = TextStyle(color = ColorProvider(0xFF1976D2.toInt())),
                modifier = GlanceModifier.clickable(actionSendBroadcast(updateIntent)),
            )
        }
    }
}

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
