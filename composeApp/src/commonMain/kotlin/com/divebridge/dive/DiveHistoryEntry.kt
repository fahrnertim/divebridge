package com.divebridge.dive

import kotlinx.datetime.LocalDateTime

data class DiveHistoryEntry(
    val dateTime: LocalDateTime,
    val maxDepthMeters: Double,
    val diveTimeMinutes: Double,
    val payload: String,
    val timestamp: Long,
)