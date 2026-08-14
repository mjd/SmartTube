# Fork changes vs upstream

What this fork changes relative to **`yuliskov/SmartTube`** (`origin/master`, currently `5d15d853c`).

All of it lives on **`migrate/exoplayer-2.19.1`**, 26 commits ahead of `master`.
**148 files changed, +2,309 / −787** — excluding the vendored player directory, which is untouched
on disk (see *Not done yet*).

> **Already upstream, so not a fork delta:** the in-place MediaCodec recovery fix (issue #5684,
> PR #5961) was merged into `yuliskov/SmartTube` and is present in `master` as `7371a8bb3`. It is
> listed here only to prevent it being double-counted as a local change.

---

## 1. The player: vendored fork → Maven artifacts

The headline change. Everything else follows from it.

- **Was:** Amazon's ExoPlayer 2.10.6 port (2019) vendored as **1,612 files** built from source, under
  `exoplayer-amzn-2.10.6/`, patched in place in 31 files.
- **Now:** `com.google.android.exoplayer:*:2.19.1` from Maven Central — `exoplayer-core`, `-dash`,
  `-hls`, `-smoothstreaming`, `-ui`, plus the `okhttp` and `cronet` extensions. Version pinned once
  at `build.gradle:73` as `ext.exoplayerMigrationVersion`.
- Package names stay `com.google.android.exoplayer2`, so **no import churn** across the ~40 app files
  that touch the player. The Media3 rename is deliberately a separate, later milestone.
- `settings.gradle` no longer applies the fork's `core_settings.gradle`.
- **`minSdk` 17 → 21** for the migrated modules (`ext.migrationMinSdk`). Forced by
  `extension-okhttp:2.19.1`, which requires 21. Drops API 17–20 devices, notably Fire TV Gen1.

**Why:** the 2019 fork predates nine years of upstream device-workaround tables, decoder-recovery
paths and async codec queueing — the exact areas implicated in the MediaTek VP9 decoder faults that
started this work.

### Fork patches that simply disappeared

Roughly a third of the fork's divergence was newer upstream code hand-backported into 2.10.6, and it
is reclaimed for free — parallel segment download (253 lines), `AudioAttributes` capture policy,
cache key factories, HDR10+ colour info, `FLAG_IMMUTABLE`. Two networking mods (the googlevideo
range fix and a TLS/DNS override) turned out to be **dead code, disabled at their call sites**, and
were deleted rather than ported. Full inventory in [`PLAYER_DELTAS.md`](PLAYER_DELTAS.md).

### Fork patches that had to be re-expressed app-side

The rule applied throughout: **app-side unless physically impossible**, so nothing has to be
re-applied on the next player bump.

| Fork patch | Replacement |
|---|---|
| Extra `Format.isDrc` / `Format.lastModified` public fields | `FormatExtras implements Metadata.Entry`, attached via `Format.metadata` |
| `DashManifest.visitorCookie` + chunk-source plumbing | `VisitorCookieResolver`, a `ResolvingDataSource.Resolver` setting the `Cookie` header |
| `isBufferLate` / `isBufferVeryLate` widened to `protected` (Sony Bravia) | Intercepted one level up in `shouldDropOutputBuffer` / `shouldDropBuffersToKeyframe` |
| `AmazonQuirks.skipProfileLevelCheck` | `MediaCodecUtil.getDecoderInfosSoftMatch` |
| `DashManifestParser2` (SmartTube-authored, parses `MediaItemFormatInfo`) | Moved app-side to `common/…/exoplayer/dash/` |

`FormatExtras` is the one worth knowing about: it carries both values on a **single** carrier, and
lives in `:sabr` for a boring structural reason — the DASH parser and track selector are in
`:common`, the SABR parser is in `:sabr`, and `:common` depends on `:sabr`, so it is the only place
both can reach. Neither value could be smuggled through `Format.id`, which is **field 3 of the
persisted preference string**: rewriting it would silently invalidate every saved audio selection.

---

## 2. SABR promoted to a first-party module

- YouTube's server-side ABR protocol — SmartTube-authored, no upstream equivalent — moved out of the
  vendored player into a repo-root **`:sabr` module (94 files)**.
- Ported to the 2.19 APIs: `ChunkExtractor`/`BundledChunkExtractor`, `ExoTrackSelection`,
  `MediaSource.Factory`, `Format.Builder`, `PlayerId`/`DrmSessionManagerProvider`.
- Verbatim DASH copies (`RangedUri`, `SegmentBase`, `UrlTemplate`, `EventStream`, the segment-index
  family) **deleted** in favour of depending on `exoplayer-dash`.
- Guarded at the dispatch site by a `SABR_SUPPORTED` kill switch in `VideoLoaderController`.

**Caveat, stated plainly:** SABR is selected only when YouTube withholds a direct URL, so it is
server-decided and mutually exclusive with DASH. This account is still served direct URLs, so the
port **has not been exercised end-to-end on real traffic** — it compiles, is wired in, and is
unvalidated. The golden-file capture harness (`SabrCapture`, compile-gated) is in place and will
record automatically if the account ever flips.

---

## 3. Device-specific player work

- **MediaTek VP9 adaptation guard** — `TweaksMediaCodecVideoRenderer.canReuseCodec` returns
  `REUSE_RESULT_NO` with `DISCARD_REASON_WORKAROUND` on a resolution change when the decoder matches
  `c2.mtk.*` / `OMX.MTK.*` + `vp9`, forcing a clean codec re-init instead of seamless reuse.
  **Opt-in, default off** — a ~30 min soak recorded *zero* unprompted adaptations, which weakens the
  hypothesis it was built on, so it is not enabled on evidence that thin.
- **Real decoder name in error reports** — the Amazon `codecName` field was never assigned, so every
  shipped error read `MediaCodec decoder error (CodecNameUnknown)`. Now reads `getCodecInfo().name`.
- Adaptation decisions are logged (decoder, old→new format, time since last) to make the correlation
  checkable on-device.
- Amlogic max-values fix and the Amazon frame-drop fix retained; both still have public hooks.

---

## 4. Tests and CI (there were none before)

The player had no test coverage at all upstream. Everything below is new.


**8 pure-JVM suites, 60 tests**, across `:common` and `:sabr`.

| Suite | | Guards |
|---|--:|---|
| `ExoFormatItemWireCompatTest` | 6 | Round-trip of the 11-field persisted preference string, incl. 9- and 10-field legacy forms |
| `SubtitleLanguageCorpusTest` | 10 | Real YouTube language labels — the BCP-47 normalization risk |
| `MediaTrackCodecWeightTest` | 9 | The VP9=31 / AVC=28 / AV1=14 selection weighting |
| `TrackSelectorDrcTest` | 9 | DRC detection across **both** carriers (id suffix *and* metadata marker) |
| `FormatExtrasTest` | 9 | The carrier that replaced the patched `Format` fields |
| `DecoderQuirksTest` | 8 | Which decoders the MediaTek VP9 guard applies to |
| `VisitorCookieResolverTest` | 6 | Cookie attachment, header merging, spec immutability |
| `PlayerTweaksWireFormatTest` | 3 | That no persisted tweak slot has shifted |

- `PlayerTweaksWireFormatTest` is deliberately a **source-level** guard: the class needs a `Context`,
  but the invariant — read-index N equals write-position N — is a property of how the two methods are
  written. It is mutation-verified: deleting the reserved slot from the write side makes it fail with
  a message naming the reason.
- Two shims shadow the mockable `android.jar` where its defaults are actively wrong:
  `android.text.TextUtils` (`isEmpty(null)` would return `false`) and `android.net.Uri`
  (`parse()` would return `null`, so no `DataSpec` can be built).
- **CI runs both modules' tests** and enforces a **reflection ratchet** — a grep guard failing the
  build if new `Helpers.getField`/`setField` calls appear under `common/…/exoplayer/`. The CI step
  previously invoked a task belonging to the now-unwired vendored player, and would have failed.
- Robolectric is deliberately *not* used: it is inert under the JDK 17 this build requires.

---

## 5. Build and packaging

- Debug builds install **side-by-side** with the official app (`applicationIdSuffix ".debug"`,
  `versionNameSuffix "-migrated"`). Without this the debug APK claims `org.smarttube.beta` — the
  published, differently-signed beta — and the install is rejected outright.
- **Firebase/Crashlytics skipped for debug builds.** The checked-in `google-services.json` belongs to
  the maintainer's project; registering a client for the suffixed id would route this fork's
  experimental decoder crashes into someone else's production Crashlytics.
- Debug builds carry a blue icon and a "SmartTube Debug" label so the two are distinguishable on the
  launcher.

---

## 6. Documentation

- [`PLAYER_DELTAS.md`](PLAYER_DELTAS.md) — every fork delta vs pristine Amazon 2.10.6, one row per
  hunk with a disposition. Produced by direct source diff, not marker-grepping, which missed the
  largest deltas entirely.
- [`MIGRATION_STATUS.md`](MIGRATION_STATUS.md) — running migration status.

---

## Not done yet

Stated explicitly so the branch is not mistaken for finished.

- **The vendored fork is still on disk.** `exoplayer-amzn-2.10.6/` (1,612 files) is unwired from
  `settings.gradle` but not deleted. Nothing builds from it; removing it is the Milestone A gate.
- **32 reflection sites remain** under `common/…/exoplayer/` — 26 in `LiveDashManifestParser`, 5 in
  `DelayMediaCodecAudioRenderer`, 1 in `TrackErrorFixer`. The migration target was zero; the CI
  ratchet currently pins the count rather than driving it down.
- **SABR is unvalidated on real traffic** (§2).
- **The MediaTek guard is unproven** — shipped off, awaiting a soak that reproduces the fault.
- **Media3 is a separate milestone.** This branch stops at ExoPlayer 2.19.1; the rename to
  `androidx.media3` 1.4.1, plus the leanback adapter and media-session rewrites it forces, is not
  started.
