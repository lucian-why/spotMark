package com.chengjiguanjia.spotmark

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.location.LocationClient
import com.chengjiguanjia.spotmark.location.LocationPoint
import com.chengjiguanjia.spotmark.location.arrowRotationDegrees
import com.chengjiguanjia.spotmark.location.formatDistance
import com.chengjiguanjia.spotmark.navigation.openNavigation
import com.chengjiguanjia.spotmark.ui.spot.SpotMarkViewModel
import com.chengjiguanjia.spotmark.ui.theme.SpotMarkTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val Night = Color(0xFF110D0A)
private val Umber = Color(0xFF211611)
private val Panel = Color(0xFF2B1B13)
private val PanelSoft = Color(0xFF372216)
private val Gold = Color(0xFFE2A84B)
private val GoldSoft = Color(0xFFFFD283)
private val Bone = Color(0xFFF4E6CF)
private val Smoke = Color(0xFFBCA78B)
private val Oxide = Color(0xFF8D4027)

private data class DeviceOrientation(
    val headingDegrees: Float = 0f,
    val isTilted: Boolean = false,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpotMarkTheme(darkTheme = true, dynamicColor = false) {
                var languageTag by remember { mutableStateOf(loadLanguageTag()) }
                val localized = remember(languageTag) { localizedContext(languageTag) }
                val configuration = remember(languageTag) { localized.resources.configuration }
                CompositionLocalProvider(
                    LocalContext provides localized,
                    LocalConfiguration provides configuration,
                ) {
                    SpotMarkApp(
                        languageTag = languageTag,
                        onLanguageChange = { nextTag ->
                            saveLanguageTag(nextTag)
                            languageTag = nextTag
                        },
                    )
                }
            }
        }
    }

    private fun loadLanguageTag(): String =
        getSharedPreferences("spotmark_settings", Context.MODE_PRIVATE)
            .getString("language_tag", "en") ?: "en"

    private fun saveLanguageTag(languageTag: String) {
        getSharedPreferences("spotmark_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("language_tag", languageTag)
            .apply()
    }

    private fun localizedContext(languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        return createConfigurationContext(config)
    }
}

private enum class PendingLocationAction {
    Capture,
    Find,
    Update,
    Route,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotMarkApp(
    languageTag: String,
    onLanguageChange: (String) -> Unit,
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
    var pendingRouteSpot by remember { mutableStateOf<SavedSpot?>(null) }

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
                PendingLocationAction.Route -> pendingRouteSpot?.let { spot ->
                    scope.launch {
                        openRoutePreview(
                            context = context,
                            spot = spot,
                            snackbarHostState = snackbarHostState,
                        )
                    }
                }
                null -> Unit
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.permission_required)) }
        }
        pendingAction = null
        pendingFindSpot = null
        pendingUpdateSpot = null
        pendingRouteSpot = null
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

    val message = uiState.messageResId?.let { stringResource(it) }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
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
                scope.launch {
                    openRoutePreview(
                        context = context,
                        spot = spot,
                        snackbarHostState = snackbarHostState,
                        origin = uiState.currentLocation,
                    )
                }
            },
            snackbarHostState = snackbarHostState,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Night,
    ) { innerPadding ->
        HomeScreen(
            spots = spots,
            isCapturing = uiState.isCapturing,
            contentPadding = innerPadding,
            languageTag = languageTag,
            onLanguageChange = onLanguageChange,
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
                if (hasLocationPermission()) {
                    scope.launch {
                        openRoutePreview(
                            context = context,
                            spot = spot,
                            snackbarHostState = snackbarHostState,
                        )
                    }
                } else {
                    pendingAction = PendingLocationAction.Route
                    pendingRouteSpot = spot
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
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

private suspend fun openRoutePreview(
    context: Context,
    spot: SavedSpot,
    snackbarHostState: SnackbarHostState,
    origin: LocationPoint? = null,
) {
    val resolvedOrigin = origin ?: runCatching {
        LocationClient(context).getCurrentLocation()
    }.getOrNull()
    if (!openNavigation(context, spot, resolvedOrigin)) {
        copyCoordinates(context, spot)
        snackbarHostState.showSnackbar(context.getString(R.string.no_map_app))
    }
}

@Composable
private fun HomeScreen(
    spots: List<SavedSpot>,
    isCapturing: Boolean,
    contentPadding: PaddingValues,
    languageTag: String,
    onLanguageChange: (String) -> Unit,
    onCapture: () -> Unit,
    onEdit: (SavedSpot) -> Unit,
    onFind: (SavedSpot) -> Unit,
    onNavigate: (SavedSpot) -> Unit,
) {
    RembrandtBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            HeroPanel(
                isCapturing = isCapturing,
                onCapture = onCapture,
                savedCount = spots.size,
                languageTag = languageTag,
                onLanguageChange = onLanguageChange,
            )
            Spacer(Modifier.height(18.dp))

            if (spots.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
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
}

@Composable
private fun RembrandtBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Gold.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.10f),
                    radius = size.maxDimension * 0.62f,
                ),
                radius = size.maxDimension * 0.62f,
                center = Offset(size.width * 0.22f, size.height * 0.10f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Oxide.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.74f),
                    radius = size.maxDimension * 0.48f,
                ),
                radius = size.maxDimension * 0.48f,
                center = Offset(size.width * 0.88f, size.height * 0.74f),
            )
        }
        content()
    }
}

@Composable
private fun HeroPanel(
    isCapturing: Boolean,
    onCapture: () -> Unit,
    savedCount: Int,
    languageTag: String,
    onLanguageChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(26.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Gold)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(PanelSoft, Panel, Umber),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
                shape = RoundedCornerShape(8.dp),
            )
            .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = Bone,
                )
                Text(
                    text = stringResource(R.string.hero_count, savedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
            }
            LanguageToggle(languageTag = languageTag, onLanguageChange = onLanguageChange)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onCapture,
            enabled = !isCapturing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isCapturing) stringResource(R.string.locating) else stringResource(R.string.save_current_location))
        }
    }
}

