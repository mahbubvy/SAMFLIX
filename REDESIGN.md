# PotFlix Sam — redesign and improvement direction

Written 13 Nov 2026 from a read of the code on the `samonline` branch. Nothing here has been
checked on a device; where a point depends on how the app feels in the hand, it says so.

## 1. Where the app stands

The app does its job: it finds films and series on the SamOnline servers, shows posters from
TMDB, and plays them with mpv. The player has had the most attention and is the strongest part.

The rest still looks and behaves like the original PotFlix it was forked from:

- **Six bottom tabs** (Home, Movies, TV, Browse, Watchlist, Settings). Three of them show the
  same catalogue cut different ways, and Search and Downloads have no tab at all.
- **Two looks in one app.** The library is neon red and purple; the player is blue.
- **No type or spacing system.** `Type.kt` defines one text style. Screens set their own sizes
  (139 hard-coded `.sp` values) and colours (115 hard-coded `Color(0x…)` values outside the theme).
- **Phone layouts on a tablet.** Search, category and skeleton grids are fixed at 3 columns.
  Only Browse adapts.
- **A few very large files.** `DetailScreen.kt` is 2,000 lines, `PlayerActivity.kt` 1,132,
  `SettingsScreen.kt` 890. The player has no ViewModel; its state lives in about 60
  `remember`/`LaunchedEffect` calls inside one composable.

## 2. Direction

**A quiet shelf for a fast local server.** The content is on the ISP's own network, so it
loads fast and costs no data. The app should get out of the way: dark, calm, poster-led, and
two taps from launch to playback.

Three rules to design against:

1. **Posters carry the colour; the interface doesn't.** One accent, neutral surfaces, no neon
   gradients competing with artwork.
2. **Resume beats discover.** Most launches are "carry on with what I was watching". That
   belongs at the top of Home, at full size.
3. **The server is the source of truth.** When TMDB matching fails, the app should still show
   the folder honestly (file name, size, quality) instead of a blank card.

## 3. Visual system

Build these as tokens in `presentation/theme/` first, then move screens onto them one at a time.

### Colour

| Role | Value | Notes |
|---|---|---|
| Background | `#0B0D12` | Slightly blue-black; replaces `#05050A` |
| Surface | `#141821` | Cards, sheets, nav |
| Surface raised | `#1D2330` | Dialogs, pressed and focused states |
| Outline | `#2A3140` | Dividers, card borders |
| Accent | `#2F8CFF` | The player's blue, now used everywhere |
| On accent | `#FFFFFF` | |
| Text | `#F2F4F8` | |
| Text muted | `#9AA3B2` | Check it reaches 4.5:1 on Surface |
| Error | `#FF5A5F` | Errors and destructive actions only |
| Rating | `#FFC107` | Star and score only |

Drop neon purple, green and cyan and the red-to-purple gradient. Put every role into
`darkColorScheme(...)` so components pick them up without per-screen colours. The app stays
dark-only.

### Type

Fill in the Material 3 scale in `Type.kt` and use only these:

| Style | Size / line | Weight | Used for |
|---|---|---|---|
| `headlineMedium` | 28 / 34 | Bold | Detail title, hero title |
| `titleLarge` | 22 / 28 | SemiBold | Screen titles |
| `titleMedium` | 16 / 22 | SemiBold | Row headers, sheet titles |
| `bodyLarge` | 16 / 24 | Regular | Overview text |
| `bodyMedium` | 14 / 20 | Regular | Lists, settings |
| `labelLarge` | 14 / 20 | Medium | Buttons, chips |
| `labelSmall` | 11 / 16 | Medium | Badges, metadata |

### Space and shape

- Spacing steps: 4, 8, 12, 16, 24, 32. Screen edge padding 16 on phones, 24 on tablets.
- Corners: 8 for posters and chips, 12 for cards, 20 for sheets, full for pills.
- Touch targets at least 48 dp.
- Poster ratio 2:3 everywhere; landscape stills 16:9 for Continue Watching and episodes.

## 4. Navigation

Go from six tabs to four.

| Tab | Holds | Replaces |
|---|---|---|
| **Home** | Continue Watching, new on server, rows by category, with All / Movies / Series chips at the top | Home, Movies, TV |
| **Browse** | The server's folders | Browse |
| **Search** | Search field, recent searches, results | Search (currently no tab) |
| **Library** | Watchlist, Downloads, History as three sections | Watchlist, Downloads |

Settings moves to a gear icon in the Home and Library top bars.

- **Phones:** bottom navigation bar, labels always visible.
- **Tablets (600 dp wide and up):** navigation rail on the left. A rail already exists in
  `BottomNavBar.kt` for TV; reuse it.
- **Detail** opens over the tabs with a back arrow, as it does now.

## 5. Screens

### Home
- Top: Continue Watching as wide 16:9 cards with a progress bar and "S01E05 · 12 min left".
  Tap resumes playback directly; long-press offers Details and Remove.
- Then one hero for the newest addition, smaller than now (about 40% of screen height), so the
  first row is visible without scrolling.
