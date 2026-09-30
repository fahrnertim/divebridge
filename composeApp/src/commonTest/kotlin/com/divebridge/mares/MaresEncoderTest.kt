package com.divebridge.mares

import com.divebridge.dive.*
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaresEncoderTest {

    private fun testDive(): Dive {
        val samples = (0..600 step 1).map { t ->
            val depth = when {
                t < 60 -> t * 10.0 / 60.0    // descent to 10m in 60s
                t < 540 -> 10.0               // hold at 10m
                else -> 10.0 * (600 - t) / 60.0 // ascent in 60s
            }
            DiveSample(
                timeSeconds = t,
                depthMeters = depth,
                temperatureCelsius = 22.0,
            )
        }
        return Dive(
            dateTime = LocalDateTime(2026, 8, 23, 14, 0),
            maxDepthMeters = 10.0,
            diveTimeMinutes = 10.0,
            minWaterTempCelsius = 20.0,
            maxWaterTempCelsius = 24.0,
            sport = DiveSport.SCUBA,
            profile = DiveProfile(samples),
        )
    }

    @Test
    fun header_hasCorrectSize() {
        val header = MaresEncoder.encodeHeader(testDive())
        assertEquals(0xC8, header.size)
    }

    @Test
    fun header_typeAndVersion() {
        val header = MaresEncoder.encodeHeader(testDive())
        // Type = 1 (uint16 LE)
        assertEquals(1, header[0].toInt() and 0xFF)
        assertEquals(0, header[1].toInt() and 0xFF)
        // Version 1.0
        assertEquals(0, header[2].toInt() and 0xFF) // minor
        assertEquals(1, header[3].toInt() and 0xFF) // major
    }

    @Test
    fun header_dateTimePacking() {
        val header = MaresEncoder.encodeHeader(testDive())
        // DateTime at offset 0x08 (uint32 LE packed)
        val packed = readUInt32LE(header, 0x08)
        val hour = (packed and 0x1F).toInt()
        val minute = ((packed shr 5) and 0x3F).toInt()
        val day = ((packed shr 11) and 0x1F).toInt()
        val month = ((packed shr 16) and 0x0F).toInt()
        val year = ((packed shr 20) and 0xFFF).toInt()
        assertEquals(14, hour)
        assertEquals(0, minute)
        assertEquals(23, day)
        assertEquals(8, month)
        assertEquals(2026, year)
    }

    @Test
    fun header_depthEncoding() {
        val header = MaresEncoder.encodeHeader(testDive())
        // Max depth at 0x22 (uint16 LE, 1/10 m)
        val maxDepth = readUInt16LE(header, 0x22)
        assertEquals(100, maxDepth) // 10.0m * 10
    }

    @Test
    fun header_temperatureEncoding() {
        val header = MaresEncoder.encodeHeader(testDive())
        // Temp max at 0x26
        val tempMax = readUInt16LE(header, 0x26)
        assertEquals(240, tempMax) // 24.0C * 10
        // Temp min at 0x28
        val tempMin = readUInt16LE(header, 0x28)
        assertEquals(200, tempMin) // 20.0C * 10
    }

    @Test
    fun header_sampleCount() {
        val header = MaresEncoder.encodeHeader(testDive())
        // Number of samples at 0x20 (resampled to 5s intervals)
        val count = readUInt16LE(header, 0x20)
        // 600s / 5s + 1 = 121 samples
        assertEquals(121, count)
    }

    @Test
    fun header_gasMix() {
        val header = MaresEncoder.encodeHeader(testDive())
        val gasMix = readUInt32LE(header, 0x54)
        val o2 = (gasMix and 0x7F).toInt()
        val n2 = ((gasMix shr 7) and 0x7F).toInt()
        val state = ((gasMix shr 21) and 0x03).toInt()
        assertEquals(21, o2)
        assertEquals(79, n2)
        assertEquals(2, state) // InUse
    }

    @Test
    fun profile_containsExpectedRecords() {
        val profile = MaresEncoder.encodeProfile(testDive())
        assertTrue(profile.size > 100, "Profile should have substantial data")

        // Check profile type/version header
        assertEquals(0, profile[0].toInt() and 0xFF) // type low
        assertEquals(0, profile[1].toInt() and 0xFF) // type high
        assertEquals(0, profile[2].toInt() and 0xFF) // minor version
        assertEquals(1, profile[3].toInt() and 0xFF) // major version

        // Check DSTR tag at offset 4
        val dstr = String(profile.sliceArray(4..7))
        assertEquals("DSTR", dstr)

        // Profile should end with DEND tag
        val lastTag = String(profile.sliceArray(profile.size - 4 until profile.size))
        assertEquals("DEND", lastTag)
    }

    @Test
    fun profile_dprsRecordCount() {
        val profile = MaresEncoder.encodeProfile(testDive())
        // Count DPRS tags in profile
        val dprsTag = "DPRS".encodeToByteArray()
        var count = 0
        for (i in 0..profile.size - 4) {
            if (profile[i] == dprsTag[0] && profile[i + 1] == dprsTag[1] &&
                profile[i + 2] == dprsTag[2] && profile[i + 3] == dprsTag[3]
            ) {
                count++
            }
        }
        // Each DPRS record has 2 tags (open + close), 121 samples = 242 tags
        assertEquals(242, count)
    }

    @Test
    fun fullBlob_headerPlusProfile() {
        val blob = MaresEncoder.encode(testDive())
        assertTrue(blob.size > 0xC8, "Full blob should be larger than header alone")
        // First bytes should be the header
        assertEquals(1, blob[0].toInt() and 0xFF) // header type
    }

    private fun readUInt16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt32LE(data: ByteArray, offset: Int): Long {
        return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }
}