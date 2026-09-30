package com.divebridge.ssi

import com.divebridge.dive.Dive

data class SsiUserInfo(
    val masterId: String,
    val firstName: String,
    val lastName: String,
)

data class SsiDiveParams(
    val diveType: Int = 0,
    val siteId: String = "",
    val waterTypeId: Int = 4,
    val diveTypeId: Int = 24,
    val leaderUserId: String = "",
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
            "divetime:${fmt(dive.diveTimeMinutes)}",
            "datetime:$datetime",
            "depth_m:${fmt(dive.maxDepthMeters)}",
            "site:${params.siteId}",
            "var_watertype_id:${params.waterTypeId}",
            "var_divetype_id:${params.diveTypeId}",
            "var_divetype_id:${params.diveTypeId}",
            "user_master_id:${user.masterId}",
            "user_firstname:${user.firstName}",
            "user_lastname:${user.lastName}",
            "user_leader_id:${params.leaderUserId}",
            "watertemp_c:${fmt(dive.minWaterTempCelsius)}",
            "watertemp_max_c:${fmt(dive.maxWaterTempCelsius)}",
        )

        return parts.joinToString(";")
    }

    private fun fmt(value: Double): String {
        val rounded = (value * 10).toLong()
        val intPart = rounded / 10
        val fracPart = kotlin.math.abs(rounded % 10)
        return "$intPart.$fracPart"
    }
}