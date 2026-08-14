package com.liskovsoft.smartyoutubetv2.common.exoplayer.dash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.dash.DashSegmentIndex;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Representation;

import org.junit.Test;

/**
 * Pins how a live stream's synthesized manifest is shaped.
 *
 * <p>There is no live MPD to work from: {@code acceptAdaptiveFormats} routes live to
 * {@link DashManifestParser2}, which fabricates a static manifest out of the adaptive format list.
 * Every property asserted here has already been the cause of a shipped bug, and each failed in a way
 * that does not look like a parser problem from the outside — the stream ends by itself, or the
 * picture freezes while audio keeps playing. That distance between cause and symptom is why these
 * are worth pinning.
 */
public class DashManifestParser2LiveTest {
    private static final long HOURS_48_MS = 48 * 60 * 60 * 1_000L;

    /**
     * A live format has no initialization range, and inventing one is not merely wasteful.
     *
     * <p>{@code parseRangedUrl} with a null range covers the whole source url, which carries no
     * {@code &sq=}, so the server answers with a complete copy of the current live segment. 2.19's
     * {@code InitializationChunk} parses that straight into the real sample queue without ever
     * setting a sample offset, so a whole segment's samples land there in the segment's own time
     * base rather than the manifest's. The audio clock anchors to them, the position leaves the
     * timeline and the picture freezes for as long as it takes something to seek and flush.
     */
    @Test
    public void liveRepresentationHasNoInitializationUri() {
        Representation video = firstVideoRepresentation(
                new DashManifestParser2().parse(TestFormatInfo.live(TestMediaFormat.liveVideo())));

        assertNull(video.getInitializationUri());
    }

    /** The audio track shares the mechanism; it is only quieter about it, being ~40x smaller. */
    @Test
    public void liveAudioRepresentationHasNoInitializationUri() {
        DashManifest manifest = new DashManifestParser2().parse(
                TestFormatInfo.live(TestMediaFormat.liveVideo(), TestMediaFormat.liveAudio()));

        for (Representation representation : allRepresentations(manifest)) {
            assertNull(representation.getInitializationUri());
        }
    }

    /**
     * The guard is on the range being absent, not on the stream being live. If a live format ever
     * does arrive with an init range, that range is real and must still be requested.
     */
    @Test
    public void liveRepresentationKeepsARealInitializationRange() {
        Representation video = firstVideoRepresentation(new DashManifestParser2().parse(
                TestFormatInfo.live(TestMediaFormat.liveVideo().withInit("0-741"))));

        assertNotNull(video.getInitializationUri());
        assertEquals(0, video.getInitializationUri().start);
        assertEquals(742, video.getInitializationUri().length);
    }

    /** Regular videos carry real byte ranges and are untouched by any of this. */
    @Test
    public void regularVideoKeepsItsInitializationRange() {
        Representation video = firstVideoRepresentation(
                new DashManifestParser2().parse(TestFormatInfo.vod(TestMediaFormat.vodVideo())));

        assertNotNull(video.getInitializationUri());
        assertEquals(0, video.getInitializationUri().start);
        assertEquals(742, video.getInitializationUri().length);
    }

    /**
     * A live stream reports no length, and leaving the manifest duration unset ends playback the
     * moment it starts: {@code C.TIME_UNSET} is {@code Long.MIN_VALUE + 1}, so
     * {@code DefaultDashChunkSource}'s clip of a static period against its duration refuses the
     * first media chunk. The synthetic window is what gives that comparison a real bound.
     */
    @Test
    public void liveManifestPublishesTheSyntheticWindowLength() {
        DashManifest manifest = new DashManifestParser2().parse(
                TestFormatInfo.live(TestMediaFormat.liveVideo()));

        assertEquals(HOURS_48_MS, manifest.durationMs);
    }

