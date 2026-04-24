package com.chengjiguanjia.spotmark.ui.spot

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chengjiguanjia.spotmark.R
import com.chengjiguanjia.spotmark.data.SavedSpotRepository
import com.chengjiguanjia.spotmark.data.SpotMarkDatabase
import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.location.LocationClient
import com.chengjiguanjia.spotmark.location.LocationPoint
import com.chengjiguanjia.spotmark.location.TargetMetrics
import com.chengjiguanjia.spotmark.location.metricsToTarget
import com.chengjiguanjia.spotmark.media.PhotoStore
import com.chengjiguanjia.spotmark.widget.updateAllWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SpotMarkUiState(
    val isCapturing: Boolean = false,
    val isUpdatingLocation: Boolean = false,
    @param:StringRes val messageResId: Int? = null,
    val currentLocation: LocationPoint? = null,
    val targetMetrics: TargetMetrics? = null,
)

class SpotMarkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SavedSpotRepository(
        SpotMarkDatabase.get(application).savedSpotDao(),
    )
    private val locationClient = LocationClient(application)
    private val photoStore = PhotoStore(application)

    val spots: StateFlow<List<SavedSpot>> = repository.observeSpots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(SpotMarkUiState())
    val uiState: StateFlow<SpotMarkUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null

    fun captureCurrentSpot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, messageResId = null) }
            runCatching {
                val location = locationClient.getCurrentLocation()
                repository.addSpot(
                    title = "Spot ${timeLabel()}",
                    note = "",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    photoPaths = emptyList(),
                )
                refreshWidgets()
                R.string.msg_location_saved
            }.onSuccess { message ->
                _uiState.update { it.copy(isCapturing = false, messageResId = message) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        messageResId = R.string.msg_location_failed,
                    )
                }
            }
        }
    }

    fun startFinding(spot: SavedSpot) {
        trackingJob?.cancel()
        val target = LocationPoint(spot.latitude, spot.longitude, spot.accuracyMeters)
        trackingJob = viewModelScope.launch {
            locationClient.observeLocation()
                .catch { error ->
                    _uiState.update {
                        it.copy(messageResId = R.string.msg_tracking_failed)
                    }
                }
                .collect { current ->
                    _uiState.update {
                        it.copy(
                            currentLocation = current,
                            targetMetrics = metricsToTarget(current, target),
                        )
                    }
                }
        }
    }

    fun stopFinding() {
        trackingJob?.cancel()
        trackingJob = null
        _uiState.update { it.copy(currentLocation = null, targetMetrics = null) }
    }

    fun saveSpot(spot: SavedSpot, title: String, note: String) {
        viewModelScope.launch {
            val cleanTitle = title.trim().ifBlank { "Untitled spot" }
            repository.updateSpot(spot.copy(title = cleanTitle, note = note.trim()))
            refreshWidgets()
            _uiState.update { it.copy(messageResId = R.string.msg_changes_saved) }
        }
    }

    fun updateSpotLocation(spot: SavedSpot) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingLocation = true, messageResId = null) }
            runCatching {
                val location = locationClient.getCurrentLocation()
                repository.updateSpot(
                    spot.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracyMeters,
                    ),
                )
                refreshWidgets()
                R.string.msg_location_updated
            }.onSuccess { message ->
                _uiState.update { it.copy(isUpdatingLocation = false, messageResId = message) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUpdatingLocation = false,
                        messageResId = R.string.msg_location_update_failed,
                    )
                }
            }
        }
    }

    fun addPhoto(spot: SavedSpot, source: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = photoStore.savePhoto(source)
                repository.updateSpot(spot.copy(photoPaths = spot.photoPaths + path))
                refreshWidgets()
            }.onSuccess {
                _uiState.update { it.copy(messageResId = R.string.msg_photo_added) }
            }.onFailure { error ->
                _uiState.update { it.copy(messageResId = R.string.msg_photo_failed) }
            }
        }
    }

    fun replaceThumbnailPhoto(spot: SavedSpot, source: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = photoStore.savePhoto(source)
                repository.updateSpot(spot.copy(photoPaths = spot.photoPaths + path))
                refreshWidgets()
            }.onSuccess {
                _uiState.update { it.copy(messageResId = R.string.msg_photo_replaced) }
            }.onFailure { error ->
                _uiState.update { it.copy(messageResId = R.string.msg_photo_failed) }
            }
        }
    }

    fun deletePhoto(spot: SavedSpot, path: String) {
        viewModelScope.launch {
            photoStore.deletePhoto(path)
            repository.updateSpot(spot.copy(photoPaths = spot.photoPaths - path))
            refreshWidgets()
            _uiState.update { it.copy(messageResId = R.string.msg_photo_deleted) }
        }
    }

    fun deleteSpot(spot: SavedSpot) {
        viewModelScope.launch {
            repository.deleteSpot(spot)
            photoStore.deletePhotos(spot.photoPaths)
            refreshWidgets()
            _uiState.update { it.copy(messageResId = R.string.msg_spot_deleted) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(messageResId = null) }
    }

    fun createCameraUri(): Uri = photoStore.createCameraUri()

    private fun timeLabel(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private suspend fun refreshWidgets() {
        updateAllWidgets(getApplication<Application>().applicationContext)
    }
}
