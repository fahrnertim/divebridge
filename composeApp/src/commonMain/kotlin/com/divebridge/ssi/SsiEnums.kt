package com.divebridge.ssi

/**
 * Known enum values for MySSI QR fields.
 * Sources: reverse-engineered from MySSI share codes and
 * https://github.com/webbertakken/takken.io
 *
 * See docs/qr-format.md for details.
 */

object DiveType {
    const val SCUBA = 0
    const val EXTENDED_RANGE = 2
    const val REBREATHER_SCR = 4
    const val FREEDIVING = 6
    const val REBREATHER_CCR = 8
}

object WaterType {
    const val FRESH = 4
    const val SALT = 5
}

object DiveSubType {
    const val EDUCATION = 23
    const val FUN_DIVE = 24
    const val SCIENTIFIC = 138
    const val WORK = 139
}

object Weather {
    const val CLOUDLESS = 1
    const val CLOUDY = 2
    const val RAINY = 3
    const val SNOW = 121
}

object EntryType {
    const val SHORE_OR_BEACH = 21
    const val BOAT = 22
    const val OTHER = 35
}

/** Incomplete -- more values likely exist. */
object BodyOfWater {
    const val OCEAN = 13
    const val RIVER = 14
    const val QUARRY = 15
    const val LAKE = 16
    const val INDOOR = 17
    const val OPEN_WATER = 54
}

object Current {
    const val NONE = 6
    const val LIGHT = 7
    const val STRONG = 8
    const val RIPPING = 9
}

object Surface {
    const val CALM = 10
    const val MOVING = 11
    const val STORMY = 12
}

object Decompression {
    const val NO = 0
    const val YES = 1
}