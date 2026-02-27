# EPUB MVP Technical Design (Android 9+)

## Scope

This document defines the implementation plan for PirateReader's first production-quality milestone:

- Android-native app (`minSdk 28`)
- EPUB-only support
- optimized for both OLED phones and Android e-ink devices
- Readest-inspired UX quality bar (themes, typography, reading controls)

Format expansion (MOBI, AZW3, FB2, DAISY, CBZ, CBR, TXT, PDF) is explicitly out of scope for this MVP.

## Implementation Status (February 26, 2026)

Implemented so far in repo:

- Android app shell (Compose)
- Room-based library persistence
- EPUB import from Android file picker
- basic `ACTION_VIEW` / `ACTION_SEND` intake for EPUBs
- app-managed file copy for imported EPUBs
- basic EPUB metadata extraction (title/creator) from `container.xml` + OPF
- basic cover extraction from EPUB package (when detectable)
- `lastOpenedAt` tracking with library-side `Resume` action
- basic library presentation for cover thumbnails/placeholders and "Recently Opened" grouping
- basic TOC parsing (EPUB3 NAV / EPUB2 NCX) with persisted TOC metadata
- basic TOC preview exposure in library rows (expandable preview from persisted TOC metadata)
- basic invalid/corrupt EPUB rejection with failed-import cleanup
- duplicate import detection by source URI with explicit warning status messaging
- duplicate-import warning action to highlight the existing library entry
- clearer malformed-EPUB import error classification for common ZIP/container/OPF failures
- additional malformed-EPUB validation for missing/invalid `mimetype` entry
- malformed TOC XML degrades to empty TOC instead of failing import metadata extraction
- staged import progress feedback in library UI (copy/parse/cover/save phases)
- library presentation polish: list/grid toggle, richer cover cards, in-memory cover thumbnail caching, recent section counts/summary copy
- Phase 2 EPUB reader MVP route: library `Resume` opens a `WebView`-based reader screen
- EPUB spine chapter loading in the reader `WebView` (with fallback reader HTML on load failure)
- ZIP-backed resource interception for package-relative CSS/images/fonts in the reader `WebView`
- reader position persistence in library records (`lastReadChapterZipPath` + optional `lastReadAnchorFragment` + `lastReadScrollX`/`lastReadScrollY` + `lastReadPageMode`) plus a serialized locator payload (`lastReadLocatorSerialized`, including a visible-text hint) saved on reader exit and used by `Resume` (including paginated-mode resume state and layout-tolerant relative-scroll/text-hint fallback)
- TOC navigation wiring using persisted TOC metadata (chapter reload + fragment jump + scripted anchor jump fallback)
- reader theme/font/text-size controls with injected WebView CSS styling for loaded EPUB chapter documents
- reader typography controls (line height, horizontal margins, alignment) via injected WebView CSS
- paginated mode using column-based WebView CSS plus viewport-step navigation controls
- improved TOC anchor handling with scripted anchor jumps and selected/current TOC state hints
- basic spine chapter navigation controls (`Prev Chapter` / `Next Chapter`) derived from EPUB spine adjacency
- internal EPUB chapter-link interception so chapter transitions route back through reader state
- viewport metrics capture (page index/count, scroll offsets/extents, a best-effort visible anchor, a TOC-anchor-aware visible anchor hint, and a visible-text hint) for page status, serialized locator persistence, and TOC/current-location syncing
- reader TOC panel now displays the full parsed TOC in a scrollable panel (no scaffold cap)
- Readest reader theme inventory task completed and mapped into PirateReader theme IDs (11 presets with matched foreground/background pairs)
- initial Phase 3 e-ink optimization mode toggle in the reader (explicit toggle, paginated preference, no-motion/reduced-effects CSS, reduced viewport-metrics polling cadence, a Balanced/High contrast preset control, and mode-aware typography tuning defaults via `Reset Type`)
- initial Phase 3 reader chrome theme tuning pass (reader top bar + control/status panel + TOC panel + reader control chip colors derived from the active reader theme)
- initial Phase 3 offline dictionary groundwork (`library/dictionary.tsv` discovery/loading with reader status wiring for upcoming long-press lookup UX)

Remaining hardening and polish (Phase 3+):

- robust EPUB resource resolution in the reader `WebView` (edge cases, media coverage, navigation interactions)
- stronger reading progress/locator persistence beyond the current best-effort locator payload + chapter/anchor/scroll/mode state
- richer TOC anchor navigation and current-location synchronization (especially precise scroll/current-anchor sync beyond current heuristics)
- stronger paginated-mode behavior (production-grade page metrics/anchors/page counts/snap behavior across diverse content)
- broader reader chrome/theming token pass and device-specific e-ink tuning/validation
- long-press definition UX and offline dictionary lookup interaction flow (building on the current dictionary file discovery/loading groundwork)

