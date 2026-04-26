package com.chengjiguanjia.spotmark.widget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chengjiguanjia.spotmark.R
import com.chengjiguanjia.spotmark.data.SpotMarkDatabase
import com.chengjiguanjia.spotmark.location.LocationClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetLocationUpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        val spotId = intent?.getLongExtra(EXTRA_SPOT_ID, -1) ?: -1
        if (spotId == -1L || !hasLocationPermission()) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            val message = runCatching {
                val db = SpotMarkDatabase.get(this@WidgetLocationUpdateService)
                val entity = db.savedSpotDao().observeSpot(spotId).first()
                    ?: error("Spot not found")
                val location = LocationClient(this@WidgetLocationUpdateService).getCurrentLocation()
                db.savedSpotDao().update(
                    entity.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracyMeters,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                updateAllWidgets(this@WidgetLocationUpdateService)
                R.string.msg_location_updated
            }.getOrElse {
                R.string.msg_location_update_failed
            }
            launch(Dispatchers.Main) {
                Toast.makeText(this@WidgetLocationUpdateService, message, Toast.LENGTH_SHORT).show()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.updating_location))
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.widget_location_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "spotmark_widget_location"
        private const val NOTIFICATION_ID = 2001
    }
}
