package com.chengjiguanjia.spotmark.data

import androidx.room.TypeConverter

class SpotTypeConverters {
    @TypeConverter
    fun fromPhotoPaths(paths: List<String>): String = paths.joinToString(separator = "\n")

    @TypeConverter
    fun toPhotoPaths(value: String): List<String> =
        value.split("\n").filter { it.isNotBlank() }
}
