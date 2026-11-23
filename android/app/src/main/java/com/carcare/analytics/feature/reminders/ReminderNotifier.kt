package com.carcare.analytics.feature.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import com.carcare.analytics.MainActivity
import com.carcare.analytics.R
import com.carcare.analytics.core.designsystem.AppLanguage
import com.carcare.analytics.core.security.TokenStore
import com.carcare.analytics.feature.insights.*
import com.carcare.analytics.feature.logbook.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.*

@Singleton
class ReminderNotifier @Inject constructor(@param:ApplicationContext private val context: Context,private val tokens: TokenStore,private val store: NotificationStore) {
    private val manager get()=context.getSystemService(NotificationManager::class.java)
    fun allowed(): Boolean = manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && manager.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    fun cancelAll() { manager.cancelAll() }
    suspend fun publish(cache: ReminderCache,stale: Boolean) {
        if(!allowed()) return
        val language=AppLanguage.read(context)
        val localized=language.applyTo(context)
        val dateFormatter=DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(language.locale)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,localized.getString(R.string.notification_channel),NotificationManager.IMPORTANCE_DEFAULT))
        val eligible=mutableSetOf<String>()
        for(vehicle in cache.vehicles) for(reminder in vehicle.reminders.content) {
            val evaluated=evaluateReminder(reminder,LocalDate.now(),vehicle.reminders.currentOdometerKm)
            val key=evaluated.notificationKey?:continue
            val tag="carcare:${cache.userId}:${vehicle.vehicleId}:${reminder.sourceKind}:${reminder.sourceId}"; eligible.add(tag)
            store.deliverOnce(cache.userId,"${vehicle.vehicleId}:$key") {
                val session=tokens.session.value
                if((session!=null && session.userId!=cache.userId) || !allowed()) false else {
                    val intent=Intent(context,MainActivity::class.java).setAction(tag).putExtra(EXTRA_VEHICLE,vehicle.vehicleId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val label=if(reminder.sourceKind=="DOCUMENT") DocumentType.entries.firstOrNull { it.name==reminder.type }?.label?:R.string.entry_document else ServiceType.entries.firstOrNull { it.name==reminder.type }?.label?:R.string.entry_service
                    val date=reminder.dueDate?.let { runCatching { LocalDate.parse(it).format(dateFormatter) }.getOrDefault(it) }
                    val details=listOfNotNull(date,reminder.dueOdometerKm?.let { localized.getString(R.string.km,it) },if(stale) localized.getString(R.string.notifications_cached) else null).joinToString(" · ")
                    val notification=Notification.Builder(localized,CHANNEL).setSmallIcon(R.drawable.ic_reminder).setContentTitle("${vehicle.vehicleLabel}: ${localized.getString(label)}")
                        .setContentText(details).setStyle(Notification.BigTextStyle().bigText(details)).setContentIntent(pending).setAutoCancel(true)
                        .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(false).build()
                    try { manager.notify(tag,0,notification); true } catch(ex: SecurityException) { false }
                }
            }
        }
        manager.activeNotifications.filter { it.tag?.startsWith("carcare:${cache.userId}:")==true && it.tag !in eligible }.forEach { manager.cancel(it.tag,it.id) }
    }
    companion object { const val CHANNEL="carcare_deadlines"; const val EXTRA_VEHICLE="open_reminders_vehicle" }
}
