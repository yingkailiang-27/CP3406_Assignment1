package au.edu.jcu.assessment.utilityapp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import au.edu.jcu.assessment.utilityapp.model.CourseOccurrence
import au.edu.jcu.assessment.utilityapp.model.PlannerSettings
import au.edu.jcu.assessment.utilityapp.model.PlannerSnapshot
import au.edu.jcu.assessment.utilityapp.model.StudyCourse
import au.edu.jcu.assessment.utilityapp.model.StudyTask
import au.edu.jcu.assessment.utilityapp.model.dueReminders
import au.edu.jcu.assessment.utilityapp.model.validateCourse
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import au.edu.jcu.assessment.utilityapp.model.nextReminderTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PlannerRepository {
    val snapshots: Flow<PlannerSnapshot>
    suspend fun addTask(title: String, deadline: String? = null)
    suspend fun toggleTask(id: String)
    suspend fun deleteTask(id: String)
    suspend fun addCourse(course: StudyCourse)
    suspend fun deleteCourse(id: String)
    suspend fun updateSettings(change: (PlannerSettings) -> PlannerSettings)
    suspend fun nextReminderAt(now: LocalDateTime): LocalDateTime?
    suspend fun claimDueReminders(now: LocalDateTime): List<CourseOccurrence>
}

/**
 * One DataStore instance per app. edit() serializes read-modify-write transactions so
 * concurrent task, settings and background reminder writes do not overwrite each other.
 */
class LocalPlannerRepository(
    private val store: DataStore<Preferences>,
    private val gson: Gson = Gson(),
    private val seedDemo: Boolean = false
) : PlannerRepository {
    private val snapshotKey = stringPreferencesKey("planner_v1")
    private val sentKey = stringSetPreferencesKey("sent_reminders_v1")
    override val snapshots: Flow<PlannerSnapshot> = store.data.map { decode(it) }

    private fun decode(preferences: Preferences): PlannerSnapshot {
        val json = preferences[snapshotKey] ?: return if (seedDemo) demoSnapshot() else PlannerSnapshot()
        return gson.fromJson(json, PlannerSnapshot::class.java)
            ?: error("Saved planner data could not be read.")
    }

    private suspend fun mutate(change: (PlannerSnapshot) -> PlannerSnapshot) {
        store.edit { preferences ->
            preferences[snapshotKey] = gson.toJson(change(decode(preferences)))
        }
    }

    override suspend fun addTask(title: String, deadline: String?) {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "Enter a task title." }
        require(clean.length <= 120) { "Use at most 120 characters for the task." }
        // Validate the stored format; equal or past deadlines are allowed.
        deadline?.let { LocalDateTime.parse(it) }
        mutate { it.copy(tasks = it.tasks + StudyTask(
            UUID.randomUUID().toString(), clean, deadline = deadline
        )) }
    }

    override suspend fun toggleTask(id: String) = mutate { snapshot ->
        snapshot.copy(tasks = snapshot.tasks.map {
            if (it.id == id) it.copy(completed = !it.completed) else it
        })
    }

    override suspend fun deleteTask(id: String) = mutate {
        it.copy(tasks = it.tasks.filterNot { task -> task.id == id })
    }

    override suspend fun addCourse(course: StudyCourse) = mutate {
        val clean = course.copy(title = course.title.trim(), room = course.room.trim())
        validateCourse(clean, it.courses)?.let { message -> throw IllegalArgumentException(message) }
        it.copy(courses = it.courses + clean)
    }

    override suspend fun deleteCourse(id: String) {
        store.edit { preferences ->
            val snapshot = decode(preferences)
            preferences[snapshotKey] = gson.toJson(
                snapshot.copy(courses = snapshot.courses.filterNot { it.id == id })
            )
            preferences[sentKey] = preferences[sentKey].orEmpty()
                .filterNot { it.startsWith("$id|") }.toSet()
        }
    }

    override suspend fun updateSettings(change: (PlannerSettings) -> PlannerSettings) =
        mutate { it.copy(settings = change(it.settings)) }

    override suspend fun nextReminderAt(now: LocalDateTime): LocalDateTime? {
        val preferences = store.data.first()
        return nextReminderTime(decode(preferences), now, preferences[sentKey].orEmpty())
    }

    override suspend fun claimDueReminders(now: LocalDateTime): List<CourseOccurrence> {
        var due: List<CourseOccurrence> = emptyList()
        store.edit { preferences ->
            val snapshot = decode(preferences)
            val cutoff = now.toLocalDate().minusDays(7).toString()
            val validIds = snapshot.courses.map { it.id }.toSet()
            val sent = preferences[sentKey].orEmpty().filter {
                it.substringBefore("|") in validIds && it.substringAfter("|") >= cutoff
            }.toSet()
            due = dueReminders(snapshot, now, sent)
            preferences[sentKey] = sent + due.map { it.reminderKey }
        }
        return due
    }
}

/** Seed only before the first write; saved user changes always take priority. */
private fun demoSnapshot(): PlannerSnapshot {
    val day = java.time.LocalDate.now().dayOfWeek.value
    return PlannerSnapshot(
        tasks = listOf(
            StudyTask("demo-task-1", "Review Kotlin and Compose", true),
            StudyTask("demo-task-2", "Complete database practice questions"),
            StudyTask("demo-task-3", "Prepare Utility App reflection")
        ),
        courses = listOf(
            StudyCourse("demo-course-1", "Mobile Technology", day, 9 * 60, 10 * 60, "Room A201"),
            StudyCourse("demo-course-2", "Database Systems", day, 14 * 60, 15 * 60, "Room B102")
        )
    )
}
