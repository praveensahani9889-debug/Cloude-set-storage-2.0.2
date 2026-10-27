package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.local.model.UploadState

class Converters {
    @TypeConverter
    fun fromUploadState(value: UploadState): String = value.name

    @TypeConverter
    fun toUploadState(value: String): UploadState {
        return try {
            UploadState.valueOf(value)
        } catch (_: Exception) {
            UploadState.LOCAL_SAVED
        }
    }
}
