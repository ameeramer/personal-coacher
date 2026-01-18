package com.personalcoacher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.personalcoacher.notification.DailyAppGenerationWorker
import com.personalcoacher.notification.NotificationHelper
import com.personalcoacher.notification.NotificationScheduler
import com.personalcoacher.data.local.TokenManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PersonalCoachApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var notificationScheduler: NotificationScheduler

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate() {
        super.onCreate()
        // Create notification channel
        notificationHelper.createNotificationChannel()

        // Re-schedule notification if it was enabled
        if (tokenManager.getNotificationsEnabledSync()) {
            notificationScheduler.scheduleJournalReminder()
        }

        // Re-schedule automatic daily tool generation if enabled
        if (tokenManager.getAutoDailyToolEnabledSync()) {
            val hour = tokenManager.getDailyToolHourSync()
            val minute = tokenManager.getDailyToolMinuteSync()
            DailyAppGenerationWorker.scheduleDaily(this, hour, minute)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
