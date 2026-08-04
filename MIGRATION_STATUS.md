# Migration status — vendored fork → Maven ExoPlayer 2.19.1

Branch: `migrate/exoplayer-2.19.1`. **This branch does not build.** It is a work-in-progress
checkpoint: the build has been repointed at Maven artifacts and the resulting breakage measured, so
the remaining work is a known list rather than an estimate.

## What has been done

- Root `build.gradle`: new `ext.exoplayerMigrationVersion = '2.19.1'`. Deliberately **not** the
  `exoplayerVersion` in `SharedModules/constants.gradle` — SharedModules is a git submodule, i.e. a
  separate repository, so edits there do not travel with this branch's commits
- `settings.gradle`: the vendored `core_settings.gradle` is no longer applied, so the ~19
  `:exoplayer-*` projects no longer exist
- `common`, `smarttubetv`, `doubletapplayerview`: project dependencies replaced with
  `com.google.android.exoplayer:*:2.19.1` artifacts
- `common/build.gradle`: `-Xmaxerrs 2000` (temporary — javac's default of 100 hid half the breakage)

The vendored tree is still on disk. It is deleted once nothing references it.

## Measured breakage: 195 errors in `:common`

`smarttubetv` and `doubletapplayerview` are not yet measurable — they compile after `common` does.

### By category

| Count | Category |
|---|---|
| 121 | cannot find symbol |
| 29 | method does not override or implement a supertype method |
| 6 | `DrmSessionManager` does not take parameters |
| 6 | `TrackErrorFixer` cannot be converted to `MediaSourceEventListener` |
| 5 | package `...source.sabr` does not exist |
| 2 | no suitable method `createMediaSource(Uri)` |
| 2 | `SimpleExoPlayer.stop` arity |
| 2 | `Cue` constructor arity |
| ~22 | listener conversions, `onCues(CueGroup)`, misc |

### By file (top of `:common`)

| Errors | File | Phase |
|---:|---|---|
| 22 | `versions/selector/RestoreTrackSelector.java` | P3 |
| 12 | `selector/TrackSelectorManager.java` | P3 |
| 10 | `ExoMediaSourceFactory.java` | P1/P5/P7 |
| 8 | `selector/ExoFormatItem.java` | P2 |
| 8 | `other/SubtitleManager.java` | P6 |
| 7 | `prefs/PlayerData.java` | P6 |
| 6 | `versions/renderer/CustomOverridesRenderersFactory.java` | P4 |
| 5 | `versions/renderer/TweaksMediaCodecVideoRenderer.java` | P4 |
| 4 | `other/ExoPlayerInitializer.java` | P4 |
| 4 | `errors/TrackErrorFixer.java` | P4 |
| 3 | `versions/renderer/DelayMediaCodecAudioRenderer.java` | P4 |
| 3 | `versions/renderer/DebugInfoMediaCodecVideoRenderer.java` | P4 |
| 2 | `selector/track/MediaTrack.java` | P3 |
| 2 | `other/VolumeBooster.java` | P4 |

### By missing symbol — the actual work list

| Count | Symbol | Disposition |
|---:|---|---|
| 35 | `TrackSelection.Definition` | **P3.** The single biggest item, as predicted |
| 12 | `CaptionStyleCompat` | **P6.** Moved package (`text` → `ui`) |
| 11 | `FrameworkMediaCrypto` | **P4.** DRM type parameters — DRM is dead code, delete rather than port |
| 6 | `AudioTrackScore` / `TextTrackScore` | **P3.** `DefaultTrackSelector` internals, non-constructible now |
| 5 | `KeepCodecResult` / `KEEP_CODEC_RESULT_NO` | **P4.** Our own MediaTek VP9 guard — `canKeepCodec` returns `DecoderReuseEvaluation` in 2.19 |
| 5 | `sabr` package / `SabrManifestParser` | **P7.** Relocate to a first-party `:sabr` module |
| 4 | `addAudioListener` / `removeAudioListener` | **P4.** Merged into `Player.Listener` |
| 3 | `DashManifestParser2` | **P5.** SmartTube-authored, moves app-side |
| 3 | `isDrc` | **P2.** The `Format` patch — carry via `Format.metadata` |
| 3 | `DefaultHttpDataSourceFactory` | **P1.** → `DefaultHttpDataSource.Factory` |
| 2 | `AmazonQuirks` | **P4.** Drop; reimplement the surviving behaviours app-side |
| 2 | `ExtractorMediaSource` | **P1.** → `ProgressiveMediaSource` |
| 2 | `DefaultMediaSourceEventListener` | **P4.** Deleted upstream; interface is all-default now |
| 1 | `ExoPlayerFactory` | **P4.** → `ExoPlayer.Builder` |
| 1 | `DISCONTINUITY_REASON_PERIOD_TRANSITION` | **P4.** Renamed `..._AUTO_TRANSITION` |

## Notes

- The distribution matches the plan's phase estimates closely — track selection (P3) really is the
  largest app-layer item, at 35 of the 121 missing symbols plus the two score classes.
- Nothing here contradicts the "no Media3 fork" conclusion. There is no error that requires
  modifying the player itself.
- `2.19.1` resolved and its transitive dependencies are consistent with the existing AndroidX
  versions — no dependency conflicts appeared.
