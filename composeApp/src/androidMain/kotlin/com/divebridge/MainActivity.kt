package com.divebridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.divebridge.dive.Dive
import com.divebridge.mares.MaresBleService
import com.divebridge.settings.AndroidSettings
import com.divebridge.ui.BleEmulationScreen
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var settings: AndroidSettings
    private lateinit var diveStore: com.divebridge.dive.AndroidDiveStore
    private var fileBytes by mutableStateOf<ByteArray?>(null)
    private var savedBrightness = -1f

    private var bleService: MaresBleService? = null
    private var bleRunning by mutableStateOf(false)
    private var bleStatus by mutableStateOf(MaresBleService.BleStatus.IDLE)
    private var bleProgress by mutableStateOf(0f)
    private var bleProgressText by mutableStateOf("")
    private val bleLogs = mutableStateListOf<String>()
    private var pendingBleDives: List<Dive>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            fileBytes = readFileBytes(uri)
        }
    }

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            pendingBleDives?.let { startBle(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AndroidSettings(applicationContext)
        diveStore = com.divebridge.dive.AndroidDiveStore(applicationContext)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        handleIntent(intent)
        setupContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        bleService?.stop()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = extractFitUri(intent) ?: return
        fileBytes = readFileBytes(uri)
    }

    private fun setupContent() {
        setContent {
            val dynamicColor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                androidx.compose.material3.dynamicDarkColorScheme(this)
                    .takeIf { resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES }
                    ?: androidx.compose.material3.dynamicLightColorScheme(this)
            } else null

            App(
                settings = settings,
                diveStore = diveStore,
                onPickFile = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                onSetBrightness = { brightness -> setBrightness(brightness) },
                onShareQr = { payload -> shareQrCode(payload) },
                colorScheme = dynamicColor,
                bleContent = { dives, onBack ->
                    BleEmulationScreen(
                        isRunning = bleRunning,
                        status = bleStatus,
                        progress = bleProgress,
                        progressText = bleProgressText,
                        logs = bleLogs,
                        onStart = { requestBleStart(dives) },
                        onStop = { stopBle() },
                        onBack = onBack,
                    )
                },
                fileBytes = fileBytes,
            )
        }
    }

    private fun requestBleStart(dives: List<Dive>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                pendingBleDives = dives
                blePermissionLauncher.launch(needed.toTypedArray())
                return
            }
        }
        startBle(dives)
    }

    private fun startBle(dives: List<Dive>) {
        bleLogs.clear()
        bleStatus = MaresBleService.BleStatus.IDLE
        bleProgress = 0f
        bleProgressText = ""
        bleService?.stop()
        bleService = MaresBleService(applicationContext).apply {
            onStateChanged = { running -> bleRunning = running }
            onLog = { msg -> bleLogs.add(msg) }
            onStatusChanged = { s -> bleStatus = s }
            onProgress = { transferred, total ->
                bleProgress = if (total > 0) transferred.toFloat() / total else 0f
                bleProgressText = "${transferred / 1024}/${total / 1024} KB"
            }
            start(dives)
        }
    }

    private fun stopBle() {
        bleService?.stop()
        bleRunning = false
        bleStatus = MaresBleService.BleStatus.IDLE
    }

    private fun shareQrCode(payload: String) {
        try {
            val size = 800
            val bitmap = generateQrBitmap(payload, size)
            val file = File(cacheDir, "divebridge_qr.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share QR Code"))
        } catch (_: Exception) {}
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }

    private fun setBrightness(brightness: Float) {
        val lp = window.attributes
        if (brightness < 0) {
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