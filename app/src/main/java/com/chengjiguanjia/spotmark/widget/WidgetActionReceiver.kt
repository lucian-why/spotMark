package com.chengjiguanjia.spotmark.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.navigation.openNavigationWithPreference

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val spotId = intent.getLongExtra(EXTRA_SPOT_ID, -1)
        if (spotId == -1L) return

        when (intent.action) {
            ACTION_NAVIGATE -> handleNavigate(context, intent, spotId)
            ACTION_UPDATE_LOCATION -> handleUpdateLocation(context, spotId)
        }
    }

    private fun handleNavigate(context: Context, intent: Intent, spotId: Long) {
        val title = intent.getStringExtra(EXTRA_SPOT_TITLE) ?: return
        val lat = intent.getDoubleExtra(EXTRA_SPOT_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_SPOT_LNG, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return

        val spot = SavedSpot(
            id = spotId,
            title = title,
            note = "",
            latitude = lat,
            longitude = lng,
            accuracyMeters = null,
            createdAt = 0,
            updatedAt = 0,
            photoPaths = emptyList(),
        )
        openNavigationWithPreference(context, spot)
    }

    private fun handleUpdateLocation(context: Context, spotId: Long) {
        val activityIntent = Intent(context, WidgetActionActivity::class.java).apply {
            action = ACTION_UPDATE_LOCATION
            putExtra(EXTRA_SPOT_ID, spotId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(activityIntent)
    }
}
