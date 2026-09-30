package com.divebridge.dive

/**
 * Central repository of parsed dives with profile data.
 * Dives can come from any source (FIT, Suunto, manual entry).
 * The store is the single source of truth for all outputs (QR, BLE, UI).
 */
interface DiveStore {
    fun getAll(): List<StoredDive>
    fun get(id: String): StoredDive?
    fun add(dive: Dive, source: String): StoredDive
    fun remove(id: String)
    fun setBleHidden(id: String, hidden: Boolean)
    fun clear()
}

/**
 * A dive persisted in the store with metadata.
 */
data class StoredDive(
    val id: String,
    val dive: Dive,
    val source: String,
    val addedAtMillis: Long,
    val bleHidden: Boolean = false,
)