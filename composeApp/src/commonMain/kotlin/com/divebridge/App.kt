package com.divebridge

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.divebridge.dive.Dive
import com.divebridge.fit.FitDecoder
import com.divebridge.fit.FitParseException
import com.divebridge.settings.Settings
import com.divebridge.ssi.SsiPayloadBuilder
import com.divebridge.ssi.toSsiDiveType
import com.divebridge.ui.*

sealed class Screen {
    data object Home : Screen()
    data object Settings : Screen()
    data class DiveReview(val dive: Dive) : Screen()
    data class QrCode(val payload: String) : Screen()
    data class Error(val message: String) : Screen()
}

@Composable
fun App(
    settings: Settings,
    onPickFile: () -> Unit,
    onSetBrightness: (Float) -> Unit,
    onShareImage: ((String) -> Unit)? = null,
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
            )
            is Screen.Settings -> SettingsScreen(
                initialUserInfo = settings.getUserInfo(),
                onSave = { settings.saveUserInfo(it) },
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
                        screen = Screen.QrCode(payload)
                    },
                    onBack = { screen = Screen.Home },
                )
            }
            is Screen.QrCode -> QrCodeScreen(
                payload = s.payload,
                onSetBrightness = onSetBrightness,
                onBack = {
                    val dive = pendingDive
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