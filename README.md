# StudyDay

An Android utility app for CP3406 / CP5307 Assessment 1. StudyDay helps students check their next class, nearest assignment deadline and campus weather at a glance.

The app has two screens, **Today** and **Settings**, connected by bottom navigation. The Today screen uses a scrollable editorial layout with a prominent date, a next-class card and compact sections for deadlines, weather, tasks and the weekly timetable. Light and dark themes share the same layout.

## Features

### Tasks and deadlines

- Add a task with a title, deadline date and time.
- Mark tasks complete or unfinished, and delete tasks with confirmation.
- Show the earliest deadline among unfinished tasks on the homepage, including all tasks sharing that deadline.
- Prioritise overdue tasks and display their elapsed time. Completed tasks do not participate in the deadline summary.
- Display the exact deadline in the task list and the completion count above it.
- Keep older tasks without deadlines readable, with a “No deadline set” label.

### Weekly classes

- Add a course name, weekday, start/end times and optional room.
- Display the next class, or the class currently in progress, with its start/end countdown.
- Repeat classes weekly and select the next occurrence after a class finishes.
- Validate names and times, including rejecting overlapping classes on the same weekday.
- Delete classes with confirmation.

### Class reminders

- Choose a reminder lead time of 15, 30 or 60 minutes.
- Schedule the next reminder using `AlarmManager.setExactAndAllowWhileIdle()` instead of periodic polling.
- Send a notification containing the course name, start time and room; tapping it opens StudyDay.
- Record notified class occurrences to avoid duplicate reminders for the same occurrence.
- Recalculate scheduling after timetable/settings changes and restore it after device reboot, clock/timezone changes or an app update.

### Weather and preferences

- Fetch current conditions from Open-Meteo using Retrofit, including temperature, apparent temperature and weather description.
- Choose Townsville, Cairns, Singapore, Anshun or London, and Celsius or Fahrenheit.
- Refresh weather manually, with loading feedback, failure messages and a retry action.
- Toggle the timetable, weather, completed-task visibility and dark theme.
- Save tasks, courses and settings locally using DataStore Preferences.

## Running the project

1. Clone this repository or download and extract its ZIP.
2. Open the project root in Android Studio, where `settings.gradle.kts` is located.
3. Install Android SDK Platform 36 if prompted and sync the Gradle project. An internet connection is needed to download uncached build tools and dependencies.
4. Use the project's Gradle wrapper and the JetBrains JDK 21 toolchain configured in `gradle/gradle-daemon-jvm.properties`. Configure a compatible Gradle JDK in Android Studio if needed.
5. Select the **app** run configuration and an emulator or physical device running Android 8.0 / API 26 or later, then click **Run**.

Current configuration: compile/target SDK 36, minimum SDK 26, Android Gradle Plugin 9.3.3, Kotlin 2.2.10 and Gradle 9.5.0. These are the existing project versions; no dependency upgrade is needed to use the app.

Sample tasks and weekly classes are supplied on first use. They can be deleted through the normal interface. Saved changes are retained on subsequent launches. Weather requires internet access; local planning functions do not.

### Enabling class reminders

1. Open **Settings → Class reminders** and turn on **Notify before class**.
2. Allow the notification permission when requested on Android 13 or later.
3. On Android 12 or later, if **Allow scheduled reminders** is shown, open it and enable **Allow setting alarms and reminders**, then return to StudyDay.
4. Choose a lead time and add a future weekly class. Keep the device date, time and timezone correct.

Notification permission and Alarms & reminders access are separate requirements. Reminders are not scheduled when the necessary access is unavailable. A course already in progress is not notified. When reminders are enabled within a future class's reminder window, the app schedules a prompt check rather than waiting until the following week.

## Implementation

| Responsibility | Implementation |
| --- | --- |
| Interface | Kotlin, Jetpack Compose and Material Design 3; separate screen, dialog and summary components |
| UI state | `PlannerViewModel`, `StateFlow`, coroutines and lifecycle-aware state collection |
| Local data | `PlannerRepository` interface and a DataStore-backed implementation; Gson serialises the planner snapshot |
| Weather | `WeatherRepository` interface, Retrofit service, Gson response models and an OkHttp client |
| Dependency injection | Manual constructor injection through `AppContainer` and a ViewModel factory |
| Reminders | AlarmManager, BroadcastReceiver, notification channel and PendingIntent |
| Time rules | `java.time` calculations for weekly occurrences, deadline summaries and reminder windows |

