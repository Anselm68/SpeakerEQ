package com.speakereq.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.speakereq.app.data.ProfileDatabase

class SpeakerEqApp : Application() {

    lateinit var database: ProfileDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            ProfileDatabase::class.java,
            "speakereq_db"
        ).fallbackToDestructiveMigration()
        .build()

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_text_no_speaker)
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "speakereq_service"
        const val NOTIFICATION_ID = 1

        lateinit var instance: SpeakerEqApp
            private set
    }
}
