package com.chengjiguanjia.spotmark.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.chengjiguanjia.spotmark.R
import com.chengjiguanjia.spotmark.data.SpotMarkDatabase
import com.chengjiguanjia.spotmark.ui.theme.SpotMarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetRenameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != ACTION_RENAME_SPOT) {
            finish()
            return
        }
        val spotId = intent.getLongExtra(EXTRA_SPOT_ID, -1)
        val initialTitle = intent.getStringExtra(EXTRA_SPOT_TITLE).orEmpty()
        if (spotId == -1L) {
            finish()
            return
        }

        setContent {
            SpotMarkTheme {
                var title by remember { mutableStateOf(initialTitle) }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(stringResource(R.string.rename_spot)) },
                    text = {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.item_name)) },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                saveTitle(spotId, title)
                            },
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }

    private fun saveTitle(spotId: Long, title: String) {
        lifecycleScope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val db = SpotMarkDatabase.get(this@WidgetRenameActivity)
                    val entity = db.savedSpotDao().observeSpot(spotId).first()
                        ?: error("Spot not found")
                    val cleanTitle = title.trim().ifBlank {
                        getString(R.string.widget_default_spot_title, timeLabel(System.currentTimeMillis()))
                    }
                    db.savedSpotDao().update(
                        entity.copy(
                            title = cleanTitle,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    updateAllWidgets(this@WidgetRenameActivity)
                }
                R.string.msg_changes_saved
            }.getOrElse {
                R.string.msg_location_update_failed
            }
            Toast.makeText(this@WidgetRenameActivity, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun timeLabel(epochMillis: Long): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(epochMillis))
}
