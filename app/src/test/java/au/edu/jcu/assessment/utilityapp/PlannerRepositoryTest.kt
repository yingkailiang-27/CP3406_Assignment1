package au.edu.jcu.assessment.utilityapp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import au.edu.jcu.assessment.utilityapp.data.LocalPlannerRepository
import au.edu.jcu.assessment.utilityapp.model.StudyCourse
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlannerRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var repository: LocalPlannerRepository

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = LocalPlannerRepository(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { folder.root.resolve("test.preferences_pb") }
        ))
    }
    @After fun tearDown() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }

    @Test fun alarmScheduleAdvancesAfterClaimAndCancelsWhenDisabled() = runBlocking {
        repository.addCourse(StudyCourse("alarm", "Test", 1, 35, 50))
        repository.updateSettings { it.copy(remindersEnabled = true, reminderMinutes = 15) }
        val now = LocalDateTime.of(2026, 10, 26, 0, 18)
        assertEquals(now.withMinute(20), repository.nextReminderAt(now))
        assertEquals(1, repository.claimDueReminders(now.withMinute(20)).size)
        assertEquals(now.plusWeeks(1).withMinute(20), repository.nextReminderAt(now.withMinute(21)))
        repository.updateSettings { it.copy(remindersEnabled = false) }
        assertNull(repository.nextReminderAt(now))
    }

    @Test fun deadlinesAllowDuplicatesAndSurviveReload() = runBlocking {
        val deadline = "2026-10-20T23:59"
        repository.addTask("First", deadline)
        repository.addTask("Second", deadline)
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = LocalPlannerRepository(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { folder.root.resolve("test.preferences_pb") }
        ))
        val tasks = repository.snapshots.first().tasks
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.deadline == deadline })
    }

    @Test fun legacyTaskWithoutDeadlineStillLoads() = runBlocking {
        val json = """{"tasks":[{"id":"old","title":"Read","completed":false}],"courses":[],"settings":{}}"""
        // Deserialize the same legacy shape used by the existing DataStore snapshot.
        val snapshot = com.google.gson.Gson().fromJson(json,
            au.edu.jcu.assessment.utilityapp.model.PlannerSnapshot::class.java)
        assertNull(snapshot.tasks.single().deadline)
        assertEquals("Read", snapshot.tasks.single().title)
    }

    @Test fun taskCompletionAndUndoSurviveRepositoryReads() = runBlocking {
        repository.addTask("  Read lecture notes  ")
        val task = repository.snapshots.first().tasks.single()
        assertEquals("Read lecture notes", task.title)
        repository.toggleTask(task.id)
        assertTrue(repository.snapshots.first().tasks.single().completed)
        repository.toggleTask(task.id)
        assertFalse(repository.snapshots.first().tasks.single().completed)
        repository.deleteTask(task.id)
        assertTrue(repository.snapshots.first().tasks.isEmpty())
    }
    @Test fun concurrentAddsDoNotLoseTasks() = runBlocking {
        (1..20).map { async(Dispatchers.IO) { repository.addTask("Task $it") } }.awaitAll()
        assertEquals(20, repository.snapshots.first().tasks.size)
    }
    @Test fun savedPlannerSurvivesStoreRecreation() = runBlocking {
        repository.addTask("Read")
        repository.updateSettings { it.copy(darkTheme = true) }
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = LocalPlannerRepository(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { folder.root.resolve("test.preferences_pb") }
        ))
        assertEquals("Read", repository.snapshots.first().tasks.single().title)
        assertTrue(repository.snapshots.first().settings.darkTheme)
    }
    @Test fun overlapFailureLeavesExistingTimetableUntouched() = runBlocking {
        repository.addCourse(StudyCourse("one", "Maths", 1, 540, 600))
        try {
            repository.addCourse(StudyCourse("two", "Physics", 1, 570, 630))
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, repository.snapshots.first().courses.size)
    }
    @Test fun reminderClaimIsAtomicAndDeleteRemovesCourse() = runBlocking {
        repository.addCourse(StudyCourse("one", "Maths", 1, 540, 600))
        repository.updateSettings { it.copy(remindersEnabled = true) }
        val now = LocalDateTime.of(2026, 10, 5, 8, 30)
        val claims = listOf(async { repository.claimDueReminders(now) },
            async { repository.claimDueReminders(now) }).awaitAll()
        assertEquals(1, claims.sumOf { it.size })
        repository.deleteCourse("one")
        assertTrue(repository.snapshots.first().courses.isEmpty())
        assertTrue(repository.claimDueReminders(now.plusWeeks(1)).isEmpty())
    }
}
