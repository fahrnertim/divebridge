package com.divebridge

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.divebridge.center.CenterStore
import com.divebridge.center.DiveCenter
import com.divebridge.dive.Dive
import com.divebridge.dive.DiveHistoryEntry
import com.divebridge.dive.DiveStore
import com.divebridge.dive.StoredDive
import com.divebridge.fit.FitDecoder
import com.divebridge.fit.FitParseException
import com.divebridge.settings.Settings
import com.divebridge.ssi.SsiPayloadBuilder
import com.divebridge.ssi.toSsiDiveType
import com.divebridge.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

sealed class Screen {
    data object Home : Screen()
    data object Settings : Screen()
    data object Loading : Screen()
    data class Import(val dive: Dive) : Screen()
    data class DiveDetail(val storedDive: StoredDive) : Screen()
    data class QrCode(val payload: String, val dive: Dive?) : Screen()
    data object BleEmulator : Screen()
    data object CenterList : Screen()
    data object CenterImport : Screen()
    data class CenterQr(val center: DiveCenter) : Screen()
    data class Error(val title: String, val message: String) : Screen()
}

@Composable
fun App(
    settings: Settings,
    diveStore: DiveStore,
    centerStore: CenterStore,
    onPickFile: () -> Unit,
    onSetBrightness: (Float) -> Unit,
    onShareQr: (String) -> Unit,
    onScanCenterQr: (() -> Unit)? = null,
    scannedCenterPayload: String? = null,
    colorScheme: ColorScheme? = null,
    bleContent: (@Composable (List<Dive>, () -> Unit) -> Unit)? = null,
    fileBytes: ByteArray?,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var storeVersion by remember { mutableStateOf(0) }
    var centerVersion by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle scanned center QR
    LaunchedEffect(scannedCenterPayload) {
        if (scannedCenterPayload != null) {
            val center = DiveCenter.parse(scannedCenterPayload)
            if (center != null) {
                centerStore.add(center)
                centerVersion++
                screen = Screen.CenterQr(center)
                snackbarHostState.showSnackbar("${center.name} saved")
            } else {
                snackbarHostState.showSnackbar("Invalid center QR code")
                screen = Screen.CenterImport
            }
        }
    }

    // When new file bytes arrive, validate and parse
    LaunchedEffect(fileBytes) {
        if (fileBytes != null) {
            screen = Screen.Loading
            try {
                val dive = withContext(Dispatchers.Default) {
                    if (!FitDecoder.isValidFitFile(fileBytes)) {
                        throw FitParseException("Not a valid FIT file")
                    }
                    FitDecoder.decode(fileBytes)
                }
                screen = Screen.Import(dive)
            } catch (e: FitParseException) {
                screen = Screen.Error(
                    title = "Invalid dive file",
                    message = e.message ?: "The file could not be parsed as a FIT dive log. Make sure you're sharing from the Garmin Dive app.",
                )
            } catch (e: Exception) {
                screen = Screen.Error(
                    title = "Something went wrong",
                    message = "An unexpected error occurred while reading the file. Please try again.",
                )
            }
        }
    }

    // Read store whenever storeVersion changes
    val storedDives = remember(storeVersion) { diveStore.getAll() }

    MaterialTheme(colorScheme = colorScheme ?: MaterialTheme.colorScheme) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith
                        fadeOut(animationSpec = tween(150))
            },
            label = "screen",
        ) { currentScreen ->
        when (currentScreen) {
            is Screen.Home -> HomeScreenNew(
                dives = storedDives,
                bleCutoffDate = settings.getBleCutoffDate(),
                onOpenFile = onPickFile,
                onOpenSettings = { screen = Screen.Settings },
                onOpenBle = if (bleContent != null) {
                    { screen = Screen.BleEmulator }
                } else null,
                onOpenCenters = { screen = Screen.CenterList },
                onDiveTap = { stored -> screen = Screen.DiveDetail(stored) },
            )
            is Screen.Loading -> LoadingScreen()
            is Screen.Settings -> SettingsScreen(
                initialUserInfo = settings.getUserInfo(),
                initialBleCutoff = settings.getBleCutoffDate(),
                onSave = { settings.saveUserInfo(it) },
                onSaveBleCutoff = { settings.setBleCutoffDate(it) },
                onBack = { screen = Screen.Home },
            )
            is Screen.Import -> {
                val lastParams = settings.getLastDiveParams()
                val paramsWithSport = lastParams.copy(
                    diveType = currentScreen.dive.sport.toSsiDiveType(),
                )
                ImportScreen(
                    dive = currentScreen.dive,
                    initialParams = paramsWithSport,
                    recentSiteIds = settings.getRecentSiteIds(),
                    onSave = { params ->
                        settings.saveLastDiveParams(params)
                        params.siteId?.let { settings.addRecentSiteId(it) }
                        diveStore.add(currentScreen.dive, "garmin-fit")
                        storeVersion++
                        screen = Screen.Home
                        scope.launch { snackbarHostState.showSnackbar("Dive saved") }
                    },
                    onBack = { screen = Screen.Home },
                )
            }
            is Screen.DiveDetail -> {
                DiveDetailScreen(
                    storedDive = currentScreen.storedDive,
                    onGenerateQr = {
                        val userInfo = settings.getUserInfo()
                        val lastParams = settings.getLastDiveParams()
                        val params = lastParams.copy(
                            diveType = currentScreen.storedDive.dive.sport.toSsiDiveType(),
                        )
                        val payload = SsiPayloadBuilder.build(currentScreen.storedDive.dive, userInfo, params)
                        settings.addDiveHistoryEntry(DiveHistoryEntry(
                            dateTime = currentScreen.storedDive.dive.dateTime,
                            maxDepthMeters = currentScreen.storedDive.dive.maxDepthMeters,
                            diveTimeMinutes = currentScreen.storedDive.dive.diveTimeMinutes,
                            payload = payload,
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                        ))
                        screen = Screen.QrCode(payload, currentScreen.storedDive.dive)
                    },
                    onToggleBleHidden = { hidden ->
                        diveStore.setBleHidden(currentScreen.storedDive.id, hidden)
                        storeVersion++
                        screen = Screen.DiveDetail(currentScreen.storedDive.copy(bleHidden = hidden))
                    },
                    onDelete = {
                        diveStore.remove(currentScreen.storedDive.id)
                        storeVersion++
                        screen = Screen.Home
                    },
                    onBack = { screen = Screen.Home },
                )
            }
            is Screen.QrCode -> QrCodeScreen(
                payload = currentScreen.payload,
                onSetBrightness = onSetBrightness,
                onShare = onShareQr,
                onBack = { screen = Screen.Home },
            )
            is Screen.BleEmulator -> {
                val cutoff = settings.getBleCutoffDate()
                val dives = storedDives
                    .filter { stored ->
                        !stored.bleHidden &&
                        stored.dive.profile != null &&
                        (cutoff == null || stored.dive.dateTime.toString() >= cutoff)
                    }
                    .map { it.dive }
                bleContent?.invoke(dives) {
                    screen = Screen.Home
                }
            }
            is Screen.CenterList -> {
                val centers = remember(centerVersion) { centerStore.getAll() }
                CenterListScreen(
                    centers = centers,
                    onCenterTap = { center -> screen = Screen.CenterQr(center) },
                    onAdd = { screen = Screen.CenterImport },
                    onBack = { screen = Screen.Home },
                )
            }
            is Screen.CenterImport -> CenterImportScreen(
                onSave = { center ->
                    centerStore.add(center)
                    centerVersion++
                    screen = Screen.CenterQr(center)
                    scope.launch { snackbarHostState.showSnackbar("${center.name} saved") }
                },
                onScanQr = { onScanCenterQr?.invoke() },
                onBack = { screen = Screen.CenterList },
            )
            is Screen.CenterQr -> CenterQrScreen(
                center = currentScreen.center,
                onSetBrightness = onSetBrightness,
                onDelete = {
                    centerStore.remove(currentScreen.center.id)
                    centerVersion++
                    screen = Screen.CenterList
                },
                onBack = { screen = Screen.CenterList },
            )
            is Screen.Error -> ErrorScreen(
                title = currentScreen.title,
                message = currentScreen.message,
                onBack = { screen = Screen.Home },
            )
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        }
    }
}