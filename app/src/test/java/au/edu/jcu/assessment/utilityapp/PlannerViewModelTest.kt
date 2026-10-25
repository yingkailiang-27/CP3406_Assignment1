package au.edu.jcu.assessment.utilityapp

import androidx.lifecycle.viewModelScope

import au.edu.jcu.assessment.utilityapp.data.*
import au.edu.jcu.assessment.utilityapp.model.*
import au.edu.jcu.assessment.utilityapp.viewmodel.PlannerViewModel
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakePlanner : PlannerRepository {
        override val snapshots = MutableStateFlow(PlannerSnapshot())
        var failWrite = false
        override suspend fun addTask(title: String, deadline: String?) {
            if (failWrite) throw IOException("Disk unavailable")
            snapshots.value = snapshots.value.copy(tasks = listOf(StudyTask("one", title, deadline = deadline)))
        }
        override suspend fun toggleTask(id: String) { }
        override suspend fun deleteTask(id: String) { }
        override suspend fun addCourse(course: StudyCourse) { }
        override suspend fun deleteCourse(id: String) { }
        override suspend fun updateSettings(change: (PlannerSettings) -> PlannerSettings) {
            snapshots.value = snapshots.value.copy(settings = change(snapshots.value.settings))
        }
        override suspend fun nextReminderAt(now: LocalDateTime): LocalDateTime? = null
        override suspend fun claimDueReminders(now: LocalDateTime) = emptyList<CourseOccurrence>()
    }
    private fun info(city: WeatherCity, unit: TemperatureUnit) =
        WeatherInfo(city, unit, 24.0, 25.0, "Clear sky", "2026-10-08T12:00", "UTC")

    @Test fun saveFailureDoesNotCloseDialogOrPretendTaskWasAdded() = runTest(dispatcher) {
        val planner = FakePlanner()
        planner.failWrite = true
        val weather = object : WeatherRepository {
            override suspend fun fetch(city: WeatherCity, unit: TemperatureUnit) = info(city, unit)
        }
        val model = PlannerViewModel(planner, weather)
        advanceUntilIdle()
        var closed = false
        model.addTask("Read") { closed = true }
        advanceUntilIdle()
        assertFalse(closed)
        assertTrue(model.uiState.value.snapshot.tasks.isEmpty())
        assertFalse(model.uiState.value.saving)
        assertTrue(model.uiState.value.message!!.contains("could not be saved"))
        model.viewModelScopeForTestCancel()
    }
    @Test fun hidingWeatherCancelsAnInFlightRequest() = runTest(dispatcher) {
        var cancelled = false
        val weather = object : WeatherRepository {
            override suspend fun fetch(city: WeatherCity, unit: TemperatureUnit): WeatherInfo {
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        val planner = FakePlanner()
        val model = PlannerViewModel(planner, weather)
        runCurrent()
        assertTrue(model.uiState.value.weather.loading)
        planner.updateSettings { it.copy(showWeather = false) }
        runCurrent()
        assertTrue(cancelled)
        assertFalse(model.uiState.value.weather.loading)
        assertNull(model.uiState.value.weather.data)
        model.viewModelScopeForTestCancel()
    }
    @Test fun aLateOldCityResponseDoesNotOverwriteNewCity() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<WeatherInfo>()
        val weather = object : WeatherRepository {
            override suspend fun fetch(city: WeatherCity, unit: TemperatureUnit): WeatherInfo {
                return if (city == WeatherCity.TOWNSVILLE) withContext(NonCancellable) {
                    oldResponse.await()
                } else info(city, unit)
            }
        }
        val planner = FakePlanner()
        val model = PlannerViewModel(planner, weather)
        runCurrent()
        planner.updateSettings { it.copy(city = WeatherCity.SINGAPORE) }
        runCurrent()
        assertEquals(WeatherCity.SINGAPORE, model.uiState.value.weather.data?.city)
        oldResponse.complete(info(WeatherCity.TOWNSVILLE, TemperatureUnit.CELSIUS))
        runCurrent()
        assertEquals(WeatherCity.SINGAPORE, model.uiState.value.weather.data?.city)
        model.viewModelScopeForTestCancel()
    }
    @Test fun offlineWeatherShowsRetryableErrorAndLeavesPlannerAvailable() = runTest(dispatcher) {
        val weather = object : WeatherRepository {
            override suspend fun fetch(city: WeatherCity, unit: TemperatureUnit): WeatherInfo {
                throw IOException("Offline")
            }
        }
        val model = PlannerViewModel(FakePlanner(), weather)
        advanceUntilIdle()
        assertFalse(model.uiState.value.loading)
        assertFalse(model.uiState.value.weather.loading)
        assertNotNull(model.uiState.value.weather.error)
        model.viewModelScopeForTestCancel()
    }
}

private fun PlannerViewModel.viewModelScopeForTestCancel() {
    // Avoid leaving the repository collector alive beyond the test's dispatcher.
    viewModelScope.cancel()
}
