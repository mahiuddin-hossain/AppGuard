package com.mahiuddin.appguard.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomConverters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        if (list == null) return null
        return gson.toJson(list)
    }

    @TypeConverter
    fun toStringList(json: String?): List<String>? {
        if (json.isNull_or_Empty()) return null
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    private fun String?.isNull_or_Empty(): Boolean = this == null || this.isEmpty()
}
