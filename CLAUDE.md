# Reinminder — notes for Claude

Personal Android reminder app (Kotlin, Jetpack Compose, Material 3). The owner talks in Chinese; reply in Chinese.
Design decisions live in the claude.ai Project "Calendar" (doc `claude/Reinminder-设计决策.md`); the UI follows the
"Reinminder 界面" design canvas, which reuses the CrossSync palette. Same build/release setup as qymartin/CrossSync.

## Rules
- Privacy: never add the INTERNET permission or any network/analytics/crash-reporting library. The manifest removes
  INTERNET explicitly. Cloud backup and device transfer are excluded (`res/xml/data_extraction_rules.xml`).
- Never commit personal data (exported backups, screenshots with real events). The repo is public.
- Languages: Chinese is the default (`res/values`), English in `res/values-en`. Every new string goes in both.
  Value-dependent phrases (dates, reminder labels) live in `logic/Formats.kt` (`Fmt`). The language is the app's own
  setting (`Prefs.lang`), applied with `Lang.wrap` in activities and receivers — not the phone's language.
- Every change to events goes through `alarm/Repo`, which reschedules alarms.
- Completing (1.2): one-off tasks are either archived (`done`, kept under 已完成) or "done and delete"
  (`done` + `purge`: struck through today, removed after midnight by `EventStore.purge`, run from
  `Alarms.rescheduleAll` and a midnight alarm). Repeating tasks complete one occurrence: an archived
  one-off copy is added and the series advances. Reminders default to "at time" (offset 0).
  Alarm-style = after the event time, a normal reminder every 10 minutes until done or moved (`nextNag`).
- Subtasks (1.3): `Event.parentId`, one level only. Deleting a task deletes its subtasks (`Repo.delete`).
  1.4: long-press a row and drop it on another to nest it (`ui/NestDrag.kt`, `Repo.makeSubtask`).
- Pinned (1.5): important + not done + first reminder reached (`pinnedAt`, `firstReminder`) -> an ongoing notification per task (same id as
  its reminder), kept in sync by `Notifier.syncPinned` from `Alarms.rescheduleAll`; a silent "pin" alarm at the first
  reminder when no reminder alarm falls then; swiping it away (Android 14+) re-pins it via the delete intent.
- List rows show title · [topic badge] · time, plus the date where the section doesn't give it. No repeat or
  reminder text in lists; those are on the task's page.

## Layout
- `data/` model, JSON codec, file stores (`events.json`, `topics.json` in private storage), prefs, language.
  Topics (主题) are user-defined; `Event.topic` holds a topic id. 1.0's fixed category keys are the ids of the seeded topics.
- `logic/` pure Kotlin, unit-tested: repeat rules (`EventLogic.kt`), quick-add parser (`QuickParser.kt`), formats
- `backup/` export/import file, optional password encryption
- `alarm/` exact alarms, notifications (done / snooze actions), boot/update/time-change receivers
- `ui/` Compose screens: Today, Schedule, Detail, Edit (one page for new and edit), Settings, topic picker/manager, AlarmActivity

## Build and release
- Every push to `main` runs unit tests, builds a signed APK in GitHub Actions and publishes Release `v<VERSION>.<run_number>`. The owner installs and updates through Obtainium.
- Versioning: `VERSION` holds the feature version (`1.0`, `1.1`, ...). Bump it when a release adds features; the last number is the CI run number (also the versionCode).
- Read CI results from the `ci-logs` branch: `git fetch origin ci-logs && git show FETCH_HEAD:ci-status.txt` (and `build.log`).
- Never print the signing password (no `set -x` in the signing step). Keystore: `signing/release.p12`, secret `SIGNING_PASSWORD`.
- Commits that only change docs: put `[skip ci]` in the message.
