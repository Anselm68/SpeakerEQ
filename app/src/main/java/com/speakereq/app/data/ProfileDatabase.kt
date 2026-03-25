package com.speakereq.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SpeakerProfile::class], version = 1, exportSchema = false)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
