package com.divebridge.dive

import kotlinx.datetime.LocalDateTime

/**
 * Domain model representing a parsed dive log.
 * All values are in metric units.
 */
data class Dive(
    val dateTime: LocalDateTime,
    val maxDepthMeters: Double,
    val diveTimeMinutes: Double,
    val minWaterTempCelsius: Double,
    val maxWaterTempCelsius: Double,
    val sport: DiveSport = DiveSport.SCUBA,
    val profile: DiveProfile? = null,
    val tank: TankInfo? = null,
)

data class TankInfo(
    val startPressureBar: Double,
    val endPressureBar: Double,
    val o2Percent: Int = 21,
)

enum class DiveSport {
    SCUBA,
    FREEDIVING,
    EXTENDED_RANGE,
    REBREATHER_SCR,
    REBREATHER_CCR,
    UNKNOWN,
}