package com.divebridge.mares

import com.divebridge.dive.*
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaresProtocolTest {

    private fun testDive(): Dive = Dive(
        dateTime = LocalDateTime(2026, 8, 23, 14, 0),
        maxDepthMeters = 10.0,
        diveTimeMinutes = 10.0,
        minWaterTempCelsius = 20.0,
        maxWaterTempCelsius = 24.0,
        sport = DiveSport.SCUBA,
        profile = DiveProfile((0..60).map { t ->
            DiveSample(t, 5.0, 22.0)
        }),
    )

    @Test
    fun version_returnsPuck4Name() {
        val protocol = MaresProtocol(testDive())
        val response = protocol.handleCommand(MaresProtocol.CMD_VERSION, ByteArray(0))
        assertEquals(140, response.size)
        // "Puck4" at offset 0x46
        val name = String(response.sliceArray(0x46..0x4A))
        assertEquals("Puck4", name)
    }

    @Test
    fun objInit_modelNumber() {
        val protocol = MaresProtocol(testDive())
        // Request model: index=0x2000, subindex=0x02
        val payload = byteArrayOf(0x40, 0x00, 0x20, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val response = protocol.handleCommand(MaresProtocol.CMD_OBJ_INIT, payload)
        assertEquals(16, response.size)
        // Should be small payload (0x42) with model 0x35
        assertEquals(0x42, response[0].toInt() and 0xFF)
        assertEquals(0x35, response[4].toInt() and 0xFF)
    }

    @Test
    fun objInit_diveCount() {
        val protocol = MaresProtocol(testDive())
        // Request dive count: index=0x2008, subindex=0x01
        val payload = byteArrayOf(0x40, 0x08, 0x20, 0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val response = protocol.handleCommand(MaresProtocol.CMD_OBJ_INIT, payload)
        assertEquals(16, response.size)
        assertEquals(0x42, response[0].toInt() and 0xFF)
        assertEquals(1, response[4].toInt() and 0xFF) // 1 dive
    }

    @Test
    fun objInit_diveHeader_triggersLargeTransfer() {
        val protocol = MaresProtocol(testDive())
        // Request dive header: index=0x3000, subindex=0x02
        val payload = byteArrayOf(0x40, 0x00, 0x30, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val response = protocol.handleCommand(MaresProtocol.CMD_OBJ_INIT, payload)
        assertEquals(16, response.size)
        // Header is 200 bytes = large payload (0x41)
        assertEquals(0x41, response[0].toInt() and 0xFF)
        val size = (response[4].toInt() and 0xFF) or
                ((response[5].toInt() and 0xFF) shl 8)
        assertEquals(0xC8, size) // 200 bytes
        assertTrue(protocol.hasPendingData())
    }

    @Test
    fun objData_transfersCorrectly() {
        val protocol = MaresProtocol(testDive())
        // Init header transfer
        val initPayload = byteArrayOf(0x40, 0x00, 0x30, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        protocol.handleCommand(MaresProtocol.CMD_OBJ_INIT, initPayload)

        // Read data packets
        val allData = mutableListOf<Byte>()
        var packetCount = 0
        while (protocol.hasPendingData()) {
            val cmd = if (packetCount % 2 == 0) MaresProtocol.CMD_OBJ_EVEN else MaresProtocol.CMD_OBJ_ODD
            val data = protocol.handleCommand(cmd, ByteArray(0))
            // Skip first byte (toggle nibble)
            allData.addAll(data.drop(1))
            packetCount++
        }

        assertEquals(0xC8, allData.size) // should get full 200-byte header
    }
}