package au.edu.jcu.assessment.utilityapp.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import au.edu.jcu.assessment.utilityapp.R
import au.edu.jcu.assessment.utilityapp.StudyDayApplication
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

/**
 * Legacy worker retained for upgrades; new reminders use ReminderAlarmReceiver.
 * Atomic reminder claims prevent a weekly occurrence from generating repeated alerts.
 */
class ClassReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()

        return try {
            val repository = (applicationContext as StudyDayApplication).container.plannerRepository
            val due = repository.claimDueReminders(LocalDateTime.now())
            ClassNotifications.send(applicationContext, due)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val CHANNEL_ID = "studyday_classes"
        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.notification_channel_description) }
            )
        }
    }
}
