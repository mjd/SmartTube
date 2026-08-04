package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.sharedutils.mylogger.Log;

public class TweaksMediaCodecVideoRenderer extends DebugInfoMediaCodecVideoRenderer {
    private static final String TAG = TweaksMediaCodecVideoRenderer.class.getSimpleName();
    private boolean mIsFrameDropFixEnabled;
    private boolean mIsFrameDropSonyFixEnabled;
    private boolean mIsAmlogicFixEnabled;
    private boolean mIsMtkVp9AdaptationFixEnabled;
    private long mLastAdaptationMs = C.TIME_UNSET;

    // Exo 2.9
    //public CustomMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                     @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener,
    //                                     int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener,
    //            maxDroppedFramesToNotify);
    //}

    // Exo 2.10, 2.11
    public TweaksMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
                                         @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    // Exo 2.12, 2.13
    //public TweaksMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     boolean enableDecoderFallback, @Nullable Handler eventHandler,
    //                                     @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    //}

    // EXO: 2.10, 2.11, 2.12
    @TargetApi(21)
    protected void renderOutputBufferV21(
            MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
        // Fix frame drops on SurfaceView
        // https://github.com/google/ExoPlayer/issues/6348
        // https://developer.android.com/reference/android/media/MediaCodec#releaseOutputBuffer(int,%20long)
        super.renderOutputBufferV21(codec, index, presentationTimeUs, mIsFrameDropFixEnabled ? 0 : releaseTimeNs);
    }

    // EXO: 2.13
    //@TargetApi(21)
    //protected void renderOutputBufferV21(
    //        MediaCodecAdapter codec, int index, long presentationTimeUs, long releaseTimeNs) {
    //    // Fix frame drops on SurfaceView
    //    // https://github.com/google/ExoPlayer/issues/6348
    //    // https://developer.android.com/reference/android/media/MediaCodec#releaseOutputBuffer(int,%20long)
    //    super.renderOutputBufferV21(codec, index, presentationTimeUs, 0);
    //}

    @Override
    protected CodecMaxValues getCodecMaxValues(
            MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
        CodecMaxValues maxValues =
                super.getCodecMaxValues(codecInfo, format, streamFormats);

        if (mIsAmlogicFixEnabled) {
            if (maxValues.width < 1920 || maxValues.height < 1089) {
                Log.d(TAG, "Applying Amlogic fix...");
                return new CodecMaxValues(
                        Math.max(maxValues.width, 1920),
                        Math.max(maxValues.height, 1089),
                        maxValues.inputSize);
            }
        }

        return maxValues;
    }

    /**
     * Seamless adaptation guard for MediaTek hardware VP9 decoders.
     *
     * <p>ExoPlayer normally keeps a live codec instance across an adaptive resolution change
     * (seamless adaptation), trusting the decoder's self-declared "adaptive" capability. Some
     * MediaTek VP9 decoders mishandle that and fault with a message-less IllegalStateException from
     * native MediaCodec deep into playback. Returning KEEP_CODEC_RESULT_NO forces a full codec
     * re-init on the resolution change instead, which is a ~50ms hitch rather than a decoder fault.
     *
     * <p>This also logs every adaptation decision so the correlation between adaptations and
     * decoder faults can be confirmed on-device.
     */
    @Override
    protected @KeepCodecResult int canKeepCodec(
            MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
        @KeepCodecResult int result = super.canKeepCodec(codec, codecInfo, oldFormat, newFormat);

        boolean resolutionChanged =
                oldFormat.width != newFormat.width || oldFormat.height != newFormat.height;

        boolean guardApplied = mIsMtkVp9AdaptationFixEnabled
                && result != KEEP_CODEC_RESULT_NO
                && resolutionChanged
                && isSuspectMtkVp9Decoder(codecInfo);

        if (guardApplied) {
            result = KEEP_CODEC_RESULT_NO;
        }

        logAdaptation(codecInfo, oldFormat, newFormat, result, guardApplied);

        return result;
    }

    /**
     * Whether the decoder is a MediaTek hardware VP9 decoder, matched by name across both the
     * Codec2 ("c2.mtk.vp9.decoder") and legacy OMX ("OMX.MTK.VIDEO.DECODER.VP9") naming schemes.
     */
    private static boolean isSuspectMtkVp9Decoder(MediaCodecInfo codecInfo) {
        if (codecInfo == null || codecInfo.name == null) {
            return false;
        }

        String name = codecInfo.name.toLowerCase();

        return (name.startsWith("c2.mtk.") || name.startsWith("omx.mtk.")) && name.contains("vp9");
    }

    private void logAdaptation(
            MediaCodecInfo codecInfo, Format oldFormat, Format newFormat,
            @KeepCodecResult int result, boolean guardApplied) {
        long nowMs = SystemClock.elapsedRealtime();
        long sinceLastMs = mLastAdaptationMs == C.TIME_UNSET ? -1 : nowMs - mLastAdaptationMs;
        mLastAdaptationMs = nowMs;

        Log.d(TAG, "Codec adaptation: %s | %s -> %s | keepCodec=%s%s | sinceLastMs=%s",
                codecInfo != null ? codecInfo.name : "unknown",
                formatToString(oldFormat),
                formatToString(newFormat),
                result,
                guardApplied ? " (MTK VP9 fix forced reinit)" : "",
                sinceLastMs);
    }

    private static String formatToString(Format format) {
        if (format == null) {
            return "null";
        }

        return format.width + "x" + format.height + "@" + format.frameRate + " " + format.codecs;
    }

    /**
     * Frame drop fixes on Sony Bravia<br/>
     * https://github.com/google/ExoPlayer/issues/6348#issuecomment-718986083
     */
    @Override
    protected boolean isBufferLate(long earlyUs) {
        if (mIsFrameDropSonyFixEnabled) {
            return earlyUs < -1000000;
        }

        return super.isBufferLate(earlyUs);
    }

    /**
     * Frame drop fixes on Sony Bravia<br/>
     * https://github.com/google/ExoPlayer/issues/6348#issuecomment-718986083
     */
    @Override
    protected boolean isBufferVeryLate(long earlyUs) {
        if (mIsFrameDropSonyFixEnabled) {
            return earlyUs < -1500000;
        }

        return super.isBufferVeryLate(earlyUs);
    }

    public void enableFrameDropFix(boolean enabled) {
        mIsFrameDropFixEnabled = enabled;
    }

    public void enableFrameDropSonyFix(boolean enabled) {
        mIsFrameDropSonyFixEnabled = enabled;
    }

    public void enableAmlogicFix(boolean enabled) {
        mIsAmlogicFixEnabled = enabled;
    }

    public void enableMtkVp9AdaptationFix(boolean enabled) {
        mIsMtkVp9AdaptationFixEnabled = enabled;
    }
}
