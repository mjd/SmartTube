# Player fork deltas — migration spec

Authoritative inventory of how `exoplayer-amzn-2.10.6/` differs from **pristine Amazon ExoPlayer 2.10.6**
(`amzn/exoplayer-amazon-port`, branch `amazon/r2.10.6`). Every row must be dispositioned before the
vendored fork can be deleted.

Produced by direct source diff, not by grepping for `MOD:` markers — marker-based surveys missed
several of the largest deltas (notably `SegmentDownloader`, the single biggest at 253 lines, which
carries no marker at all).

**Scope:** 26 modified files + 2 new files under `library/` (excluding `library/sabr/`, which is
covered separately by the SABR port), plus 5 modified files under `extensions/`.

**Caveat:** the baseline is the *current* head of `amazon/r2.10.6`. If Amazon moved that branch after
SmartTube vendored it, a few rows may be Amazon's changes rather than SmartTube's. Attribution is
noted where it's uncertain; **disposition is unaffected** either way.

## Disposition key

| Code | Meaning |
|---|---|
| **DROP** | Upstream has it natively in 2.19/Media3 — delete, do not port |
| **APP** | Re-express app-side (subclass, factory, or `common/.../exoplayer/versions/`) |
| **DELETE** | Dead or obsolete — remove outright |
| **SABR** | Belongs to the SABR port, handled there |

---

## DROP — 9 files, ~430 lines that delete themselves

The largest single category. Much of the fork's divergence is *newer ExoPlayer code hand-backported
into 2.10.6*; the migration reclaims all of it for free.

| File | Δ | What it is |
|---|---|---|
| `core/offline/SegmentDownloader.java` | 253 | Parallel segment download (`ExecutorService`, merged segments) backported from a later release |
| `core/audio/AudioAttributes.java` | 39 | `allowedCapturePolicy` + `spatializationBehavior` backport |
| `core/C.java` | 37 | `AudioAllowedCapturePolicy` IntDef + `ALLOW_CAPTURE_BY_*` (pairs with the above) |
| `core/upstream/cache/CacheUtil.java` | 31 | `DEFAULT_CACHE_KEY_FACTORY`, parallel chunk downloads |
| `core/video/ColorInfo.java` | 18 | HDR10+ fix — **verify** present in 2.19 before dropping |
| `core/upstream/cache/CacheKeyFactory.java` | 7 | Companion to `CacheUtil` |
| `ui/PlayerNotificationManager.java` | 9 | `FLAG_IMMUTABLE` for Android S+ — fixed upstream |
| `core/video/VideoFrameReleaseTimeHelper.java` | 8 | Exception swallow; class is rewritten upstream as `VideoFrameReleaseHelper` |
| `core/extractor/mkv/MatroskaExtractor.java` | 2 | Widens `read()` visibility purely so SABR can subclass it. SABR moves to **composition** (see plan P7), so this need disappears |

Also DROP: `core/upstream/cache/CacheDataSourceTest.java` (16), `CacheUtilTest.java` (9) — tests for the
backported cache code above.

---

## APP — re-express app-side

These are genuine SmartTube behaviour. None require forking Media3.

