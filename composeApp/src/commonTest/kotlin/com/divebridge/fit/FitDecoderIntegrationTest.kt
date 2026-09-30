package com.divebridge.fit

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class FitDecoderIntegrationTest {

    private fun loadTestFile(name: String): ByteArray {
        val stream = this::class.java.classLoader?.getResourceAsStream(name)
            ?: throw IllegalStateException("Test file not found: $name")
        return stream.readBytes()
    }

    @Test
    fun decode_file1_garminDive() {
        val data = loadTestFile("23889250511_ACTIVITY.fit")
        assertTrue(FitDecoder.isValidFitFile(data))

        val dive = FitDecoder.decode(data)

        // Dive summary (reference_mesg=18): field 3 max_depth=13615 (13.615m)
        // bottom_time field 11=2879275 -> 2879275/1000/60 = 47.98 min
        // Session temps: field 150 min=13, field 58 max=26

        assertEquals(13.615, dive.maxDepthMeters, 0.001)
        assertTrue(dive.diveTimeMinutes > 47.0 && dive.diveTimeMinutes < 49.0,
            "Expected ~48 min, got ${dive.diveTimeMinutes}")
        assertEquals(13.0, dive.minWaterTempCelsius, 0.1)
        assertEquals(26.0, dive.maxWaterTempCelsius, 0.1)

        // Date should be Aug 7, 2026 (local time)
        assertEquals(2026, dive.dateTime.year)
        assertEquals(8, dive.dateTime.monthNumber)
        assertEquals(7, dive.dateTime.dayOfMonth)
    }

    @Test
    fun decode_file2_garminDive() {
        val data = loadTestFile("24536040045_ACTIVITY.fit")
        assertTrue(FitDecoder.isValidFitFile(data))

        val dive = FitDecoder.decode(data)

        // Dive summary (reference_mesg=18): field 3 max_depth=7930 (7.930m)
        // bottom_time field 11=4374632 -> 4374632/1000/60 = 72.91 min
        // Session temps: min=29, max=30

        assertEquals(7.930, dive.maxDepthMeters, 0.001)
        assertTrue(dive.diveTimeMinutes > 72.0 && dive.diveTimeMinutes < 74.0,
            "Expected ~73 min, got ${dive.diveTimeMinutes}")
        assertEquals(29.0, dive.minWaterTempCelsius, 0.1)
        assertEquals(30.0, dive.maxWaterTempCelsius, 0.1)

        // Date should be Sep 29, 2026 (local time)
        assertEquals(2026, dive.dateTime.year)
        assertEquals(9, dive.dateTime.monthNumber)
        assertEquals(29, dive.dateTime.dayOfMonth)
    }
}