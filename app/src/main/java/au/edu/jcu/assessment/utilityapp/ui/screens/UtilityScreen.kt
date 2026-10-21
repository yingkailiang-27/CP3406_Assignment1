package au.edu.jcu.assessment.utilityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import au.edu.jcu.assessment.utilityapp.model.*
import au.edu.jcu.assessment.utilityapp.viewmodel.PlannerUiState
import au.edu.jcu.assessment.utilityapp.viewmodel.WeatherUiState
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun UtilityScreen(
    state: PlannerUiState, now: LocalDateTime, modifier: Modifier = Modifier,
    onAddTask: () -> Unit, onToggleTask: (String) -> Unit, onDeleteTask: (String) -> Unit,
    onAddCourse: () -> Unit, onDeleteCourse: (String) -> Unit, onRefreshWeather: () -> Unit
) {
    val snapshot = state.snapshot
    val completed = snapshot.tasks.count { it.completed }
    val visibleTasks = snapshot.tasks
        .filter { snapshot.settings.showCompleted || !it.completed }
        .sortedBy { it.completed }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                style = MaterialTheme.typography.titleMedium)
        }
        if (snapshot.settings.showCourses) {
            item { NextClassCard(nextClass(snapshot.courses, now), now) }
        }
        item { DeadlineSummaryCard(snapshot.tasks, now) }
        if (snapshot.settings.showWeather) {
            item { WeatherCard(state.weather, snapshot.settings, onRefreshWeather) }
        }
        item {
            SectionHeader("Tasks", "Add task", !state.saving, onAddTask,
                subtitle = "$completed/${snapshot.tasks.size} completed")
        }
        if (visibleTasks.isEmpty()) {
            item { EmptyCard(
                if (snapshot.tasks.isEmpty()) "A little plan goes a long way."
                else "No unfinished tasks. Your completed tasks are hidden.",
                if (snapshot.tasks.isEmpty()) "Add your first task to get started." else "Add a new task or change the display setting."
            ) }
        }
        items(visibleTasks, key = { "task-${it.id}" }) { task ->
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = task.completed, onCheckedChange = { onToggleTask(task.id) },
                        enabled = !state.saving,
                        modifier = Modifier.semantics {
                            contentDescription = if (task.completed) "Mark unfinished: ${task.title}"
                                else "Complete task: ${task.title}"
                        }
                    )
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(
                            task.title,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            task.deadline?.let {
                                "Due: " + LocalDateTime.parse(it).format(
                                    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH))
                            } ?: "No deadline set",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (isTaskOverdue(task, now)) Text("Overdue",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(enabled = !state.saving, onClick = { onDeleteTask(task.id) }) {
                        Icon(Icons.Default.DeleteOutline, "Delete task: ${task.title}")
                    }
                }
            }
        }
        if (snapshot.settings.showCourses) {
            item { SectionHeader("Weekly timetable", "Add class", !state.saving, onAddCourse) }
            if (snapshot.courses.isEmpty()) item {
                EmptyCard("Your week starts here.", "Add a weekly class to see your next lesson.")
            }
            items(snapshot.courses.sortedWith(compareBy({ it.weekday }, { it.startMinute })),
                key = { "course-${it.id}" }) { course ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(course.title, style = MaterialTheme.typography.titleSmall)
                            Text("${dayLabel(course.weekday)} · ${minuteLabel(course.startMinute)}–${minuteLabel(course.endMinute)}",
                                style = MaterialTheme.typography.bodyMedium)
                            if (course.room.isNotBlank()) Text(course.room,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(enabled = !state.saving, onClick = { onDeleteCourse(course.id) }) {
                            Icon(Icons.Default.DeleteOutline, "Delete class: ${course.title}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeadlineSummaryCard(tasks: List<StudyTask>, now: LocalDateTime) {
    val urgentTasks = earliestDeadlineTasks(tasks)
    val deadline = urgentTasks.firstOrNull()?.deadline?.let { LocalDateTime.parse(it) }
    val overdue = deadline != null && !deadline.isAfter(now)
    val containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (overdue) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Card(colors = CardDefaults.cardColors(
        containerColor = containerColor, contentColor = contentColor
    )) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (overdue) "OVERDUE DEADLINE" else "NEXT DEADLINE",
                style = MaterialTheme.typography.labelLarge)
            if (deadline == null) {
                Text(if (tasks.any { !it.completed }) "No deadlines set" else "No unfinished tasks",
                    style = MaterialTheme.typography.titleLarge)
                Text(if (tasks.any { !it.completed })
                    "Tasks without a deadline remain in your list below."
                    else "Add a task with a deadline to see it here.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(deadlineCountdown(deadline, now),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Due: " + deadline.format(
                    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)),
                    style = MaterialTheme.typography.bodyMedium)
                if (urgentTasks.size > 1) Text("${urgentTasks.size} tasks share this deadline",
                    style = MaterialTheme.typography.labelLarge)
                urgentTasks.forEach { task ->
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                }
                if (overdue) Text("Complete or delete these tasks in the list below to reveal the next deadline.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String, action: String, enabled: Boolean, onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(enabled = enabled, onClick = onClick) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(action)
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NextClassCard(occurrence: CourseOccurrence?, now: LocalDateTime) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val ongoing = occurrence != null && !occurrence.start.isAfter(now)
            Text(if (ongoing) "IN CLASS NOW" else "NEXT CLASS",
                style = MaterialTheme.typography.labelLarge)
            if (occurrence == null) {
                Text("A clear schedule", style = MaterialTheme.typography.headlineSmall)
                Text("Add a class below to plan your week.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(occurrence.course.title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("${occurrence.start.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))} · " +
                    "${minuteLabel(occurrence.course.startMinute)}–${minuteLabel(occurrence.course.endMinute)}")
                if (occurrence.course.room.isNotBlank()) Text(occurrence.course.room)
                val minutes = Duration.between(now, if (ongoing) occurrence.end else occurrence.start)
                    .seconds.coerceAtLeast(0).let { (it + 59) / 60 }
                val countdown = when {
                    minutes < 60 -> "$minutes min"
                    minutes < 1440 -> "${minutes / 60} h ${minutes % 60} min"
                    else -> "${minutes / 1440} d ${minutes % 1440 / 60} h"
                }
                Text(if (ongoing) "Ends in $countdown" else "Starts in $countdown",
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun WeatherCard(state: WeatherUiState, settings: PlannerSettings, onRefresh: () -> Unit) {
    val uri = LocalUriHandler.current
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Campus weather", style = MaterialTheme.typography.titleMedium)
                    Text(settings.city.label, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(enabled = !state.loading, onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh weather")
                }
            }
            when {
                state.loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading current conditions…", style = MaterialTheme.typography.bodySmall)
                }
                state.error != null -> {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh) { Text("Retry weather") }
                }
                state.data != null -> {
                    val data = state.data
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("${data.temperature.roundToInt()}${data.unit.symbol}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(data.description, style = MaterialTheme.typography.bodyLarge)
                            data.apparentTemperature?.takeIf { it.isFinite() }?.let {
                                Text("Feels like ${it.roundToInt()}${data.unit.symbol}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("Provider time: ${data.observedAt.replace('T', ' ')} · ${data.timezone}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(
                onClick = { uri.openUri("https://open-meteo.com/") },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text("Weather data by Open-Meteo", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
