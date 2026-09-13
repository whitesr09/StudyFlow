# StudyFlow

An offline Android workspace connecting chapter notes with a realistic exam plan.

## Study kit edition — 0.6.0

UI fixes:
- Cards now reserve space above and below their surfaces, including the Today momentum card after the exam shortcut.
- Minimal headings use bold type. Study activity explains its units and rolling seven-day range, with an actionable empty state. Populated charts support day/session drill-down and an accessible daily-totals list.
- Both Settings designs share a centered Follow panel with labelled social icons, a divider, version and creator credit.
- Dialog surfaces are height-bounded during measurement instead of resizing after their first frame; one window animation replaces the former competing animations. Review and quiz sessions reuse their window across questions. Answer reveal reserves space, and same-screen refreshes avoid restarting the page entrance.
- PPTX still uses a visual HTML preview, not PDF conversion. Reader labels/details make this explicit; imported originals remain unchanged. Office preview fidelity limits below still apply.

Five new offline features:
1. Assignments with course/details, deadlines, overdue/today states, completion/reopen, edit/delete and paginated lists.
2. Quick-note inbox with edit/delete and atomic transfer into chapter notes.
3. A separate Monday–Sunday weekly study target based on actual logged minutes.
4. Shuffled practice quizzes from up to 20 saved flashcards, answer reveal and self-assessed scores saved to Insights. They do not change spaced review dates or log study minutes; unfinished quizzes are not recorded.
5. Needs revision: chapters with Need help/Okay confidence, sorted by confidence then exam date, with direct notes/cards/confidence actions. No revision work is silently added to the plan.

New collections initialize without clearing existing data. New feature mutations use atomic persistence with rollback. CI includes deadline/week-boundary checks and Android emulator smoke checks for spacing, centered footer, review window reuse, feature forms, quiz results, chart states and exact HEX. Screenshots are uploaded as StudyFlow-UI-checks. An emulator pass does not establish bug-free behavior on every phone.

## Visual workspace edition — 0.5.0

- Minimal and Minimal Dark now select distinct screen structures: an editorial dashboard with an original vector study illustration, two-column action tiles, a seven-day animated chart of real study logs, compact Library cards, a two-week date strip in Plan, grouped settings rows and four icon navigation destinations. All screens share lighter typography, outlined surfaces, line icons and motion that respects the reduced-motion setting.
- Custom HEX stays exact on filled buttons, chart bars, rings and artwork. A separate shade is used for readable foreground text; black/white button labels are chosen for contrast. Tinted surfaces intentionally remain lighter/darker than the raw accent.
- DOCX opens a formatted visual preview with run styling, paragraph alignment, tables and embedded raster images. PPTX opens slides in presentation order with slide dimensions, positioned text/shapes/images, basic tables, theme colors, and basic layout/master artwork. Pinch zoom and slide controls are provided. Documents stay offline; JavaScript, external images, file/content access and network loads are disabled.
- **This is a visual preview, not a complete Office rendering engine.** DOCX pagination, floating objects, fonts, styles inherited through complex chains, numbering, equations, charts/SmartArt, grouped shapes, cropped images and some master effects may differ or be omitted. Open the untouched original using the reader's external-viewer icon for full-fidelity viewing in a compatible installed Office app. Legacy DOC/PPT use this route directly. Exporting the original as PDF also preserves its static layout in StudyFlow's PDF reader.
- Preview bounds: 24 MB of expanded content and at most 300 slides; large or unsupported files can still be opened externally. Visual position is stored separately from previous text-reader positions.
- CI tests include Office formatting, embedded images, tables, slide geometry/order, Unicode, HTML escaping and external-entity/image blocking, as well as exact-accent preservation and contrast.

## Active recall edition — 0.4.0

Five new offline features, using the existing minimalist themes and rounded forms:

1. Chapter flashcards with editable questions/answers, a reveal step, and a due-review queue. Again schedules tomorrow; Good doubles the previous interval (initially two days); Easy triples it (initially four days), capped at 365 days. Reviews never silently log study minutes. Deleting a chapter removes its cards.
2. Workspace search across subjects, chapters, written-note content and attachment names, with direct open actions and a 50-result limit. Attachment contents remain searchable inside the reader.
3. Exam countdowns ordered by exam date with today/past states, chapter completion and remaining planned minutes.
4. A configurable daily goal separate from planning availability, with a progress bar and consecutive study-day streak. Any positive logged study counts; an unfinished today retains yesterday's streak.
5. Session history showing actual logged minutes, dates, subjects and links back to chapter notes, with 30-row pagination. Deleted chapters remain represented in history.

Existing data is retained; the cards collection is initialized automatically. No new permissions, cloud services or Material 3 dependency. Flashcards and goal changes use atomic save with rollback on write failure. Android device visual testing remains required.

## Minimal edition — 0.3.0

- All app confirmation forms, including focus discard and deletion, now use the themed dialog engine. Confidence is a themed choice sheet; exam dates use a themed month calendar with future-date validation. Form errors appear inline.
- Dedicated Appearance screen with a live preview, Minimal and Minimal Dark themes inspired by the supplied reference, original themes, eight color presets, custom hex color with preview, reduced motion, and Android palette matching on Android 12+. Android mode reads the system accent palette; it does not read wallpaper files or require wallpaper/storage permission. System colors refresh on return to the app. Older versions retain theme/custom accents.
- Foreground accent shades adapt for at least 4.5:1 contrast against both surfaces and backgrounds, with contrasting button labels. 20,480 sampled combinations are tested.
- Five additions: (1) PDF/image pinch, double-tap and toolbar zoom with panning; (2) three recently opened documents/notes on Today and Library; (3) pin subjects to the top of Library; (4) actual logged study minutes over seven days and all-time subject totals; (5) export saved written notes to UTF-8 text with Android's save picker.
- Raster zoom enlarges the existing bounded render, so extreme zoom is not a new high-resolution PDF render. Reset to Fit page for page-swipe gestures.

