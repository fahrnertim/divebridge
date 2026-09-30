package com.divebridge

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.divebridge.dive.Dive
import com.divebridge.dive.DiveHistoryEntry
import com.divebridge.fit.FitDecoder
import com.divebridge.fit.FitParseException
import com.divebridge.settings.Settings
import com.divebridge.ssi.SsiPayloadBuilder
import com.divebridge.ssi.toSsiDiveType
import com.divebridge.ui.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed class Screen {
    data object Home : Screen()
    data object Settings : Screen()
    data object History : Screen()
    data class DiveReview(val dive: Dive) : Screen()
    data class QrCode(val payload: String, val dive: Dive?) : Screen()
    data class Error(val message: String) : Screen()
}

@Composable
fun App(
    settings: Settings,
    onPickFile: () -> Unit,
    onSetBrightness: (Float) -> Unit,
    onShareQr: (String) -> Unit,
    fileBytes: ByteArray?,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var pendingDive by remember { mutableStateOf<Dive?>(null) }

    // When new file bytes arrive, validate and parse
    LaunchedEffect(fileBytes) {
        if (fileBytes != null) {
            if (!FitDecoder.isValidFitFile(fileBytes)) {
                screen = Screen.Error("Not a valid FIT file. Please select a .fit file exported from a dive computer.")
                return@LaunchedEffect
            }
            try {
                val dive = FitDecoder.decode(fileBytes)
                pendingDive = dive
                screen = Screen.DiveReview(dive)
            } catch (e: FitParseException) {
                screen = Screen.Error("Failed to parse dive data: ${e.message}")
            } catch (e: Exception) {
                screen = Screen.Error("Unexpected error: ${e.message}")
            }
        }
    }

    MaterialTheme {
        when (val s = screen) {
            is Screen.Home -> HomeScreen(
                onOpenFile = onPickFile,
                onOpenSettings = { screen = Screen.Settings },
                onOpenHistory = { screen = Screen.History },
                historyCount = settings.getDiveHistory().size,
            )
            is Screen.Settings -> SettingsScreen(
                initialUserInfo = settings.getUserInfo(),
                onSave = { settings.saveUserInfo(it) },
                onBack = { screen = Screen.Home },
            )
            is Screen.History -> HistoryScreen(
                entries = settings.getDiveHistory(),
                onSelect = { entry -> screen = Screen.QrCode(entry.payload, null) },
                onBack = { screen = Screen.Home },
            )
            is Screen.DiveReview -> {
                val lastParams = settings.getLastDiveParams()
                val paramsWithSport = lastParams.copy(
                    diveType = s.dive.sport.toSsiDiveType(),
                )
                DiveReviewScreen(
                    dive = s.dive,
                    initialParams = paramsWithSport,
                    recentSiteIds = settings.getRecentSiteIds(),
                    onGenerate = { params ->
                        settings.saveLastDiveParams(params)
                        params.siteId?.let { settings.addRecentSiteId(it) }
                        val userInfo = settings.getUserInfo()
                        val payload = SsiPayloadBuilder.build(s.dive, userInfo, params)

                        settings.addDiveHistoryEntry(DiveHistoryEntry(
                            dateTime = s.dive.dateTime,
                            maxDepthMeters = s.dive.maxDepthMeters,
                            diveTimeMinutes = s.dive.diveTimeMinutes,
                            payload = payload,
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                        ))

                        screen = Screen.QrCode(payload, s.dive)
                    },
                    onBack = { screen = Screen.Home },
                )
            }
            is Screen.QrCode -> QrCodeScreen(
                payload = s.payload,
                onSetBrightness = onSetBrightness,
                onShare = onShareQr,
                onBack = {
                    val dive = s.dive ?: pendingDive
                    screen = if (dive != null) Screen.DiveReview(dive) else Screen.Home
                },
            )
            is Screen.Error -> ErrorScreen(
                message = s.message,
                onBack = { screen = Screen.Home },
            )
        }
    }
}