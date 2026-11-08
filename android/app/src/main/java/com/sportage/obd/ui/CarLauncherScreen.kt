package com.sportage.obd.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import com.sportage.obd.map.HybridLocationMap
import androidx.compose.ui.platform.testTag
import com.sportage.obd.map.NavigationLauncher
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.media.MediaAppInfo
import com.sportage.obd.media.MediaApps
import com.sportage.obd.media.NowPlayingController
import com.sportage.obd.media.chosenMusicApp
import com.sportage.obd.media.saveChosenMusicApp
import com.sportage.obd.obd.ActuatorTestState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.DtcSeverity
import com.sportage.obd.service.ActiveAlert
import com.sportage.obd.service.AlertSeverity
import com.sportage.obd.service.AlertThresholds
import com.sportage.obd.ui.theme.*
import com.sportage.obd.ui.boards.BoardData
import com.sportage.obd.ui.boards.BoardScreen
import com.sportage.obd.ui.boards.PrefsBoardStore
import com.sportage.obd.viewmodel.ObdViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/** Shows a dialog with all installed music apps for the user to choose from. */
private fun showMusicAppPicker(context: Context) {
    val apps = MediaApps.musicApps(context)
    if (apps.isEmpty()) {
        Toast.makeText(context, "No music apps found on this device.", Toast.LENGTH_LONG).show()
        return
    }
    val chosenApp = chosenMusicApp(context)
    val defaultIdx = if (chosenApp != null) apps.indexOfFirst { it.packageName == chosenApp } else 0
    var selectedIdx = defaultIdx
    android.app.AlertDialog.Builder(context)
        .setTitle("Select Music App")
        .setSingleChoiceItems(
            apps.map { it.label }.toTypedArray(),
            defaultIdx,
            { _, which -> selectedIdx = which }
        )
        .setPositiveButton("Open") { _, _ ->
            val selected = apps[selectedIdx]
            runCatching { context.startActivity(selected.launchIntent) }
            // Save the user's choice for future launches
            saveChosenMusicApp(context, selected.packageName)
        }
        .setNegativeButton("Cancel") { _, _ -> }
        .show()
}

data class RadioStation(val freq: Float, val name: String, val genre: String)

val SPORTAGE_RADIO_PRESETS = listOf(
    RadioStation(91.7f, "QBS Radio", "English Hits"),
    RadioStation(94.0f, "Al Rayyan FM", "Arabic Culture"),
    RadioStation(97.5f, "Doha Hit FM", "Top 40"),
    RadioStation(101.4f, "Sportage Drive", "Automotive Stereo"),
    RadioStation(104.8f, "BBC World Service", "Global News"),
    RadioStation(107.6f, "Oryx FM", "French & Jazz")
)

