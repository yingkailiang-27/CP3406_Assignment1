package au.edu.jcu.assessment.utilityapp.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import au.edu.jcu.assessment.utilityapp.MainActivity
import au.edu.jcu.assessment.utilityapp.R
import au.edu.jcu.assessment.utilityapp.model.CourseOccurrence
import au.edu.jcu.assessment.utilityapp.model.minuteLabel

object ClassNotifications {
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun send(applicationContext: Context, due: List<CourseOccurrence>) {
        val manager = NotificationManagerCompat.from(applicationContext)
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        due.forEach { occurrence ->
            val room = occurrence.course.room.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            val notification = NotificationCompat.Builder(applicationContext, "studyday_classes")
                .setSmallIcon(R.drawable.ic_class_notification)
                .setContentTitle(applicationContext.getString(R.string.notification_title))
                .setContentText(applicationContext.getString(
                    R.string.notification_body, occurrence.course.title,
                    minuteLabel(occurrence.course.startMinute), room
                ))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            // Permission can be revoked between the initial check and notify().
            try { manager.notify(occurrence.course.id.hashCode(), notification) }
            catch (_: SecurityException) { /* Notification permission was revoked. */ }
        }
    }
}