## Product Goals (EPUB MVP)

- Open local EPUB files reliably
- Provide a fast, comfortable reading UI (scroll + paginated)
- Persist reading progress robustly
- Offer strong typography and theme customization
- Run smoothly on Boox-class e-ink devices (reduced repaint/animations)

## Readest-Inspired Feature Parity (Targeted for EPUB Phase)

The goal is not a clone. The goal is parity on the reading customization experience quality.

Target parity categories:

- multiple UI themes / reading themes
- scroll mode and paginated mode
- typography controls (font, size, spacing)
- margins / layout adjustments
- responsive reading controls and overlays

Theme parity process:

1. Inventory Readest's current theme list and preset values from source/UI.
2. Create PirateReader theme tokens with a stable internal schema.
3. Add a parity mapping table (`readest_theme_key -> pirate_theme_key`).
4. Validate on both Pixel 9 and Boox Note Air 3.

Status:

- The exact Readest theme list is now captured in `docs/readest-theme-parity-checklist.md` and mapped to PirateReader theme IDs for the Phase 2 MVP baseline.

## Recommended Architecture

## High-Level Modules

- `app` (Android UI shell, navigation, DI entry points)
- `reader-core` (reader state, pagination abstractions, selection model, theme model) [can start inside `app` and split later]
- `epub` (EPUB parsing, package metadata, spine loading, resource resolution)
- `library` (imported books metadata, cover cache, reading progress)
- `settings` (user preferences, theme/font choices, e-ink options)

For the current MVP, everything can live in `app`. Split to modules once EPUB MVP stabilizes.

## Rendering Strategy (EPUB)

Recommended MVP path: HTML/CSS rendering via `WebView` with a controlled reader bridge.

Why this is the right first step:

- EPUB content is HTML/CSS-based; `WebView` handles layout complexity better than reimplementing HTML rendering natively
- allows high-quality typography/theming quickly
- easier to support scroll/paginated modes with JS-assisted layout and viewport measurement
- can reach Readest-like customization faster

Required controls around `WebView`:

- load only local sanitized EPUB resources
- inject controlled CSS variables for themes and typography
- JS bridge for progress, selection coordinates, and pagination metrics
- strict navigation handling for internal links/footnotes

Non-goal for MVP:

- building a custom text layout engine

## EPUB Pipeline

## Import Flow

1. User picks `.epub` from Android system picker (`ACTION_OPEN_DOCUMENT`) or share intent
2. Persist URI permission (where applicable)
3. Copy source to app-managed storage (recommended for performance/reliability) or maintain URI-backed mode initially
4. Parse package metadata (`container.xml`, OPF, NCX/nav)
5. Extract cover (if available)
6. Insert/update library record
7. Open reader at last position or start

## Parsing Requirements (MVP)

- ZIP container parsing
- `META-INF/container.xml` resolution
- OPF package parse:
- manifest
- spine
- metadata (title, authors, language, identifier)
- NAV document and/or NCX TOC support
- relative resource resolution across spine items
- image assets, stylesheets, fonts referenced in EPUB package

## Data Models (Initial)

### LibraryBook

- `id`
- `sourceUri`
- `localPath`
- `title`
- `authors`
- `coverPath`
- `tocEntryCount`
- `tocEntriesSerialized` (temporary persistence format for Phase 1/2; may be normalized later)
- `lastReadChapterZipPath` (Phase 2 reader position persistence)
- `lastReadAnchorFragment` (Phase 2 anchor-assisted position persistence)
- `lastReadScrollX` (Phase 2 paginated horizontal resume fallback/auxiliary)
- `lastReadScrollY` (Phase 2 position persistence fallback/auxiliary)
- `lastReadPageMode` (Phase 2 mode restore: `SCROLL` / `PAGINATED`)
- `lastReadLocatorSerialized` (extensible locator payload: page metrics + scroll extents + anchor/mode hints + visible-text hint for more layout-tolerant restore)
- `format` (`EPUB`)
- `addedAt`
- `lastOpenedAt`

### ReadingSessionState

- `bookId`
- `spineIndex`
- `progression` (0.0-1.0 overall)
- `locator` (CFI-like or custom anchor selector + offset)
- `mode` (`SCROLL` / `PAGINATED`)
- `themeId`
- `fontId`
- `fontScale`
- `lineHeight`
- `marginScale`

### ReaderTheme

- `id`
- `displayName`
- `surface`
- `textPrimary`
- `textSecondary`
- `link`
- `selection`
- `chromeBackground`
- `chromeForeground`
- `isEinkPreset`

## Reader UI Design (MVP)

## Screens

- Library screen
- Reader screen
- Reader settings sheet (theme, font, typography controls)
- Table of contents panel

## Reader Modes