| File | Δ | Behaviour | Destination |
|---|---|---|---|
| `core/upstream/DataSpec.java` | 86 | googlevideo throttle range fix | **DONE — DELETED.** The call site was commented out, so `applyRangeQuery()` was dead code; `Helpers` was used nowhere else in the file. Removed rather than ported — porting dead code would have been worse than not porting it |
| `core/upstream/DefaultHttpDataSource.java` | 14 | TLS ciphers/DNS; headers from `DataSpec` | **Partly DONE.** The `NetworkHelpers` TLS/DNS line was already commented out with a `TODO` about API 34 — deleted along with its import. The header loop stays live until the swap, where it is **DROP** (native since 2.14) |
| `ext/cronet/CronetDataSource.java` | — | Same header change | **DROP** — native |
| `ext/okhttp/OkHttpDataSource.java` | — | Same header change | **DROP** — native |
| `ext/cronet/CronetEngineWrapper.java` | — | QUIC enable; noted in-tree as unused (replaced by `sharedutils.cronet.CronetManager`) | **DELETE** |
| `dash/manifest/DashManifest.java` | 34 | `visitorCookie` for subtitle bot-check | **REGRESSION — not yet reimplemented.** The fork added the cookie as an extra `DashManifest` field and `DefaultDashChunkSource` applied it; neither can be patched on a published artifact. `DashManifestParser2` no longer passes it, so the subtitle bot-check cookie is currently not sent. The natural replacement is a request header on the DASH `DataSource.Factory` — a cookie is an HTTP concern, not a manifest field. Fix before shipping |
| `dash/DefaultDashChunkSource.java` | 10 | Applies that cookie | Same regression as above |
| `hls/HlsSampleStreamWrapper.java` | 1 | Pass fps into `Format` | App-side HLS parser subclass |
| `hls/playlist/HlsPlaylistParser.java` | 4 | Don't append "Default" to format id | App-side parser subclass |
| `ui/AspectRatioFrameLayout.java` | 44 | `zoomPercents` get/set — **not upstream API**; referenced by name in `lb_playback_fragment.xml:17` | New `common/.../exoplayer/ui/ZoomableAspectRatioFrameLayout`, repoint XML (P6) |
| `ui/SubtitlePainter.java` | 11 | Subtitle bg padding, uniform line height | `SubtitleView` style APIs in `SubtitleManager` (P6) |
| `ui/SubtitleView.java` | 6 | Overlapped-subs fix | As above (P6) |
| `core/text/webvtt/WebvttCueParser.java` | 5 | Decode HTML entities in styles | Verify vs 2.19; else app-side |
| `core/text/webvtt/WebvttSubtitle.java` | 10 | Long-lasting subs fix | Verify vs 2.19; else app-side |
| `core/DefaultRenderersFactory.java` | 3 | Guard `NoClassDefFoundError: DecoderAudioRenderer` | try/catch in `CustomOverridesRenderersFactory` |
| `core/video/MediaCodecVideoRenderer.java` | 10 | Makes `isBufferLate`/`isBufferVeryLate` `protected` for the Sony Bravia fix | **The one delta with no upstream hook.** Reimplement the thresholds inside a `processOutputBuffer` override in `TweaksMediaCodecVideoRenderer` (P4) |
| `ext/leanback/LeanbackPlayerAdapter.java` | — | Mem-leak fix (`SegmentTimelineElement`) | Carry into our vendored single-file copy at P9 |
| `ext/mediasession/MediaSessionConnector.java` | — | NPE + notification fixes | **Moot** — replaced by the hand-written `MediaSessionCompat` connector (P9) |

---

## Special cases

| File | Δ | Disposition |
|---|---|---|
| `core/Format.java` | 230 | **DELETE the patch.** `lastModified` has exactly one consumer (`FormatSelector:69`, inside SABR) → move onto SABR's own `Representation`. `isDrc` → use the `format.id` suffix check already at `TrackSelectorUtil:215`. See plan P2 — this is what makes "no Media3 fork" possible |
| `core/mediacodec/MediaCodecRenderer.java` | 127 | Our own work: the merged #5684 in-place codec recovery + the `codecName` fix. 2.19/Media3 have recovery natively. Port **only** the error-taxonomy nuance (don't let a renderer-typed error wipe the user's video preset) into `ErrorFixerController`, classifying on `PlaybackException.errorCode` (P4) |
| `dash/manifest/DashManifestParser2.java` | **new** | SmartTube-authored; parses `MediaItemFormatInfo`, not XML. Move app-side to `common/.../exoplayer/dash/` (P5) |
| `core/upstream/DataSpecTest.java` | **new** | Test for the googlevideo range fix. Port alongside it into the P1 work — it is one of the few existing player tests and should survive |

---

## Summary

| Disposition | Files | Notes |
|---|---|---|
| DROP | 11 | ~470 lines reclaimed for free — mostly hand-backports of newer upstream code |
| APP | 18 | Genuine SmartTube behaviour; all expressible against public 2.19/Media3 APIs |
| DELETE | 2 | `CronetEngineWrapper` (dead), the `Format` patch |
| SABR | — | `library/sabr/` — see plan P7 |

**The headline for the migration:** roughly a third of the fork's non-SABR divergence is upstream code
that was manually backported into 2.10.6, and it disappears at no cost. Exactly one delta
(`MediaCodecVideoRenderer`'s `isBufferLate` visibility) has no public-API equivalent and needs a local
reimplementation. Nothing in this table requires forking Media3.

**Blocker 6 is nearly empty.** The plan treated "fork mods that import `com.liskovsoft.*` from inside
the vendored player" as a structural obstacle, since a Maven-consumed player cannot be patched that
way. In practice both networking offenders were **dead code** — the googlevideo range fix and the
TLS/DNS override were each disabled at their call site — and have been deleted. Two cross-layer
imports remain outside SABR: `ui/SubtitlePainter` (subtitle span helpers) and `dash/DashManifestParser2`
(SmartTube-authored, moves app-side at P5). Neither blocks consuming the player from Maven.
