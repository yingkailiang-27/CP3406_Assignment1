package au.edu.jcu.assessment.utilityapp.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import au.edu.jcu.assessment.utilityapp.StudyDayApplication
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Handles a due alarm and restores scheduling after reboot, clock or permission changes. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    val app = context.applicationContext as StudyDayApplication
                    if (intent.action == REMIND && ClassNotifications.allowed(context)) {
                        val due = app.container.plannerRepository.claimDueReminders(LocalDateTime.now())
                        ClassNotifications.send(context, due)
                        Log.i("StudyDayReminders", "Alarm checked; notifications: ${due.size}")
                    }
                    ReminderScheduler.reschedule(context)
                }
            } catch (exception: Exception) {
                Log.e("StudyDayReminders", "Unable to process reminder", exception)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val REMIND = "au.edu.jcu.assessment.utilityapp.REMIND_CLASS"
    }
}
