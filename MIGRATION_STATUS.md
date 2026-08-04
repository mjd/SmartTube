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

---

## SABR module promotion (in progress)

`library/sabr` is now a first-party module at `sabr/`, registered in `settings.gradle` and depended
on by `:common`. Protobuf generation works (28 `.proto` files), so the module builds up to Java
compilation.

The package is deliberately still `com.google.android.exoplayer2.source.sabr`. Nothing is split --
that package is ours alone, the player never defines it -- and keeping it avoids rewriting imports
across 63 files while the API port is in flight. Renaming to a `liskovsoft` package is a follow-up,
as is the `protocol`/`exo` split the plan calls for.

`protobufVersion` moved to the root `build.gradle` alongside `exoplayerMigrationVersion`, since the
vendored player's `constants.gradle` is going away.

### Measured port surface: 79 errors

| Errors | File |
|---:|---|
| 23 | `DefaultSabrChunkSource.java` |
| 8 | `SabrMediaSource.java` |
| 7 | `parser/misc/SabrExtractorInput.java` |
| 6 | `SabrMediaPeriod.java` |
| 6 | `PlayerEmsgHandler.java` |
| 6 | `manifest/SabrManifestParser.java` |
| 5 | `parser/frames/AVCFrameExtractor.java` |
| ~18 | the remaining `parser/` files, 1-4 each |

This matches the plan's prediction closely: the four plumbing files
(`DefaultSabrChunkSource`, `SabrMediaSource`, `SabrMediaPeriod`, `PlayerEmsgHandler`) account for 43
of the 79, and the `parser/` tree -- the protocol logic that was supposed to be nearly portable --
needs only small mechanical fixes.

Dominant causes:
- **`TrackSelection` was split.** `getSelectedFormat`, `getSelectionReason`, `getSelectionData`,
  `getSelectedIndex`, `updateSelectedTrack`, `evaluateQueueSize`, `blacklist` and friends moved to
  `ExoTrackSelection`; `TrackSelection` itself is now a minimal interface. ~14 errors.
- **`ChunkExtractorWrapper` no longer exists** (5 errors) -- replaced by the `ChunkExtractor`
  interface and `BundledChunkExtractor`. The genuine redesign in this port.
- **`Format` factories and the vendored `lastModified` field**, same as everywhere else.


---

## Milestone A reached: the app builds and the tests run

`assembleStbetaDebug` produces a **31 MB APK against Maven ExoPlayer 2.19.1**, and all **25 unit
tests pass**. That is the first real validation this migration has had — everything before it was
"it compiles".

The most important green light is `ExoFormatItemWireCompatTest`: the persisted preference string
survived the `Format.Builder` rewrite intact, so saved quality settings still round-trip
byte-for-byte. That was the single largest silent-regression risk in the plan.

### minSdk is 21, not 19 — earlier note corrected

`extension-okhttp:2.19.1` declares **minSdk 21**; every other ExoPlayer artifact we use declares 16.
The P0.4 spike only measured `exoplayer-core`, so the earlier "17 → 19, not 21" conclusion was wrong
for the full dependency set. Fire TV Gen1 and Stick Gen1 are lost after all — the original decision
was right. Set via `ext.migrationMinSdk` in the root `build.gradle` so the SharedModules submodule
stays untouched.

### SABR is ported and back on

The module compiles (79 errors → 0), `:common` depends on it again, and
`VideoLoaderController.SABR_SUPPORTED` is back to `true`. The flag stays as a kill switch: SABR is
the least-tested part of the player, and flipping it is the fastest way to rule it out if playback
misbehaves.

The port's substantive pieces:

- **`ChunkExtractorWrapper` → `ChunkExtractor`/`BundledChunkExtractor`** — the redesign the plan
  flagged, which turned out to be largely a type swap across five sites plus one constructor change
- **`SabrMatroskaAdapter` moved from inheritance to composition** — `MatroskaExtractor.read` is
  `final` upstream so it can no longer be overridden. It now delegates through the public
  `Extractor` interface, which is the better shape regardless: it no longer depends on the
  extractor's internals. (`FragmentedMp4Extractor.read` is *not* final, so that adapter still
  extends.)
- **`Timeline.Window.set` now takes a `MediaItem` and `LiveConfiguration`**, and `getWindow` lost its
  `setTag` flag — the tag rides inside the `MediaItem` instead
- **`ChunkSource` gained `release()` and `shouldCancelLoad()`**. SABR never cancels a load: its
  requests are a protocol exchange rather than plain range requests, so cancelling mid-flight would
  desynchronise the stream
- **`onChunkLoadError` now receives a `LoadErrorInfo` and the policy** rather than a precomputed
  exclusion duration, so the source asks the policy itself
- **`ExoTrackSelection.blacklist` → `excludeTrack`**; **`RawCcExtractor`** dropped (SABR never serves
  RAWCC — YouTube delivers captions as separate text tracks)
- **`lastModified` rides as `Format.metadata`** via `SabrFormatExtras`, since SABR echoes it back in
  every request's `FormatId` and reads it off a `Format` that has already passed through track
  selection

**Still entirely unvalidated at runtime.** SABR is dormant on the test TV (YouTube serves DASH), the
golden-file capture came back empty, and the module has no tests. It compiles and is wired in; that
is the whole of what is known.

### Other things now missing or moved, beyond SABR

- **Subtitle bot-check visitor cookie** — regression, see PLAYER_DELTAS.md
- **DRC detection on two of three format paths** — degrades to "not DRC", which is the safe direction
- **`AmazonQuirks`** — dropped; needs Fire TV validation
- **Zoom** moved out of the player: `AspectRatioFrameLayout` is `final` upstream, so the percentage
  now lives in `SurfacePlaybackFragment` and is applied as a scale transform. No player change needed
- **`ControlDispatcher`** removed: the AFR pause fix became a `ForwardingPlayer` wrapping the player
  the media session sees, so it still only affects session-originated commands
- **`SubtitleManager`** registers as a `Player.Listener`; there is no text component to attach to
