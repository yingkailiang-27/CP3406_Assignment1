package au.edu.jcu.assessment.utilityapp

import android.net.Uri
import au.edu.jcu.assessment.utilityapp.reminders.ReminderScheduler
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import au.edu.jcu.assessment.utilityapp.ui.components.AddCourseDialog
import au.edu.jcu.assessment.utilityapp.ui.components.AddTaskDialog
import au.edu.jcu.assessment.utilityapp.ui.screens.SettingsScreen
import au.edu.jcu.assessment.utilityapp.ui.screens.UtilityScreen
import au.edu.jcu.assessment.utilityapp.ui.theme.StudyDayTheme
import au.edu.jcu.assessment.utilityapp.viewmodel.PlannerViewModel
import java.time.LocalDateTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilityApp(viewModel: PlannerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    var showCourseDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCourseId by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var exactAlarmsAllowed by remember { mutableStateOf(ReminderScheduler.exactAlarmsAllowed(context)) }
    val snackbar = remember { SnackbarHostState() }
    SideEffect {
        val style = if (state.snapshot.settings.darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = style, navigationBarStyle = style
        )
    }
    val now by produceState(LocalDateTime.now(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDateTime.now()
                delay(15_000)
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exactAlarmsAllowed = ReminderScheduler.exactAlarmsAllowed(context)
        (context.applicationContext as StudyDayApplication).rescheduleReminders()
        onPauseOrDispose { }
    }
    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notificationsAllowed) {
            viewModel.updateSettings { it.copy(remindersEnabled = true) }
        } else {
            viewModel.showMessage("Notifications weren't enabled. You can try again in Settings.")
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    BackHandler(enabled = page == 1) { page = 0 }

    StudyDayTheme(state.snapshot.settings.darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (page == 0) "StudyDay" else "Settings",
                                style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (page == 0) "Your day, at a glance" else "Make it work for your day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == 0, onClick = { page = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Today") }
                    )
                    NavigationBarItem(
                        selected = page == 1, onClick = { page = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                val contentModifier = Modifier.widthIn(max = 760.dp).fillMaxSize()
                when {
                    state.loading -> CircularProgressIndicator(Modifier.padding(48.dp))
                    state.storageError != null -> Column(Modifier.padding(24.dp)) {
                        Text(state.storageError ?: "", color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::observeSavedData) { Text("Retry saved data") }
                    }
                    page == 0 -> UtilityScreen(
                        state = state, now = now, modifier = contentModifier,
                        onAddTask = { showTaskDialog = true },
                        onToggleTask = viewModel::toggleTask,
                        onDeleteTask = { deleteTaskId = it },
                        onAddCourse = { showCourseDialog = true },
                        onDeleteCourse = { deleteCourseId = it },
                        onRefreshWeather = viewModel::refreshWeather
                    )
                    else -> SettingsScreen(
                        settings = state.snapshot.settings,
                        enabled = !state.saving,
                        notificationsAllowed = notificationsAllowed,
                        exactAlarmsAllowed = exactAlarmsAllowed,
                        onOpenAlarmSettings = {
                            if (Build.VERSION.SDK_INT >= 31) context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")))
                        },
                        onChange = viewModel::updateSettings,
                        onReminderToggle = { enabled ->
                            when {
                                !enabled -> viewModel.updateSettings { it.copy(remindersEnabled = false) }
                                Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED ->
                                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                                    viewModel.showMessage("Enable StudyDay notifications in Android settings first.")
                                else -> viewModel.updateSettings { it.copy(remindersEnabled = true) }
                            }
                        },
                        onOpenNotificationSettings = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        },
                        modifier = contentModifier
                    )
                }
            }
        }

        if (showTaskDialog) AddTaskDialog(
            saving = state.saving,
            onDismiss = { showTaskDialog = false },
            onAdd = { title, deadline ->
                viewModel.addTask(title, deadline) { showTaskDialog = false }
            }
        )
        if (showCourseDialog) AddCourseDialog(
            existing = state.snapshot.courses, saving = state.saving,
            onDismiss = { showCourseDialog = false },
            onAdd = { course -> viewModel.addCourse(course) { showCourseDialog = false } }
        )
        if (deleteTaskId != null || deleteCourseId != null) {
            AlertDialog(
                onDismissRequest = { if (!state.saving) { deleteTaskId = null; deleteCourseId = null } },
                title = { Text(if (deleteTaskId != null) "Delete this task?" else "Delete this weekly class?") },
                text = { Text("This removes the item from your planner.") },
                confirmButton = {
                    TextButton(enabled = !state.saving, onClick = {
                        deleteTaskId?.let(viewModel::deleteTask)
                        deleteCourseId?.let(viewModel::deleteCourse)
                        deleteTaskId = null
                        deleteCourseId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(enabled = !state.saving, onClick = {
                        deleteTaskId = null; deleteCourseId = null
                    }) { Text("Cancel") }
                }
            )
        }
    }
}