- Rows: "New on the server", then categories. Each header is tappable and opens the full grid.
- Remove the Top 10 row unless it reflects something real on this server.

### Browse
- Keep the folder list, add a breadcrumb under the title so the path is visible and tappable.
- Sort (name, newest) and a list/grid switch in the top bar.
- Unmatched folders show a neutral card with the folder name, not an empty poster.

### Detail
- Backdrop, title, one row of facts (year · runtime · rating · quality), then a full-width
  **Play / Resume** button and three small actions (Watchlist, Download, Trailer).
- Series: season chips, then episode rows with number, title, runtime and a watched tick.
  Use the new episode parser's numbers so the list and the player agree.
- Move the "wrong match?" TMDB correction into the overflow menu.
- On tablets, two columns: poster and actions left, overview and episodes right.

### Search
- Field focused on open, recent searches below, results as you type.
- Results in an adaptive grid, with Movies / Series chips.
- An empty result offers "Look in Browse" because the file may exist without a TMDB match.

### Player
Recent work stands; remaining direction:
- One seek step everywhere: double-tap is 3 s, D-pad left/right is still 10 s. Pick one or
  make it a setting.
- Episode list as a right-side panel on tablets instead of a full sheet.
- Show "Next episode in 10 s" at the end of an episode, with Cancel.
- Remember audio and subtitle language per series.

### Library
- Three sections on one screen: Downloads (with storage used), Watchlist, History.
- Downloads show progress, size and a pause/cancel control per item.

### Settings
- Group into Playback, Downloads, Server, About. Each row is one line of title and one line
  of current value.
- Server section shows which SamOnline servers are reachable right now.

### First run
- Check whether the Login and Onboarding screens are still needed for this fork. If the app
  only needs to confirm it can reach the server, replace both with one screen: "Checking your
  connection to SamOnline…" then straight to Home, or a plain message if the user isn't on
  the ISP's network.

## 6. Tablets and TV

- Replace every `GridCells.Fixed(3)` with `GridCells.Adaptive(minSize = 120.dp)`
  (`SearchScreen.kt`, `CategoryDetailScreen.kt`, `HomeSkeleton.kt`).
- Cap reading width for text blocks at about 640 dp.
- Decide layout from window width, not from device type, so split-screen works.
- Every interactive element needs a visible focus state for D-pad and keyboard use.

## 7. Code changes that make the redesign possible

| Change | Why |
|---|---|
| Shared components: `PosterCard`, `WideCard`, `SectionHeader`, `AppTopBar`, `EmptyState`, `ErrorState` | Each screen currently builds its own |
| Split `DetailScreen.kt` into header, actions, episode list, trailer and TMDB dialog files | 2,000 lines in one file |
| Add `PlayerViewModel` and move playback state out of the `VideoPlayer` composable | State survives rotation and can be tested |
| Pass a movie ID in the Detail route instead of Base64 JSON of the whole object | Long routes are fragile and can't be deep-linked |
| Move user-facing text to `strings.xml` | There are no `stringResource` calls today; needed for Bangla |
| Remove the fallback TMDB key from `app/build.gradle.kts` and read it only from `local.properties` | A key in the build file ships to anyone with the source |
| Tests for the parser, URL mapping and Browse rules; one Compose test per main screen | 16 unit tests today, no UI tests |

## 8. Order of work

| Phase | Work | Done when |
|---|---|---|
| 1. Foundations | Colour, type, spacing tokens; shared components; strings file | No `Color(0x…)` or `.sp` outside `theme/`; app builds and looks consistent |
| 2. Navigation | Four tabs, rail on tablets, Settings behind a gear | Every screen reachable in two taps on phone and tablet |
| 3. Home and Library | Continue Watching first, merged Movies/TV, Library screen | Resuming takes one tap from launch |
| 4. Detail and Search | New Detail layout, adaptive grids, Browse fallback | Detail reads well on a 10-inch tablet in both orientations |
| 5. Player follow-ups | ViewModel, next-episode prompt, side panel | Rotation and episode changes keep state |
| 6. Polish | Focus states, TalkBack labels, Bangla strings, first-run check | TalkBack can play an episode end to end |

Phases 1 and 2 touch nearly every file, so do them before any other feature work.

## 9. Decisions (made 13 Nov 2026)

1. **Accent colour:** blue (`#2F8CFF`) across the whole app. Neon red, purple and the
   gradients go.
2. **App name:** **SAMFLIX**, with a new icon to match.
3. **Login and Firebase sync:** removed. First launch checks the SamOnline connection and goes
   straight to Home; watch history and watchlist stay on the device.
4. **Languages:** English only, but all text moves to `strings.xml` so Bangla can be added later.
5. **Android TV:** not a target. Phones and tablets only; the side rail is for tablets.
6. **Scope:** build phases 1 and 2 first (visual system, shared components, four tabs with a
   tablet rail), then test on a phone and tablet before going further.

The player already has a 5-second "Next episode" countdown with Cancel, so that item in §5
Player is done.
