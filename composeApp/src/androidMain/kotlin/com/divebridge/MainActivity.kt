package com.divebridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.divebridge.settings.AndroidSettings

class MainActivity : ComponentActivity() {

    private lateinit var settings: AndroidSettings
    private var fileBytes by mutableStateOf<ByteArray?>(null)
    private var savedBrightness = -1f

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            fileBytes = readFileBytes(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AndroidSettings(applicationContext)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        handleIntent(intent)
        setupContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = extractFitUri(intent) ?: return
        fileBytes = readFileBytes(uri)
    }

    private fun setupContent() {
        setContent {
            App(
                settings = settings,
                onPickFile = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                onSetBrightness = { brightness -> setBrightness(brightness) },
                fileBytes = fileBytes,
            )
        }
    }

    private fun setBrightness(brightness: Float) {
        val lp = window.attributes
        if (brightness < 0) {
            // Restore previous brightness
            lp.screenBrightness = savedBrightness
        } else {
            savedBrightness = lp.screenBrightness
            lp.screenBrightness = brightness
        }
        window.attributes = lp
    }

    private fun extractFitUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    private fun readFileBytes(uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}