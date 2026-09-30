package com.divebridge.mares

import com.divebridge.dive.Dive

/**
 * Handles the Mares Icon HD object protocol.
 * Translates incoming commands into responses as if we were a Puck 4.
 */
class MaresProtocol(
    private val dive: Dive,
    private val serialNumber: String = "000001",
) {
    private val diveHeader: ByteArray by lazy { MaresEncoder.encodeHeader(dive) }
    private val diveProfile: ByteArray by lazy { MaresEncoder.encodeProfile(dive) }

    companion object {
        const val CMD_VERSION: Byte = 0xC2.toByte()
        const val CMD_READ: Byte = 0xE7.toByte()
        const val CMD_OBJ_INIT: Byte = 0xBF.toByte()
        const val CMD_OBJ_EVEN: Byte = 0xAC.toByte()
        const val CMD_OBJ_ODD: Byte = 0xFE.toByte()
        const val CMD_SET_TIME: Byte = 0xB0.toByte()

        const val ACK: Byte = 0xAA.toByte()
        const val END: Byte = 0xEA.toByte()

        private const val MODEL_ID = 0x35 // Puck 4

        // Object indices
        private const val OBJ_DEVICE_INFO = 0x2000
        private const val OBJ_DIVE_COUNT = 0x2008
        private const val OBJ_DIVE_BASE = 0x3000

        // Sub-indices
        private const val SUB_MODEL = 0x02
        private const val SUB_SERIAL = 0x04
        private const val SUB_COUNT = 0x01
        private const val SUB_HEADER = 0x02
        private const val SUB_PROFILE = 0x03
    }

    // State for multi-packet object transfer
    private var pendingData: ByteArray? = null
    private var pendingOffset: Int = 0
    private var packetCounter: Int = 0

    /**
     * Handle a received command. Returns the response bytes to send.
     * The caller is responsible for BLE framing (ACK prefix, END suffix).
     */
    fun handleCommand(cmd: Byte, payload: ByteArray): ByteArray {
        return when (cmd) {
            CMD_VERSION -> handleVersion()
            CMD_OBJ_INIT -> handleObjInit(payload)
            CMD_OBJ_EVEN, CMD_OBJ_ODD -> handleObjData(cmd)
            CMD_SET_TIME -> ByteArray(0) // acknowledge, do nothing
            else -> ByteArray(0)
        }
    }

    /**
     * Returns true if there is still pending data to transfer.
     */
    fun hasPendingData(): Boolean = pendingData != null && pendingOffset < (pendingData?.size ?: 0)

    private fun handleVersion(): ByteArray {
        val response = ByteArray(140)
        // Product name at offset 0x46 (16 bytes, null-terminated)
        val name = "Puck4"
        name.toByteArray().copyInto(response, 0x46)
        return response
    }

    private fun handleObjInit(payload: ByteArray): ByteArray {
        if (payload.size < 4) return ByteArray(16)

        val index = (payload[1].toInt() and 0xFF) or ((payload[2].toInt() and 0xFF) shl 8)
        val subIndex = payload[3].toInt() and 0xFF

        val data = resolveObject(index, subIndex)
        return startObjectTransfer(data)
    }

    private fun resolveObject(index: Int, subIndex: Int): ByteArray {
        return when {
            index == OBJ_DEVICE_INFO && subIndex == SUB_MODEL -> {
                // 4-byte LE model number
                val buf = ByteArray(4)
                buf[0] = MODEL_ID.toByte()
                buf
            }
            index == OBJ_DEVICE_INFO && subIndex == SUB_SERIAL -> {
                // 6 bytes ASCII decimal serial (fits in expedited response)
                serialNumber.padStart(6, '0').take(6).toByteArray()
            }
            index == OBJ_DIVE_COUNT && subIndex == SUB_COUNT -> {
                // 2-byte LE count: always 1 dive
                byteArrayOf(1, 0)
            }
            index >= OBJ_DIVE_BASE && subIndex == SUB_HEADER -> {
                diveHeader
            }
            index >= OBJ_DIVE_BASE && subIndex == SUB_PROFILE -> {
                diveProfile
            }
            else -> ByteArray(0)
        }
    }

    private fun startObjectTransfer(data: ByteArray): ByteArray {
        val response = ByteArray(16)

        if (data.size <= 12) {
            // Small payload: embedded in response
            response[0] = 0x42
            data.copyInto(response, 4, 0, data.size.coerceAtMost(12))
            pendingData = null
        } else {
            // Large payload: signal size, data follows in subsequent packets
            response[0] = 0x41
            response[4] = (data.size and 0xFF).toByte()
            response[5] = ((data.size shr 8) and 0xFF).toByte()
            response[6] = ((data.size shr 16) and 0xFF).toByte()
            response[7] = ((data.size shr 24) and 0xFF).toByte()
            pendingData = data
            pendingOffset = 0
            packetCounter = 0
        }

        return response
    }

    private fun handleObjData(cmd: Byte): ByteArray {
        val data = pendingData ?: return ByteArray(0)
        val maxPayload = 511 // 512 bytes total with toggle byte; BLE layer splits into notifications

        val remaining = data.size - pendingOffset
        val chunkSize = remaining.coerceAtMost(maxPayload)

        val response = ByteArray(chunkSize + 1)
        // Toggle nibble in high bits of first byte
        val toggle = (packetCounter % 2) shl 4
        response[0] = toggle.toByte()
        data.copyInto(response, 1, pendingOffset, pendingOffset + chunkSize)

        pendingOffset += chunkSize
        packetCounter++

        if (pendingOffset >= data.size) {
            pendingData = null
        }

        return response
    }
}