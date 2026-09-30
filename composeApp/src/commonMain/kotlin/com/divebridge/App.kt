package com.divebridge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App(fileUri: String? = null) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "DiveBridge",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Convert dive logs to QR codes",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (fileUri != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Received file: $fileUri",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}