@Composable
private fun LanguageToggle(
    languageTag: String,
    onLanguageChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LanguageButton(
            text = stringResource(R.string.language_english),
            selected = languageTag == "en",
            onClick = { onLanguageChange("en") },
        )
        LanguageButton(
            text = stringResource(R.string.language_chinese),
            selected = languageTag == "zh-CN",
            onClick = { onLanguageChange("zh-CN") },
        )
    }
}

@Composable
private fun LanguageButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
            Text(text)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 52.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Bone,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
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
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpotThumbnail(spot.photoPaths.firstOrNull(), Modifier.size(82.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spot.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Bone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = spot.note.ifBlank { stringResource(R.string.no_note) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${formatDate(spot.updatedAt)} | ${"%.5f".format(Locale.US, spot.latitude)}, ${"%.5f".format(Locale.US, spot.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldSoft.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onFind, shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.find))
                    }
                    OutlinedButton(onClick = onNavigate, shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.map))
                    }
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.edit))
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        contentColor = Bone,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.edit_spot),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Bone,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.item_name)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.note)) },
                minLines = 3,
            )
            Text(
                text = stringResource(R.string.photos),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Bone,
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
                    Text(stringResource(R.string.gallery))
                }
                OutlinedButton(onClick = onTakePhoto, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.camera))
                }
            }
            OutlinedButton(
                onClick = onUpdateLocation,
                enabled = !isUpdatingLocation,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (isUpdatingLocation) stringResource(R.string.updating_location) else stringResource(R.string.update_current_location))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.delete))
                }
                Button(onClick = { onSave(title, note) }, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Panel,
            titleContentColor = Bone,
            textContentColor = Smoke,
            title = { Text(stringResource(R.string.delete_this_spot)) },
            text = { Text(stringResource(R.string.delete_photo_warning)) },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
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
    val orientation = rememberDeviceOrientation()
    val rotation = targetBearing?.let { arrowRotationDegrees(it, orientation.headingDegrees) } ?: 0f

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Night,
    ) { innerPadding ->
        RembrandtBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                    OutlinedButton(onClick = onNavigate, shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.map_navigation))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = spot.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Bone,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = spot.note.ifBlank { stringResource(R.string.find_hint) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
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
                    color = Bone,
                )
                Text(
                    text = when {
                        targetBearing == null -> stringResource(R.string.getting_current_location)
                        orientation.isTilted -> stringResource(R.string.keep_phone_level)
                        else -> stringResource(R.string.heading_follows_phone)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (orientation.isTilted) GoldSoft else Smoke,
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
}

@Composable
private fun CompassDial(rotationDegrees: Float) {
    Canvas(
        modifier = Modifier
            .size(238.dp)
            .shadow(34.dp, CircleShape, ambientColor = Color.Black, spotColor = Gold)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(PanelSoft, Panel, Night),
                    radius = 300f,
                ),
            )
            .border(1.dp, Gold.copy(alpha = 0.34f), CircleShape),
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = Gold.copy(alpha = 0.24f),
            radius = radius * 0.86f,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        repeat(24) { index ->
            val isCardinal = index % 6 == 0
            rotate(degrees = index * 15f, pivot = center) {
                drawLine(
                    color = if (isCardinal) GoldSoft else Smoke.copy(alpha = 0.42f),
                    start = Offset(center.x, center.y - radius * 0.78f),
                    end = Offset(center.x, center.y - radius * if (isCardinal) 0.62f else 0.70f),
                    strokeWidth = if (isCardinal) 3.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        rotate(degrees = rotationDegrees, pivot = center) {
            val arrow = Path().apply {
                moveTo(center.x, center.y - radius * 0.68f)
                lineTo(center.x - radius * 0.18f, center.y + radius * 0.25f)
                lineTo(center.x, center.y + radius * 0.11f)
                lineTo(center.x + radius * 0.18f, center.y + radius * 0.25f)
                close()
            }
            drawPath(path = arrow, color = Gold)
            drawCircle(color = Bone, radius = radius * 0.05f, center = center)
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
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Umber)
                .border(1.dp, Gold.copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.no_photo),
                style = MaterialTheme.typography.labelMedium,
                color = Smoke,
            )
        }
    }
}

@Composable
private fun rememberDeviceOrientation(): DeviceOrientation {
    val context = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }
    var isTilted by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val rotationMatrix = FloatArray(9)
                    val adjustedRotationMatrix = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val rotation = context.display?.rotation ?: Surface.ROTATION_0
                    val (axisX, axisY) = when (rotation) {
                        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        axisX,
                        axisY,
                        adjustedRotationMatrix,
                    )
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation)

                    val nextHeading = Math.toDegrees(orientation[0].toDouble()).toFloat().let {
                        (it + 360f) % 360f
                    }
                    val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                    isTilted = abs(pitch) > 45f || abs(roll) > 45f
                    heading = smoothHeading(heading, nextHeading)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
    return DeviceOrientation(headingDegrees = heading, isTilted = isTilted)
}

private fun smoothHeading(current: Float, target: Float): Float {
    val delta = ((target - current + 540f) % 360f) - 180f
    return (current + delta * 0.18f + 360f) % 360f
}

private fun copyCoordinates(context: Context, spot: SavedSpot) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = "${spot.latitude},${spot.longitude}"
    clipboard.setPrimaryClip(ClipData.newPlainText("SpotMark coordinates", text))
}

private fun formatDate(timeMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
