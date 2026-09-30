package com.divebridge.dive

/**
 * A single sample point from the dive profile.
 * Timestamps are seconds from dive start.
 */
data class DiveSample(
    val timeSeconds: Int,
    val depthMeters: Double,
    val temperatureCelsius: Double,
)

/**
 * Full dive profile with second-by-second (or near) sample data.
 */
data class DiveProfile(
    val samples: List<DiveSample>,
) {
    /** Resample to a fixed interval (e.g., 5 seconds for Mares). */
    fun resample(intervalSeconds: Int): List<DiveSample> {
        if (samples.isEmpty()) return emptyList()
        val result = mutableListOf<DiveSample>()
        val maxTime = samples.last().timeSeconds
        var t = 0
        while (t <= maxTime) {
            val sample = interpolateAt(t)
            result.add(sample)
            t += intervalSeconds
        }
        return result
    }

    private fun interpolateAt(timeSeconds: Int): DiveSample {
        if (samples.isEmpty()) return DiveSample(timeSeconds, 0.0, 0.0)

        // Find bracketing samples
        val idx = samples.indexOfLast { it.timeSeconds <= timeSeconds }
        if (idx < 0) return samples.first().copy(timeSeconds = timeSeconds)
        if (idx >= samples.size - 1) return samples.last().copy(timeSeconds = timeSeconds)

        val a = samples[idx]
        val b = samples[idx + 1]
        val dt = b.timeSeconds - a.timeSeconds
        if (dt == 0) return a.copy(timeSeconds = timeSeconds)

        val frac = (timeSeconds - a.timeSeconds).toDouble() / dt
        return DiveSample(
            timeSeconds = timeSeconds,
            depthMeters = a.depthMeters + (b.depthMeters - a.depthMeters) * frac,
            temperatureCelsius = a.temperatureCelsius + (b.temperatureCelsius - a.temperatureCelsius) * frac,
        )
    }
}