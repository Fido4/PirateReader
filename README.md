# PirateReader

Android-first eReader project focused on a polished EPUB experience before expanding to additional formats.

## Vision

Build a high-quality reader that works well on both:

- modern Android phones (primary target: Pixel 9 / GrapheneOS)
- e-ink Android devices (primary target: Boox Note Air 3)

The goal is a fast, distraction-light reading experience with strong typography, flexible theming, and excellent EPUB handling.

## Platform Scope

- OS support: Android 9+ (`minSdk 28`, i.e. later than Android 8)
- Form factors: phones + tablets + e-ink Android devices

Priority behaviors for e-ink:

- reduced animation / optional no-motion mode
- high-contrast themes
- paginated mode optimized for page-turn latency
- larger touch targets and stylus-friendly controls
- optional refresh-friendly UI updates (avoid unnecessary repaints)

## Format Strategy (Roadmap Order)

Start with EPUB only and make it excellent. Add formats one at a time after EPUB is stable and well-tested.

Planned formats (eventual):

- EPUB (first and only initial format)
- MOBI
- KF8 (AZW3)
- FB2
- DAISY
- CBZ
- CBR
- TXT
- PDF

## Readest-Inspired Direction (EPUB Phase)

Reference project: [Readest](https://github.com/readest/readest)

For the EPUB implementation and Android reader UX, this project will take inspiration from Readest's:

- smooth Android reading UI
- strong EPUB rendering behavior
- scroll and paginated reading modes
- theme and typography customization

Target for this project:

- support all UI themes/theme variants offered by Readest (theme parity goal)
- provide at least 5 reading fonts at launch
- include at least 2 classic terminal-style fonts

Initial font set proposal (EPUB v1):

- Literata (serif)
- Merriweather (serif)
- Bitter (serif)
- Noto Sans (sans)
- Fira Code (terminal/monospace)
- JetBrains Mono (terminal/monospace)

## Project Assets

Android launcher/icon source files are available in:

- `android_icon_assets/`

Current files include multiple launcher densities and store artwork (e.g. `ic_launcher_*` plus `icon_1024.png` / `ic_launcher_512_playstore.png`).

## Current Status (as of February 26, 2026)

Implemented in this repo:

- Android app scaffold (Kotlin + Jetpack Compose)
- Android 9+ support (`minSdk 28`)
- launcher icons imported from `android_icon_assets/`
- Gradle wrapper configuration + scripts (`gradlew`, `gradlew.bat`, wrapper jar)
- Android PR CI workflow for build/lint/unit-test checks (`assembleDebug`, `lintDebug`, `testDebugUnitTest`)
- Android-focused repo formatting/lint defaults (`.editorconfig`, Gradle-based Husky hooks replacing inherited pnpm hooks)
- Room-backed local library persistence
- EPUB import from Android file picker (`ACTION_OPEN_DOCUMENT`)
- basic "Open with"/share handling for EPUB intents (`ACTION_VIEW` / `ACTION_SEND`)
- basic EPUB metadata extraction (title/author from `container.xml` + OPF when available)
- basic cover extraction from EPUB package (stored in app-managed files when detected)
- last-opened timestamp tracking with library `Resume` action
- basic cover thumbnail rendering in library list rows (with EPUB placeholder fallback)
- "Recently Opened" and "All EPUBs" library sections using `lastOpenedAt`
- basic TOC parsing (EPUB3 NAV and EPUB2 NCX) with persisted TOC metadata/count
- expandable TOC preview UI exposure in library rows (using persisted TOC metadata)
- basic invalid/corrupt EPUB rejection with failed-import file cleanup
- explicit duplicate-import detection/status messaging (same source URI)
- duplicate-import status action to highlight the existing library entry ("Show Existing")
- stronger malformed-EPUB import classification (ZIP/container/OPF failure reasons) with clearer user-visible messages
- malformed TOC XML now degrades gracefully (book imports with empty TOC instead of failing import)
- additional malformed-EPUB import validation for missing/invalid `mimetype` entry (clearer early rejection)
- staged import progress feedback in the library UI (preparing/copying/parsing/cover/save)
- library presentation mode toggle (list/grid) with richer cover display cards
- in-memory cover thumbnail caching for library cover rendering
- recent-books UX refinement (section counts, quick-resume cap/summary copy)
- Phase 2 EPUB reader MVP route: `Resume` opens a `WebView`-based reader with TOC + typography/theme controls
- EPUB spine chapter loading into the reader `WebView` (with reader fallback HTML on load failure)
- EPUB `WebView` resource resolution for package-relative CSS/images/fonts via ZIP-backed request interception
- reader position persistence (chapter ZIP path + optional anchor fragment + scroll X/Y + page mode) plus a persisted locator payload (viewport/page metrics + scroll extents + visible-text hint) saved on reader exit and used by `Resume` for layout-tolerant restore
- TOC navigation wiring in the reader (chapter reload + fragment jump attempts)
- reader theme/font/text-size controls with WebView CSS injection for loaded EPUB chapters
- richer reader typography controls (line height, margins, alignment) via the same WebView CSS injection path
- paginated-mode WebView reading mode (CSS columns + prev/next viewport navigation controls)
- improved TOC navigation reliability (scripted anchor jumps + selected/current TOC sync hints)
- spine chapter navigation controls (`Prev Chapter` / `Next Chapter`)
- internal EPUB chapter-link interception so chapter transitions route through reader state (improves TOC/current sync)
- viewport metrics capture for the reader (`page/pageCount`, scroll offsets/extents, a best-effort visible anchor, a TOC-anchor-aware visible anchor hint, and a visible-text hint) to support page indicators, more robust resume locators, and TOC/current-location syncing
- full Readest `THEME_LIST` reader theme preset inventory implemented (11 presets with matched background/foreground pairs) and documented in the parity checklist
- reader TOC panel now exposes the full parsed TOC in a scrollable panel (no 12-entry scaffold cap)
- initial Phase 3 e-ink optimization mode in the reader (explicit toggle, paginated preference, no-motion/reduced-effects CSS, lower viewport-metrics polling cadence, a Balanced/High contrast preset control, and mode-aware typography tuning defaults via `Reset Type`)
- initial Phase 3 reader chrome theme tuning pass (reader top bar + control/status panel + TOC panel + reader control chips now derive colors from the active reader theme for better visual parity with the WebView page)
- initial Phase 3 offline dictionary groundwork (reader now discovers and loads `library/dictionary.tsv` and reports dictionary readiness for upcoming long-press lookup UX)
- EPUB MVP technical design doc
- Readest theme parity checklist doc
- automated tests (JVM unit tests + Android instrumentation smoke test)

Current app state:

- project opens/builds as an Android application shell
- library screen supports importing EPUB files into app-managed storage and listing imported books
- library records persist cover path (when detected) and last-opened timestamps
- library records persist basic TOC metadata (entry count + serialized entries for future reader use)
- library UI shows basic cover thumbnails/placeholders, recent/all sections, and expandable TOC previews
- invalid/corrupt EPUB imports are rejected with a user-visible status message (and temporary copied files are cleaned up)
- duplicate imports by the same source URI return an "Already in library" warning instead of a duplicate insert
- duplicate-import warning can highlight the existing library row via a "Show Existing" status action
- malformed EPUB import failures now report clearer causes for common ZIP/container/OPF issues
- malformed EPUB import validation also catches missing/invalid `mimetype` entries before container parsing
- malformed TOC XML does not block import; TOC metadata falls back to empty for that book
- library import flow shows staged progress feedback while the EPUB is copied/parsed/saved
- `Resume` now opens a Phase 2 EPUB reader MVP screen with a `WebView` reading surface and reader controls
- reader now loads and displays EPUB spine chapters in the `WebView`
- reader now resolves package-relative CSS/images/fonts for loaded EPUB chapters
- reader now saves/restores chapter + anchor + scroll + page mode and a persisted viewport/page-metrics locator with a visible-text hint for layout-tolerant resume
- reader now exposes a TOC panel and can jump to TOC targets by reloading chapters (with fragment jump attempts and scripted anchor jumps)
- reader now supports theme/font/text-size controls with injected reader CSS applied to loaded EPUB chapter documents
- reader now supports line-height/margin/alignment controls using the same injected reader CSS path
- reader now supports both scroll mode and paginated mode (column-based WebView pagination + Prev/Next viewport navigation controls)
- reader TOC now shows selected/current TOC state for better navigation reliability and current-location hints
- reader now exposes spine chapter navigation controls (`Prev Chapter` / `Next Chapter`)
- reader now intercepts internal EPUB chapter links and routes chapter transitions through reader state (instead of free-floating WebView navigation)
- reader now captures viewport metrics (including paginated page index/count, scroll extents, a best-effort visible anchor, a TOC-anchor-aware visible anchor hint, and a visible-text hint), persists them as a locator on reader exit, and uses them for page status + tighter TOC/current-location syncing (including TOC-anchor-preferred scroll updates, then exact/fuzzy/chapter fallback) with text-hint-assisted restore fallback
- reader theme picker now includes the full inventoried Readest theme preset set (`default`, `gray`, `sepia`, `grass`, `cherry`, `eye`, `night`, `gold`, `berry`, `bw`, `wbb`) with matched background/foreground values
- Phase 2 MVP is complete; remaining reader hardening/polish/app-shell work is tracked in Phases 3-4
- reader now includes an explicit e-ink optimization mode toggle (prefers paginated mode, e-ink-only theme cycling, no-motion/reduced-effects CSS, less frequent viewport metrics polling to reduce UI churn, a Balanced/High e-ink contrast preset, and auto-applies tuned typography defaults when enabled from the baseline reader settings)
- reader now includes a `Reset Type` control that reapplies mode-aware recommended typography defaults (phone vs e-ink)
- reader chrome now uses active-theme-derived colors for the top bar, control/status panel, TOC panel, and reader control chips (first Phase 3 chrome/theming pass)
- reader now discovers an offline dictionary file at `library/dictionary.tsv` (tab-delimited `term<TAB>definition`) and surfaces readiness/validation status in the reader for upcoming long-press word lookup
- launch scope remains EPUB-only by design

## MVP (EPUB-Only) Requirements

### Core Reader

- open local `.epub` files from Android file picker / share intent
- library view for imported EPUB files
- resume reading position reliably
- paginated mode and scrolling mode
- font size, line height, margins, alignment controls
- theme switching (including e-ink-friendly themes)
- table of contents navigation
- chapter navigation
- text selection (phase-appropriate; highlights/notes can follow)
- full-text search within current EPUB (stretch goal for EPUB v1)

### Quality Targets

- fast open for typical EPUBs
- no crashes on malformed but common EPUB variants (graceful failure where needed)
- stable state restore after app kill/backgrounding
- good performance on e-ink hardware

## Technical Direction (Initial)

This README defines product direction only, but the implementation should prioritize:

- Android-native app architecture
- EPUB parser/rendering stack chosen for correctness first, then performance
- renderer abstraction so additional formats can be added later without rewriting reader UI
- test corpus of real-world EPUBs (reflow, fixed-layout, images, footnotes, TOC edge cases)

Implementation docs:

- EPUB MVP technical design: `docs/epub-mvp-technical-design.md`
- Readest theme parity checklist: `docs/readest-theme-parity-checklist.md`

## Build and Test (Current Scaffold)

### Toolchain Snapshot (Current Repo)

- Gradle wrapper: `9.2.1`
- Android Gradle Plugin: `9.0.1`
- Kotlin: `2.2.10`
- KSP plugin: `2.3.2`
- Room: `2.6.1`

### Prerequisites

- Android SDK installed (local `sdk.dir` expected in `local.properties`)
- Java 17
- Android emulator/AVD (example used: `Pixel_9`)

### Unit Tests (JVM)

```bash
./gradlew testDebugUnitTest
```

### Lint (Android Lint)

```bash
./gradlew lintDebug
```

### Local CI-Equivalent Check

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest
```

### Formatting Baseline

- Repo formatting conventions are defined in `.editorconfig` (Kotlin/Gradle/XML/YAML/JSON).
- Use Android Studio / IntelliJ "Reformat Code" for Kotlin/XML formatting before commits.

### Instrumentation Tests (Headless Emulator)

Start a headless emulator (example):

```bash
/home/developer/Android/Sdk/emulator/emulator -avd Pixel_9 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
```

Wait for boot completion:

```bash
adb wait-for-device
adb shell getprop sys.boot_completed
```

Build test APKs:

```bash
./gradlew assembleDebug assembleDebugAndroidTest
```

Install and run the instrumentation test:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.piratereader.MainActivityTest \
  com.piratereader.test/androidx.test.runner.AndroidJUnitRunner
```

Notes:

- `./gradlew connectedDebugAndroidTest` may hang in some environments due UTP/ADB orchestration; direct `adb shell am instrument` is an acceptable fallback for local headless validation.
- Current instrumentation coverage includes a UI smoke test for the app shell/library import UI/recent-all section layout plus a seeded EPUB reader save/restore flow test (enter reader via `Resume`, persist chapter/anchor/scroll position plus page mode and a serialized scaffold locator payload, reopen with persisted position available to the reader state).
- Current JVM coverage includes launch-scope/font policy checks plus EPUB metadata/cover/TOC parsing tests (including malformed ZIP/container/OPF, missing/invalid `mimetype`, and malformed TOC fallback cases), EPUB spine document loader tests (including preferred-chapter resume selection and adjacent-chapter navigation metadata), EPUB WebView resource resolver tests, TOC codec round-trip tests, TOC navigation target resolver tests, TOC preview formatter tests, reader fallback HTML builder tests, reader WebView style injector tests (including paginated/alignment CSS output), reader viewport metrics codec tests, reader position locator codec tests, reader TOC selection resolver tests, and library status/dedupe/progress message formatting tests.
- Build compatibility note: with the current Gradle/AGP/KSP/Room versions, Room KSP codegen was unstable for `suspend` DAO methods in this project; DAO methods are intentionally non-`suspend`, and repository/database work is dispatched on `Dispatchers.IO`.
- `gradle.properties` compatibility cleanup was validated one flag at a time (`assembleDebug` checks). Most legacy AGP compatibility flags were removed; only `android.builtInKotlin=false` and `android.newDsl=false` are still required with the current AGP 9.0.1 + explicit Kotlin plugin/build-script setup.
- AGP 9 still emits deprecation warnings for those two remaining compatibility flags; this is expected until a follow-up build-script migration removes the need for them.

## Roadmap

### Phase 0 - Project Setup

- initialize Android app project in this folder
- configure Android 9+ support (`minSdk 28`)
- import launcher icons from `android_icon_assets/`
- choose app architecture and module layout (reader, library, parsing, settings)
- set up CI, linting, formatting, and baseline tests

Phase 0 status:

- Android scaffold, minSdk, icons, and baseline tests are now in place
- Android CI/lint automation is now in place for PRs (`assembleDebug`, `lintDebug`, `testDebugUnitTest`)
- formatting conventions and local Gradle-based Husky checks are configured for this Android repo
- Phase 0 status: complete

### Phase 1 - EPUB Import + Library

- file picker and "Open with" flow for `.epub`
- persistent local library metadata
- cover extraction and basic library list/grid
- recent books and last-opened tracking
- error handling for invalid/corrupt EPUB files

Phase 1 status:

- Implemented: file picker import, basic `ACTION_VIEW`/`ACTION_SEND`, Room library persistence, app-managed file copy, basic library list UI
- Implemented (basic): EPUB title/author metadata extraction from OPF when present, cover extraction when detectable, last-opened tracking + Resume placeholder action, NAV/NCX TOC parsing + persistence
- Implemented (UI/basic): recent/all library sectioning, cover thumbnail/placeholder rendering in list rows, expandable TOC preview exposure in library rows
- Implemented (error handling/basic): invalid/corrupt EPUB imports are rejected and failed copied files are cleaned up
- Implemented (error handling/improved): common malformed EPUB failures now surface clearer ZIP/container/OPF-specific messages; malformed TOC XML degrades to empty TOC instead of blocking import
- Implemented (error handling/hardening): import validation now rejects missing/invalid EPUB `mimetype` entries with explicit user-facing errors
- Implemented (dedupe/basic): duplicate imports by same source URI surface a warning status message and reuse the existing library record
- Implemented (dedupe UX/basic): duplicate-import status includes a "Show Existing" action that highlights the existing library row
- Implemented (polish): staged import progress UI, list/grid library presentation mode, in-memory cover thumbnail caching, and recent-books section count/quick-resume UX refinement
- Phase 1 status: complete for the current EPUB import/library milestone; remaining work has moved to Phase 2+ reader/polish phases

### Phase 2 - EPUB Reader MVP (Dial It In)

- EPUB parsing + chapter/document loading
- reading surface with scroll mode + paginated mode
- reading progress + position persistence
- TOC navigation
- typography controls (size, spacing, margins, alignment)
- 5+ fonts available, including 2 terminal-style fonts
- theme system with Readest-inspired theme parity goal
- document the exact Readest theme list/color presets to match (phase task before final theme implementation)

Phase 2 status:

- Implemented: `Resume` opens the EPUB reader MVP (`WebView` reading surface + reader controls)
- Implemented: EPUB spine chapter loading, chapter navigation, and internal chapter-link interception
- Implemented: ZIP-backed resource interception for package-relative CSS/images/fonts
- Implemented: scroll mode + paginated mode viewport navigation
- Implemented: reader position persistence (chapter/anchor/scroll/page mode) plus a viewport/page-metrics locator payload with visible-text hint for more layout-tolerant restore
- Implemented: TOC panel navigation, scripted anchor jumps, and selected/current TOC sync hints
- Implemented: typography controls (font, size, line height, margins, alignment) via injected reader CSS
- Implemented: Readest theme inventory task + reader theme preset parity baseline (11 inventoried Readest presets with matched background/foreground pairs)
- Implemented: full TOC panel exposure (scrollable panel, no scaffold entry cap)
- Phase 2 status: complete for the EPUB reader MVP milestone; remaining robustness/performance/polish work continues in Phase 3

Exit criteria:

- daily-driver usable on Pixel 9 and Boox Note Air 3
- stable on a representative EPUB test set
- no major layout regressions in common books

### Phase 3 - EPUB Polish (Perfect First)

- improve typography defaults and theme tuning
- e-ink optimization pass (reduced animations, contrast presets, repaint minimization)
- selection UX improvements
- long-press word lookup (offline dictionary/quick definition first; app should load a user-provided dictionary file placed in the root library folder, with optional Wikipedia follow-up later)
- bookmarks and highlights
- in-book search
- footnotes, images, links, and edge-case navigation hardening
- import performance optimization for large EPUBs

Phase 3 status:

- Started: initial e-ink optimization mode toggle is implemented in the reader (reduced motion/effects CSS + paginated preference + reduced viewport-metrics polling cadence + Balanced/High contrast preset control) and now includes mode-aware typography tuning defaults (`Reset Type`, plus safe auto-apply on first e-ink enable from baseline settings), an initial reader chrome theme tuning pass (top bar + control/status panel + TOC panel + reader control chips), and offline dictionary file discovery/loading groundwork (`library/dictionary.tsv`)
- Remaining: typography/theme tuning, selection UX (including offline long-press dictionary/quick lookup backed by a dictionary file in the root library folder), bookmarks/highlights, search, and broader EPUB edge-case hardening

### Phase 4 - App Shell + Navigation UX

- introduce top-level app shell with `Home`, `Library`, and `Settings`
- keep reader as full-screen route (tabs hidden while actively reading)
- Home: currently-reading hero, recent items, quick actions
- Library: improved visual design plus search (title/author), sort, and list/grid
- Settings: functional essentials and About area (move current static informational cards here)
- dictionary source policy: fixed root file `library/dictionary.tsv` with optional override support

Phase 4 status:

- planned (starts after Phase 3 EPUB polish is complete)

### Phase 5 - Format Expansion Framework

- define shared document model / reader interface
- isolate format-specific parsing/rendering adapters
- add regression tests to ensure EPUB quality is not degraded by new formats

### Phase 6+ - Additional Formats (One at a Time)

Recommended order after EPUB:

1. TXT (easy parser, quick UX validation)
2. FB2
3. CBZ
4. CBR
5. PDF
6. MOBI
7. KF8 (AZW3)
8. DAISY

Notes:

- PDF likely needs a separate rendering path/UI behavior from reflowable formats
- MOBI/KF8 may require more format-specific parsing work and test coverage
- DAISY should be planned with accessibility-first requirements

## Non-Goals (Initial Phases)

- sync across devices
- cloud library
- DRM support
- all formats at once
- advanced annotation system before EPUB core is stable

## Development Principles

- EPUB-first until the experience is excellent
- optimize for real reading sessions, not demos
- preserve battery and responsiveness on e-ink devices
- add features only when they do not regress core reading quality

## Inspiration / References

- Readest (UI/UX and feature inspiration): https://github.com/readest/readest
- Readest website (product presentation/themes customization direction): https://readest.com/
