package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

/**
 * Decoder quirks matched purely by codec name.
 *
 * <p>Split out of {@link TweaksMediaCodecVideoRenderer} so the matching rules can be tested. The
 * renderer's own copy took a {@code MediaCodecInfo}, which cannot be constructed in a plain-JUnit
 * test — it is built from a live {@code android.media.MediaCodecInfo.CodecCapabilities}. The rule
 * being verified is really about the name string, so it belongs somewhere that a test can reach
 * without an Android runtime.
 *
 * <p>Keep this class free of Android and player types.
 */
public final class DecoderQuirks {
    private DecoderQuirks() {
    }

    /**
     * Whether the decoder is a MediaTek hardware VP9 decoder.
     *
     * <p>Matched across both naming schemes in the wild: Codec2 ({@code c2.mtk.vp9.decoder}) and
     * legacy OMX ({@code OMX.MTK.VIDEO.DECODER.VP9}). The vendor prefix is required rather than
     * just looking for "mtk" anywhere, so that a third-party decoder mentioning the vendor in a
     * suffix is not caught by accident.
     *
     * <p>These decoders advertise seamless adaptation but can fault with a message-less
     * {@code IllegalStateException} from native MediaCodec when a live codec is reused across a
     * resolution change. Callers use this to force a full codec re-init instead.
     */
    public static boolean isSuspectMtkVp9Decoder(String codecName) {
        if (codecName == null) {
            return false;
        }

        String name = codecName.toLowerCase();

        return (name.startsWith("c2.mtk.") || name.startsWith("omx.mtk.")) && name.contains("vp9");
    }
}
