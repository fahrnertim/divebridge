package com.divebridge.center

interface CenterStore {
    fun getAll(): List<DiveCenter>
    fun add(center: DiveCenter)
    fun remove(id: String)
}