@Composable
fun CarLauncherScreen(
    connectionState: ConnectionState,
    measuredHz: Float? = null,
    speed: Int?,
    rpm: Int?,
    coolant: Int?,
    intakeTemp: Int?,
    fuelLevel: Int?,
    throttle: Float?,
    load: Float?,
    voltage: Float?,
    gear: Int,
    transmissionGear: Int? = null,
    weatherTempC: String? = null,
    weatherText: String? = null,
    speedHistory: List<Pair<Long, Int>>,
    rpmHistory: List<Pair<Long, Int>>,
    currentLat: Double = 25.2854,
    currentLon: Double = 51.5310,
    currentBearing: Float = 0f,
    activePage: Int,
    onNavigatePage: (Int) -> Unit,
    onOpenBluetoothClick: () -> Unit,
    dtcCodes: List<DtcItem>,
    isScanningDtc: Boolean,
    onScanDtc: () -> Unit,
    onClearDtc: () -> Unit,
    tripDistanceKm: Double,
    tripDurationSeconds: Long,
    tripMaxSpeed: Int,
    tripAvgSpeed: Int,
    onResetTrip: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    isPollingPaused: Boolean = false,
    onTogglePausePolling: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},
    viewModel: ObdViewModel? = null,
    autoNightDim: Boolean = true,
    nightDimLevel: Float = 0.55f,
    onSetAutoNightDim: (Boolean) -> Unit = {},
    onSetNightDimLevel: (Float) -> Unit = {},
    gpsStatus: String? = null,
    themeId: String = "cockpit-dark",
    accentOverride: String? = null,
    onSetThemeId: (String) -> Unit = {},
    onSetAccentOverride: (String?) -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp
    val activeAlerts = viewModel?.activeAlerts?.collectAsState()?.value ?: emptyList()

    var speedUnit by remember { mutableStateOf("km/h") }
    val displaySpeed = if (speed != null) {
        if (speedUnit == "mph") (speed * 0.621371).toInt() else speed
    } else null

    // Customizable swipeable boards: live values bundled once for every widget.
    val boardStore = remember { PrefsBoardStore(context) }
    val boardTelemetry = viewModel?.telemetry?.collectAsState()?.value
    val boardData = BoardData(
        speed = displaySpeed,
        speedUnit = speedUnit,
        rpm = rpm,
        coolant = coolant,
        intakeTemp = intakeTemp,
        fuelLevel = fuelLevel,
        throttle = throttle,
        load = load,
        voltage = voltage,
        gear = gear,
        mafGs = boardTelemetry?.mafGs,
        timingAdvanceDeg = boardTelemetry?.timingAdvanceDeg,
        currentLat = currentLat,
        currentLon = currentLon,
        gpsStatus = gpsStatus,
        tripDistanceKm = tripDistanceKm,
        tripDurationSeconds = tripDurationSeconds,
        connectionLabel = when (connectionState) {
            is ConnectionState.Connected -> "Connected · ${connectionState.deviceName}"
            is ConnectionState.Connecting -> "Connecting"
            is ConnectionState.Error -> "Error · reconnect"
            ConnectionState.Disconnected -> "Disconnected"
        },
        connectionOk = connectionState is ConnectionState.Connected,
        speedHistory = speedHistory,
        rpmHistory = rpmHistory
    )

    // Radio State
    var radioFreq by remember { mutableStateOf(101.4f) }
    var radioStationName by remember { mutableStateOf("Sportage Drive FM") }
    var isMediaPlaying by remember { mutableStateOf(true) }

    fun sendMediaKey(keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {}
    }

    fun launchLocalMusicApp() {
        val candidates = listOf(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
            Intent(Intent.ACTION_VIEW).apply { type = "audio/*" },
            Intent("android.intent.action.MUSIC_PLAYER")
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        // Show app picker dialog for user to choose music app
        showMusicAppPicker(context)
    }

    fun launchHeadUnitRadioApp() {
        val radioPackages = listOf(
            "com.ts.main.radio",
            "com.syu.radio",
            "com.microntek.radio",
            "com.android.fmradio"
        )
        for (pkg in radioPackages) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        try {
            val generic = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(generic)
        } catch (_: Exception) {}
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground())
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isLandscape = screenWidth >= screenHeight
        val aspectRatio = if (screenHeight.value > 0) screenWidth.value / screenHeight.value else 1.7f
        if (isLandscape) {
            // LANDSCAPE: Adaptive Left Slim Dock + Cockpit Viewport (Head Units 1024x600, 1280x720, 1920x720 & Phone Landscape)
            Row(modifier = Modifier.fillMaxSize()) {
                CockpitNavigation(activePage, onNavigatePage, onOpenAppDrawer, rail = true)

                // Cockpit Viewport
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CockpitStatusStrip(
                    connectionState = connectionState,
                    isDemoMode = isDemoMode,
                    onOpenBluetoothClick = onOpenBluetoothClick
                )

                    ActiveAlertBanner(activeAlerts)

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activePage) {
                            0 -> PermanentHomeSplitView(
                                isLandscape = true,
                                aspectRatio = aspectRatio,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                gpsStatus = gpsStatus,
                                speed = displaySpeed,
                                speedUnit = speedUnit,
                                onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                                rpm = rpm,
                                coolant = coolant,
                                voltage = voltage,
                                load = load,
                                throttle = throttle,
                                gear = gear,
                                connectionState = connectionState,
                                measuredHz = measuredHz,
                                isDemoMode = isDemoMode,
                                onOpenNavigation = {
                                    if (!NavigationLauncher.open(context, currentLat, currentLon)) {
                                        Toast.makeText(context, "No compatible navigation app could open. Install or enable a maps app.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                            1 -> GaugesAndGraphsPage(
                                speed = displaySpeed,
                                speedUnit = speedUnit,
                                rpm = rpm,
                                coolant = coolant,
                                intakeTemp = intakeTemp,
                                fuelLevel = fuelLevel,
                                throttle = throttle,
                                load = load,
                                voltage = voltage,
                                gear = gear,
                                speedHistory = speedHistory,
                                rpmHistory = rpmHistory,
                                viewModel = viewModel
                            )
                            6 -> SensorExplorer(viewModel)
                            7 -> BoardScreen(data = boardData, store = boardStore)
                            2 -> DiagnosticsPage(
                                dtcCodes = dtcCodes,
                                isScanning = isScanningDtc,
                                onScan = onScanDtc,
                                onClear = onClearDtc,
                                connectionState = connectionState,
                                viewModel = viewModel
                            )
                            3 -> TripComputerPage(
                                distanceKm = tripDistanceKm,
                                durationSeconds = tripDurationSeconds,
                                maxSpeed = tripMaxSpeed,
                                avgSpeed = tripAvgSpeed,
                                currentSpeed = displaySpeed ?: 0,
                                speedUnit = speedUnit,
                                currentLat = currentLat,
                                currentLon = currentLon,
                                onReset = onResetTrip,
                                viewModel = viewModel
                            )
                            8 -> FuelAnalyticsPage(
                                distanceKm = tripDistanceKm,
                                tripDurationSeconds = tripDurationSeconds,
                                maxSpeedKmh = tripMaxSpeed,
                                avgSpeedKmh = tripAvgSpeed,
                                onResetTrip = onResetTrip,
                                viewModel = viewModel
                            )
                            9 -> AdvancedDiagnosticsPage(
                                dtcCodes = dtcCodes,
                                viewModel = viewModel
                            )
                            4 -> MediaRadioPage(
                                radioFreq = radioFreq,
                                onFreqChange = { radioFreq = it },
                                radioStationName = radioStationName,
                                onStationChange = { name, freq ->
                                    radioStationName = name
                                    radioFreq = freq
                                },
                                isMediaPlaying = isMediaPlaying,
                                onTogglePlayPause = {
                                    isMediaPlaying = !isMediaPlaying
                                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                },
                                onPrev = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                                onNext = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                                onLaunchNativeRadio = { launchHeadUnitRadioApp() },
                                onLaunchLocalMusic = { launchLocalMusicApp() }
                            )
                            5 -> SettingsPage(
                                speedUnit = speedUnit,
                                onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                                onSetDefaultLauncher = onSetDefaultLauncher,
                                isDemoMode = isDemoMode,
                                onToggleDemoMode = onToggleDemoMode,
                                onOpenBluetoothSettings = {
                                    try {
                                        context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (_: Exception) {}
                                },
                                isPollingPaused = isPollingPaused,
                                onTogglePausePolling = onTogglePausePolling,
                                onOpenAppDrawer = onOpenAppDrawer,
                                viewModel = viewModel,
                                autoNightDim = autoNightDim,
                                nightDimLevel = nightDimLevel,
                                onSetAutoNightDim = onSetAutoNightDim,
                                onSetNightDimLevel = onSetNightDimLevel,
                                themeId = themeId,
                                accentOverride = accentOverride,
                                onSetThemeId = onSetThemeId,
                                onSetAccentOverride = onSetAccentOverride,
                                onOpenPrivacyPolicy = onOpenPrivacyPolicy
                            )
                        }
                    }
                    QuickActionDock(
                        onMusic = { launchLocalMusicApp() }, onMediaKey = { sendMediaKey(it) },
                        onPhone = { openPhoneDialer(context) }, onApps = onOpenAppDrawer,
                        onBluetooth = onOpenBluetoothClick,
                        logging = viewModel?.isCsvLogging?.collectAsState()?.value ?: false,
                        canLog = viewModel != null && connectionState is ConnectionState.Connected && !isDemoMode,
                        onLogging = {
                            if (viewModel?.isCsvLogging?.value == true) viewModel.stopCsvLogging()
                            else if (viewModel?.startCsvLogging() != true) Toast.makeText(context, "Connect an OBD adapter before recording.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        } else {
            // PORTRAIT: Full-width Top Status Strip + Viewport + Bottom Navigation Bar (e.g. Samsung Galaxy S24 Ultra Phone Mount)
            Column(modifier = Modifier.fillMaxSize()) {
                CockpitStatusStrip(
                    connectionState = connectionState,
                    isDemoMode = isDemoMode,
                    onOpenBluetoothClick = onOpenBluetoothClick
                )

                ActiveAlertBanner(activeAlerts)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activePage) {
                        0 -> PermanentHomeSplitView(
                            isLandscape = false,
                            aspectRatio = aspectRatio,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            gpsStatus = gpsStatus,
                            speed = displaySpeed,
                            speedUnit = speedUnit,
                            onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                            rpm = rpm,
                            coolant = coolant,
                            voltage = voltage,
                            load = load,
                            throttle = throttle,
                            intake = intakeTemp,
                            fuel = fuelLevel,
                            transmissionGear = transmissionGear,
                            gear = gear,
                            weatherTempC = weatherTempC,
                            weatherText = weatherText,
                            currentBearing = currentBearing,
                            connectionState = connectionState,
                            measuredHz = measuredHz,
                            isDemoMode = isDemoMode,
                            onOpenNavigation = {
                                if (!NavigationLauncher.open(context, currentLat, currentLon)) {
                                    Toast.makeText(context, "No compatible navigation app could open. Install or enable a maps app.", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        1 -> GaugesAndGraphsPage(
                            speed = displaySpeed,
                            speedUnit = speedUnit,
                            rpm = rpm,
                            coolant = coolant,
                            intakeTemp = intakeTemp,
                            fuelLevel = fuelLevel,
                            throttle = throttle,
                            load = load,
                            voltage = voltage,
                            gear = gear,
                            speedHistory = speedHistory,
                            rpmHistory = rpmHistory,
                            viewModel = viewModel
                        )
                        6 -> SensorExplorer(viewModel)
                        7 -> BoardScreen(data = boardData, store = boardStore)
                            2 -> DiagnosticsPage(
                            dtcCodes = dtcCodes,
                            isScanning = isScanningDtc,
                            onScan = onScanDtc,
                            onClear = onClearDtc,
                            connectionState = connectionState,
                            viewModel = viewModel
                        )
                        3 -> TripComputerPage(
                            distanceKm = tripDistanceKm,
                            durationSeconds = tripDurationSeconds,
                            maxSpeed = tripMaxSpeed,
                            avgSpeed = tripAvgSpeed,
                            currentSpeed = displaySpeed ?: 0,
                            speedUnit = speedUnit,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            onReset = onResetTrip,
                            viewModel = viewModel
                        )
                        8 -> FuelAnalyticsPage(
                            distanceKm = tripDistanceKm,
                            tripDurationSeconds = tripDurationSeconds,
                            maxSpeedKmh = tripMaxSpeed,
                            avgSpeedKmh = tripAvgSpeed,
                            onResetTrip = onResetTrip,
                            viewModel = viewModel
                        )
                        9 -> AdvancedDiagnosticsPage(
                            dtcCodes = dtcCodes,
                            viewModel = viewModel
                        )
                        4 -> MediaRadioPage(
                            radioFreq = radioFreq,
                            onFreqChange = { radioFreq = it },
                            radioStationName = radioStationName,
                            onStationChange = { name, freq ->
                                radioStationName = name
                                radioFreq = freq
                            },
                            isMediaPlaying = isMediaPlaying,
                            onTogglePlayPause = {
                                isMediaPlaying = !isMediaPlaying
                                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            },
                            onPrev = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                            onNext = { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                            onLaunchNativeRadio = { launchHeadUnitRadioApp() },
                            onLaunchLocalMusic = { launchLocalMusicApp() }
                        )
                        5 -> SettingsPage(
                            speedUnit = speedUnit,
                            onToggleSpeedUnit = { speedUnit = if (speedUnit == "km/h") "mph" else "km/h" },
                            onSetDefaultLauncher = onSetDefaultLauncher,
                            isDemoMode = isDemoMode,
                            onToggleDemoMode = onToggleDemoMode,
                            onOpenBluetoothSettings = {
                                try {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            },
                            isPollingPaused = isPollingPaused,
                            onTogglePausePolling = onTogglePausePolling,
                            onOpenAppDrawer = onOpenAppDrawer,
                            viewModel = viewModel,
                            autoNightDim = autoNightDim,
                            nightDimLevel = nightDimLevel,
                            onSetAutoNightDim = onSetAutoNightDim,
                            onSetNightDimLevel = onSetNightDimLevel,
                            themeId = themeId,
                            accentOverride = accentOverride,
                            onSetThemeId = onSetThemeId,
                            onSetAccentOverride = onSetAccentOverride,
                            onOpenPrivacyPolicy = onOpenPrivacyPolicy
                        )
                    }
                }

                QuickActionDock(
                    onMusic = { launchLocalMusicApp() }, onMediaKey = { sendMediaKey(it) },
                    onPhone = { openPhoneDialer(context) }, onApps = onOpenAppDrawer,
                    onBluetooth = onOpenBluetoothClick,
                    logging = viewModel?.isCsvLogging?.collectAsState()?.value ?: false,
                    canLog = viewModel != null && connectionState is ConnectionState.Connected && !isDemoMode,
                    onLogging = {
                        if (viewModel?.isCsvLogging?.value == true) viewModel.stopCsvLogging()
                        else if (viewModel?.startCsvLogging() != true) Toast.makeText(context, "Connect an OBD adapter before recording.", Toast.LENGTH_SHORT).show()
                    }
                )
                CockpitNavigation(activePage, onNavigatePage, onOpenAppDrawer, rail = false)
            }
        }
    }
}

// =========================================================================
// HELPER: ACTIVE WARNING BANNER (threshold alerts)
// =========================================================================
@Composable
fun ActiveAlertBanner(activeAlerts: List<ActiveAlert>) {
    if (activeAlerts.isEmpty()) return
    val critical = activeAlerts.any { it.severity == AlertSeverity.CRITICAL }
    val color = if (critical) CriticalRed() else AmberWarning()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = activeAlerts.joinToString("  ·  ") { it.message },
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// =========================================================================
// HELPER: TOP COCKPIT STATUS STRIP
// =========================================================================
@Composable
fun PermanentHomeSplitView(
    isLandscape: Boolean,
    aspectRatio: Float = 1.7f,
    currentLat: Double,
    currentLon: Double,
    speed: Int?,
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    rpm: Int?,
    coolant: Int?,
    voltage: Float?,
    load: Float?,
    throttle: Float?,
    intake: Int? = null,
    fuel: Int? = null,
    transmissionGear: Int? = null,
    gear: Int,
    weatherTempC: String? = null,
    weatherText: String? = null,
    currentBearing: Float = 0f,
    connectionState: ConnectionState,
    measuredHz: Float?,
    isDemoMode: Boolean,
    onOpenNavigation: () -> Unit,
    gpsStatus: String? = null
) {
    if (isLandscape) {
        // Landscape: Adaptive Dominant Live Map Pane (approx 58-64%) + Persistent OBD Pane (approx 36-42%)
        val mapWeight = if (aspectRatio >= 2.0f) 0.64f else 0.58f
        val obdWeight = 1.0f - mapWeight

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(mapWeight).fillMaxHeight()) {
                LiveMapSurface(
                    currentLat = currentLat,
                    currentLon = currentLon,
                    currentBearing = currentBearing,
                    speed = speed,
                    speedUnit = speedUnit,
                    onOpenNavigation = onOpenNavigation,
                    gpsStatus = gpsStatus
                )
            }

            Box(modifier = Modifier.weight(obdWeight).fillMaxHeight()) {
                CockpitTelemetryPane(
                    speed = speed,
                    speedUnit = speedUnit,
                    onToggleSpeedUnit = onToggleSpeedUnit,
                    rpm = rpm,
                    coolant = coolant,
                    voltage = voltage,
                    load = load,
                    throttle = throttle,
                    intake = intake,
                    fuel = fuel,
                    transmissionGear = transmissionGear,
                    gear = gear,
                    weatherTempC = weatherTempC,
                    weatherText = weatherText,
                    connectionState = connectionState,
                    measuredHz = measuredHz,
                    isDemoMode = isDemoMode
                )
            }
        }
    } else {
        // Portrait: prioritize map height, retaining extra telemetry room at enlarged font scales.
        val portraitMapWeight = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f) 0.52f else 0.56f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(portraitMapWeight).fillMaxWidth()) {
                LiveMapSurface(
                    currentLat = currentLat,
                    currentLon = currentLon,
                    currentBearing = currentBearing,
                    speed = speed,
                    speedUnit = speedUnit,
                    onOpenNavigation = onOpenNavigation,
                    gpsStatus = gpsStatus
                )
            }

            Box(modifier = Modifier.weight(1f - portraitMapWeight).fillMaxWidth()) {
                CockpitTelemetryPane(
                    speed = speed,
                    speedUnit = speedUnit,
                    onToggleSpeedUnit = onToggleSpeedUnit,
                    rpm = rpm,
                    coolant = coolant,
                    voltage = voltage,
                    load = load,
                    throttle = throttle,
                    intake = intake,
                    fuel = fuel,
                    transmissionGear = transmissionGear,
                    gear = gear,
                    weatherTempC = weatherTempC,
                    weatherText = weatherText,
                    connectionState = connectionState,
                    measuredHz = measuredHz,
                    isDemoMode = isDemoMode
                )
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: NATIVE LOCATION TRACKER
// =========================================================================
@Composable
fun LiveMapSurface(
    currentLat: Double, currentLon: Double, speed: Int?, speedUnit: String,
    onOpenNavigation: () -> Unit,
    gpsStatus: String? = null,
    currentBearing: Float = 0f
) {
    var speedAlertThreshold by remember { mutableIntStateOf(100) }
    val overspeed = speed != null && speedAlertThreshold > 0 && speed > speedAlertThreshold
    Surface(color = DarkSurface(), shape = RoundedCornerShape(16.dp), modifier = Modifier.testTag("home_map_card").fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.testTag("home_map_header").fillMaxWidth().padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Map", color = PrimaryText(), style = MaterialTheme.typography.titleMedium)
                    gpsStatus?.let { Text(it, color = SecondaryText(), style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
                FilledIconButton(
                    onClick = onOpenNavigation, modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DarkSurfaceElevated(), contentColor = ElectricCyan()
                    )
                ) { Icon(Icons.Default.Navigation, "Open navigation app", Modifier.size(24.dp)) }
            }
            Box(Modifier.testTag("home_map_viewport").fillMaxWidth().weight(1f)) { HybridLocationMap(currentLat, currentLon, 0, currentBearing) }
            Row(
                Modifier.testTag("home_map_footer").fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        speedAlertThreshold = when (speedAlertThreshold) {
                            80 -> 100; 100 -> 120; 120 -> 140; 140 -> 0; else -> 80
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (overspeed) CriticalRed() else SecondaryText())
                ) {
                    Icon(if (overspeed) Icons.Default.Warning else Icons.Default.NotificationsNone, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (speedAlertThreshold == 0) "Alert off" else "Alert · $speedAlertThreshold $speedUnit",
                        style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "${"%.4f".format(currentLat)}, ${"%.4f".format(currentLon)}",
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = SecondaryText(), style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// =========================================================================
// SUB-VIEW: AUTHORITATIVE PRIMARY OBD TELEMETRY PANE
// =========================================================================
@Composable
fun GaugesAndGraphsPage(
    speed: Int?,
    speedUnit: String,
    rpm: Int?,
    coolant: Int?,
    intakeTemp: Int?,
    fuelLevel: Int?,
    throttle: Float?,
    load: Float?,
    voltage: Float?,
    gear: Int,
    speedHistory: List<Pair<Long, Int>>,
    rpmHistory: List<Pair<Long, Int>>,
    viewModel: ObdViewModel? = null
) {
    val liveTelemetry = viewModel?.telemetry?.collectAsState()?.value
    val metrics = listOf(
        PanelReading("Engine speed", rpm?.toString() ?: "--", "rpm", if ((rpm ?: 0) > 6500) CriticalRed() else PrimaryText()),
        PanelReading("Coolant", coolant?.toString() ?: "--", "°C", if ((coolant ?: 0) > 105) CriticalRed() else PrimaryText()),
        PanelReading("Intake air", intakeTemp?.toString() ?: "--", "°C"),
        PanelReading("Engine load", load?.let { "%.1f".format(it) } ?: "--", "%"),
        PanelReading("Throttle", throttle?.let { "%.1f".format(it) } ?: "--", "%"),
        PanelReading("Fuel level", fuelLevel?.toString() ?: "--", "%"),
        PanelReading("Battery", voltage?.let { "%.1f".format(it) } ?: "--", "V"),
        PanelReading("Gear estimate", if (gear == 0) "P/N" else "D$gear", "automatic"),
        PanelReading("Air flow", liveTelemetry?.mafGs?.let { "%.1f".format(it) } ?: "--", "g/s"),
        PanelReading("Timing advance", liveTelemetry?.timingAdvanceDeg?.let { "%.1f".format(it) } ?: "--", "°BTDC"),
        PanelReading("Short fuel trim", liveTelemetry?.shortTermFuelTrimPct?.let { "%+.1f".format(it) } ?: "--", "%",
            if (kotlin.math.abs(liveTelemetry?.shortTermFuelTrimPct ?: 0f) > 10f) CriticalRed() else PrimaryText()),
        PanelReading("Long fuel trim", liveTelemetry?.longTermFuelTrimPct?.let { "%+.1f".format(it) } ?: "--", "%",
            if (kotlin.math.abs(liveTelemetry?.longTermFuelTrimPct ?: 0f) > 10f) CriticalRed() else PrimaryText()),
        PanelReading("Instant economy", liveTelemetry?.fuelEconomyL100?.let { "%.1f".format(it) } ?: "--", "L/100 km"),
        PanelReading("Fuel used", liveTelemetry?.fuelUsedLiters?.let { "%.2f".format(it) } ?: "--", "liters")
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 720.dp) 4 else 2
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text("Gauges", color = PrimaryText(), style = MaterialTheme.typography.titleLarge)
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Surface(color = DarkSurface(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Engine history · 60 seconds", color = PrimaryText(), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Speed · ${speed ?: "--"} $speedUnit", color = ElectricCyan(), style = MaterialTheme.typography.labelMedium)
                            Text("RPM · ${rpm ?: "--"}", color = WarningAmber(), style = MaterialTheme.typography.labelMedium)
                        }
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            // Draw-scope lambdas cannot call composables, so resolve theme colors first.
                            val plotShade = DeepShade()
                            val plotAccent = ElectricCyan()
                            val plotWarning = WarningAmber()
                            Canvas(Modifier.fillMaxSize().background(DarkBackground(), RoundedCornerShape(8.dp)).padding(8.dp)) {

                    val w = size.width
                    val h = size.height

                    // Grid lines
                    for (i in 1..4) {
                        val y = h * (i / 5f)
                        drawLine(
                            color = plotShade,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    // Draw Speed Line (max 200 km/h)
                    if (speedHistory.size > 1) {
                        val path = Path()
                        speedHistory.forEachIndexed { idx, pair ->
                            val x = w * (idx.toFloat() / (speedHistory.size - 1))
                            val y = h - (h * (pair.second.coerceIn(0, 200) / 200f))
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = plotAccent, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                    }

                    // Draw RPM Line (max 7000 RPM)
                    if (rpmHistory.size > 1) {
                        val rpmPath = Path()
                        rpmHistory.forEachIndexed { idx, pair ->
                            val x = w * (idx.toFloat() / (rpmHistory.size - 1))
                            val y = h - (h * (pair.second.coerceIn(0, 7000) / 7000f))
                            if (idx == 0) rpmPath.moveTo(x, y) else rpmPath.lineTo(x, y)
                        }
                        drawPath(rpmPath, color = plotWarning, style = Stroke(width = 2f, cap = StrokeCap.Round))
                    }

                            }
                            if (speedHistory.size < 2 && rpmHistory.size < 2) {
                                Text("Waiting for readings", color = SecondaryText(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            items(metrics.size) { index -> MetricTile(metrics[index]) }
        }
    }
}

@Composable
fun DiagnosticsPage(
    dtcCodes: List<DtcItem>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onClear: () -> Unit,
    connectionState: ConnectionState,
    viewModel: ObdViewModel? = null
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val pendingCodes = viewModel?.pendingDtcList?.collectAsState()?.value ?: emptyList()
    val permanentCodes = viewModel?.permanentDtcList?.collectAsState()?.value ?: emptyList()
    val freezeFrame = viewModel?.freezeFrame?.collectAsState()?.value
    val readiness = viewModel?.readiness?.collectAsState()?.value
    val vin = viewModel?.vin?.collectAsState()?.value
    val supportedPids = viewModel?.supportedPids?.collectAsState()?.value ?: emptySet()
    val vehicleIdentity = viewModel?.vehicleIdentity?.collectAsState()?.value
    val mode06TestReport = viewModel?.mode06Tests?.collectAsState()?.value
    val o2TestReport = viewModel?.o2Tests?.collectAsState()?.value
    val actuatorState = viewModel?.actuatorState?.collectAsState()?.value ?: ActuatorTestState()
    val liveSpeed = viewModel?.telemetry?.collectAsState()?.value?.speedKmh
    val isPaused = viewModel?.isPollingPaused?.collectAsState()?.value == true
    val scope = rememberCoroutineScope()

    var auxScanning by remember { mutableStateOf(false) }
    var freezeFrameDtc by remember { mutableStateOf<String?>(null) }
    val tabs = listOf(
        "Stored (03)", "Pending (07)", "Permanent (0A)", "Freeze Frame", "Readiness",
        "Vehicle ID (09)", "Monitors (06)", "O2 Tests (05)", "Actuators (08)", "Report"
    ).mapIndexed { index, title ->
        // A pending-code badge keeps the count visible without opening the tab.
        if (index == 1 && pendingCodes.isNotEmpty()) "$title · ${pendingCodes.size}" else title
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Action Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkBackground(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = "Diagnostics",
                        color = PrimaryText(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "MODES 02 / 03 / 05 / 06 / 07 / 08 / 09 / 0A · READINESS · ACTUATORS",
                        color = SecondaryText(),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    val displayedVin = vehicleIdentity?.vin ?: vin
                    if (displayedVin != null) {
                        Text(
                            text = "VIN: $displayedVin",
                            color = ElectricCyan(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                if (isPaused) {
                    Text(
                        "Polling is paused, so Mode 05/06/08/09 requests are held back. Resume polling to read them.",
                        color = AmberWarning(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        onClick = onScan,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = OnAccent(), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", color = OnAccent(), fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = OnAccent(), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan ECU (03)", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                        }
                    }

                    Button(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        onClick = { showClearConfirm = true },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Clear Codes (04)", color = CriticalRed(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = DarkBackground(),
            contentColor = ElectricCyan()
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            title,
                            maxLines = 1,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = if (selectedTab == idx) ElectricCyan() else SecondaryText()
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> DtcListContent(dtcCodes, emptyHint = "No Stored Diagnostic Trouble Codes Found")
            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.scanPendingDtcsNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Scan Pending (07)", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }
                DtcListContent(pendingCodes, emptyHint = "No Pending Codes · ECU monitors have not confirmed a fault yet")
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.scanPermanentDtcsNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Scan Permanent (0A)", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }
                DtcListContent(permanentCodes, emptyHint = "No Permanent Codes · These survive a Mode 04 clear until the ECU re-verifies the repair")
            }
            3 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.readFreezeFrameNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Read Freeze Frame (02)", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }
                if (dtcCodes.isNotEmpty()) {
                    Text(
                        "Select the stored code you are diagnosing:",
                        color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dtcCodes.forEach { dtc ->
                            val selected = freezeFrameDtc == dtc.code
                            Button(
                                onClick = { freezeFrameDtc = if (selected) null else dtc.code },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) ElectricCyan() else DarkSurfaceElevated()
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    dtc.code,
                                    color = if (selected) OnAccent() else SecondaryText(),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
                if (freezeFrame == null || freezeFrame.items.isEmpty()) {
                    EmptyStateBox(
                        if (freezeFrameDtc != null) "No Freeze Frame Stored for $freezeFrameDtc"
                        else "No Freeze Frame Stored",
                        "The ECU snapshots engine data when a fault is confirmed. None is currently stored."
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Surface(
                                color = DeepShade(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AmberWarning())
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(
                                        "FREEZE FRAME SNAPSHOT" + (freezeFrame.dtc?.code?.let { " · $it" } ?: ""),
                                        color = AmberWarning(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    freezeFrame.items.forEach {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(it.label, color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                            Text(it.value, color = PrimaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                        }
                                    }
                                    if (freezeFrameDtc != null && freezeFrame.dtc?.code != null && freezeFrame.dtc?.code != freezeFrameDtc) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "The ECU stores a single freeze frame (Mode 02 frame 00) for the last confirmed fault, " +
                                                "so this snapshot belongs to ${freezeFrame.dtc?.code}, not $freezeFrameDtc.",
                                            color = AmberWarning(), fontSize = 10.sp, fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            4 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            auxScanning = true
                            scope.launch {
                                viewModel?.readReadinessNow()
                                auxScanning = false
                            }
                        },
                        enabled = !auxScanning && viewModel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Read Readiness (0101)", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }
                if (readiness == null) {
                    EmptyStateBox("Readiness Not Read", "Query the ECU to see MIL status and emissions monitor completion before an inspection.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Surface(
                                color = DeepShade(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (readiness.milOn) CriticalRed() else TealAccent())
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            if (readiness.milOn) "CHECK ENGINE LIGHT: ON" else "CHECK ENGINE LIGHT: OFF",
                                            color = if (readiness.milOn) CriticalRed() else TealAccent(),
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif
                                        )
                                        Text(
                                            "Stored codes: ${readiness.storedDtcCount}",
                                            color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    Text(if (readiness.compressionIgnitionMonitors.isNotEmpty()) "DIESEL READINESS MONITORS" else "GASOLINE READINESS MONITORS",
                                        color = ElectricCyan(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                                    (readiness.sparkIgnitionMonitors + readiness.compressionIgnitionMonitors).forEach { m ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(m.name, color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                            Text(
                                                if (m.complete) "COMPLETE" else "NOT READY",
                                                color = if (m.complete) TealAccent() else AmberWarning(),
                                                fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (supportedPids.isNotEmpty()) {
                            item {
                                Surface(
                                    color = DeepShade(),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, DarkSurfaceElevated())
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text("ECU SUPPORTED PIDs (${supportedPids.size} discovered)", color = ElectricCyan(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            supportedPids.sorted().joinToString(" "),
                                            color = SecondaryText(),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            5 -> VehicleIdentityContent(vehicleIdentity, viewModel)
            6 -> Mode06TestsContent(mode06TestReport, viewModel)
            7 -> O2TestsContent(o2TestReport, viewModel)
            8 -> ActuatorTestsContent(actuatorState, liveSpeed, viewModel)
            9 -> DiagnosticReportCard(
                viewModel = viewModel,
                hasData = dtcCodes.isNotEmpty() || pendingCodes.isNotEmpty() || permanentCodes.isNotEmpty()
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Fault Codes (Mode 04)?", fontFamily = FontFamily.SansSerif, color = PrimaryText()) },
            text = {
                Text(
                    "Warning: Clearing ECU codes resets emissions inspection readiness monitors and turns off the check engine light. If the physical fault is not repaired, the code will reoccur.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = SecondaryText()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed())
                ) {
                    Text("Confirm Clear", fontFamily = FontFamily.SansSerif, color = DarkBackground())
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated())
                ) {
                    Text("Cancel", fontFamily = FontFamily.SansSerif, color = SecondaryText())
                }
            },
            containerColor = DeepShade()
        )
    }
}

@Composable
private fun DtcListContent(dtcCodes: List<DtcItem>, emptyHint: String) {
    if (dtcCodes.isEmpty()) {
        EmptyStateBox("No Trouble Codes Found", emptyHint)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dtcCodes) { dtc ->
                Surface(
                    color = DeepShade(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (dtc.severity == DtcSeverity.CRITICAL) CriticalRed() else AmberWarning())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = dtc.code,
                                    color = if (dtc.severity == DtcSeverity.CRITICAL) CriticalRed() else AmberWarning(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    text = dtc.system,
                                    color = SecondaryText(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }

                            Text(
                                text = dtc.severity.name,
                                color = if (dtc.severity == DtcSeverity.CRITICAL) CriticalRed() else AmberWarning(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier
                                    .background(
                                        (if (dtc.severity == DtcSeverity.CRITICAL) CriticalRed() else AmberWarning()).copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = dtc.description, color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Probable Cause: ${dtc.probableCause}", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBox(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .background(DeepShade(), RoundedCornerShape(10.dp))
            .border(1.dp, DarkSurfaceElevated(), RoundedCornerShape(10.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealAccent(), modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, color = PrimaryText(), fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.SansSerif)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        }
    }
}

// =========================================================================
// SUB-VIEW: TRIP COMPUTER PAGE
// =========================================================================
@Composable
fun TripComputerPage(
    distanceKm: Double,
    durationSeconds: Long,
    maxSpeed: Int,
    avgSpeed: Int,
    currentSpeed: Int,
    speedUnit: String,
    currentLat: Double,
    currentLon: Double,
    onReset: () -> Unit,
    viewModel: ObdViewModel? = null
) {
    val hrs = durationSeconds / 3600
    val mins = (durationSeconds % 3600) / 60
    val secs = durationSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)

    val liveTelemetry = viewModel?.telemetry?.collectAsState()?.value
    val fuelUsedL = liveTelemetry?.fuelUsedLiters
    val instantEconomy = liveTelemetry?.fuelEconomyL100
    // Average economy for the trip = liters used / distance * 100
    val avgEconomy = if (fuelUsedL != null && distanceKm > 0.5) fuelUsedL / distanceKm * 100.0 else null
    // Range estimate from remaining tank fuel (62 L Sportage tank) and trip average
    val tankLiters = 62.0
    val rangeKm = if (avgEconomy != null && avgEconomy > 0.5 && liveTelemetry?.fuelLevelPct != null) {
        (tankLiters * liveTelemetry.fuelLevelPct / 100.0) / avgEconomy * 100.0
    } else null

    val metrics = listOf(
        PanelReading("Distance", "%.1f".format(if (speedUnit == "mph") distanceKm * 0.621371 else distanceKm),
            if (speedUnit == "mph") "miles" else "km", ElectricCyan()),
        PanelReading("Elapsed time", timeFormatted, "hh:mm:ss"),
        PanelReading("Average speed", "$avgSpeed", speedUnit),
        PanelReading("Maximum speed", "$maxSpeed", speedUnit),
        PanelReading("Average economy", avgEconomy?.let { "%.1f".format(it) } ?: "--", "L/100 km"),
        PanelReading("Instant economy", instantEconomy?.let { "%.1f".format(it) } ?: "--", "L/100 km"),
        PanelReading("Fuel used", fuelUsedL?.let { "%.2f".format(it) } ?: "--", "liters"),
        PanelReading("Range estimate", rangeKm?.let { "%.0f".format(it) } ?: "--", "km to empty")
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 700.dp) 4 else if (maxWidth < 380.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f) 1 else 2
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Trip", Modifier.weight(1f), color = PrimaryText(), style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset trip")
                }
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(metrics.size) { index -> MetricTile(metrics[index]) }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp), tint = SecondaryText())
                Spacer(Modifier.width(8.dp))
                Text("Coordinates", Modifier.weight(1f), color = SecondaryText(), style = MaterialTheme.typography.labelMedium)
                Text("${"%.5f".format(currentLat)}, ${"%.5f".format(currentLon)}",
                    color = SecondaryText(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private data class PanelReading(val title: String, val value: String, val unit: String, val color: Color? = null)

@Composable
private fun MetricTile(reading: PanelReading) {
    Surface(color = DarkSurface(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(120.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(reading.title, color = SecondaryText(), style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(reading.value, color = reading.color ?: PrimaryText(), fontSize = if (reading.value.length > 6) 20.sp else 28.sp,
                fontWeight = FontWeight.Medium, maxLines = 1)
            Text(reading.unit, color = SecondaryText(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

// =========================================================================
// SUB-VIEW: MEDIA & RADIO PAGE
// =========================================================================
@Composable
fun MediaRadioPage(
    radioFreq: Float,
    onFreqChange: (Float) -> Unit,
    radioStationName: String,
    onStationChange: (String, Float) -> Unit,
    isMediaPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLaunchNativeRadio: () -> Unit,
    onLaunchLocalMusic: () -> Unit
) {
    val context = LocalContext.current
    val controller = remember { NowPlayingController(context) }
    val nowPlaying by controller.state.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var pickerApps by remember { mutableStateOf<List<MediaAppInfo>>(emptyList()) }
    var radioApps by remember { mutableStateOf<List<MediaAppInfo>>(emptyList()) }

    // Connect to the saved music app (or the first MediaBrowserService found) while
    // this page is open, so now-playing info and controls reflect the real session.
    DisposableEffect(Unit) {
        controller.connect(chosenMusicApp(context))
        radioApps = MediaApps.radioApps(context)
        onDispose { controller.disconnect() }
    }
    // Remember the last manually chosen app so the connection follows it next time.
    fun pickApp(app: MediaAppInfo) {
        saveChosenMusicApp(context, app.packageName)
        if (app.browseComponent != null) {
            controller.connect(app.packageName)
        } else {
            controller.disconnect()
            app.launchIntent?.let { runCatching { context.startActivity(it) } }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Media", color = PrimaryText(), style = MaterialTheme.typography.titleLarge)

        // ---- Now playing + music controls ----
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("media_now_playing"),
            color = DarkBackground(), shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", color = SecondaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (nowPlaying != null) {
                    Text(nowPlaying?.title ?: "Unknown track", color = PrimaryText(), fontSize = 22.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        (nowPlaying?.artist ?: "") + "  ·  " + (nowPlaying?.appName ?: ""),
                        color = SecondaryText(), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                } else {
                    Text("No connected music app", color = PrimaryText(), fontSize = 18.sp)
                    Text("Controls send standard media keys to the active player",
                        color = SecondaryText(), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    IconButton(
                        onClick = { if (controller.isConnected) controller.previous() else onPrev() },
                        modifier = Modifier.size(56.dp).background(DarkSurfaceElevated(), CircleShape)
                            .testTag("media_prev")
                    ) { Icon(Icons.Default.SkipPrevious, "Previous track", tint = PrimaryText()) }
                    IconButton(
                        onClick = { if (controller.isConnected) controller.playPause() else onTogglePlayPause() },
                        modifier = Modifier.size(64.dp).background(ElectricCyan(), CircleShape)
                            .testTag("media_play_pause")
                    ) {
                        Icon(
                            if (nowPlaying?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (nowPlaying?.isPlaying == true) "Pause" else "Play",
                            tint = OnAccent()
                        )
                    }
                    IconButton(
                        onClick = { if (controller.isConnected) controller.next() else onNext() },
                        modifier = Modifier.size(56.dp).background(DarkSurfaceElevated(), CircleShape)
                            .testTag("media_next")
                    ) { Icon(Icons.Default.SkipNext, "Next track", tint = PrimaryText()) }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        pickerApps = MediaApps.musicApps(context)
                        showPicker = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = TealAccent(), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Choose music app", color = PrimaryText(), fontSize = 12.sp)
                }
            }
        }

        // ---- Radio ----
        Surface(
            modifier = Modifier.fillMaxWidth(), color = DarkBackground(), shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("RADIO", color = SecondaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "The FM tuner is controlled by the head unit's own radio hardware and app - this " +
                        "dashboard cannot tune it over generic OBD/Android. Open the radio app below to change station.",
                    color = SecondaryText(), style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLaunchNativeRadio,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = ElectricCyan(), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Head Unit Radio App", color = PrimaryText(), fontSize = 11.sp)
                    }
                    Button(
                        onClick = onLaunchLocalMusic,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = TealAccent(), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Local Music Player", color = PrimaryText(), fontSize = 11.sp)
                    }
                }
                if (radioApps.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Radio apps on this device", color = PrimaryText(), style = MaterialTheme.typography.labelLarge)
                    radioApps.forEach { app ->
                        TextButton(onClick = { app.launchIntent?.let { runCatching { context.startActivity(it) } } }) {
                            Text(app.label, color = TealAccent(), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Close", color = SecondaryText()) }
            },
            title = { Text("Choose music app", color = PrimaryText()) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (pickerApps.isEmpty()) {
                        Text("No music apps found on this device.", color = SecondaryText())
                    }
                    pickerApps.forEach { app ->
                        TextButton(onClick = { pickApp(app); showPicker = false }) {
                            Column {
                                Text(app.label, color = PrimaryText())
                                Text(
                                    if (app.browseComponent != null) "Shows now-playing info" else "Launch only",
                                    color = SecondaryText(), style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

// =========================================================================
// SUB-VIEW: SETTINGS & DEFAULT LAUNCHER ONBOARDING PAGE
// =========================================================================
@Composable
fun SettingsPage(
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    isPollingPaused: Boolean = false,
    onTogglePausePolling: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},
    viewModel: ObdViewModel? = null,
    autoNightDim: Boolean = true,
    nightDimLevel: Float = 0.55f,
    onSetAutoNightDim: (Boolean) -> Unit = {},
    onSetNightDimLevel: (Float) -> Unit = {},
    themeId: String = "cockpit-dark",
    accentOverride: String? = null,
    onSetThemeId: (String) -> Unit = {},
    onSetAccentOverride: (String?) -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    var thresholds by remember { mutableStateOf(viewModel?.alertThresholds() ?: AlertThresholds()) }
    var csvLogging by remember { mutableStateOf(false) }
    csvLogging = viewModel?.isCsvLogging?.collectAsState()?.value ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Settings", color = PrimaryText(), style = MaterialTheme.typography.titleLarge)

        // 0. Theme & Colors Card: restyles the whole app instantly, persisted across restarts.
        ThemeCustomizationCard(themeId, accentOverride, onSetThemeId, onSetAccentOverride, onOpenPrivacyPolicy)

        // 1. Set as Default Launcher Card (Non-Negotiable P0 Launcher Requirement)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkBackground(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ElectricCyan().copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("SET AS DEFAULT ANDROID HOME LAUNCHER", color = ElectricCyan(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Makes Sportage OBD the permanent car dashboard. Booting the head unit or pressing Home returns here.",
                        color = SecondaryText(),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Button(
                    modifier = Modifier.widthIn(min = 128.dp).heightIn(min = 48.dp),
                    onClick = onSetDefaultLauncher,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Set Default Home", color = OnAccent(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                }
            }
        }

        // 2. Units & Telemetry Settings Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkBackground(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DarkSurfaceElevated())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Speed Unit
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Speed & Distance Units", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Current: $speedUnit", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }

                    Button(
                        modifier = Modifier.widthIn(min = 128.dp).heightIn(min = 48.dp),
                        onClick = onToggleSpeedUnit,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = if (speedUnit == "km/h") "Switch to MPH" else "Switch to KM/H", color = ElectricCyan(), fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }

                // Installed Apps Drawer
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Installed App Drawer", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Browse and launch all head unit installed apps", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }

                    Button(
                        modifier = Modifier.widthIn(min = 128.dp).heightIn(min = 48.dp),
                        onClick = onOpenAppDrawer,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = ElectricCyan(), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Apps", color = ElectricCyan(), fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }

                // Pause OBD Polling Toggle (Ignition Off / Save Battery)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Pause OBD Polling (Engine Off)", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Stop Bluetooth querying when car engine is switched off", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }

                    Switch(
                        checked = isPollingPaused,
                        onCheckedChange = { onTogglePausePolling() },
                        colors = SwitchDefaults.colors(checkedThumbColor = AmberWarning(), checkedTrackColor = AmberWarning().copy(alpha = 0.4f))
                    )
                }

                // Bench Test / Demo Telemetry
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Bench Test / Demo Simulation", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Simulates live RPM, speed and sensors for indoor testing without car", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }

                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = onToggleDemoMode,
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan(), checkedTrackColor = ElectricCyan().copy(alpha = 0.4f))
                    )
                }

                // Android Bluetooth Settings Shortcut
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Android Bluetooth System Settings", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Pair new ELM327 adapters or view system devices", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }

                    Button(
                        modifier = Modifier.widthIn(min = 128.dp).heightIn(min = 48.dp),
                        onClick = onOpenBluetoothSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Open Settings", color = SecondaryText(), fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                    }
                }
            }
        }

        // 3. Warning Thresholds Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkBackground(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AmberWarning().copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("VEHICLE WARNING THRESHOLDS", color = AmberWarning(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                Text("Audible beep + red banner when a value crosses its limit", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)

                ThresholdStepper(
                    label = "Coolant Overheat Limit",
                    value = "${thresholds.coolantMaxC}°C",
                    onMinus = { thresholds = thresholds.copy(coolantMaxC = (thresholds.coolantMaxC - 5).coerceAtLeast(90)); viewModel?.updateAlertThresholds(thresholds) },
                    onPlus = { thresholds = thresholds.copy(coolantMaxC = (thresholds.coolantMaxC + 5).coerceAtMost(130)); viewModel?.updateAlertThresholds(thresholds) }
                )
                ThresholdStepper(
                    label = "Battery Low Limit",
                    value = "%.1fV".format(thresholds.batteryMinV),
                    onMinus = { thresholds = thresholds.copy(batteryMinV = (thresholds.batteryMinV - 0.5f).coerceAtLeast(9f)); viewModel?.updateAlertThresholds(thresholds) },
                    onPlus = { thresholds = thresholds.copy(batteryMinV = (thresholds.batteryMinV + 0.5f).coerceAtMost(14f)); viewModel?.updateAlertThresholds(thresholds) }
                )
                ThresholdStepper(
                    label = "RPM Redline Limit",
                    value = "${thresholds.rpmMax}",
                    onMinus = { thresholds = thresholds.copy(rpmMax = (thresholds.rpmMax - 500).coerceAtLeast(3000)); viewModel?.updateAlertThresholds(thresholds) },
                    onPlus = { thresholds = thresholds.copy(rpmMax = (thresholds.rpmMax + 500).coerceAtMost(7500)); viewModel?.updateAlertThresholds(thresholds) }
                )
                ThresholdStepper(
                    label = "Speed Limit",
                    value = "${thresholds.speedMaxKmh} km/h",
                    onMinus = { thresholds = thresholds.copy(speedMaxKmh = (thresholds.speedMaxKmh - 10).coerceAtLeast(60)); viewModel?.updateAlertThresholds(thresholds) },
                    onPlus = { thresholds = thresholds.copy(speedMaxKmh = (thresholds.speedMaxKmh + 10).coerceAtMost(220)); viewModel?.updateAlertThresholds(thresholds) }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Audible Warning Beeps", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Play a tone when a threshold is crossed", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }
                    Switch(
                        checked = thresholds.audibleAlerts,
                        onCheckedChange = {
                            thresholds = thresholds.copy(audibleAlerts = it)
                            viewModel?.updateAlertThresholds(thresholds)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AmberWarning(), checkedTrackColor = AmberWarning().copy(alpha = 0.4f))
                    )
                }
            }
        }

        // 4. CSV Data Logging Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkBackground(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, TealAccent().copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("CSV TELEMETRY LOGGING", color = TealAccent(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Record every sensor sample to a CSV file on this drive", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }

                    Switch(
                        checked = csvLogging,
                        onCheckedChange = { enable ->
                            if (enable) viewModel?.startCsvLogging() else viewModel?.stopCsvLogging()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealAccent(), checkedTrackColor = TealAccent().copy(alpha = 0.4f))
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Auto-Connect On Car Ignition", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Automatically pair and link when vehicle ignition powers on", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }
                    var autoConnect by remember { mutableStateOf(viewModel?.isAutoConnectEnabled() ?: true) }
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = {
                            autoConnect = it
                            viewModel?.setAutoConnectEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealAccent(), checkedTrackColor = TealAccent().copy(alpha = 0.4f))
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Auto-Start With Connection", color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Begin data stream logging every time the adapter connects", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }
                    var csvAuto by remember { mutableStateOf(viewModel?.csvAutostart() ?: true) }
                    Switch(
                        checked = csvAuto,
                        onCheckedChange = {
                            csvAuto = it
                            viewModel?.setCsvAutostart(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealAccent(), checkedTrackColor = TealAccent().copy(alpha = 0.4f))
                    )
                }

                Button(
                    modifier = Modifier.widthIn(min = 128.dp).heightIn(min = 48.dp),
                    onClick = {
                        val file = viewModel?.csvLogFile()
                        if (file != null && file.exists()) {
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".fileprovider",
                                    file
                                )
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share OBD Log").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not share log: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No active log file. Start logging first.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = TealAccent(), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Current Log", color = TealAccent(), fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                }
            }
        }

        // 5. Night Dimming Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkBackground(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ElectricCyan().copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("AUTO NIGHT DIMMING", color = ElectricCyan(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        Text("Dim the screen automatically after dark (follows system day/night)", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }
                    Switch(
                        checked = autoNightDim,
                        onCheckedChange = onSetAutoNightDim,
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan(), checkedTrackColor = ElectricCyan().copy(alpha = 0.4f))
                    )
                }

                if (autoNightDim) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("BRIGHTNESS", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                        Slider(
                            value = 1f - nightDimLevel,
                            onValueChange = { onSetNightDimLevel(1f - it) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan(), activeTrackColor = ElectricCyan().copy(alpha = 0.5f))
                        )
                        Text("${((1f - nightDimLevel) * 100).toInt()}%", color = PrimaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCustomizationCard(
    themeId: String,
    accentOverride: String?,
    onSetThemeId: (String) -> Unit,
    onSetAccentOverride: (String?) -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    val selected = remember(themeId) { com.sportage.obd.ui.theme.CockpitThemes.byId(themeId) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("theme_card"),
        color = DarkBackground(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ElectricCyan().copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text("THEME & COLORS", color = ElectricCyan(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                Text("Restyle the whole app instantly · ${selected.title}", color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
            }

            HorizontalDivider(color = DarkSurfaceElevated())

            ListItem(
                headlineContent = { Text("Privacy Policy", color = PrimaryText(), fontWeight = FontWeight.Medium) },
                supportingContent = { Text("View our privacy policy and data practices", color = SecondaryText()) },
                leadingContent = {
                    Icon(
                        Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = ElectricCyan(),
                        modifier = Modifier.size(24.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPrivacyPolicy() },
                colors = ListItemDefaults.colors(containerColor = DarkSurface())
            )

            // Full-app theme presets.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.sportage.obd.ui.theme.CockpitThemes.presets.forEach { preset ->
                    val isSel = preset.id == themeId
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .testTag("theme_preset_${preset.id}")
                                .size(52.dp)
                                .border(
                                    BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) ElectricCyan() else DarkSurfaceElevated()),
                                    RoundedCornerShape(10.dp)
                                )
                                .background(preset.colors.background, RoundedCornerShape(10.dp))
                                .clickable { onSetThemeId(preset.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(22.dp)
                                    .background(preset.colors.accent, CircleShape)
                                    .border(BorderStroke(2.dp, preset.colors.surface), CircleShape)
                            )
                        }
                        Text(preset.title, fontSize = 10.sp, maxLines = 1, color = if (isSel) ElectricCyan() else SecondaryText())
                    }
                }
            }

            // Accent color layered on top of the chosen preset.
            Text("ACCENT COLOR", color = SecondaryText(), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // "Preset default" clears any override.
                Box(
                    modifier = Modifier
                        .testTag("accent_default")
                        .size(36.dp)
                        .border(
                            BorderStroke(if (accentOverride == null) 2.dp else 1.dp, if (accentOverride == null) ElectricCyan() else DarkSurfaceElevated()),
                            CircleShape
                        )
                        .background(DarkSurfaceElevated(), CircleShape)
                        .clickable { onSetAccentOverride(null) },
                    contentAlignment = Alignment.Center
                ) { Text("A", color = ElectricCyan(), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                com.sportage.obd.ui.theme.CockpitThemes.accentChoices.forEach { accent ->
                    val hex = "FF%06X".format(accent.toArgb() and 0xFFFFFF)
                    val isSel = accentOverride != null && com.sportage.obd.ui.theme.CockpitThemes.parseAccent(accentOverride) == accent
                    Box(
                        modifier = Modifier
                            .testTag("accent_$hex")
                            .size(36.dp)
                            .border(
                                BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) ElectricCyan() else DarkSurfaceElevated()),
                                CircleShape
                            )
                            .background(accent, CircleShape)
                            .clickable { onSetAccentOverride(hex) }
                    )
                }
            }
            Text("Tap a color to override the accent everywhere; A restores the preset default.", color = SecondaryText(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        }
    }
}

@Composable
private fun ThresholdStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), color = PrimaryText(), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onMinus,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("-", color = AmberWarning(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Text(value, color = AmberWarning(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
            Button(
                onClick = onPlus,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated()),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("+", color = AmberWarning(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}
