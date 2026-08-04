package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins which decoders the MediaTek VP9 adaptation guard applies to.
 *
 * <p>The guard forces a full codec re-init instead of seamless adaptation, which costs a visible
 * hitch on every resolution change. That is the right trade only on decoders that actually fault,
 * so both halves of this predicate carry a cost when wrong: too narrow and the fault it exists to
 * prevent still happens, too broad and unaffected hardware pays the hitch for nothing.
 *
 * <p>The real decoder names on the affected Hisense/MediaTek Google TV are asserted directly —
 * {@code c2.mtk.vp9.decoder} and its {@code .secure} variant — alongside the AVC decoder on the same
 * device, which must <b>not</b> match.
 */
public class DecoderQuirksTest {
    /** The decoder that motivated the guard, as reported by the affected device. */
    @Test
    public void matchesCodec2MtkVp9() {
        assertTrue(DecoderQuirks.isSuspectMtkVp9Decoder("c2.mtk.vp9.decoder"));
    }

    /**
     * The secure variant is a distinct decoder instance with the same underlying implementation, so
     * it carries the same fault and must match too.
     */
    @Test
    public void matchesCodec2MtkVp9Secure() {
        assertTrue(DecoderQuirks.isSuspectMtkVp9Decoder("c2.mtk.vp9.decoder.secure"));
    }

    /** Legacy OMX naming, still used by older MediaTek firmware. Matching is case-insensitive. */
    @Test
    public void matchesLegacyOmxMtkVp9() {
        assertTrue(DecoderQuirks.isSuspectMtkVp9Decoder("OMX.MTK.VIDEO.DECODER.VP9"));
        assertTrue(DecoderQuirks.isSuspectMtkVp9Decoder("omx.mtk.video.decoder.vp9"));
    }

    /**
     * Same vendor, different codec. AVC on this hardware is unaffected, and forcing re-inits on it
     * would impose the hitch for no benefit.
     */
    @Test
    public void doesNotMatchMtkAvc() {
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder("c2.mtk.avc.decoder"));
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder("OMX.MTK.VIDEO.DECODER.AVC"));
    }

    /** VP9 from other vendors is out of scope — the fault is MediaTek's, not VP9's. */
    @Test
    public void doesNotMatchOtherVendorVp9() {
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder("c2.android.vp9.decoder"));
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder("OMX.google.vp9.decoder"));
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder("c2.exynos.vp9.decoder"));
    }

    /**
     * The vendor prefix is anchored at the start. A decoder that merely mentions the vendor
     * elsewhere in its name is a different implementation and must not be caught.
     */
    @Test
    public void requiresVendorPrefixAtStart() {
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder("c2.vendor.mtk.vp9.decoder"));
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder("vp9.c2.mtk.decoder"));
    }

    /**
     * Encoders share the vendor prefix and the codec name but are never involved in playback
     * adaptation. Matching one would be harmless in practice only because the guard is reached from
     * a video renderer -- so this pins intent rather than a live failure.
     */
    @Test
    public void matchesOnlyOnBothVendorAndCodec() {
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder("c2.mtk.avc.encoder"));
        assertTrue(DecoderQuirks.isSuspectMtkVp9Decoder("c2.mtk.vp9.encoder"));
    }

    /**
     * {@code MediaCodecInfo.name} is annotated non-null upstream, but this runs on devices with
     * vendor-modified frameworks and the guard sits on the hot adaptation path -- it must not be
     * what turns a codec change into a crash.
     */
    @Test
    public void nullNameIsNotSuspect() {
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder(null));
        assertFalse(DecoderQuirks.isSuspectMtkVp9Decoder(""));
    }
}