### Scroll Mode

- best for fast navigation and some e-ink workflows
- continuous chapter scroll (MVP can begin chapter-scoped; later unify across spine)

### Paginated Mode

- optimized for immersive reading
- e-ink priority mode
- precompute next/previous page positions when feasible

## Controls / Gestures

- tap center: toggle chrome
- tap left/right zones: previous/next page (configurable)
- swipe horizontal in paginated mode (optional on e-ink; can disable by default)
- vertical scroll in scroll mode
- long-press text selection (phase-dependent polish)

## Theming and Typography (MVP)

## Theme System

Theme system should use semantic tokens instead of raw colors in UI code.

Required presets:

- Light (paper)
- Sepia / warm paper
- Dark
- High-contrast light (e-ink)
- High-contrast dark (night/e-ink)

Readest parity requirement:

- Final theme list should match Readest's available reader themes and key variants once inventoried.

## Fonts

Launch target: at least 6 bundled fonts (minimum requirement is 5, including 2 terminal-style).

Proposed launch set:

- Literata
- Merriweather
- Bitter
- Noto Sans
- Fira Code (terminal)
- JetBrains Mono (terminal)

Implementation note:

- Bundle font files with verified licenses and record attribution in `NOTICE`/licenses doc.
- Use a font registry (`fontId -> asset file`) so future formats reuse typography settings.

## E-Ink Optimization Plan

MVP should include a dedicated e-ink mode toggle (automatic detection can come later).

Status (February 26, 2026):

- Implemented (initial Phase 3 step): explicit reader e-ink mode toggle that prefers paginated mode when enabled,
  applies no-motion/reduced-effects CSS overrides in the WebView reading surface, reduces viewport-metrics polling cadence,
  exposes a Balanced/High e-ink contrast preset control for stronger border/rule/link emphasis, and supports
  mode-aware typography defaults (`Reset Type`, with safe auto-apply when enabling e-ink mode from baseline settings).
- Implemented (initial Phase 3 theming step): reader top bar, control/status panel, TOC panel, and reader control chips now use
  active-theme-derived chrome tokens so the surrounding Compose chrome better matches the themed WebView reading surface.
- Remaining: larger tap-zone tuning, chrome/fade behavior tuning, and broader on-device validation on Boox hardware.

Settings in e-ink mode:

- disable nonessential animations/transitions
- reduce overlay fade durations or turn off
- prefer paginated mode by default
- high-contrast themes at top of theme list
- tuned typography defaults (size/line-height/margins/alignment) for e-ink mode
- larger tap zones
- avoid frequent progress/chrome redraws while page is static

Performance considerations:

- avoid unnecessary `WebView` invalidations
- debounce settings changes before reinjecting CSS
- cache rendered chapter resources and computed pagination anchors where possible

## Storage & Persistence

## Storage

- App-private storage for imported EPUB copies and extracted artifacts (covers/cache)
- Optional later setting: "reference original file without copying" (deferred)

## Database

Recommended MVP:

- Room database for `LibraryBook`, reading progress, bookmarks (when added), and settings snapshots

If MVP speed is prioritized over schema work:

- start with a lightweight local store and migrate to Room before EPUB polish phase

## Testing Strategy

## Test Corpus

Build a local EPUB corpus covering:

- standard reflow EPUBs
- large chapters
- image-heavy books
- malformed metadata
- NAV-only TOC
- NCX-only TOC
- footnotes/internal links
- mixed CSS edge cases

## Automated Tests (MVP)

- unit tests for OPF/NAV parsing and path resolution
- instrumentation tests for import flow and progress restore
- reader smoke tests for mode/theme/font toggles

## Manual Device Matrix (Initial)

- Pixel 9 (GrapheneOS): import, render quality, theme/font controls, background/restore
- Boox Note Air 3: page-turn latency, redraw behavior, chrome visibility, contrast

## Milestones / Delivery Plan

### Milestone A - App Shell + Library Stub

- Android project scaffold
- library screen placeholder
- settings models and theme token schema
- icon assets integrated

### Milestone B - EPUB Import + Metadata

- file picker import
- parser for metadata/spine/TOC
- library persistence and cover extraction

### Milestone C - Reader MVP

- `WebView` reader with scroll + paginated mode
- progress persistence
- theme + typography controls
- bundled fonts

### Milestone D - EPUB Polish

- e-ink optimization pass
- malformed EPUB hardening
- selection/bookmark UX improvements
- regression test corpus expansion

## Open Decisions (Resolve Before Reader Implementation)

- parser stack choice (custom ZIP/OPF parse vs existing library integration)
- locator format (CFI vs custom anchors for MVP)
- chapter-scoped vs whole-book scroll mode in first release
- whether to copy all imports to app storage by default (recommended: yes)
- exact Readest theme preset inventory and naming
