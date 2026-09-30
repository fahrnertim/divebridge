package com.divebridge.dive

import android.content.Context
import kotlinx.datetime.LocalDateTime
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * File-based dive store. Each dive is saved as a binary file in the app's
 * private storage. Profile samples are included for BLE transfer.
 */
class AndroidDiveStore(context: Context) : DiveStore {

    private val dir = File(context.filesDir, "dives").also { it.mkdirs() }
    private val indexFile = File(dir, "index.dat")

    // In-memory cache
    private var cache: MutableList<StoredDive>? = null

    override fun getAll(): List<StoredDive> {
        if (cache == null) cache = loadIndex().toMutableList()
        return cache!!.sortedByDescending { it.dive.dateTime.toString() }
    }

    override fun get(id: String): StoredDive? = getAll().find { it.id == id }

    override fun add(dive: Dive, source: String): StoredDive {
        val id = "dive_${dive.dateTime.toString().replace(Regex("[^0-9]"), "")}_${System.currentTimeMillis()}"
        val stored = StoredDive(
            id = id,
            dive = dive,
            source = source,
            addedAtMillis = System.currentTimeMillis(),
        )

        // Remove existing dive with same datetime (re-import)
        val list = getAll().toMutableList()
        val removed = list.filter { it.dive.dateTime == dive.dateTime }
        removed.forEach { File(dir, "${it.id}.dive").delete() }
        list.removeAll { it.dive.dateTime == dive.dateTime }
        list.add(stored)
        cache = list.toMutableList()

        saveDiveFile(stored)
        saveIndex(list)
        return stored
    }

    override fun remove(id: String) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == id }
        cache = list
        File(dir, "$id.dive").delete()
        saveIndex(list)
    }

    override fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        cache = mutableListOf()
    }

    private fun loadIndex(): List<StoredDive> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val ids = indexFile.readLines().filter { it.isNotBlank() }
            val dives = ids.mapNotNull { id -> loadDiveFile(id) }
            // Clean up orphaned files not in the index
            val validIds = dives.map { it.id }.toSet()
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".dive")) {
                    val fileId = file.nameWithoutExtension
                    if (fileId !in validIds) file.delete()
                }
            }
            dives
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveIndex(dives: List<StoredDive>) {
        indexFile.writeText(dives.joinToString("\n") { it.id })
    }

    private fun saveDiveFile(stored: StoredDive) {
        val file = File(dir, "${stored.id}.dive")
        DataOutputStream(file.outputStream().buffered()).use { out ->
            val dive = stored.dive
            out.writeInt(1) // version
            out.writeUTF(stored.id)
            out.writeUTF(stored.source)
            out.writeLong(stored.addedAtMillis)

            // Dive fields
            out.writeUTF(dive.dateTime.toString())
            out.writeDouble(dive.maxDepthMeters)
            out.writeDouble(dive.diveTimeMinutes)
            out.writeDouble(dive.minWaterTempCelsius)
            out.writeDouble(dive.maxWaterTempCelsius)
            out.writeUTF(dive.sport.name)

            // Tank
            out.writeBoolean(dive.tank != null)
            if (dive.tank != null) {
                out.writeDouble(dive.tank.startPressureBar)
                out.writeDouble(dive.tank.endPressureBar)
                out.writeInt(dive.tank.o2Percent)
            }

            // Profile samples
            val samples = dive.profile?.samples ?: emptyList()
            out.writeInt(samples.size)
            for (s in samples) {
                out.writeInt(s.timeSeconds)
                out.writeDouble(s.depthMeters)
                out.writeDouble(s.temperatureCelsius)
            }
        }
    }

    private fun loadDiveFile(id: String): StoredDive? {
        val file = File(dir, "$id.dive")
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { inp ->
                val version = inp.readInt()
                if (version != 1) return null
                val storedId = inp.readUTF()
                val source = inp.readUTF()
                val addedAt = inp.readLong()

                val dateTime = LocalDateTime.parse(inp.readUTF())
                val maxDepth = inp.readDouble()
                val diveTime = inp.readDouble()
                val minTemp = inp.readDouble()
                val maxTemp = inp.readDouble()
                val sport = DiveSport.valueOf(inp.readUTF())

                val hasTank = inp.readBoolean()
                val tank = if (hasTank) {
                    TankInfo(inp.readDouble(), inp.readDouble(), inp.readInt())
                } else null

                val sampleCount = inp.readInt()
                val samples = (0 until sampleCount).map {
                    DiveSample(inp.readInt(), inp.readDouble(), inp.readDouble())
                }

                StoredDive(
                    id = storedId,
                    dive = Dive(
                        dateTime = dateTime,
                        maxDepthMeters = maxDepth,
                        diveTimeMinutes = diveTime,
                        minWaterTempCelsius = minTemp,
                        maxWaterTempCelsius = maxTemp,
                        sport = sport,
                        profile = if (samples.isNotEmpty()) DiveProfile(samples) else null,
                        tank = tank,
                    ),
                    source = source,
                    addedAtMillis = addedAt,
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}