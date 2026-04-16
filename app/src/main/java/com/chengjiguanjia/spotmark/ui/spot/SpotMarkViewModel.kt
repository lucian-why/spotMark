package com.chengjiguanjia.spotmark.ui.spot

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chengjiguanjia.spotmark.data.SavedSpotRepository
import com.chengjiguanjia.spotmark.data.SpotMarkDatabase
import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.location.LocationClient
import com.chengjiguanjia.spotmark.location.LocationPoint
import com.chengjiguanjia.spotmark.location.TargetMetrics
import com.chengjiguanjia.spotmark.location.metricsToTarget
import com.chengjiguanjia.spotmark.media.PhotoStore
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
    val message: String? = null,
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
            _uiState.update { it.copy(isCapturing = true, message = null) }
            runCatching {
                val location = locationClient.getCurrentLocation()
                repository.addSpot(
                    title = "新位置 ${timeLabel()}",
                    note = "",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    photoPaths = emptyList(),
                )
                "已保存当前位置，可继续编辑备注和图片"
            }.onSuccess { message ->
                _uiState.update { it.copy(isCapturing = false, message = message) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        message = error.message ?: "定位失败，请检查权限和系统定位开关",
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
                        it.copy(message = error.message ?: "无法持续获取当前位置")
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
            val cleanTitle = title.trim().ifBlank { "未命名位置" }
            repository.updateSpot(spot.copy(title = cleanTitle, note = note.trim()))
            _uiState.update { it.copy(message = "已保存修改") }
        }
    }

    fun updateSpotLocation(spot: SavedSpot) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingLocation = true, message = null) }
            runCatching {
                val location = locationClient.getCurrentLocation()
                repository.updateSpot(
                    spot.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracyMeters,
                    ),
                )
                "已更新为当前位置"
            }.onSuccess { message ->
                _uiState.update { it.copy(isUpdatingLocation = false, message = message) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUpdatingLocation = false,
                        message = error.message ?: "更新定位失败，请检查权限和系统定位开关",
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
            }.onSuccess {
                _uiState.update { it.copy(message = "图片已添加") }
            }.onFailure { error ->
                _uiState.update { it.copy(message = error.message ?: "图片保存失败") }
            }
        }
    }

    fun deleteSpot(spot: SavedSpot) {
        viewModelScope.launch {
            repository.deleteSpot(spot)
            photoStore.deletePhotos(spot.photoPaths)
            _uiState.update { it.copy(message = "已删除位置") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun createCameraUri(): Uri = photoStore.createCameraUri()

    private fun timeLabel(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
