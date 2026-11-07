package com.boat.control

enum class EqPreset(val id: Byte, val title: String) {
    SIGNATURE(0x00.toByte(), "Signature"),
    BASS_BOOST(0x01.toByte(), "Bass Boost"),
    BALANCED(0x02.toByte(), "Balanced"),
    POP(0x03.toByte(), "Pop"),
    ROCK(0x04.toByte(), "Rock"),
    CLUB(0x05.toByte(), "Club")
}