    /** A premiere reports a real length; that is the truth and must survive. */
    @Test
    public void liveManifestKeepsAReportedLength() {
        DashManifest manifest = new DashManifestParser2().parse(
                TestFormatInfo.live(TestMediaFormat.liveVideo()).withLengthSeconds("3600"));

        assertEquals(3600 * 1_000L, manifest.durationMs);
    }

    /**
     * The segment template's {@code startNumber} and {@code presentationTimeOffset} have to be
     * derived from the same start segment, or media time and segment number stop agreeing: a seek
     * resolves to the wrong {@code &sq=} and the samples that come back are labelled for a different
     * moment. Pinned as the round trip that matters — the first available segment sits at media time
     * zero, and segment N sits N segment-durations later.
     */
    @Test
    public void liveSegmentNumbersAndMediaTimesAgree() {
        Representation video = firstVideoRepresentation(
                new DashManifestParser2().parse(TestFormatInfo.live(TestMediaFormat.liveVideo())));
        DashSegmentIndex index = video.getIndex();

        assertNotNull(index);
        assertEquals(TestFormatInfo.START_SEGMENT_NUM, index.getFirstSegmentNum());
        assertEquals(0, index.getTimeUs(index.getFirstSegmentNum()));
        assertEquals(3L * TestFormatInfo.SEGMENT_DURATION_US,
                index.getTimeUs(index.getFirstSegmentNum() + 3));
        assertEquals(TestFormatInfo.START_SEGMENT_NUM,
                index.getSegmentNum(/* timeUs= */ 0, /* periodDurationUs= */ HOURS_48_MS * 1_000));
    }

    /** The window has to reach the live edge, which is up to a full DVR window ahead of media zero. */
    @Test
    public void liveSegmentTemplateSpansTheWholeSyntheticWindow() {
        Representation video = firstVideoRepresentation(
                new DashManifestParser2().parse(TestFormatInfo.live(TestMediaFormat.liveVideo())));
        DashSegmentIndex index = video.getIndex();

        long segmentCount = index.getSegmentCount(/* periodDurationUs= */ HOURS_48_MS * 1_000);

        assertTrue("expected ~48h of segments, got " + segmentCount,
                segmentCount >= HOURS_48_MS * 1_000 / TestFormatInfo.SEGMENT_DURATION_US);
        assertTrue(index.getTimeUs(index.getFirstSegmentNum() + segmentCount - 1)
                >= HOURS_48_MS * 1_000 - TestFormatInfo.SEGMENT_DURATION_US);
    }

    /** Live segment urls are addressed by sequence number; that is the only handle the server takes. */
    @Test
    public void liveSegmentUrlsCarryTheSequenceNumber() {
        Representation video = firstVideoRepresentation(
                new DashManifestParser2().parse(TestFormatInfo.live(TestMediaFormat.liveVideo())));

        String url = video.getIndex().getSegmentUrl(TestFormatInfo.START_SEGMENT_NUM).resolveUriString(
                video.baseUrls.get(0).url);

        assertTrue(url, url.endsWith("&sq=" + TestFormatInfo.START_SEGMENT_NUM));
    }

    private static Representation firstVideoRepresentation(DashManifest manifest) {
        for (Representation representation : allRepresentations(manifest)) {
            if (representation.format.sampleMimeType != null
                    && representation.format.sampleMimeType.startsWith("video")) {
                return representation;
            }
        }

        throw new AssertionError("no video representation in the manifest");
    }

    private static java.util.List<Representation> allRepresentations(DashManifest manifest) {
        java.util.List<Representation> result = new java.util.ArrayList<>();

        for (int period = 0; period < manifest.getPeriodCount(); period++) {
            for (com.google.android.exoplayer2.source.dash.manifest.AdaptationSet set
                    : manifest.getPeriod(period).adaptationSets) {
                result.addAll(set.representations);
            }
        }

        assertTrue("manifest has no representations", !result.isEmpty());

        return result;
    }
}
