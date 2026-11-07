package com.boat.control

object BoatModels {
    val SUPPORTED_PREFIXES = listOf(
        "Airdopes",
        "boAt",
        "Nirvana",
        "Rockerz",
        "Immortal"
    )

    fun isBoatDevice(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return SUPPORTED_PREFIXES.any { prefix -> name.contains(prefix, ignoreCase = true) }
    }
}
