package au.edu.jcu.assessment.utilityapp.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import au.edu.jcu.assessment.utilityapp.StudyDayApplication
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ReminderScheduler {
    private val mutex = Mutex()

    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Same PendingIntent replaces the previous alarm, so settings changes cannot leave old alarms. */
    suspend fun reschedule(context: Context) = mutex.withLock {
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.REMIND)
        val pending = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pending)
        if (!exactAlarmsAllowed(context) || !ClassNotifications.allowed(context)) return@withLock
        val repository = (context.applicationContext as StudyDayApplication).container.plannerRepository
        val next = repository.nextReminderAt(LocalDateTime.now()) ?: return@withLock
        val trigger = maxOf(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            System.currentTimeMillis() + 1000)
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } catch (_: SecurityException) {
            // The user may revoke Alarms & reminders access during scheduling.
        }
    }
}
