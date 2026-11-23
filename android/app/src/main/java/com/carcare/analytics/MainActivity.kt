package com.carcare.analytics

import android.os.Bundle
import android.content.Intent
import android.content.Context
import androidx.compose.runtime.*
import com.carcare.analytics.feature.reminders.ReminderNotifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.carcare.analytics.core.designsystem.CarCareTheme
import com.carcare.analytics.core.designsystem.AppLanguage
import com.carcare.analytics.navigation.CarCareApp
import com.carcare.analytics.feature.vehicles.LocalVehiclePhotos
import com.carcare.analytics.feature.vehicles.VehiclePhotoImages
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var vehiclePhotos: VehiclePhotoImages
    private var reminderVehicle by mutableStateOf<Long?>(null)
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.read(newBase).applyTo(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderVehicle=intent.getLongExtra(ReminderNotifier.EXTRA_VEHICLE,-1).takeIf { it>0 }
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT), navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        val language=AppLanguage.read(this)
        setContent {
            CompositionLocalProvider(LocalVehiclePhotos provides vehiclePhotos) { CarCareTheme {
                CarCareApp(
                    notificationVehicleId=reminderVehicle,
                    onNotificationHandled={ reminderVehicle=null; intent.removeExtra(ReminderNotifier.EXTRA_VEHICLE) },
                    language=language,
                    onLanguageSelected={
                        if(it!=language) {
                            AppLanguage.save(this,it)
                            recreate()
                        }
                    }
                )
            } }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); reminderVehicle=intent.getLongExtra(ReminderNotifier.EXTRA_VEHICLE,-1).takeIf { it>0 } }
}
