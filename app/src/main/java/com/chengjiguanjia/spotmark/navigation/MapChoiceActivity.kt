package com.chengjiguanjia.spotmark.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chengjiguanjia.spotmark.R
import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.location.LocationPoint
import com.chengjiguanjia.spotmark.ui.theme.SpotMarkTheme

class MapChoiceActivity : ComponentActivity() {
    private val spot: SavedSpot? by lazy { intent.toSpot() }
    private val origin: LocationPoint? by lazy { intent.toOrigin() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spot = spot
        if (spot == null) {
            finish()
            return
        }

        setContent {
            SpotMarkTheme {
                MapChoiceDialog(
                    onDismiss = { finish() },
                    onConfirm = { provider, rememberChoice ->
                        if (rememberChoice) {
                            rememberMapProvider(this, provider)
                        }
                        if (!openNavigation(this, spot, origin, provider) && !openNavigation(this, spot, origin)) {
                            Toast.makeText(this, R.string.no_map_app, Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"
        private const val EXTRA_ORIGIN_LAT = "origin_lat"
        private const val EXTRA_ORIGIN_LNG = "origin_lng"
        private const val EXTRA_ORIGIN_ACCURACY = "origin_accuracy"

        fun start(context: Context, spot: SavedSpot, origin: LocationPoint? = null): Boolean {
            val intent = Intent(context, MapChoiceActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_TITLE, spot.title)
                putExtra(EXTRA_LAT, spot.latitude)
                putExtra(EXTRA_LNG, spot.longitude)
                origin?.let {
                    putExtra(EXTRA_ORIGIN_LAT, it.latitude)
                    putExtra(EXTRA_ORIGIN_LNG, it.longitude)
                    it.accuracyMeters?.let { accuracy -> putExtra(EXTRA_ORIGIN_ACCURACY, accuracy) }
                }
            }
            return runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }

        private fun Intent.toSpot(): SavedSpot? {
            val title = getStringExtra(EXTRA_TITLE) ?: return null
            val lat = getDoubleExtra(EXTRA_LAT, Double.NaN)
            val lng = getDoubleExtra(EXTRA_LNG, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) return null
            return SavedSpot(
                title = title,
                note = "",
                latitude = lat,
                longitude = lng,
                accuracyMeters = null,
                createdAt = 0,
                updatedAt = 0,
                photoPaths = emptyList(),
            )
        }

        private fun Intent.toOrigin(): LocationPoint? {
            if (!hasExtra(EXTRA_ORIGIN_LAT) || !hasExtra(EXTRA_ORIGIN_LNG)) return null
            val lat = getDoubleExtra(EXTRA_ORIGIN_LAT, Double.NaN)
            val lng = getDoubleExtra(EXTRA_ORIGIN_LNG, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) return null
            val accuracy = if (hasExtra(EXTRA_ORIGIN_ACCURACY)) getFloatExtra(EXTRA_ORIGIN_ACCURACY, 0f) else null
            return LocationPoint(lat, lng, accuracy)
        }
    }
}

@Composable
private fun MapChoiceDialog(
    onDismiss: () -> Unit,
    onConfirm: (MapProvider, Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(MapProvider.Amap) }
    var rememberChoice by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_map_app)) },
        text = {
            Column {
                MapProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = provider }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == provider, onClick = { selected = provider })
                        Spacer(Modifier.width(8.dp))
                        Text(provider.label())
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberChoice = !rememberChoice }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.remember_map_choice))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, rememberChoice) }) {
                Text(stringResource(R.string.map_navigation))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun MapProvider.label(): String =
    when (this) {
        MapProvider.Amap -> stringResource(R.string.map_provider_amap)
        MapProvider.Baidu -> stringResource(R.string.map_provider_baidu)
        MapProvider.Tencent -> stringResource(R.string.map_provider_tencent)
        MapProvider.Google -> stringResource(R.string.map_provider_google)
        MapProvider.System -> stringResource(R.string.map_provider_system)
    }
