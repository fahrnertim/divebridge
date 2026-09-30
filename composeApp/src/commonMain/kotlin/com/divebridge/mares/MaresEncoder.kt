package com.divebridge.mares

import com.divebridge.dive.Dive
import com.divebridge.dive.DiveSample
import com.divebridge.dive.DiveSport

/**
 * Encodes dive data into the Mares Puck 4 (Genius family) binary format.
 *
 * Produces a dive header + profile data blob as the Puck 4 would store it,
 * suitable for serving over the Icon HD BLE object protocol.
 */
object MaresEncoder {

    private const val SAMPLE_INTERVAL_MS = 5000
    private const val SAMPLE_INTERVAL_SEC = 5

    // Header constants
    private const val HEADER_TYPE: Short = 1
    private const val HEADER_VERSION_MAJOR: Byte = 1
    private const val HEADER_VERSION_MINOR: Byte = 0
    private const val LOG_FORMAT_GENIUS: Byte = 0

    // Profile constants
    private const val PROFILE_TYPE: Short = 0 // normal (not SCR)
    private const val PROFILE_VERSION_MAJOR: Byte = 1
    private const val PROFILE_VERSION_MINOR: Byte = 0

    // Record tags (4 bytes, big-endian ASCII)
    private val TAG_DSTR = "DSTR".encodeToByteArray()
    private val TAG_TISS = "TISS".encodeToByteArray()
    private val TAG_DPRS = "DPRS".encodeToByteArray()
    private val TAG_AIRS = "AIRS".encodeToByteArray()
    private val TAG_DEND = "DEND".encodeToByteArray()

    // Record payload sizes (between tags, excluding CRC)
    private const val DSTR_PAYLOAD_SIZE = 48
    private const val TISS_PAYLOAD_SIZE = 128
    private const val DPRS_PAYLOAD_SIZE = 24
    private const val AIRS_PAYLOAD_SIZE = 6
    private const val DEND_PAYLOAD_SIZE = 152

    /**
     * Encode a complete dive blob (header + profile) in Mares Puck 4 format.
     */
    fun encode(dive: Dive, serialNumber: String = "000001"): ByteArray {
        val header = encodeHeader(dive)
        val profile = encodeProfile(dive)
        return header + profile
    }

    /**
     * Encode just the dive header (~200 bytes).
     */
    fun encodeHeader(dive: Dive, model: MaresModel = MaresModel.GENIUS): ByteArray {
        val buf = ByteArray(0xC8) // 200 bytes: 0xB8 base + 16 for version >= 1.0

        // Type and version (bytes 0-3)
        writeUInt16LE(buf, 0, HEADER_TYPE.toInt())
        buf[2] = HEADER_VERSION_MINOR
        buf[3] = HEADER_VERSION_MAJOR

        // Fingerprint / DateTime at offset 0x08
        val packedDateTime = packDateTime(dive)
        writeUInt32LE(buf, 0x08, packedDateTime)

        // Settings at offset 0x0C
        val diveMode = mapDiveMode(dive.sport)
        val waterType = 0 // Fresh by default
        val settings = (diveMode and 0x0F) or ((waterType and 0x03) shl 5)
        writeUInt32LE(buf, 0x0C, settings.toLong())

        // Log format at offset 0x10
        buf[0x10] = LOG_FORMAT_GENIUS

        // Number of samples at offset 0x20
        val samples = dive.profile?.resample(SAMPLE_INTERVAL_SEC) ?: emptyList()
        writeUInt16LE(buf, 0x20, samples.size)

        // Max depth at offset 0x22 (uint16 LE, 1/10 meter)
        writeUInt16LE(buf, 0x22, (dive.maxDepthMeters * 10).toInt())

        // Avg depth at offset 0x24
        val avgDepth = if (samples.isNotEmpty()) {
            samples.sumOf { it.depthMeters } / samples.size
        } else {
            dive.maxDepthMeters / 2
        }
        writeUInt16LE(buf, 0x24, (avgDepth * 10).toInt())

        // Temperature max at offset 0x26 (uint16 LE signed, 1/10 degC)
        writeUInt16LE(buf, 0x26, (dive.maxWaterTempCelsius * 10).toInt())

        // Temperature min at offset 0x28
        writeUInt16LE(buf, 0x28, (dive.minWaterTempCelsius * 10).toInt())

        // Metric flag at offset 0x34
        buf[0x34] = 1 // metric

        // Atmospheric pressure at offset 0x3E (uint16 LE, mbar)
        writeUInt16LE(buf, 0x3E, 1013) // standard atmosphere

        // Gas mix at offset 0x54 (first entry: Air, 21% O2)
        val gasMixOffset = 0x54
        val o2Pct = 21
        val n2Pct = 79
        val hePct = 0
        val gasState = 2 // InUse
        val gasMix = (o2Pct and 0x7F) or
                ((n2Pct and 0x7F) shl 7) or
                ((hePct and 0x7F) shl 14) or
                ((gasState and 0x03) shl 21)
        writeUInt32LE(buf, gasMixOffset, gasMix.toLong())

        // Tank pressure data (in gas mix entry, offset +4 and +6)
        if (model.hasAirIntegration && dive.tank != null) {
            // Begin pressure at gasMixOffset + 4 (uint16 LE, 1/100 bar)
            writeUInt16LE(buf, gasMixOffset + 4, (dive.tank.startPressureBar * 100).toInt())
            // End pressure at gasMixOffset + 6 (uint16 LE, 1/100 bar)
            writeUInt16LE(buf, gasMixOffset + 6, (dive.tank.endPressureBar * 100).toInt())
        }

        return buf
    }

