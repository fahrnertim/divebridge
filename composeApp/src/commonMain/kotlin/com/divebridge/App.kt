package com.divebridge

import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.datetime.Clock

sealed class Screen {
    data object Home : Screen()
    data object Settings : Screen()
    data class Import(val dive: Dive) : Screen()
    data class DiveDetail(val storedDive: StoredDive) : Screen()
    data class QrCode(val payload: String, val dive: Dive?) : Screen()
    data object BleEmulator : Screen()
    data class Error(val message: String) : Screen()
}

@Composable
fun App(
    settings: Settings,
    diveStore: DiveStore,
    onPickFile: () -> Unit,
    onSetBrightness: (Float) -> Unit,
    onShareQr: (String) -> Unit,
    bleContent: (@Composable (List<Dive>, () -> Unit) -> Unit)? = null,
    fileBytes: ByteArray?,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var storeVersion by remember { mutableStateOf(0) }

    // When new file bytes arrive, validate and parse
    LaunchedEffect(fileBytes) {
        if (fileBytes != null) {
            if (!FitDecoder.isValidFitFile(fileBytes)) {
                screen = Screen.Error("Not a valid FIT file. Please select a .fit file exported from a dive computer.")
                return@LaunchedEffect
            }
            try {
                val dive = FitDecoder.decode(fileBytes)
                screen = Screen.Import(dive)
            } catch (e: FitParseException) {
                screen = Screen.Error("Failed to parse dive data: ${e.message}")
            } catch (e: Exception) {
                screen = Screen.Error("Unexpected error: ${e.message}")
            }
        }
    }

    // Read store whenever storeVersion changes
    val storedDives = remember(storeVersion) { diveStore.getAll() }

    MaterialTheme {
        when (val s = screen) {
            is Screen.Home -> HomeScreenNew(
                dives = storedDives,
                bleCutoffDate = settings.getBleCutoffDate(),
                onOpenFile = onPickFile,
                onOpenSettings = { screen = Screen.Settings },
                onOpenBle = if (bleContent != null) {
                    { screen = Screen.BleEmulator }
                } else null,
                onDiveTap = { stored -> screen = Screen.DiveDetail(stored) },
            )
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
                    diveType = s.dive.sport.toSsiDiveType(),
                )
                ImportScreen(
                    dive = s.dive,
                    initialParams = paramsWithSport,
                    recentSiteIds = settings.getRecentSiteIds(),
                    onSave = { params ->
                        settings.saveLastDiveParams(params)
                        params.siteId?.let { settings.addRecentSiteId(it) }
                        diveStore.add(s.dive, "garmin-fit")
                        storeVersion++
                        screen = Screen.Home
                    },
                    onBack = { screen = Screen.Home },
                )
            }
            is Screen.DiveDetail -> {
                DiveDetailScreen(
                    storedDive = s.storedDive,
                    onGenerateQr = {
                        val userInfo = settings.getUserInfo()
                        val lastParams = settings.getLastDiveParams()
                        val params = lastParams.copy(
                            diveType = s.storedDive.dive.sport.toSsiDiveType(),
                        )
                        val payload = SsiPayloadBuilder.build(s.storedDive.dive, userInfo, params)
                        settings.addDiveHistoryEntry(DiveHistoryEntry(
                            dateTime = s.storedDive.dive.dateTime,
                            maxDepthMeters = s.storedDive.dive.maxDepthMeters,
                            diveTimeMinutes = s.storedDive.dive.diveTimeMinutes,
                            payload = payload,
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                        ))
                        screen = Screen.QrCode(payload, s.storedDive.dive)
                    },
                    onToggleBleHidden = { hidden ->
                        diveStore.setBleHidden(s.storedDive.id, hidden)
                        storeVersion++
                        // Update the screen with the new state
                        screen = Screen.DiveDetail(s.storedDive.copy(bleHidden = hidden))
                    },
                    onDelete = {
                        diveStore.remove(s.storedDive.id)
                        storeVersion++
                        screen = Screen.Home
                    },
                    onBack = { screen = Screen.Home },
                )
            }
            is Screen.QrCode -> QrCodeScreen(
                payload = s.payload,
                onSetBrightness = onSetBrightness,
                onShare = onShareQr,
                onBack = {
                    screen = Screen.Home
                },
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
            is Screen.Error -> ErrorScreen(
                message = s.message,
                onBack = { screen = Screen.Home },
            )
        }
    }
}