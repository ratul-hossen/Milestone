# Milestone: Study Progress Tracker

An **offline** Android app for students to track study time per subject, built from the Figma design
*"Milestone — Study Progress Tracker"*.

**Stack:** Kotlin · Jetpack Compose (Material 3) · MVVM · Room (local SQLite) · Navigation Compose · on-device ML.
The app has no network permission, so it never sends your data anywhere. The only copy that can leave the phone is Android's
own system backup (Google account backup / device transfer), and only if you have that switched on in Android settings.

## Features
- **Your own subjects:** name, colour tag (8 colours, contrast-checked), weekly goal. Add, edit or delete at any time.
- **Log sessions** manually (subject, minutes, date, notes), with validation, or with the **Focus timer**
  (Pomodoro 25/50 min or stopwatch, pause/resume, ongoing notification, alert at the end).
  The timer state lives in Room, so it survives the app being closed or killed.
- **History:** filter by subject and date range, swipe to delete with Undo, session details, edit.
  Tapping a subject opens its own page with its full history.
- **Timer details:** a Pomodoro saves itself when it ends, even if the app is closed. Sessions are dated by the day
  they started. After *Finish & save* the session opens so you can add notes. Discard asks for confirmation.
- **Progress:** study-time chart (1W–ALL), comparison with the previous period, streaks, daily average, goal weeks,
  and a per-subject weekly watchlist.
- **On-device ML, each with a "Why?" explanation in the app** (details below).

## Architecture
```
ui/            Compose screens + ViewModels (one per screen), shared components, theme (Figma tokens)
data/db        Room entities, DAOs, database (schema exported to app/schemas)
data/repo      StudyRepository (single source of truth), StudyMath (streaks, summaries)
ml/            WeeklyForecaster, SubjectRecommender, DurationSuggester (+ config from assets/ml/*.json)
timer/         FocusTimer (Room-backed timer, notification, Pomodoro alarm)
util/          AppLog (Logcat tags), formatting
```
Dependencies are wired by hand in `MilestoneApp` (no DI framework needed at this size).

## Machine learning (all in `ml/`, notebooks run top to bottom)
| Notebook | What it does | Data | Held-out result |
|---|---|---|---|
| `00_dataset_preprocessing.ipynb` | Downloads and cleans the **Open University Learning Analytics Dataset** (OULAD, CC-BY 4.0) | real | 21,471 enrolments kept, every cleaning rule documented |
| `01_weekly_goal_forecast.ipynb` | Ridge regression: minutes you'll reach by Sunday | OULAD | 38.6% relative error vs 50.0% "keep current pace" |
| `02_next_subject_recommender.ipynb` | Logistic regression P(study subject today), calibrated, used as *need = goal left × (1 − p)* | OULAD (412 multi-module learners) | AUC 0.795, calibration error 0.007 |
| `03_session_duration_knn.ipynb` | Session-length suggestion: k-NN vs simple baselines | simulated (see limitations) | per-subject median won, so it ships |

Re-run everything: `cd ml && python build_notebooks.py && jupyter nbconvert --to notebook --execute --inplace 0*.ipynb`.
The notebooks write the model parameters to `app/src/main/assets/ml/*.json`, which the app reads at start-up.

**Honest limitations**
- OULAD records *clicks*, not minutes. The forecast and recommender only use unit-free or unit-linear features, so the weights
  transfer, but clicks are still only a proxy for study time.
- OULAD is distance-learning students from 2013–14, so the forecast re-fits itself to your own data after 3 full weeks
  (a threshold chosen on validation students). On held-out students the effect is neutral at 3 weeks and a small
  improvement with more history.
- The forecast is hidden for the first 14 days, because the model never saw shorter histories.
- The recommender does **not** beat "same subject as yesterday" at *guessing* what you'll study (top-1 79.7% vs 80.6%).
  It is therefore used only for its calibrated probabilities, to find the subject you're most likely to neglect.
  That decision rule is a transparent product choice: it can't be scored offline, because there's no ground truth for what a
  student *should* have studied.
- No public dataset of timed study sessions could be downloaded (BLS blocks scripted ATUS downloads), so the duration
  method was compared on simulated logs. The winner (per-subject median) uses only your own data on the phone.

## Known gaps (not done yet, by choice of scope)
- Weekly goals aren't stored per week, so "weeks at goal" in Stats uses today's goals for past weeks too (labelled that way).
- No reminders, CSV export, subject archiving, dark theme, or start-time field for manual sessions.
- A stopwatch left running is capped at 10 h. Pomodoro alarms aren't re-scheduled after a phone reboot (the timer itself
  survives, and you can still finish it).
- Room schema is version 1. Add AutoMigrations and a migration test before changing any table.

## Logcat (demo / debugging)
All app logs use four tags:
```
adb logcat -s Milestone.UI Milestone.DB Milestone.ML Milestone.Timer
```
or filter `tag:Milestone` in Android Studio. `Milestone.ML` prints every model's inputs, weights and decision, for example
`Recommender: Biology(p=0.14, left=1.00, need=0.86), ... -> 'Biology'`.

## Build
Android Studio (AGP 9.4, Gradle 9.6, JDK 17+), minSdk 26, target 37.
```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # model + statistics unit tests
```

## License
Copyright (C) 2026 Ratul. Licensed under the **GNU Affero General Public License v3.0 only** (see `LICENSE`),
with **additional attribution / no-misrepresentation terms** under AGPL Section 7 (see `NOTICE`).

Anyone who publishes this app or a modified version must:
- release their full source code under the same license,
- keep the credit "Based on Milestone by Ratul", and
- not present it as their own original work or use the Milestone name or logo.

Third-party components (font, dataset, AndroidX) keep their own licenses, listed in `NOTICE`.

## Documentation
Full Bangla technical write-up (IEEE style): `docs/Milestone_Documentation_BN.md` and `docs/Milestone_Documentation_BN.pdf`.
Rebuild the PDF with `bash docs/build_pdf.sh`.
