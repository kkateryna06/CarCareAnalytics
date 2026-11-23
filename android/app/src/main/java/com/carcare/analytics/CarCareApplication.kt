package com.carcare.analytics

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Configuration
import com.carcare.analytics.feature.reminders.*
import javax.inject.Inject

@HiltAndroidApp
class CarCareApplication : Application(),Configuration.Provider {
    @Inject lateinit var workerFactory: ReminderWorkerFactory
    @Inject lateinit var coordinator: ReminderCoordinator
    override val workManagerConfiguration: Configuration get()=Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate() { super.onCreate(); coordinator.start() }
}
