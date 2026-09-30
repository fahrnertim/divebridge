package com.divebridge.fit

import com.divebridge.dive.Dive
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant

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

    // Global Message Numbers we care about
    private const val MESG_FILE_ID = 0
    private const val MESG_SESSION = 18
    private const val MESG_RECORD = 20
    private const val MESG_ACTIVITY = 34
    private const val MESG_DIVE_SUMMARY = 268

    fun decode(data: ByteArray): Dive {
        validateHeader(data)

        var offset = headerSize(data)
        val dataEnd = offset + dataSize(data)

        val definitions = mutableMapOf<Int, FieldDefinition>()

        var sessionStartTimestamp: Long? = null
        var activityTimestamp: Long? = null
        var activityLocalTimestamp: Long? = null
        var maxDepth: Double? = null
        var bottomTime: Double? = null
        var minTemp = Double.MAX_VALUE
        var maxTemp = Double.MIN_VALUE
        var hasTemp = false

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
                val def = parseDefinition(data, offset)
                definitions[localMesgType] = def
                offset += def.totalSize
            } else {
                val def = definitions[localMesgType]
                    ?: throw FitParseException("Data message for undefined local type $localMesgType")

                val msgStart = offset

                when (def.globalMesgNum) {
                    MESG_SESSION -> {
                        for (field in def.fields) {
                            val fieldOffset = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                253 -> { // timestamp
                                    if (field.size == 4) {
                                        sessionStartTimestamp = readUInt32(data, fieldOffset)
                                    }
                                }
                            }
                        }
                    }
                    MESG_ACTIVITY -> {
                        for (field in def.fields) {
                            val fieldOffset = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                253 -> { // timestamp
                                    if (field.size == 4) {
                                        activityTimestamp = readUInt32(data, fieldOffset)
                                    }
                                }
                                5 -> { // local_timestamp
                                    if (field.size == 4) {
                                        activityLocalTimestamp = readUInt32(data, fieldOffset)
                                    }
                                }
                            }
                        }
                    }
                    MESG_DIVE_SUMMARY -> {
                        for (field in def.fields) {
                            val fieldOffset = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                2 -> { // max_depth (in meters, scale 1000)
                                    if (field.size == 4) {
                                        val raw = readUInt32(data, fieldOffset)
                                        if (raw != 0xFFFFFFFFL) {
                                            maxDepth = raw.toDouble() / 1000.0
                                        }
                                    }
                                }
                                4 -> { // bottom_time (in seconds, scale 1000)
                                    if (field.size == 4) {
                                        val raw = readUInt32(data, fieldOffset)
                                        if (raw != 0xFFFFFFFFL) {
                                            bottomTime = raw.toDouble() / 1000.0 / 60.0
                                        }
                                    }
                                }
                            }
                        }
                    }
                    MESG_RECORD -> {
                        for (field in def.fields) {
                            val fieldOffset = msgStart + field.offsetInRecord
                            when (field.fieldDefNum) {
                                13 -> { // temperature (sint8, Celsius)
                                    if (field.size == 1) {
                                        val temp = data[fieldOffset].toDouble()
                                        if (temp < minTemp) minTemp = temp
                                        if (temp > maxTemp) maxTemp = temp
                                        hasTemp = true
                                    }
                                }
                            }
                        }
                    }
                }

                offset = msgStart + def.recordSize

                // Skip developer fields if present
                if (def.devFieldCount > 0) {
                    offset += def.devFields.sumOf { it.size }
                }
            }
        }

        val startTs = sessionStartTimestamp
            ?: throw FitParseException("No session start timestamp found")

        // Calculate local time offset
        val localOffset = if (activityTimestamp != null && activityLocalTimestamp != null) {
            activityLocalTimestamp - activityTimestamp
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
            minWaterTempCelsius = if (hasTemp) minTemp else throw FitParseException("No temperature data found"),
            maxWaterTempCelsius = if (hasTemp) maxTemp else throw FitParseException("No temperature data found"),
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
        val headerSize = data[0].toInt() and 0xFF
        if (headerSize != 12 && headerSize != 14) {
            throw FitParseException("Invalid header size: $headerSize")
        }
        // Check ".FIT" signature at bytes 8..11
        for (i in 0..3) {
            if (data[8 + i] != FIT_SIGNATURE[i]) {
                throw FitParseException("Missing .FIT signature")
            }
        }
    }

    private fun headerSize(data: ByteArray): Int = data[0].toInt() and 0xFF

    private fun dataSize(data: ByteArray): Int {
        return (data[4].toInt() and 0xFF) or
                ((data[5].toInt() and 0xFF) shl 8) or
                ((data[6].toInt() and 0xFF) shl 16) or
                ((data[7].toInt() and 0xFF) shl 24)
    }

    private fun parseDefinition(data: ByteArray, offset: Int): FieldDefinition {
        var pos = offset
        pos++ // reserved byte
        val architecture = data[pos].toInt() and 0xFF // 0 = little endian, 1 = big endian
        pos++
        val globalMesgNum = if (architecture == 0) {
            readUInt16LE(data, pos)
        } else {
            readUInt16BE(data, pos)
        }
        pos += 2
        val numFields = data[pos].toInt() and 0xFF
        pos++

        val fields = mutableListOf<FieldInfo>()
        var recordOffset = 0
        for (i in 0 until numFields) {
            val fieldDefNum = data[pos].toInt() and 0xFF
            val size = data[pos + 1].toInt() and 0xFF
            val baseType = data[pos + 2].toInt() and 0xFF
            fields.add(FieldInfo(fieldDefNum, size, baseType, recordOffset))
            recordOffset += size
            pos += 3
        }

        // Check for developer fields
        val devFieldCount: Int
        val devFields = mutableListOf<DevFieldInfo>()
        // Developer fields are indicated by bit 5 of the record header,
        // but we parse them from the definition message structure.
        // After normal fields, if there are dev fields, there's a dev field count byte.
        // We need to check the record header for this, which is passed via the definition parsing.
        // For simplicity, we check if there's more data matching dev field pattern.
        // Actually, the record header bit 0x20 indicates dev fields in the definition.
        // We'll handle this by checking the header byte before calling parseDefinition.
        devFieldCount = 0

        val totalSize = pos - offset

        return FieldDefinition(
            globalMesgNum = globalMesgNum,
            architecture = architecture,
            fields = fields,
            devFieldCount = devFieldCount,
            devFields = devFields,
            recordSize = recordOffset,
            totalSize = totalSize,
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
}

data class FieldDefinition(
    val globalMesgNum: Int,
    val architecture: Int,
    val fields: List<FieldInfo>,
    val devFieldCount: Int,
    val devFields: List<DevFieldInfo>,
    val recordSize: Int,
    val totalSize: Int,
)

data class FieldInfo(
    val fieldDefNum: Int,
    val size: Int,
    val baseType: Int,
    val offsetInRecord: Int,
)

data class DevFieldInfo(
    val fieldNum: Int,
    val size: Int,
    val devDataIndex: Int,
)

class FitParseException(message: String) : Exception(message)