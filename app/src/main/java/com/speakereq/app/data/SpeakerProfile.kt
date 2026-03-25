package com.speakereq.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speaker_profiles")
data class SpeakerProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileName: String,
    val deviceName: String,
    val deviceAddress: String,
    val measuredResponse: String,   // JSON-encoded FloatArray (31 bands)
    val correctionCurve: String,    // JSON-encoded FloatArray (31 bands)
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)
