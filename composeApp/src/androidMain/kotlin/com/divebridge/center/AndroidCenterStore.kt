package com.divebridge.center

import android.content.Context
import java.io.File

class AndroidCenterStore(context: Context) : CenterStore {

    private val file = File(context.filesDir, "centers.txt")
    private var cache: MutableList<DiveCenter>? = null

    override fun getAll(): List<DiveCenter> {
        if (cache == null) cache = load().toMutableList()
        return cache!!.sortedBy { it.name.lowercase() }
    }

    override fun add(center: DiveCenter) {
        val list = getAll().toMutableList()
        list.removeAll { it.centerId == center.centerId }
        list.add(center)
        cache = list
        save(list)
    }

    override fun remove(id: String) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == id }
        cache = list
        save(list)
    }

    private fun load(): List<DiveCenter> {
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { DiveCenter.parse(it) }
    }

    private fun save(centers: List<DiveCenter>) {
        file.writeText(centers.joinToString("\n") { it.rawPayload })
    }
}