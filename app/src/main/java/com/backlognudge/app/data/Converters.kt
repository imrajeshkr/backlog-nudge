package com.backlognudge.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun toTimeEstimate(value: Int): TimeEstimate =
        TimeEstimate.values().firstOrNull { it.minutes == value } ?: TimeEstimate.MIN_15

    @TypeConverter
    fun fromTimeEstimate(value: TimeEstimate): Int = value.minutes

    @TypeConverter
    fun toEnergy(value: String): EnergyLevel = EnergyLevel.valueOf(value)

    @TypeConverter
    fun fromEnergy(value: EnergyLevel): String = value.name

    @TypeConverter
    fun toCategory(value: String): ItemCategory = ItemCategory.valueOf(value)

    @TypeConverter
    fun fromCategory(value: ItemCategory): String = value.name

    @TypeConverter
    fun toStatus(value: String): ItemStatus = ItemStatus.valueOf(value)

    @TypeConverter
    fun fromStatus(value: ItemStatus): String = value.name

    @TypeConverter
    fun toResponse(value: String): NudgeResponse = NudgeResponse.valueOf(value)

    @TypeConverter
    fun fromResponse(value: NudgeResponse): String = value.name
}