### Features retained from 0.2.0

- Themed rounded dialogs, filled input fields, visible focus borders, compact reader toolbar, vector Settings icon, gradient panels, press animation, animated momentum ring and reduced-motion support. Custom Android Views, without Material 3.
- Swipe left/right to change reader pages. Vertical scrolling stays inside a page; an additional upward swipe begun at its bottom goes next, and a downward swipe begun at its top goes back. Buttons and page/section jump remain available.
- All file types can be imported through Android's file picker, up to 100 MB each, copied privately for offline access.
- Internal reader: PDF, Android-decodable images, TXT, Markdown, CSV, TSV, JSON, XML, HTML, DOCX, PPTX, XLSX, ODT, ODS, ODP and EPUB. In 0.2–0.4, Office/OpenDocument/EPUB files were **reflowed text**; 0.5 adds visual DOCX/PPTX previews described above. Other structured formats remain reflowed, not original-layout rendering: embedded images, formulas/styles, chart visuals and rich formatting are not reproduced. XLSX displays cell references and stored values, including cached formula results; it does not recalculate formulas. Text sections are not original document page numbers. UTF-8 and BOM-marked UTF-16 text are supported.
- Legacy DOC/PPT/XLS, RTF, encrypted files, unsupported formats, image-only Office files and documents exceeding the 4 MB expanded-text limit use **Open with another app**, requiring a compatible installed viewer. Imported originals remain intact. External viewers receive temporary read access to only the selected attachment.
- Saved reading position, per-document bookmarks, search within readable document text, and adjustable reading text size. PDF/image text search and OCR are not included.
- Search chapter notes by title or written content. Attachment contents are searched from inside the text reader.
- Configurable focus timer with pause/resume. It pauses when closed or when the app enters the background, stores remaining time and can be resumed from Today. After one minute, finish and confirm actual study time to update chapter progress. Opening a file or completing a timer does not silently log progress.
- Subjects, exam dates, chapters, confidence, adaptive planning, weekly availability, daily overrides and actual study logs remain available with existing data.
- Settings includes Instagram and WhatsApp icons linking to the creator, followed by `MADE  BY  N S H D`.
- Offline app with no internet permission, accounts, analytics or cloud dependency. Social links open the browser or corresponding installed app. Android 8.0+.

## Build from a phone

Open **Actions → Build StudyFlow APK → Run workflow → main**. After the run succeeds, open its **Artifacts** section and download **StudyFlow-0.6.0-debug**. Extract the ZIP and install `app-debug.apk`. Pushes to main also trigger builds.

The workflow runs scheduling invariants and accent-contrast tests plus document parser tests (Office/EPUB ordering, Unicode, malformed inputs, XML entity rejection and expansion limits), builds the APK, and runs Android lint using JDK 17, Gradle 8.9, and AGP 8.7.3. For a local build with Android SDK 35 and Gradle 8.9 installed, run `gradle assembleDebug lintDebug`. This repository does not yet contain a Gradle wrapper.

This artifact is a debug-signed testing APK, not a production release. Fresh runners can generate different debug keys, so seamless updates between builds are not guaranteed until private release signing is configured. Do not uninstall a version containing important study data to resolve a signing mismatch: uninstallation deletes app data.

## Current boundaries

- PDF highlighting, OCR, PDF search, automatic chapter mapping, full app backup/restore, background timers, notifications and AI are not implemented yet.
- Revision time is entered manually as remaining chapter work; confidence influences scheduling order but does not yet generate spaced repetitions.
- Session progress is logged manually. The app never treats opening a note as completion.
- Planning covers up to two years. The Plan screen shows the next 100 sessions for responsiveness.
- Keep original note files. Private app storage is removed on uninstall, and this version has no backup/restore feature.

## Structure

`MainActivity.java`: screens, document import/reader, forms and interactions.
`DocumentText.java`: bounded offline document text extraction.
`AttachmentProvider.java`: read-only attachment sharing.
`FlowIcon.java`: scalable interface/social icons.
`Store.java`: atomic JSON persistence and model adapters.
`Planner.java`: deterministic Android-independent scheduling engine.
`tests/PlannerTest.java`: deadline, capacity, days-off, completion and override invariants.

### 0.7 navigation and document handling

- Back arrows and Android Back follow the visited section/document history. Workspace scroll position is restored and history survives Activity recreation.
- Telegram joins the centered Connect footer. Minimal themes include an interactive Learn / Recall / Review diagram driven by chapter and card counts.
- Office files first offer opening the unchanged original in a compatible installed viewer. The homegrown preview is explicitly optional and simplified; it is not a complete Office renderer.
- Attach a PDF exported by your Office app for an accurate static page layout inside StudyFlow. This does not convert the original or preserve slide animations. Imported PDFs are validated and limited to 100 MB.
- Permanent release signing is configured with a pinned certificate. See [SIGNING.md](SIGNING.md) for the one-time secret setup and existing debug-build migration limitation. PR artifacts remain debug test builds; manual builds additionally produce signed releases after validation.
