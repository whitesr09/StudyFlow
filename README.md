# StudyFlow

An offline Android workspace connecting chapter notes with a realistic exam plan.

## First APK — 0.1.0

- Custom ink-blue/mint interface with Paper and AMOLED themes, rounded surfaces, press feedback, and optional screen transitions. Uses Android Views without the Material 3 library.
- Create subjects and exam dates; add/edit/delete chapters with remaining study minutes and confidence.
- Import PDFs and JPG/PNG/WebP images through the Android file picker. Files are copied into private storage (100 MB maximum each).
- Read PDFs inside the app, move between pages, jump to a page, and remember the last page. Create/edit written chapter notes.
- Generate sessions of at most 45 minutes around daily availability and weekly days off. Earlier exams are prioritised, then lower-confidence chapters.
- Log actual study minutes; remaining work and future scheduling update. Adjust today's budget independently of the weekly default.
- Flag workload that cannot fit before an exam. No fake subjects, completion, or seeded history.
- No account, internet permission, analytics, or cloud dependency. Android 8.0+.

## Build from a phone

Open **Actions → Build StudyFlow APK → Run workflow → main**. After the run succeeds, open its **Artifacts** section and download **StudyFlow-0.1.0-debug**. Extract the ZIP and install `app-debug.apk`. Pushes to main also trigger builds.

The workflow runs scheduling invariant tests, builds the APK, and runs Android lint using JDK 17, Gradle 8.9, and AGP 8.7.3. For a local build with Android SDK 35 and Gradle 8.9 installed, run `gradle assembleDebug lintDebug`. This repository does not yet contain a Gradle wrapper.

This first artifact is a debug-signed testing APK, not a production release. Fresh runners can generate different debug keys, so seamless updates between builds are not guaranteed until private release signing is configured. Do not uninstall a version containing important study data to resolve a signing mismatch: uninstallation deletes app data.

## Current boundaries

- PDF highlighting, OCR, full-text search, zoom, automatic chapter mapping, backup/export, timers, notifications, and AI are not implemented yet.
- Revision time is entered manually as remaining chapter work; confidence influences scheduling order but does not yet generate spaced repetitions.
- Session progress is logged manually. The app never treats opening a note as completion.
- Planning covers up to two years. The Plan screen shows the next 100 sessions for responsiveness.
- Keep original note files. Private app storage is removed on uninstall, and this version has no backup/restore feature.

## Structure

`MainActivity.java`: screens, document import/reader, forms and interactions.
`Store.java`: atomic JSON persistence and model adapters.
`Planner.java`: deterministic Android-independent scheduling engine.
`tests/PlannerTest.java`: deadline, capacity, days-off, completion and override invariants.
