package com.divebridge.center

enum class VerificationType { CENTER, PROFESSIONAL }

data class DiveCenter(
    val id: String,
    val type: VerificationType,
    val centerId: String,
    val name: String,
    val subtitle: String,
    val rawPayload: String,
) {
    companion object {
        /**
         * Parse a verification QR payload. Supports:
         * - Center: "center;720008;name:Coraya Divers, Madinat Coraya - Coraya Bay"
         * - Professional: "buddy;2857464;firstName:Gaetan;lastName:Jaubert;email:...;leaderNr:94811"
         */
        fun parse(payload: String): DiveCenter? {
            val trimmed = payload.trim()
            return when {
                trimmed.startsWith("center;") -> parseCenter(trimmed)
                trimmed.startsWith("buddy;") -> parseBuddy(trimmed)
                else -> null
            }
        }

        private fun parseCenter(payload: String): DiveCenter? {
            val parts = payload.split(";", limit = 3)
            if (parts.size < 2) return null
            val centerId = parts[1]
            val name = if (parts.size >= 3 && parts[2].startsWith("name:")) {
                parts[2].removePrefix("name:")
            } else {
                "Center $centerId"
            }
            return DiveCenter(
                id = "center_$centerId",
                type = VerificationType.CENTER,
                centerId = centerId,
                name = name,
                subtitle = "Center #$centerId",
                rawPayload = payload,
            )
        }

        private fun parseBuddy(payload: String): DiveCenter? {
            val parts = payload.split(";")
            if (parts.size < 2) return null
            val buddyId = parts[1]
            val fields = mutableMapOf<String, String>()
            for (i in 2 until parts.size) {
                val kv = parts[i].split(":", limit = 2)
                if (kv.size == 2) fields[kv[0]] = kv[1]
            }
            val firstName = fields["firstName"] ?: ""
            val lastName = fields["lastName"] ?: ""
            val name = "$firstName $lastName".trim().ifEmpty { "Professional $buddyId" }
            val leaderNr = fields["leaderNr"]
            val subtitle = buildString {
                if (leaderNr != null) append("Leader #$leaderNr")
                else append("ID #$buddyId")
            }
            return DiveCenter(
                id = "buddy_$buddyId",
                type = VerificationType.PROFESSIONAL,
                centerId = buddyId,
                name = name,
                subtitle = subtitle,
                rawPayload = payload,
            )
        }
    }
}