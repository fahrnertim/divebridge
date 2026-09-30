package com.divebridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScreen(
    payload: String,
    onSetBrightness: (Float) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var showDebug by remember { mutableStateOf(false) }

    // Max brightness while showing QR, restore on leave
    DisposableEffect(Unit) {
        onSetBrightness(1f)
        onDispose { onSetBrightness(-1f) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Code") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { showDebug = !showDebug }) {
                        Text(if (showDebug) "Hide payload" else "Show payload")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val qrPainter = rememberQrCodePainter(data = payload)

            Image(
                painter = qrPainter,
                contentDescription = "QR Code for MySSI",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(32.dp),
            )

            if (showDebug) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = payload,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}