package com.divebridge.ssi

import com.divebridge.dive.Dive

data class SsiUserInfo(
    val masterId: String,
    val firstName: String,
    val lastName: String,
)

data class SsiDiveParams(
    val diveType: Int = DiveType.SCUBA,
    val siteId: String? = null,
    val waterTypeId: Int? = null,
    val diveSubTypeId: Int? = null,
    val weatherId: Int? = null,
    val entryId: Int? = null,
    val bodyOfWaterId: Int? = null,
    val currentId: Int? = null,
    val surfaceId: Int? = null,
    val leaderUserId: String? = null,
    val airTempCelsius: Double? = null,
    val visibilityMeters: Double? = null,
    val decompression: Int? = null,
)

object SsiPayloadBuilder {

    fun build(dive: Dive, user: SsiUserInfo, params: SsiDiveParams): String {
        val dt = dive.dateTime
        val datetime = "%04d%02d%02d%02d%02d".format(
            dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute
        )

        val parts = mutableListOf(
            "dive",
            "noid",
            "dive_type:${params.diveType}",
            "divetime:${fmtInt(dive.diveTimeMinutes)}",
            "datetime:$datetime",
            "depth_m:${fmt1(dive.maxDepthMeters)}",
        )

        params.siteId?.let { parts.add("site:$it") }
        params.weatherId?.let { parts.add("var_weather_id:$it") }
        params.entryId?.let { parts.add("var_entry_id:$it") }
        params.bodyOfWaterId?.let { parts.add("var_water_body_id:$it") }
        params.waterTypeId?.let { parts.add("var_watertype_id:$it") }
        params.currentId?.let { parts.add("var_current_id:$it") }
        params.surfaceId?.let { parts.add("var_surface_id:$it") }
        params.diveSubTypeId?.let { parts.add("var_divetype_id:$it") }

        user.masterId.takeIf { it.isNotEmpty() }?.let { parts.add("user_master_id:$it") }
        parts.add("user_firstname:${user.firstName}")
        parts.add("user_lastname:${user.lastName}")
        params.leaderUserId?.let { parts.add("user_leader_id:$it") }

        parts.add("watertemp_c:${fmtInt(dive.minWaterTempCelsius)}")
        parts.add("watertemp_max_c:${fmtInt(dive.maxWaterTempCelsius)}")

        params.airTempCelsius?.let { parts.add("airtemp_c:${fmt1(it)}") }
        params.visibilityMeters?.let { parts.add("vis_m:${fmtInt(it)}") }
        // Omit deco entirely when no deco -- setting 0 still opens deco UI in SSI app
        params.decompression?.takeIf { it != Decompression.NO }?.let { parts.add("deco:$it") }

        return parts.joinToString(";")
    }

    /** Format as integer (rounded). */
    private fun fmtInt(value: Double): String {
        return kotlin.math.round(value).toLong().toString()
    }

    /** Format with one decimal place. */
    private fun fmt1(value: Double): String {
        val rounded = kotlin.math.round(value * 10).toLong()
        val intPart = rounded / 10
        val fracPart = kotlin.math.abs(rounded % 10)
        return "$intPart.$fracPart"
    }
}