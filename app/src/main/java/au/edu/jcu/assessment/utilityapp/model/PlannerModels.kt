package au.edu.jcu.assessment.utilityapp.model

import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

data class StudyTask(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    // ISO local date-time, for example 2026-10-20T23:59. Null supports older tasks.
    val deadline: String? = null
)

fun isTaskOverdue(task: StudyTask, now: LocalDateTime): Boolean =
    !task.completed && task.deadline?.let { !LocalDateTime.parse(it).isAfter(now) } == true

/** Earliest unfinished deadline wins, including overdue tasks. Keep all tied tasks. */
fun earliestDeadlineTasks(tasks: List<StudyTask>): List<StudyTask> {
    val candidates = tasks.filter { !it.completed && it.deadline != null }
    val earliest = candidates.minOfOrNull { LocalDateTime.parse(it.deadline!!) }
        ?: return emptyList()
    return candidates.filter { LocalDateTime.parse(it.deadline!!) == earliest }
}

fun deadlineCountdown(deadline: LocalDateTime, now: LocalDateTime): String {
    val overdue = !deadline.isAfter(now)
    val duration = if (overdue) Duration.between(deadline, now) else Duration.between(now, deadline)
    // Round remaining time up so a future deadline never looks already due.
    // Round elapsed time down so we do not overstate how late a task is.
    val minutes = duration.toMinutes() +
        if (!overdue && (duration.seconds % 60 != 0L || duration.nano != 0)) 1 else 0
    val label = when {
        minutes == 0L -> "less than 1 min"
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / 1440} d ${minutes % 1440 / 60} h"
    }
    return if (overdue) "Overdue by $label" else "Due in $label"
}

/** Weekday follows ISO-8601: Monday = 1, Sunday = 7. Times use the device timezone. */
data class StudyCourse(
    val id: String,
    val title: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val room: String = ""
)

enum class TemperatureUnit(val apiValue: String, val symbol: String) {
    CELSIUS("celsius", "°C"), FAHRENHEIT("fahrenheit", "°F")
}

enum class WeatherCity(val label: String, val latitude: Double, val longitude: Double) {
    TOWNSVILLE("Townsville, AU", -19.2590, 146.8169),
    CAIRNS("Cairns, AU", -16.9186, 145.7781),
    SINGAPORE("Singapore, SG", 1.3521, 103.8198),
    ANSHUN("Anshun, CN", 26.2456, 105.9340),
    LONDON("London, UK", 51.5074, -0.1278)
}

data class PlannerSettings(
    val city: WeatherCity = WeatherCity.TOWNSVILLE,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val showWeather: Boolean = true,
    val showCourses: Boolean = true,
    val showCompleted: Boolean = true,
    val darkTheme: Boolean = false,
    val remindersEnabled: Boolean = false,
    val reminderMinutes: Int = 60
)

data class PlannerSnapshot(
    val tasks: List<StudyTask> = emptyList(),
    val courses: List<StudyCourse> = emptyList(),
    val settings: PlannerSettings = PlannerSettings()
)

data class CourseOccurrence(val course: StudyCourse, val start: LocalDateTime) {
    val end: LocalDateTime get() = start.plusMinutes((course.endMinute - course.startMinute).toLong())
    val reminderKey: String get() = "${course.id}|${start.toLocalDate()}"
}

fun minuteLabel(minute: Int): String =
    LocalTime.of(minute / 60, minute % 60).toString()

fun dayLabel(day: Int): String =
    DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)

/** Includes an ongoing lesson; after it finishes, the next occurrence is next week. */
fun nextOccurrence(course: StudyCourse, now: LocalDateTime): CourseOccurrence {
    val offset = (course.weekday - now.dayOfWeek.value + 7) % 7
    var start = now.toLocalDate().plusDays(offset.toLong())
        .atTime(course.startMinute / 60, course.startMinute % 60)
    val end = start.plusMinutes((course.endMinute - course.startMinute).toLong())
    if (!end.isAfter(now)) start = start.plusWeeks(1)
    return CourseOccurrence(course, start)
}

fun nextClass(courses: List<StudyCourse>, now: LocalDateTime): CourseOccurrence? =
    courses.map { nextOccurrence(it, now) }.minByOrNull { it.start }

/** Adjacent time intervals are allowed. Overlaps on the same weekday are rejected. */
fun overlaps(a: StudyCourse, b: StudyCourse): Boolean =
    a.weekday == b.weekday && a.startMinute < b.endMinute && b.startMinute < a.endMinute

fun validateCourse(course: StudyCourse, existing: List<StudyCourse>): String? = when {
    course.title.isBlank() -> "Enter a course name."
    course.title.length > 80 -> "Use at most 80 characters for the course name."
    course.room.length > 80 -> "Use at most 80 characters for the room."
    course.weekday !in 1..7 -> "Choose a day of the week."
    course.startMinute !in 0..1439 || course.endMinute !in 0..1439 ->
        "Choose valid start and end times."
    course.endMinute <= course.startMinute -> "End time must be after start time on the same day."
    existing.any { it.id != course.id && overlaps(it, course) } ->
        "This time overlaps another class. Choose a different time."
    else -> null
}

/** Returns only future lessons in the reminder window; ongoing lessons are not notified. */
fun dueReminders(
    snapshot: PlannerSnapshot,
    now: LocalDateTime,
    sent: Set<String>
): List<CourseOccurrence> {
    if (!snapshot.settings.remindersEnabled) return emptyList()
    return snapshot.courses.map { nextOccurrence(it, now) }.filter {
        it.start.isAfter(now) &&
            ChronoUnit.SECONDS.between(now, it.start) <= snapshot.settings.reminderMinutes * 60L &&
            it.reminderKey !in sent
    }
}

/** Plan only one next alarm. Already notified lessons advance to the following week. */
fun nextReminderTime(
    snapshot: PlannerSnapshot, now: LocalDateTime, sent: Set<String>
): LocalDateTime? {
    if (!snapshot.settings.remindersEnabled) return null
    return snapshot.courses.map { course ->
        var occurrence = nextOccurrence(course, now)
        if (!occurrence.start.isAfter(now) || occurrence.reminderKey in sent) {
            occurrence = CourseOccurrence(course, occurrence.start.plusWeeks(1))
        }
        val reminder = occurrence.start.minusMinutes(snapshot.settings.reminderMinutes.toLong())
        // Enabling reminders inside the window should notify promptly, while still before class.
        if (reminder.isBefore(now)) now else reminder
    }.minOrNull()
}
