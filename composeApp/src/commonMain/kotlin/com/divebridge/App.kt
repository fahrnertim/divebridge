package com.divebridge

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.divebridge.dive.Dive
import com.divebridge.fit.FitDecoder
import com.divebridge.fit.FitParseException
import com.divebridge.settings.Settings
import com.divebridge.ssi.SsiDiveParams
import com.divebridge.ssi.SsiPayloadBuilder
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
    fileBytes: ByteArray?,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var pendingDive by remember { mutableStateOf<Dive?>(null) }

    // When new file bytes arrive, parse and navigate
    LaunchedEffect(fileBytes) {
        if (fileBytes != null) {
            try {
                val dive = FitDecoder.decode(fileBytes)
                pendingDive = dive
                screen = Screen.DiveReview(dive)
            } catch (e: FitParseException) {
                screen = Screen.Error("Failed to parse FIT file: ${e.message}")
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
            is Screen.DiveReview -> DiveReviewScreen(
                dive = s.dive,
                initialParams = settings.getLastDiveParams(),
                onGenerate = { params ->
                    settings.saveLastDiveParams(params)
                    val userInfo = settings.getUserInfo()
                    val payload = SsiPayloadBuilder.build(s.dive, userInfo, params)
                    screen = Screen.QrCode(payload)
                },
                onBack = { screen = Screen.Home },
            )
            is Screen.QrCode -> QrCodeScreen(
                payload = s.payload,
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