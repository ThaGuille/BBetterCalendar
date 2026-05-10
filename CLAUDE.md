# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BBetterCalendar is an Android productivity app combining a Pomodoro timer, habit streak tracking, and a calendar for events/tasks/reminders. Single-module Java project (`com.example.bbettercalendar`).

## Tech Stack

- **Language:** Java (source compat Java 8)
- **Architecture:** MVVM + Hilt DI + Room + LiveData
- **Min/Target SDK:** 21 / 34 — **do not upgrade `material` past `1.9.0`**
- **Build:** AGP 8.13.0, Gradle 8.13
- **Key libs:** Room 2.6.1, Hilt 2.51.1, Navigation 2.5.3, Gson 2.9.1

## Key Directories

| Path | Purpose |
|---|---|
| `app/src/main/java/.../ui/` | MVVM screen packages: `home/`, `calendar/`, `progress/`, `projects/` |
| `app/src/main/java/.../calendarEntries/` | `CalendarEntry` entity + `EventBuilder`; `AddEventActivity` |
| `app/src/main/java/.../configuration/` | `Configuration` entity, `ConfigurationManager` singleton, `InitialConfiguration` (startup logic), Hilt modules |
| `app/src/main/java/.../database/` | `AppDatabase` (v6, destructive migration), `DBConverter` (Gson type converters) |
| `app/src/main/java/.../stats/` | `Stats` entity + DAO |
| `app/src/main/java/.../helpers/` | `FormatHelper`, `ScreenHelper`, `ToolbarHelper`, toolbar listener interfaces |
| `app/src/main/java/.../popups/` | `PopupHelper` base + 6 concrete dialog fragments |
| `app/src/main/res/navigation/` | Navigation graph (Bottom Nav: Home, Progress, Calendar, Projects) |

## Build & Test Commands

```bash
./gradlew assembleDebug       # build debug APK
./gradlew assembleRelease     # build release APK
./gradlew test                # unit tests
./gradlew connectedAndroidTest # instrumented (Espresso) tests
./gradlew lint                # lint
./gradlew clean               # clean
```

## Additional Documentation

Check these files when working on the relevant area:

- [`.claude/docs/architectural_patterns.md`](.claude/docs/architectural_patterns.md) — threading model, DI wiring, DAO conventions, LiveData flow, popup creation, singleton initialization
