package com.krizbuds.control

object EarbudModels {
    val SUPPORTED_PREFIXES = listOf(
        "KrizBuds",
        "Earbuds",
        "Buds",
        "Wireless",
        "Headphones",
        "Headset",
        "Audio",
        "TWS"
    )

    fun isSupportedDevice(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return true
    }
}
