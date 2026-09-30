package com.divebridge.mares

/**
 * Mares dive computer models we can emulate.
 * All use the Genius-family Icon HD protocol.
 */
/**
 * Default model. Genius has air integration but its SSI parser expects
 * a different profile format we haven't fully reverse-engineered yet.
 * Puck 4 works reliably for profile import.
 */
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