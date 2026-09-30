package com.divebridge.fit

import com.divebridge.dive.Dive
import com.divebridge.dive.DiveProfile
import com.divebridge.dive.DiveSample
import com.divebridge.dive.DiveSport
import com.divebridge.dive.TankInfo
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Minimal FIT file decoder that extracts only the fields needed for DiveBridge.
 *
 * FIT file structure:
 * - 14-byte file header (or 12-byte legacy)
 * - Data records (definition messages and data messages)
 * - 2-byte CRC
 *
 * FIT epoch: 1989-12-31 00:00:00 UTC (631065600 seconds after Unix epoch)
 */
object FitDecoder {

    private const val FIT_EPOCH_OFFSET = 631065600L
    private val FIT_SIGNATURE = byteArrayOf('.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    private const val MESG_SESSION = 18
    private const val MESG_RECORD = 20
    private const val MESG_ACTIVITY = 34
    private const val MESG_TANK_SUMMARY = 233
    private const val MESG_DIVE_SUMMARY = 268

    // dive_summary.reference_mesg: 18 = references session (the one we want)
    private const val REFERENCE_MESG_SESSION = 18

    fun decode(data: ByteArray): Dive {
        validateHeader(data)

        var offset = headerSize(data)
        val dataEnd = offset + dataSize(data)

        val definitions = mutableMapOf<Int, FieldDefinition>()

        var sessionTimestamp: Long? = null
        var sessionSport: Int? = null
        var sessionSubSport: Int? = null
        var sessionMinTemp: Int? = null
        var sessionMaxTemp: Int? = null
        var activityTimestamp: Long? = null
        var activityLocalTimestamp: Long? = null
        var maxDepth: Double? = null
        var bottomTime: Double? = null
        var tankStartPressure: Double? = null
        var tankEndPressure: Double? = null
        val samples = mutableListOf<DiveSample>()
        var firstRecordTimestamp: Long? = null

        while (offset < dataEnd) {
            val recordHeader = data[offset].toInt() and 0xFF
            offset++

            val isDefinition: Boolean
            val localMesgType: Int

            if (recordHeader and 0x80 != 0) {
                // Compressed timestamp header
                isDefinition = false
                localMesgType = (recordHeader shr 5) and 0x03
            } else {
                isDefinition = recordHeader and 0x40 != 0
                localMesgType = recordHeader and 0x0F
            }

            if (isDefinition) {
                val hasDevFields = recordHeader and 0x20 != 0
                val def = parseDefinition(data, offset, hasDevFields)
                definitions[localMesgType] = def
                offset += def.totalSize
            } else {
                val def = definitions[localMesgType]
                    ?: throw FitParseException("Data message for undefined local type $localMesgType")

                val msgStart = offset

                when (def.globalMesgNum) {
                    MESG_SESSION -> {
                        for (field in def.fields) {
                            val fo = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                253 -> if (field.size == 4) sessionTimestamp = readUInt32(data, fo)
                                // field 0 = event (uint8), field 1 = event_type (uint8)
                                5 -> if (field.size == 1) sessionSport = data[fo].toInt() and 0xFF
                                6 -> if (field.size == 1) sessionSubSport = data[fo].toInt() and 0xFF
                                // field 58 = max_temperature (sint8, Celsius)
                                58 -> if (field.size == 1) sessionMaxTemp = data[fo].toInt()
                                // field 150 = min_temperature (sint8, Celsius)
                                150 -> if (field.size == 1) sessionMinTemp = data[fo].toInt()
                            }
                        }
                    }
                    MESG_ACTIVITY -> {
                        for (field in def.fields) {
                            val fo = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                253 -> if (field.size == 4) activityTimestamp = readUInt32(data, fo)
                                5 -> if (field.size == 4) activityLocalTimestamp = readUInt32(data, fo)
                            }
                        }
                    }
                    MESG_DIVE_SUMMARY -> {
                        // Check reference_mesg (field 0) to filter for session-referenced summary
                        var referenceMesg: Int? = null
                        var summaryMaxDepth: Long? = null
                        var summaryBottomTime: Long? = null

                        for (field in def.fields) {
                            val fo = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                0 -> if (field.size == 2) referenceMesg = readUInt16LE(data, fo)
                                // field 3 = max_depth (uint32, scale 1000, meters)
                                3 -> if (field.size == 4) {
                                    val raw = readUInt32(data, fo)
                                    if (raw != UINT32_INVALID) summaryMaxDepth = raw
                                }
                                // field 11 = bottom_time (uint32, scale 1000, seconds)
                                11 -> if (field.size == 4) {
                                    val raw = readUInt32(data, fo)
                                    if (raw != UINT32_INVALID) summaryBottomTime = raw
                                }
                            }
                        }

                        // Only use the dive_summary that references the session
                        if (referenceMesg == REFERENCE_MESG_SESSION) {
                            summaryMaxDepth?.let { maxDepth = it.toDouble() / 1000.0 }
                            summaryBottomTime?.let { bottomTime = it.toDouble() / 1000.0 / 60.0 }
                        }
                    }
                    MESG_TANK_SUMMARY -> {
                        for (field in def.fields) {
                            val fo = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                // field 2 = start_pressure (uint16, scale 100, bar)
                                2 -> if (field.size == 2) {
                                    val raw = readUInt16LE(data, fo)
                                    if (raw != 0xFFFF) tankStartPressure = raw.toDouble() / 100.0
                                }
                                // field 3 = end_pressure (uint16, scale 100, bar)
                                3 -> if (field.size == 2) {
                                    val raw = readUInt16LE(data, fo)
                                    if (raw != 0xFFFF) tankEndPressure = raw.toDouble() / 100.0
                                }
                            }
                        }
                    }
                    MESG_RECORD -> {
                        var recordTs: Long? = null
                        var recordDepth: Double? = null
                        var recordTemp: Double? = null

                        for (field in def.fields) {
                            val fo = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                253 -> if (field.size == 4) recordTs = readUInt32(data, fo)
                                // field 92 = depth (uint32, scale 1000, meters) -- Garmin dive records
                                92 -> if (field.size == 4) {
                                    val raw = readUInt32(data, fo)
                                    if (raw != UINT32_INVALID) recordDepth = raw.toDouble() / 1000.0
                                }
                                // field 13 = temperature (sint8, Celsius)
                                13 -> if (field.size == 1) recordTemp = data[fo].toDouble()
                            }
                        }

                        if (recordTs != null && recordDepth != null) {
                            if (firstRecordTimestamp == null) firstRecordTimestamp = recordTs
                            val timeOffset = (recordTs - firstRecordTimestamp!!).toInt()
                            samples.add(DiveSample(
                                timeSeconds = timeOffset,
                                depthMeters = recordDepth,
                                temperatureCelsius = recordTemp ?: 0.0,
                            ))
                        }
                    }
                }

                offset = msgStart + def.recordSize + def.devFieldSize
            }
        }

        val startTs = sessionTimestamp
            ?: throw FitParseException("No session start timestamp found")

        // Calculate local time offset from activity message
        val localOffset = if (activityTimestamp != null && activityLocalTimestamp != null) {
            activityLocalTimestamp!! - activityTimestamp!!
        } else {
            0L
        }

        val localEpochSeconds = startTs + FIT_EPOCH_OFFSET + localOffset
        val instant = Instant.fromEpochSeconds(localEpochSeconds)
        val localDateTime = instant.toLocalDateTime(TimeZone.UTC)

        return Dive(
            dateTime = localDateTime,
            maxDepthMeters = maxDepth ?: throw FitParseException("No max depth found"),
            diveTimeMinutes = bottomTime ?: throw FitParseException("No bottom time found"),
            minWaterTempCelsius = sessionMinTemp?.toDouble()
                ?: throw FitParseException("No min temperature found"),
            maxWaterTempCelsius = sessionMaxTemp?.toDouble()
                ?: throw FitParseException("No max temperature found"),
            sport = mapSport(sessionSport, sessionSubSport),
            profile = if (samples.isNotEmpty()) DiveProfile(samples) else null,
            tank = if (tankStartPressure != null && tankEndPressure != null) {
                TankInfo(tankStartPressure!!, tankEndPressure!!)
            } else null,
        )
    }

    fun isValidFitFile(data: ByteArray): Boolean {
        if (data.size < 14) return false
        return try {
            validateHeader(data)
            true
        } catch (e: FitParseException) {
            false
        }
    }

    private fun validateHeader(data: ByteArray) {
        if (data.size < 12) throw FitParseException("File too small")
        val hs = data[0].toInt() and 0xFF
        if (hs != 12 && hs != 14) throw FitParseException("Invalid header size: $hs")
        for (i in 0..3) {
            if (data[8 + i] != FIT_SIGNATURE[i]) throw FitParseException("Missing .FIT signature")
        }
    }

    private fun headerSize(data: ByteArray): Int = data[0].toInt() and 0xFF

    private fun dataSize(data: ByteArray): Int {
        return (data[4].toInt() and 0xFF) or
                ((data[5].toInt() and 0xFF) shl 8) or
                ((data[6].toInt() and 0xFF) shl 16) or
                ((data[7].toInt() and 0xFF) shl 24)
    }

    private fun parseDefinition(data: ByteArray, offset: Int, hasDevFields: Boolean): FieldDefinition {
        var pos = offset
        pos++ // reserved byte
        val architecture = data[pos].toInt() and 0xFF
        pos++
        val globalMesgNum = if (architecture == 0) readUInt16LE(data, pos) else readUInt16BE(data, pos)
        pos += 2
        val numFields = data[pos].toInt() and 0xFF
        pos++

        val fields = mutableListOf<FieldInfo>()
        var recordSize = 0
        for (i in 0 until numFields) {
            val fieldDefNum = data[pos].toInt() and 0xFF
            val size = data[pos + 1].toInt() and 0xFF
            val baseType = data[pos + 2].toInt() and 0xFF
            fields.add(FieldInfo(fieldDefNum, size, baseType, recordSize))
            recordSize += size
            pos += 3
        }

        var devFieldSize = 0
        if (hasDevFields) {
            val devFieldCount = data[pos].toInt() and 0xFF
            pos++
            for (i in 0 until devFieldCount) {
                val size = data[pos + 1].toInt() and 0xFF
                devFieldSize += size
                pos += 3
            }
        }

        return FieldDefinition(
            globalMesgNum = globalMesgNum,
            architecture = architecture,
            fields = fields,
            recordSize = recordSize,
            devFieldSize = devFieldSize,
            totalSize = pos - offset,
        )
    }

    private fun readUInt16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt16BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
    }

    private fun readUInt32(data: ByteArray, offset: Int): Long {
        return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    private const val UINT32_INVALID = 0xFFFFFFFFL

    // FIT sport enum values
    private const val FIT_SPORT_DIVING = 53

    // FIT sub_sport enum values
    private const val FIT_SUB_SPORT_SINGLE_GAS_DIVING = 57
    private const val FIT_SUB_SPORT_MULTI_GAS_DIVING = 58
    private const val FIT_SUB_SPORT_GAUGE_DIVING = 59
    private const val FIT_SUB_SPORT_APNEA_DIVING = 62
    private const val FIT_SUB_SPORT_CCR_DIVING = 74

    private fun mapSport(sport: Int?, subSport: Int?): DiveSport {
        if (sport != FIT_SPORT_DIVING) return DiveSport.UNKNOWN
        return when (subSport) {
            FIT_SUB_SPORT_APNEA_DIVING -> DiveSport.FREEDIVING
            FIT_SUB_SPORT_CCR_DIVING -> DiveSport.REBREATHER_CCR
            FIT_SUB_SPORT_MULTI_GAS_DIVING -> DiveSport.EXTENDED_RANGE
            FIT_SUB_SPORT_SINGLE_GAS_DIVING, FIT_SUB_SPORT_GAUGE_DIVING -> DiveSport.SCUBA
            else -> DiveSport.SCUBA
        }
    }
}

data class FieldDefinition(
    val globalMesgNum: Int,
    val architecture: Int,
    val fields: List<FieldInfo>,
    val recordSize: Int,
    val devFieldSize: Int,
    val totalSize: Int,
)

data class FieldInfo(
    val fieldDefNum: Int,
    val size: Int,
    val baseType: Int,
    val offsetInRecord: Int,
)

class FitParseException(message: String) : Exception(message)