package com.speakereq.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM speaker_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<SpeakerProfile>>

    @Query("SELECT * FROM speaker_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): SpeakerProfile?

    @Query("SELECT * FROM speaker_profiles WHERE deviceAddress = :address ORDER BY createdAt DESC LIMIT 1")
    suspend fun getProfileForDevice(address: String): SpeakerProfile?

    @Query("SELECT * FROM speaker_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): SpeakerProfile?

    @Insert
    suspend fun insert(profile: SpeakerProfile): Long

    @Update
    suspend fun update(profile: SpeakerProfile)

    @Delete
    suspend fun delete(profile: SpeakerProfile)

    @Query("UPDATE speaker_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE speaker_profiles SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Transaction
    suspend fun activateProfile(id: Long) {
        deactivateAll()
        activate(id)
    }
}
