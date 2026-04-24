package com.chengjiguanjia.spotmark.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chengjiguanjia.spotmark.R
import com.chengjiguanjia.spotmark.data.SpotMarkDatabase
import com.chengjiguanjia.spotmark.location.LocationClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetActionActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            updateSpotLocation()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != ACTION_UPDATE_LOCATION) {
            finish()
            return
        }
        if (hasLocationPermission()) {
            updateSpotLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    private fun updateSpotLocation() {
        val spotId = intent.getLongExtra(EXTRA_SPOT_ID, -1)
        if (spotId == -1L) {
            finish()
            return
        }

        lifecycleScope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val db = SpotMarkDatabase.get(this@WidgetActionActivity)
                    val entity = db.savedSpotDao().observeSpot(spotId).first()
                        ?: error("Spot not found")
                    val location = LocationClient(this@WidgetActionActivity).getCurrentLocation()
                    db.savedSpotDao().update(
                        entity.copy(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracyMeters,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    updateAllWidgets(this@WidgetActionActivity)
                }
                R.string.msg_location_updated
            }.getOrElse {
                R.string.msg_location_update_failed
            }
            Toast.makeText(this@WidgetActionActivity, message, Toast.LENGTH_SHORT).show()
            finish()
        }
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
}
