package com.chengjiguanjia.spotmark

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.location.arrowRotationDegrees
import com.chengjiguanjia.spotmark.location.formatDistance
import com.chengjiguanjia.spotmark.navigation.openNavigation
import com.chengjiguanjia.spotmark.ui.spot.SpotMarkViewModel
import com.chengjiguanjia.spotmark.ui.theme.SpotMarkTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpotMarkTheme(dynamicColor = false) {
                SpotMarkApp()
            }
        }
    }
}

private enum class PendingLocationAction {
    Capture,
    Find,
    Update,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotMarkApp(
    viewModel: SpotMarkViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spots by viewModel.spots.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingSpot by remember { mutableStateOf<SavedSpot?>(null) }
    var findingSpot by remember { mutableStateOf<SavedSpot?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAction by remember { mutableStateOf<PendingLocationAction?>(null) }
    var pendingFindSpot by remember { mutableStateOf<SavedSpot?>(null) }
    var pendingUpdateSpot by remember { mutableStateOf<SavedSpot?>(null) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            when (pendingAction) {
                PendingLocationAction.Capture -> viewModel.captureCurrentSpot()
                PendingLocationAction.Find -> pendingFindSpot?.let { spot ->
                    findingSpot = spot
                    viewModel.startFinding(spot)
                }
                PendingLocationAction.Update -> pendingUpdateSpot?.let { spot ->
                    viewModel.updateSpotLocation(spot)
                }

                null -> Unit
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("需要定位权限才能保存、更新或找回物品") }
        }
        pendingAction = null
        pendingFindSpot = null
        pendingUpdateSpot = null
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val spot = editingSpot?.let { selected ->
            spots.firstOrNull { it.id == selected.id } ?: selected
        }
        if (uri != null && spot != null) {
            viewModel.addPhoto(spot, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingCameraUri
        val spot = editingSpot?.let { selected ->
            spots.firstOrNull { it.id == selected.id } ?: selected
        }
        if (saved && uri != null && spot != null) {
            viewModel.addPhoto(spot, uri)
        }
        pendingCameraUri = null
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    findingSpot?.let { spot ->
        FindSpotScreen(
            spot = spot,
            distanceText = formatDistance(uiState.targetMetrics?.distanceMeters),
            targetBearing = uiState.targetMetrics?.bearingDegrees,
            onBack = {
                viewModel.stopFinding()
                findingSpot = null
            },
            onNavigate = {
                if (!openNavigation(context, spot)) {
                    copyCoordinates(context, spot)
                    scope.launch { snackbarHostState.showSnackbar("未找到地图 APP，已复制经纬度") }
                }
            },
            snackbarHostState = snackbarHostState,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        HomeScreen(
            spots = spots,
            isCapturing = uiState.isCapturing,
            contentPadding = innerPadding,
            onCapture = {
                if (hasLocationPermission()) {
                    viewModel.captureCurrentSpot()
                } else {
                    pendingAction = PendingLocationAction.Capture
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
            },
            onEdit = { editingSpot = it },
            onFind = { spot ->
                if (hasLocationPermission()) {
                    findingSpot = spot
                    viewModel.startFinding(spot)
                } else {
                    pendingAction = PendingLocationAction.Find
                    pendingFindSpot = spot
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
            },
            onNavigate = { spot ->
                if (!openNavigation(context, spot)) {
                    copyCoordinates(context, spot)
                    scope.launch { snackbarHostState.showSnackbar("未找到地图 APP，已复制经纬度") }
                }
            },
        )
    }

    editingSpot?.let { spot ->
        val latestSpot = spots.firstOrNull { it.id == spot.id } ?: spot
        EditSpotSheet(
            spot = latestSpot,
            onDismiss = { editingSpot = null },
            onSave = { title, note ->
                viewModel.saveSpot(latestSpot, title, note)
                editingSpot = null
            },
            onPickPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onTakePhoto = {
                val uri = viewModel.createCameraUri()
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            },
            onUpdateLocation = {
                if (hasLocationPermission()) {
                    viewModel.updateSpotLocation(latestSpot)
                } else {
                    pendingAction = PendingLocationAction.Update
                    pendingUpdateSpot = latestSpot
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
            },
            isUpdatingLocation = uiState.isUpdatingLocation,
            onDelete = {
                viewModel.deleteSpot(latestSpot)
                editingSpot = null
            },
        )
    }
}

@Composable
private fun HomeScreen(
    spots: List<SavedSpot>,
    isCapturing: Boolean,
    contentPadding: PaddingValues,
    onCapture: () -> Unit,
    onEdit: (SavedSpot) -> Unit,
    onFind: (SavedSpot) -> Unit,
    onNavigate: (SavedSpot) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "SpotMark",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "锁定物品位置，回来时按方向和距离找回。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onCapture,
            enabled = !isCapturing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isCapturing) "正在定位..." else "定位并保存当前位置")
        }
        Spacer(Modifier.height(18.dp))

        if (spots.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(spots, key = { it.id }) { spot ->
                    SpotCard(
                        spot = spot,
                        onEdit = { onEdit(spot) },
                        onFind = { onFind(spot) },
                        onNavigate = { onNavigate(spot) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "还没有保存的位置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "先保存车、钥匙、行李的位置，之后就能按方向找回。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpotCard(
    spot: SavedSpot,
    onEdit: () -> Unit,
    onFind: () -> Unit,
    onNavigate: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpotThumbnail(spot.photoPaths.firstOrNull(), Modifier.size(76.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spot.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = spot.note.ifBlank { "暂无备注" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${formatDate(spot.updatedAt)} | ${"%.5f".format(Locale.US, spot.latitude)}, ${"%.5f".format(Locale.US, spot.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onFind, shape = RoundedCornerShape(8.dp)) {
                        Text("找回")
                    }
                    OutlinedButton(onClick = onNavigate, shape = RoundedCornerShape(8.dp)) {
                        Text("地图")
                    }
                    TextButton(onClick = onEdit) {
                        Text("编辑")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSpotSheet(
    spot: SavedSpot,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onUpdateLocation: () -> Unit,
    isUpdatingLocation: Boolean,
    onDelete: () -> Unit,
) {
    var title by rememberSaveable(spot.id) { mutableStateOf(spot.title) }
    var note by rememberSaveable(spot.id) { mutableStateOf(spot.note) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "编辑位置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("物品名称") },
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                minLines = 3,
            )
            Text(
                text = "图片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (spot.photoPaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(spot.photoPaths) { path ->
                        SpotThumbnail(path, Modifier.size(92.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPickPhoto, shape = RoundedCornerShape(8.dp)) {
                    Text("相册")
                }
                OutlinedButton(onClick = onTakePhoto, shape = RoundedCornerShape(8.dp)) {
                    Text("拍照")
                }
            }
            OutlinedButton(
                onClick = onUpdateLocation,
                enabled = !isUpdatingLocation,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (isUpdatingLocation) "正在更新位置..." else "更新为当前位置")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除")
                }
                Button(onClick = { onSave(title, note) }, shape = RoundedCornerShape(8.dp)) {
                    Text("保存")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个位置？") },
            text = { Text("删除后，本地保存的图片也会一起移除。") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun FindSpotScreen(
    spot: SavedSpot,
    distanceText: String,
    targetBearing: Float?,
    onBack: () -> Unit,
    onNavigate: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val heading = rememberDeviceHeading()
    val rotation = targetBearing?.let { arrowRotationDegrees(it, heading) } ?: 0f

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) {
                    Text("返回")
                }
                OutlinedButton(onClick = onNavigate, shape = RoundedCornerShape(8.dp)) {
                    Text("地图导航")
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = spot.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = spot.note.ifBlank { "朝箭头方向移动，距离会持续刷新。" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(34.dp))
            CompassDial(rotationDegrees = rotation)
            Spacer(Modifier.height(22.dp))
            Text(
                text = distanceText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (targetBearing == null) "正在获取当前位置..." else "方向会随手机朝向实时调整",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            spot.photoPaths.firstOrNull()?.let { path ->
                SpotThumbnail(
                    path = path,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            }
        }
    }
}

@Composable
private fun CompassDial(rotationDegrees: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val outline = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .size(230.dp)
            .clip(CircleShape)
            .background(primaryContainer),
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = outline,
            radius = radius * 0.86f,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        repeat(24) { index ->
            val isCardinal = index % 6 == 0
            rotate(degrees = index * 15f, pivot = center) {
                drawLine(
                    color = if (isCardinal) onPrimaryContainer else outline,
                    start = Offset(center.x, center.y - radius * 0.78f),
                    end = Offset(center.x, center.y - radius * if (isCardinal) 0.64f else 0.70f),
                    strokeWidth = if (isCardinal) 3.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        rotate(degrees = rotationDegrees, pivot = center) {
            val arrow = Path().apply {
                moveTo(center.x, center.y - radius * 0.66f)
                lineTo(center.x - radius * 0.18f, center.y + radius * 0.24f)
                lineTo(center.x, center.y + radius * 0.10f)
                lineTo(center.x + radius * 0.18f, center.y + radius * 0.24f)
                close()
            }
            drawPath(path = arrow, color = primary)
            drawCircle(
                color = onPrimaryContainer,
                radius = radius * 0.05f,
                center = center,
            )
        }
    }
}

@Composable
private fun SpotThumbnail(path: String?, modifier: Modifier) {
    val bitmap = remember(path) {
        path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "无图",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun rememberDeviceHeading(): Float {
    val context = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val rotationMatrix = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    heading = Math.toDegrees(orientation[0].toDouble()).toFloat().let {
                        (it + 360f) % 360f
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
    return heading
}

private fun copyCoordinates(context: Context, spot: SavedSpot) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = "${spot.latitude},${spot.longitude}"
    clipboard.setPrimaryClip(ClipData.newPlainText("SpotMark coordinates", text))
}

private fun formatDate(timeMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
