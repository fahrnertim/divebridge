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
            diveType = DiveType.SCUBA,
            siteId = "16887",
            waterTypeId = WaterType.FRESH,
            diveSubTypeId = DiveSubType.FUN_DIVE,
            leaderUserId = "",
        )

        val result = SsiPayloadBuilder.build(dive, user, params)

        val expected = "dive;noid;dive_type:0;divetime:30;datetime:202608231353;" +
                "depth_m:13.1;site:16887;var_watertype_id:4;var_divetype_id:24;" +
                "user_master_id:3664600;user_firstname:Tim;" +
                "user_lastname:Fahrner;user_leader_id:;watertemp_c:16;watertemp_max_c:22"

        assertEquals(expected, result)
    }

    @Test
    fun minimalPayload_omitsOptionalFields() {
        val dive = Dive(
            dateTime = LocalDateTime(2025, 1, 15, 9, 30),
            maxDepthMeters = 20.5,
            diveTimeMinutes = 45.0,
            minWaterTempCelsius = 8.0,
            maxWaterTempCelsius = 12.0,
        )
        val user = SsiUserInfo(masterId = "", firstName = "", lastName = "")
        val params = SsiDiveParams()

        val result = SsiPayloadBuilder.build(dive, user, params)

        val expected = "dive;noid;dive_type:0;divetime:45;datetime:202501150930;" +
                "depth_m:20.5;user_firstname:;user_lastname:;watertemp_c:8;watertemp_max_c:12"

        assertEquals(expected, result)
    }

    @Test
    fun decoOmittedWhenNo() {
        val dive = Dive(
            dateTime = LocalDateTime(2025, 6, 1, 14, 0),
            maxDepthMeters = 30.0,
            diveTimeMinutes = 25.0,
            minWaterTempCelsius = 10.0,
            maxWaterTempCelsius = 15.0,
        )
        val user = SsiUserInfo(masterId = "123", firstName = "A", lastName = "B")
        val noDeco = SsiDiveParams(decompression = Decompression.NO)
        val yesDeco = SsiDiveParams(decompression = Decompression.YES)

        val resultNo = SsiPayloadBuilder.build(dive, user, noDeco)
        val resultYes = SsiPayloadBuilder.build(dive, user, yesDeco)

        assertEquals(false, resultNo.contains("deco:"))
        assertEquals(true, resultYes.contains("deco:1"))
    }

    @Test
    fun allOptionalFields() {
        val dive = Dive(
            dateTime = LocalDateTime(2025, 7, 20, 10, 0),
            maxDepthMeters = 18.3,
            diveTimeMinutes = 42.0,
            minWaterTempCelsius = 22.0,
            maxWaterTempCelsius = 26.0,
        )
        val user = SsiUserInfo(masterId = "999", firstName = "Jane", lastName = "Doe")
        val params = SsiDiveParams(
            diveType = DiveType.SCUBA,
            siteId = "80095",
            waterTypeId = WaterType.SALT,
            diveSubTypeId = DiveSubType.FUN_DIVE,
            weatherId = Weather.CLOUDY,
            entryId = EntryType.BOAT,
            bodyOfWaterId = BodyOfWater.OCEAN,
            currentId = Current.LIGHT,
            surfaceId = Surface.CALM,
            leaderUserId = "555",
            airTempCelsius = 28.0,
            visibilityMeters = 15.0,
            decompression = Decompression.YES,
        )

        val result = SsiPayloadBuilder.build(dive, user, params)

        assertEquals(true, result.contains("var_weather_id:2"))
        assertEquals(true, result.contains("var_entry_id:22"))
        assertEquals(true, result.contains("var_water_body_id:13"))
        assertEquals(true, result.contains("var_current_id:7"))
        assertEquals(true, result.contains("var_surface_id:10"))
        assertEquals(true, result.contains("airtemp_c:28.0"))
        assertEquals(true, result.contains("vis_m:15"))
        assertEquals(true, result.contains("deco:1"))
    }
}