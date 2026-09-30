package com.divebridge

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform