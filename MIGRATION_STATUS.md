# Migration status — vendored fork → Maven ExoPlayer 2.19.1

**Milestone A is code-complete and on `master`.** The vendored Amazon ExoPlayer 2.10.6 tree has
been deleted; the player is `com.google.android.exoplayer:*:2.19.1` from Maven, pinned by
`ext.exoplayerMigrationVersion` in the root `build.gradle`.

Where each modification of the old vendored tree ended up is recorded in `PLAYER_DELTAS.md`.

## Done

- **P1 DataSource** — 2.19 `*.Factory` builders; googlevideo range fix via `ResolvingDataSource`;
  OkHttp as the default `HttpDataSource`.
- **P2 `Format` de-patch** — `isDrc` and `lastModified` travel as `Format.metadata`
  (`FormatExtras`) instead of patched core fields. The 11-field `ExoFormatItem` prefs string is
  unchanged, covered by wire-compat tests.
- **P3 Track selection** — `RestoreTrackSelector` on 2.19's `Pair<Definition, Integer>` API;
  `RENDERER_INDEX_*` still the app-facing enum, mapped to `C.TRACK_TYPE_*` only at the boundary.
- **P4 Renderers / workarounds** — MediaTek VP9 adaptation guard, Amlogic and Amazon tweaks,
  Sony Bravia lateness thresholds reimplemented app-side.
- **P5 Live DASH** — `DashManifestParser2` moved app-side to `common/.../exoplayer/dash/`.
- **P6 UI / subtitles** — zoomable aspect-ratio view, subtitle styling via public API.
- **P7 SABR** — promoted to the repo-root `:sabr` module with a `protocol/` vs `exo/` split.
- **Fork deleted** — `exoplayer-amzn-2.10.6/` (1,612 files, ~334k lines) removed; `settings.gradle`
  no longer defines or applies it.

## Verification

CI (`.github/workflows/CI.yml`) runs, and all pass on `master`:

| Gate | State |
|---|---|
| Reflection ratchet | 32 / baseline 32 |
| `:common` + `:sabr` unit tests | 510, 0 failures |
| `lintStbetaRelease` (root) | 0 errors |
| `clean assembleStbetaRelease` | green |

## Remaining before the Milestone A gate

1. **32 reflection sites** into player internals, against a P4/P5 exit criterion of zero.
   `LiveDashManifestParser` 26, `DelayMediaCodecAudioRenderer` 5, `TrackErrorFixer` 1. These fail
   silently rather than at compile time, which is why CI ratchets them. `LiveDashManifestParser`
   was meant to become an override-the-builders subclass of `DashManifestParser` and has not been
   rewritten yet. Lower `REFLECTION_BASELINE` as they go.
2. **Playback matrix on ≥3 devices**, plus APK size and cold-start-to-first-frame within 10% of
   pre-migration. Only the MediaTek Google TV has had real soak time.
3. **Ship to beta.** Do not carry Milestone A unreleased into Milestone B.

## Known, not blocking

- **SABR is unvalidated.** It is selected only when YouTube withholds a direct URL, and this
  account is served DASH, so the port has never executed. It compiles and upstream's own
  `SabrCdnSelectorTest` passes; that is the ceiling of what can be checked from here.
- **HLS live 403** — inherited from before the migration, not caused by it.
- Live 4K stalls on this hardware are a googlevideo delivery pace (~18 Mbps) below itag 315's
  ~19.5 Mbps, not a migration defect. Unrelated to the gate.

## Next milestone

**B — ExoPlayer 2.19.1 → `androidx.media3:*:1.4.1`.** Mechanical package rename plus minSdk 19,
then the leanback adapter and a hand-written `MediaSessionCompat` connector, neither of which has a
Media3 successor. Should be boring; if it is not, Milestone A was under-done.
