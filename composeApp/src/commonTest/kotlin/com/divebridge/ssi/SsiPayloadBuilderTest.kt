package com.divebridge.ssi

import com.divebridge.dive.Dive
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SsiPayloadBuilderTest {

    @Test
    fun goldenTest_dive90_attersee() {
        val dive = Dive(
            dateTime = LocalDateTime(2026, 8, 23, 13, 53),
            maxDepthMeters = 13.1,
            diveTimeMinutes = 30.0,
            minWaterTempCelsius = 16.0,
            maxWaterTempCelsius = 22.0,
        )
        val user = SsiUserInfo(
            masterId = "3664600",
            firstName = "Tim",
            lastName = "Fahrner",
        )
        val params = SsiDiveParams(
            diveType = 0,
            siteId = "16887",
            waterTypeId = 4,
            diveTypeId = 24,
        )

        val result = SsiPayloadBuilder.build(dive, user, params)

        val expected = "dive;noid;dive_type:0;divetime:30.0;datetime:202608231353;" +
                "depth_m:13.1;site:16887;var_watertype_id:4;var_divetype_id:24;" +
                "var_divetype_id:24;user_master_id:3664600;user_firstname:Tim;" +
                "user_lastname:Fahrner;user_leader_id:;watertemp_c:16.0;watertemp_max_c:22.0"

        assertEquals(expected, result)
    }
}