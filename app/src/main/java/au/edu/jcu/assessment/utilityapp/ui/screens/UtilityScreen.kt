package au.edu.jcu.assessment.utilityapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
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
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(now.dayOfMonth.toString(), fontFamily = FontFamily.Serif,
                    fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Normal)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(now.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)),
                        style = MaterialTheme.typography.headlineSmall)
                    Text(now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(end = 0.dp, top = 4.dp, bottom = 4.dp),
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
                            task.title, style = MaterialTheme.typography.titleSmall,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            task.deadline?.let {
                                "Due: " + LocalDateTime.parse(it).format(
                                    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH))
                            } ?: "No deadline set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
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
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(dayLabel(course.weekday).uppercase(Locale.ENGLISH),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(course.title, style = MaterialTheme.typography.titleSmall)
                            Text("${minuteLabel(course.startMinute)}–${minuteLabel(course.endMinute)}",
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
    val contentColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.EventNote, null, Modifier.size(16.dp), tint = contentColor)
            Text(if (overdue) "OVERDUE DEADLINE" else "NEXT DEADLINE",
                style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
        if (deadline == null) {
            Text(if (tasks.any { !it.completed }) "No deadlines set" else "No unfinished tasks",
                fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall)
            Text(if (tasks.any { !it.completed }) "Tasks without a deadline remain in your list below."
                else "Add a task with a deadline to see it here.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            urgentTasks.forEach { task ->
                Text(task.title, fontFamily = FontFamily.Serif,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
            }
            if (urgentTasks.size > 1) Text("${urgentTasks.size} tasks share this deadline",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(deadlineCountdown(deadline, now), style = MaterialTheme.typography.titleMedium,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text("Due: " + deadline.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (overdue) Text("Complete or delete these tasks below to reveal the next deadline.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SectionHeader(
    title: String, action: String, enabled: Boolean, onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalButton(enabled = enabled, onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
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
    Card(shape = RoundedCornerShape(topStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 4.dp),
        colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ongoing = occurrence != null && !occurrence.start.isAfter(now)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                Text(if (ongoing) "IN CLASS NOW" else "UP NEXT · CLASS",
                    style = MaterialTheme.typography.labelLarge)
            }
            if (occurrence == null) {
                Text("Room for a fresh start", style = MaterialTheme.typography.headlineSmall)
                Text("Add your weekly classes below.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(occurrence.course.title, style = MaterialTheme.typography.headlineSmall)
                Text("${occurrence.start.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))} · " +
                    "${minuteLabel(occurrence.course.startMinute)}–${minuteLabel(occurrence.course.endMinute)}",
                    style = MaterialTheme.typography.bodyMedium)
                if (occurrence.course.room.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Place, null, Modifier.size(16.dp))
                        Text(occurrence.course.room, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val minutes = Duration.between(now, if (ongoing) occurrence.end else occurrence.start)
                    .seconds.coerceAtLeast(0).let { (it + 59) / 60 }
                val countdown = when {
                    minutes < 60 -> "$minutes min"
                    minutes < 1440 -> "${minutes / 60} h ${minutes % 60} min"
                    else -> "${minutes / 1440} d ${minutes % 1440 / 60} h"
                }
                Surface(shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Text(if (ongoing) "Ends in $countdown" else "Starts in $countdown",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(state: WeatherUiState, settings: PlannerSettings, onRefresh: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.data != null && !state.loading && state.error == null) {
                Text("${state.data.temperature.roundToInt()}${state.data.unit.symbol}",
                    style = MaterialTheme.typography.headlineMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(settings.city.label, style = MaterialTheme.typography.titleSmall)
                when {
                    state.loading -> Text("Loading current conditions…", style = MaterialTheme.typography.bodySmall)
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    state.data != null -> {
                        val data = state.data
                        Text(data.description, style = MaterialTheme.typography.bodyMedium)
                        data.apparentTemperature?.takeIf { it.isFinite() }?.let {
                            Text("Feels like ${it.roundToInt()}${data.unit.symbol}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            IconButton(enabled = !state.loading, onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh weather")
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.error != null) TextButton(onClick = onRefresh) { Text("Retry weather") }
        state.data?.takeIf { !state.loading && state.error == null }?.let { data ->
            Text("Updated ${data.observedAt.replace('T', ' ')} · ${data.timezone}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { uri.openUri("https://open-meteo.com/") },
            contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Weather data by Open-Meteo", style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
