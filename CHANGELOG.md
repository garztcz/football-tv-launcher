# Changelog

Versions are the launcher's (`3.2-nothingX.Y`, versionCode in brackets).
The base is upstream v3.2 (versionCode 11).

## nothing5.10 (49) — 2026-10-31
- Header alignment: the clock row starts 8dp further right, so the
  clock's left edge lines up with the greeting and the cards below
  (the Doto digits previously protruded ~16px left of both).

## nothing5.9 (48) — 2026-10-31
- **Doto date**: the header date is now a second dot-matrix hero next to
  the clock, in the short `EEE dd MMM` format ("SAT 31 OCT"). Clock and
  date share one size (38sp), tuned so the pair ends before the pill
  row, and the pill row's bottom margin was adjusted so the text and
  the pills share a vertical centre.
- **Pill order**: Inputs, Theme, Wi-Fi, Settings (Theme moved after
  Inputs).

## nothing5.8 (47) — 2026-10-31
- **Theme picker**: a small palette-icon pill now sits left of the Inputs
  pill. It opens a list of all six themes (the active one marked ●);
  picking one switches straight to it. The Settings menu's Theme row
  opens the same list.
- **Fix**: theme switching could toggle between the first theme and
  Persebaya only. `ClubTheme.next()` compared theme objects by
  reference, but the live overlay returns copies, so the comparison
  missed for every theme with live data; it now compares by id.
- Header: the left block no longer ends where the pill row starts, so
  the date line keeps its full width with the extra pill present.

## nothing5.7 (46) — 2026-10-31
- Live-data overlay: `ClubTheme.current()` now overlays data published by
  the new **TV Live Feed** add-on (v1.0) over the baked snapshot, per
  field, with the snapshot as fallback. Launcher manifest declares the
  add-on's provider under `<queries>`.
- Add-on: ESPN public API (standings, team schedule, per-date
  scoreboards), refresh every 6 h via JobScheduler, boot re-arm, status
  screen with manual refresh. Covers Serie A, Premier League, La Liga;
  Indonesian Super League not on ESPN yet (see README).

## nothing5.6 (45) — 2026-10-31
- Manchester United standings card shows positions 1–7 then 16 (the club's
  actual place), replacing the 9–16 window of 5.5. `ClubTheme` gained a
  per-theme `positions[]` label array.

## nothing5.5 (44) — 2026-10-31
- All six themes wired into `ClubTheme` (data snapshots of 31 Oct 2026):
  Persebaya, Como 1907, Manchester United, Arsenal, Liverpool, Barcelona.
  Theme switching cycles the full order instead of toggling two.

## nothing5.4 (43) — 2026-10-31
- Switchable themes introduced: `ClubTheme` holds each theme's card data,
  crest-sampled accents, crest drawable and bundled ghost wallpaper; the
  Settings long-press menu gained a "Theme: …" row. Persebaya + Como 1907.

## nothing5.3 (42) — 2026-10-31
- Boot self-repair for the Home helper: `BootRepairReceiver` +
  `ServiceRepair` re-bind the accessibility service after reboot (this
  firmware resets the accessibility master switch at boot, and TCL's
  TclAppBoot requires the `APP_AUTO_START` app-op).
- The Persebaya-only build that ran as the daily driver on the reference
  TV; kept as the rollback baseline.

## nothing5.2 (41) — 2026-10-31
- Settings menu rows "Make default Home" (ROLE_HOME request) and "Open
  stock launcher" (permanent escape hatch).

## nothing5.0–5.1 (39–40) — 2026-10-31
- English UI; left-aligned greeting (a centred variant was tried and
  reverted); ghost Persebaya wallpaper accepted.

## nothing1.0–4.x — 2026-10-31
- Initial Nothing restyle of the upstream launcher: header with greeting +
  Doto clock, Persebaya fixture/standings cards, dock behaviour, PS5-style
  navigation sounds, package-by-package TCL T6C debloat session performed
  the CleanYourTV way (backup first, disable never uninstall).