    /**
     * Encode the profile data (DSTR + samples + DEND).
     * When hasTankData is true, AIRS records are interleaved with DPRS records.
     */
    fun encodeProfile(dive: Dive, hasTankData: Boolean = false): ByteArray {
        val samples = dive.profile?.resample(SAMPLE_INTERVAL_SEC) ?: emptyList()
        val out = mutableListOf<Byte>()

        // Profile type/version header (4 bytes)
        out.addAll(writeUInt16LEBytes(PROFILE_TYPE.toInt()))
        out.add(PROFILE_VERSION_MINOR)
        out.add(PROFILE_VERSION_MAJOR)

        // DSTR record (dive start)
        out.addAll(encodeRecord(TAG_DSTR, ByteArray(DSTR_PAYLOAD_SIZE)))

        // TISS record (tissue saturation -- zeroed, surface)
        out.addAll(encodeRecord(TAG_TISS, ByteArray(TISS_PAYLOAD_SIZE)))

        // Interpolate tank pressure for AIRS records
        val startBar = dive.tank?.startPressureBar ?: 200.0
        val endBar = dive.tank?.endPressureBar ?: 50.0
        val totalSamples = samples.size.coerceAtLeast(1)

        // DPRS records (depth/pressure samples every 5 seconds)
        // with AIRS records interleaved when air integration is enabled
        for ((i, sample) in samples.withIndex()) {
            out.addAll(encodeRecord(TAG_DPRS, encodeDprs(sample)))
            if (hasTankData) {
                val frac = i.toDouble() / totalSamples
                val pressure = startBar + (endBar - startBar) * frac
                out.addAll(encodeRecord(TAG_AIRS, encodeAirs(pressure)))
            }
        }

        // DEND record (dive end)
        out.addAll(encodeRecord(TAG_DEND, ByteArray(DEND_PAYLOAD_SIZE)))

        return out.toByteArray()
    }

    private fun encodeAirs(pressureBar: Double): ByteArray {
        val buf = ByteArray(AIRS_PAYLOAD_SIZE)
        // Tank pressure at offset 0 (uint16 LE, 1/100 bar)
        writeUInt16LE(buf, 0, (pressureBar * 100).toInt())
        return buf
    }

    private fun encodeDprs(sample: DiveSample): ByteArray {
        val buf = ByteArray(DPRS_PAYLOAD_SIZE)
        // Depth at offset 0 (uint16 LE, 1/10 meter)
        writeUInt16LE(buf, 0, (sample.depthMeters * 10).toInt())
        // Temperature at offset 4 (uint16 LE, 1/10 degC)
        writeUInt16LE(buf, 4, (sample.temperatureCelsius * 10).toInt())
        // NDL time at offset 0x0A (uint16 LE, minutes) -- 99 = no deco
        writeUInt16LE(buf, 0x0A, 99)
        return buf
    }

    /**
     * Wrap a payload in a tagged record: [tag][payload][crc16][tag]
     */
    private fun encodeRecord(tag: ByteArray, payload: ByteArray): List<Byte> {
        val result = mutableListOf<Byte>()
        result.addAll(tag.toList())
        result.addAll(payload.toList())
        val crc = crc16Ccitt(payload)
        result.add((crc and 0xFF).toByte())
        result.add(((crc shr 8) and 0xFF).toByte())
        result.addAll(tag.toList())
        return result
    }

    private fun packDateTime(dive: Dive): Long {
        val dt = dive.dateTime
        return ((dt.hour and 0x1F).toLong()) or
                ((dt.minute and 0x3F).toLong() shl 5) or
                ((dt.dayOfMonth and 0x1F).toLong() shl 11) or
                ((dt.monthNumber and 0x0F).toLong() shl 16) or
                (dt.year.toLong() shl 20)
    }

    private fun mapDiveMode(sport: DiveSport): Int = when (sport) {
        DiveSport.SCUBA -> 0           // Air
        DiveSport.FREEDIVING -> 5      // Freedive
        DiveSport.EXTENDED_RANGE -> 2  // NitroxMulti
        DiveSport.REBREATHER_SCR -> 6  // SCR
        DiveSport.REBREATHER_CCR -> 7  // OC (closest match)
        DiveSport.UNKNOWN -> 0         // Air
    }

    /** CRC16-CCITT (init=0x0000, poly=0x1021) */
    private fun crc16Ccitt(data: ByteArray): Int {
        var crc = 0x0000
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            for (i in 0 until 8) {
                crc = if (crc and 0x8000 != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    // --- Binary helpers ---

    private fun writeUInt16LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeUInt32LE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeUInt16LEBytes(value: Int): List<Byte> {
        return listOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
    }
}