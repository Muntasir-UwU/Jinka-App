package com.jinki.app.player

import androidx.media3.common.Player


enum class RepeatMode {
    OFF,
    ALL,
    ONE;

    
    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    fun toExo(): Int = when (this) {
        OFF -> Player.REPEAT_MODE_OFF
        ALL -> Player.REPEAT_MODE_ALL
        ONE -> Player.REPEAT_MODE_ONE
    }

    companion object {
        fun fromExo(mode: Int): RepeatMode = when (mode) {
            Player.REPEAT_MODE_ALL -> ALL
            Player.REPEAT_MODE_ONE -> ONE
            else -> OFF
        }

        fun parse(name: String?): RepeatMode =
            values().firstOrNull { it.name == name } ?: OFF
    }
}
