package au.edu.jcu.assessment.utilityapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import au.edu.jcu.assessment.utilityapp.model.PlannerSettings
import au.edu.jcu.assessment.utilityapp.model.TemperatureUnit
import au.edu.jcu.assessment.utilityapp.model.WeatherCity
import au.edu.jcu.assessment.utilityapp.ui.components.SelectionField

@Composable
fun SettingsScreen(
    settings: PlannerSettings, enabled: Boolean, notificationsAllowed: Boolean,
    exactAlarmsAllowed: Boolean, onOpenAlarmSettings: () -> Unit,
    onChange: ((PlannerSettings) -> PlannerSettings) -> Unit,
    onReminderToggle: (Boolean) -> Unit, onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsGroup("Display") {
                SettingSwitch("Show timetable", "Next class and weekly schedule",
                    settings.showCourses, enabled) { checked -> onChange { it.copy(showCourses = checked) } }
                SettingSwitch("Show completed tasks", "Keep finished tasks visible in your list",
                    settings.showCompleted, enabled) { checked -> onChange { it.copy(showCompleted = checked) } }
                SettingSwitch("Dark theme", "Use a darker colour palette",
                    settings.darkTheme, enabled) { checked -> onChange { it.copy(darkTheme = checked) } }
            }
        }
        item {
            SettingsGroup("Weather") {
                SettingSwitch("Show weather", "Current conditions for your campus city",
                    settings.showWeather, enabled) { checked -> onChange { it.copy(showWeather = checked) } }
                SelectionField("City", settings.city.label, WeatherCity.entries.map { it.label }, enabled) { label ->
                    onChange { it.copy(city = WeatherCity.entries.first { city -> city.label == label }) }
                }
                Spacer(Modifier.height(12.dp))
                val unitLabels = listOf("Celsius (°C)", "Fahrenheit (°F)")
                SelectionField(
                    "Temperature unit", unitLabels[settings.temperatureUnit.ordinal], unitLabels, enabled
                ) { label -> onChange {
                    it.copy(temperatureUnit = TemperatureUnit.entries[unitLabels.indexOf(label)])
                } }
            }
        }
        item {
            SettingsGroup("Class reminders") {
                SettingSwitch("Notify before class", "A reminder for each weekly lesson",
                    settings.remindersEnabled, enabled, onReminderToggle)
                SelectionField(
                    "Remind me", "${settings.reminderMinutes} minutes before",
                    listOf(15, 30, 60).map { "$it minutes before" },
                    enabled && settings.remindersEnabled
                ) { label -> onChange { it.copy(reminderMinutes = label.substringBefore(" ").toInt()) } }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Reminders are scheduled for the selected time before class. " +
                        "Classes already started are not notified.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!exactAlarmsAllowed) {
                    Spacer(Modifier.height(8.dp))
                    Text("Allow Alarms & reminders in Android settings to schedule class reminders.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onOpenAlarmSettings) { Text("Allow scheduled reminders") }
                }
                if (!notificationsAllowed) {
                    Spacer(Modifier.height(8.dp))
                    Text("Notifications are currently blocked by Android.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onOpenNotificationSettings) { Text("Open notification settings") }
                }
            }
        }
        item {
            SettingsGroup("About StudyDay") {
                Text("Tasks, classes and campus weather, at a glance.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your planner is saved on this device. Tasks stay until you delete them. " +
                        "Classes repeat weekly using the device timezone. Only weather coordinates " +
                        "and units are sent to the weather provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String, subtitle: String, checked: Boolean, enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