UI events are passed to the ViewModel, which calls the appropriate repository. Updated state flows back to Compose. The dependency container creates shared repository instances. Tasks and deadlines remain local; weather requests send the selected city's coordinates and temperature unit to the provider.

WorkManager remains in the project to cancel periodic reminder work from earlier versions. `ClassReminderWorker` is retained for upgrade compatibility; it is no longer the active reminder scheduling mechanism.

### Source structure

Main Kotlin sources are under `app/src/main/java/au/edu/jcu/assessment/utilityapp/`.

| File or directory | Responsibility |
| --- | --- |
| `MainActivity.kt` | Android entry point and ViewModel creation |
| `UtilityApp.kt` | App scaffold, bottom navigation, dialogs and permission-related actions |
| `StudyDayApplication.kt` | Shared dependency container and reminder rescheduling |
| `model/PlannerModels.kt` | Tasks, courses, settings and time/validation rules |
| `data/` | Planner persistence and weather networking |
| `di/AppContainer.kt` | Shared dependencies and ViewModel factory |
| `viewmodel/PlannerViewModel.kt` | Screen state, saves, weather requests and error handling |
| `ui/screens/` | Today and Settings interfaces |
| `ui/components/` | Task/course dialogs and reusable selection input |
| `ui/theme/` | Shared palette, typography and shapes |
| `reminders/` | Alarm scheduling, broadcasts, notification delivery and legacy worker compatibility |

## Verification

The latest verified debug build succeeded and **32 local unit tests passed**. The tests cover deadline selection/ties and countdown boundaries, weekly class calculations, input validation, repository persistence and concurrent writes, reminder planning/deduplication, ViewModel failures/cancellation, and weather requests using MockWebServer.

To run the build and local tests from Android Studio's terminal with Java configured:

```sh
# macOS / Linux
sh ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

```bat
:: Windows
gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Manual checks on the Android emulator confirmed:

- Task creation, completion, deletion, matching deadlines, overdue display and deadline retention after restarting the app.
- Homepage deadline selection updates after completion, and light/dark layouts with vertical scrolling.
- Class-card transitions before, during and after a short test class.
- Weather failure feedback while offline and recovery through retry after reconnecting.
- A class reminder received with the alarm-based implementation, with notification tapping opening the app.

The successful notification check is one observed emulator test, not proof of delivery under every device or power-management condition. Physical-device, reboot and permission-revocation behaviour have not been fully verified. The starter's example instrumented test is not evidence of a complete UI test suite.

## Current limitations

- Courses are entered manually; there is no school timetable integration, account system or cloud synchronisation.
- Tasks are kept until deleted, and courses repeat weekly without semester dates or holiday exceptions. Editing existing items is not currently provided.
- Course start and end times must fall on the same day. Deadlines and courses follow the device's local time; there is no independent timetable timezone selector.
- The homepage clock updates approximately every 15 seconds while the screen is active, so visible status changes may lag slightly. The class countdown can briefly show “0 min” in the final fraction of a second before a boundary.
- Reminders require notification and, where applicable, exact-alarm access. Delivery can still be affected by system restrictions or force-stopping the app; reopening it recalculates scheduling.
- Weather supports a fixed city list, requires connectivity and is refreshed on initial load, relevant preference changes or manual refresh. It is not continuously updated in the background.

## Template and data sources

- Based on the lecturer-provided **MobileTech Utility App** Starter Template for this assessment. The two-screen scaffold and bottom-navigation approach have been retained and extended. The original template README states that it is provided for educational use in CP3406 and may be modified for the assessment.
- Weather data: [Open-Meteo](https://open-meteo.com/), accessed through its forecast endpoint at `https://api.open-meteo.com/v1/forecast`. The app also displays a provider attribution link.
