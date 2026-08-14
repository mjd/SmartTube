package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * Pins the codec-preference weighting that drives video track selection.
 *
 * <p>SmartTube does not rank codecs with a comparator. It converts the codec into a small integer
 * and <b>adds it to the pixel width</b>, then sorts on the sum (see
 * {@code TrackSelectorManager.MediaTrackFormatComparator}, which computes
 * {@code width + frameRate + codecWeight}). Codec preference is therefore a ±3 nudge on a number
 * whose dominant term is around 1920 — deliberately weak, so it only breaks ties between otherwise
 * comparable renditions.
 *
 * <p>That makes the exact weights load-bearing in a way their size disguises: VP9 (31) beats AVC
 * (28) beats AV1 (14), and the gaps are small enough that changing any of them alters which track
 * gets picked for real videos. This is the logic the planned track-selection rewrite has to
 * preserve while the surrounding API changes underneath it, so it is pinned here first.
 *
 * <p>Note {@link MediaTrack#setAvcOverVp9Preferred(boolean)} mutates <b>static</b> state, so these
 * tests reset it in {@link #resetCodecPreference()} — otherwise ordering between test classes would
 * leak.
 */
public class MediaTrackCodecWeightTest {
    private static final int VP9_WEIGHT = 31;
    private static final int AVC_WEIGHT = 28;
    private static final int AV1_WEIGHT = 14;
    private static final int UNKNOWN_WEIGHT = 0;

    @After
    public void resetCodecPreference() {
        MediaTrack.setAvcOverVp9Preferred(false);
    }

    @Test
    public void weights_areOrderedVp9ThenAvcThenAv1() {
        assertEquals(VP9_WEIGHT, MediaTrack.getCodecWeight("vp9"));
        assertEquals(AVC_WEIGHT, MediaTrack.getCodecWeight("avc1.640028"));
        assertEquals(AV1_WEIGHT, MediaTrack.getCodecWeight("av01.0.08M.08"));

        assertTrue("VP9 must outrank AVC by default",
                MediaTrack.getCodecWeight("vp9") > MediaTrack.getCodecWeight("avc1.640028"));
        assertTrue("AVC must outrank AV1",
                MediaTrack.getCodecWeight("avc1.640028") > MediaTrack.getCodecWeight("av01.0.08M.08"));
    }

    @Test
    public void weights_recogniseBothVp9Spellings() {
        // YouTube uses both "vp9" and the RFC 6381 "vp09..." form.
        assertEquals(VP9_WEIGHT, MediaTrack.getCodecWeight("vp9"));
        assertEquals(VP9_WEIGHT, MediaTrack.getCodecWeight("vp09.00.10.08"));
    }

    @Test
    public void weights_areCaseInsensitive() {
        assertEquals(VP9_WEIGHT, MediaTrack.getCodecWeight("VP9"));
        assertEquals(AVC_WEIGHT, MediaTrack.getCodecWeight("AVC1.640028"));
    }

    @Test
    public void weights_unknownAndNullCodecsScoreZero() {
        // Zero means "no nudge", so an unrecognised codec sorts purely on resolution and fps
        // rather than being pushed to the bottom.
        assertEquals(UNKNOWN_WEIGHT, MediaTrack.getCodecWeight("opus"));
        assertEquals(UNKNOWN_WEIGHT, MediaTrack.getCodecWeight((String) null));
        assertEquals(UNKNOWN_WEIGHT, MediaTrack.getCodecWeight(""));
    }

    @Test
    public void setAvcOverVp9Preferred_swapsOnlyThoseTwoWeights() {
        MediaTrack.setAvcOverVp9Preferred(true);

        assertEquals("AVC takes VP9's weight", VP9_WEIGHT, MediaTrack.getCodecWeight("avc1.640028"));
        assertEquals("VP9 takes AVC's weight", AVC_WEIGHT, MediaTrack.getCodecWeight("vp9"));
        assertEquals("AV1 is untouched by the swap", AV1_WEIGHT, MediaTrack.getCodecWeight("av01.0.08M.08"));

        assertTrue("AVC must now outrank VP9",
                MediaTrack.getCodecWeight("avc1.640028") > MediaTrack.getCodecWeight("vp9"));
    }

    @Test
    public void setAvcOverVp9Preferred_isReversible() {
        MediaTrack.setAvcOverVp9Preferred(true);
        MediaTrack.setAvcOverVp9Preferred(false);

        assertEquals(VP9_WEIGHT, MediaTrack.getCodecWeight("vp9"));
        assertEquals(AVC_WEIGHT, MediaTrack.getCodecWeight("avc1.640028"));
    }

    @Test
    public void codecNudgeIsSmallRelativeToResolution() {
        // The guard rail that makes the whole scheme work: the codec term must never outweigh a
        // genuine resolution difference. 1280p VP9 must not beat 1920p AVC on the summed score.
        int vp9At1280 = 1280 + MediaTrack.getCodecWeight("vp9");
        int avcAt1920 = 1920 + MediaTrack.getCodecWeight("avc1.640028");

        assertTrue("resolution must dominate codec preference", avcAt1920 > vp9At1280);

        // ...but it must still break a tie at equal resolution.
        int vp9At1920 = 1920 + MediaTrack.getCodecWeight("vp9");
        assertTrue("codec must break ties at equal resolution", vp9At1920 > avcAt1920);
    }

    @Test
    public void preferByCodec_comparesWeightsOfTwoTracks() {
        MediaTrack vp9 = trackWithCodec("vp9");
        MediaTrack avc = trackWithCodec("avc1.640028");

        assertTrue(MediaTrack.preferByCodec(vp9, avc));
        assertFalse(MediaTrack.preferByCodec(avc, vp9));
        assertFalse("equal weights are not a preference", MediaTrack.preferByCodec(vp9, trackWithCodec("vp09.00.10.08")));
    }

    @Test
    public void getCodecWeight_isNullSafeForTracks() {
        assertEquals(UNKNOWN_WEIGHT, MediaTrack.getCodecWeight((MediaTrack) null));
        assertEquals("a track with no format scores zero", UNKNOWN_WEIGHT,
                MediaTrack.getCodecWeight(new VideoTrack(0)));
    }

    private static MediaTrack trackWithCodec(String codecs) {
        VideoTrack track = new VideoTrack(0);
        track.format = new com.google.android.exoplayer2.Format.Builder()
                .setId("1")
                .setCodecs(codecs)
                .setWidth(1920)
                .setHeight(1080)
                .setFrameRate(30f)
                .build();
        return track;
    }
}
