package com.divebridge.mares

/**
 * Mares dive computer models we can emulate.
 * All use the Genius-family Icon HD protocol.
 */
/** Default model. Auto-selected based on tank data availability. */
val DEFAULT_MODEL = MaresModel.PUCK_4

enum class MaresModel(
    val modelId: Int,
    val bleName: String,
    val displayName: String,
    val hasAirIntegration: Boolean,
) {
    PUCK_4(
        modelId = 0x35,
        bleName = "Puck4",
        displayName = "Puck 4",
        hasAirIntegration = false,
    ),
    GENIUS(
        modelId = 0x1C,
        bleName = "Genius",
        displayName = "Genius",
        hasAirIntegration = true,
    ),
    QUAD_AIR(
        modelId = 0x23,
        bleName = "Quad Air",
        displayName = "Quad Air",
        hasAirIntegration = true,
    ),
    SIRIUS(
        modelId = 0x2F,
        bleName = "Sirius",
        displayName = "Sirius",
        hasAirIntegration = true,
    ),
}