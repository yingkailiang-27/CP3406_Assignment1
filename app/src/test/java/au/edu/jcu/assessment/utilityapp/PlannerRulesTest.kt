package au.edu.jcu.assessment.utilityapp

import au.edu.jcu.assessment.utilityapp.model.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class PlannerRulesTest {
    @Test fun alarmTimeUsesLeadTimeAndCatchesCurrentWindow() {
        val course = StudyCourse("test", "Test", 1, 35, 50)
        val snapshot = PlannerSnapshot(courses = listOf(course),
            settings = PlannerSettings(remindersEnabled = true, reminderMinutes = 15))
        val now = LocalDateTime.of(2026, 10, 26, 0, 18)
        assertEquals(now.withMinute(20), nextReminderTime(snapshot, now, emptySet()))
        assertEquals(now.withMinute(25), nextReminderTime(snapshot, now.withMinute(25), emptySet()))
        assertEquals(now.plusWeeks(1).withMinute(20),
            nextReminderTime(snapshot, now.withMinute(35), emptySet()))
        assertEquals(now.plusWeeks(1).withMinute(20),
            nextReminderTime(snapshot, now, setOf("test|2026-10-26")))
        assertNull(nextReminderTime(snapshot.copy(settings = PlannerSettings()), now, emptySet()))
        assertNull(nextReminderTime(snapshot.copy(courses = emptyList()), now, emptySet()))
    }

    @Test fun alarmTimeHandlesMidnightAndChoosesEarliestCourse() {
        val course = StudyCourse("test", "Test", 1, 35, 50)
        val later = StudyCourse("later", "Later", 2, 600, 660)
        val snapshot = PlannerSnapshot(courses = listOf(later, course),
            settings = PlannerSettings(remindersEnabled = true, reminderMinutes = 60))
        val now = LocalDateTime.of(2026, 10, 25, 23, 0)
        assertEquals(now.withMinute(35), nextReminderTime(snapshot, now, emptySet()))
    }

    @Test fun summaryKeepsAllTiesAndIgnoresCompletedOrUndatedTasks() {
        val first = StudyTask("first", "Read", deadline = "2026-10-20T12:00")
        val tied = StudyTask("tied", "Write", deadline = "2026-10-20T12:00:00")
        val later = StudyTask("later", "Revise", deadline = "2026-10-21T12:00")
        val completed = StudyTask("done", "Done", true, "2026-10-19T12:00")
        assertEquals(listOf(first, tied), earliestDeadlineTasks(listOf(
            later, completed, StudyTask("none", "No date"), first, tied)))
        assertEquals(listOf(later), earliestDeadlineTasks(listOf(first.copy(completed = true), later)))
    }

    @Test fun overdueDeadlineTakesPriorityAndEmptySummaryIsSafe() {
        val overdue = StudyTask("old", "Overdue", deadline = "2026-10-19T12:00")
        val future = StudyTask("future", "Future", deadline = "2026-10-21T12:00")
        assertEquals(listOf(overdue), earliestDeadlineTasks(listOf(future, overdue)))
        assertTrue(earliestDeadlineTasks(emptyList()).isEmpty())
        assertTrue(earliestDeadlineTasks(listOf(StudyTask("none", "No date"),
            overdue.copy(completed = true))).isEmpty())
    }

    @Test fun countdownRoundsFutureUpAndElapsedDownAtDeadlineBoundary() {
        val deadline = LocalDateTime.of(2026, 10, 20, 12, 0)
        assertEquals("Due in 1 min", deadlineCountdown(deadline, deadline.minusSeconds(1)))
        assertEquals("Due in 2 min", deadlineCountdown(deadline, deadline.minusMinutes(1).minusNanos(1)))
        assertEquals("Overdue by less than 1 min", deadlineCountdown(deadline, deadline))
        assertEquals("Overdue by less than 1 min", deadlineCountdown(deadline, deadline.plusSeconds(59)))
        assertEquals("Overdue by 1 min", deadlineCountdown(deadline, deadline.plusMinutes(1)))
    }

    @Test fun countdownFormatsHoursDaysAndCrossesMidnight() {
        val now = LocalDateTime.of(2026, 10, 20, 23, 30)
        assertEquals("Due in 1 h 30 min", deadlineCountdown(now.plusMinutes(90), now))
        assertEquals("Due in 2 d 3 h", deadlineCountdown(now.plusDays(2).plusHours(3), now))
        assertEquals("Overdue by 1 d 2 h", deadlineCountdown(now.minusDays(1).minusHours(2), now))
    }

    @Test fun deadlineBoundaryAndCompletedTasks() {
        val now = LocalDateTime.of(2026, 10, 20, 23, 59)
        val task = StudyTask("task", "Read", deadline = now.toString())
        assertFalse(isTaskOverdue(task, now.minusMinutes(1)))
        assertTrue(isTaskOverdue(task, now))
        assertTrue(isTaskOverdue(task, now.plusMinutes(1)))
        assertFalse(isTaskOverdue(task.copy(completed = true), now))
        assertFalse(isTaskOverdue(task.copy(deadline = null), now))
    }

    private val mondayClass = StudyCourse("math", "Maths", 1, 9 * 60, 10 * 60)
    private val monday = LocalDateTime.of(2026, 10, 5, 8, 30)

    @Test fun upcomingLessonUsesSameDay() {
        assertEquals(monday.withHour(9).withMinute(0), nextOccurrence(mondayClass, monday).start)
    }
    @Test fun ongoingLessonRemainsCurrent() {
        assertEquals(monday.withHour(9).withMinute(0),
            nextOccurrence(mondayClass, monday.withHour(9).withMinute(30)).start)
    }
    @Test fun lessonAtEndTimeMovesToNextWeek() {
        assertEquals(monday.plusWeeks(1).withHour(9).withMinute(0),
            nextOccurrence(mondayClass, monday.withHour(10).withMinute(0)).start)
    }
    @Test fun sundayToMondayRollsForwardOneDay() {
        val sunday = LocalDateTime.of(2026, 10, 11, 18, 0)
        assertEquals(sunday.plusDays(1).withHour(9), nextOccurrence(mondayClass, sunday).start)
    }
    @Test fun nextClassSortsByActualOccurrence() {
        val fridayClass = StudyCourse("fri", "Friday class", 5, 11 * 60, 12 * 60)
        val thursday = LocalDateTime.of(2026, 10, 8, 15, 0)
        assertEquals("fri", nextClass(listOf(mondayClass, fridayClass), thursday)?.course?.id)
        assertNull(nextClass(emptyList(), thursday))
    }
    @Test fun adjacentClassesDoNotOverlap() {
        val adjacent = StudyCourse("other", "Physics", 1, 10 * 60, 11 * 60)
        assertFalse(overlaps(mondayClass, adjacent))
        assertNull(validateCourse(adjacent, listOf(mondayClass)))
    }
    @Test fun overlappingClassesAreRejectedOnlyOnSameDay() {
        val overlap = StudyCourse("other", "Physics", 1, 9 * 60 + 30, 11 * 60)
        assertNotNull(validateCourse(overlap, listOf(mondayClass)))
        assertNull(validateCourse(overlap.copy(weekday = 2), listOf(mondayClass)))
    }
    @Test fun invalidTimesAndBlankTitlesAreRejected() {
        assertNotNull(validateCourse(mondayClass.copy(endMinute = 8 * 60), emptyList()))
        assertNotNull(validateCourse(mondayClass.copy(title = " "), emptyList()))
        assertNotNull(validateCourse(mondayClass.copy(weekday = 8), emptyList()))
        assertNotNull(validateCourse(mondayClass.copy(endMinute = 1440), emptyList()))
    }
    @Test fun reminderWindowExcludesSentAndStartedLessons() {
        val snapshot = PlannerSnapshot(courses = listOf(mondayClass),
            settings = PlannerSettings(remindersEnabled = true, reminderMinutes = 60))
        assertEquals(1, dueReminders(snapshot, monday, emptySet()).size)
        assertTrue(dueReminders(snapshot, monday, setOf("math|2026-10-05")).isEmpty())
        assertTrue(dueReminders(snapshot, monday.withHour(9).withMinute(0), emptySet()).isEmpty())
        assertTrue(dueReminders(snapshot, monday.withHour(7), emptySet()).isEmpty())
        assertTrue(dueReminders(snapshot.copy(settings = PlannerSettings()), monday, emptySet()).isEmpty())
    }
    @Test fun reminderWindowUsesSecondsWithoutEarlyRounding() {
        val snapshot = PlannerSnapshot(courses = listOf(mondayClass),
            settings = PlannerSettings(remindersEnabled = true, reminderMinutes = 30))
        assertTrue(dueReminders(snapshot, monday.minusSeconds(1), emptySet()).isEmpty())
        assertEquals(1, dueReminders(snapshot, monday, emptySet()).size)
    }
}
