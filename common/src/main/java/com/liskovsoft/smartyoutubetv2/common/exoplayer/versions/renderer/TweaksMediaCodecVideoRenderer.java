package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import java.util.List;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;

public class TweaksMediaCodecVideoRenderer extends DebugInfoMediaCodecVideoRenderer {
    private static final String TAG = TweaksMediaCodecVideoRenderer.class.getSimpleName();
    private boolean mIsFrameDropFixEnabled;
    private boolean mIsFrameDropSonyFixEnabled;
    private boolean mIsAmlogicFixEnabled;
    private boolean mIsMtkVp9AdaptationFixEnabled;
    private boolean mIsProfileLevelCheckSkipped;
    private long mLastAdaptationMs = C.TIME_UNSET;

    // Exo 2.9
    //public CustomMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                     @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener,
    //                                     int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener,
    //            maxDroppedFramesToNotify);
    //}

    // Exo 2.12+ (DRM parameters dropped from renderer constructors upstream)
    public TweaksMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
                                         boolean enableDecoderFallback, @Nullable Handler eventHandler,
                                         @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    @TargetApi(21)
    @Override
    protected void renderOutputBufferV21(
            MediaCodecAdapter codec, int index, long presentationTimeUs, long releaseTimeNs) {
        // Fix frame drops on SurfaceView
        // https://github.com/google/ExoPlayer/issues/6348
        // https://developer.android.com/reference/android/media/MediaCodec#releaseOutputBuffer(int,%20long)
        super.renderOutputBufferV21(codec, index, presentationTimeUs, mIsFrameDropFixEnabled ? 0 : releaseTimeNs);
    }

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
     * native MediaCodec deep into playback. Returning {@code REUSE_RESULT_NO} forces a full codec
     * re-init on the resolution change instead, which is a brief hitch rather than a decoder fault.
     *
     * <p>This also logs every adaptation decision so the correlation between adaptations and
     * decoder faults can be confirmed on-device.
     */
    @Override
    protected DecoderReuseEvaluation canReuseCodec(
            MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
        DecoderReuseEvaluation evaluation = super.canReuseCodec(codecInfo, oldFormat, newFormat);

        boolean resolutionChanged =
                oldFormat.width != newFormat.width || oldFormat.height != newFormat.height;

        boolean guardApplied = mIsMtkVp9AdaptationFixEnabled
                && evaluation.result != DecoderReuseEvaluation.REUSE_RESULT_NO
                && resolutionChanged
                && isSuspectMtkVp9Decoder(codecInfo);

        if (guardApplied) {
            // Report it as a workaround-driven discard so the reason survives into playback
            // analytics, rather than masquerading as an ordinary resolution change.
            evaluation = new DecoderReuseEvaluation(
                    codecInfo.name,
                    oldFormat,
                    newFormat,
                    DecoderReuseEvaluation.REUSE_RESULT_NO,
                    DecoderReuseEvaluation.DISCARD_REASON_WORKAROUND);
        }

        logAdaptation(codecInfo, oldFormat, newFormat, evaluation.result, guardApplied);

        return evaluation;
    }

    /**
     * Whether the decoder is a MediaTek hardware VP9 decoder. The name matching itself lives in
     * {@link DecoderQuirks}, which is free of player types and therefore unit-testable.
     */
    private static boolean isSuspectMtkVp9Decoder(MediaCodecInfo codecInfo) {
        return codecInfo != null && DecoderQuirks.isSuspectMtkVp9Decoder(codecInfo.name);
    }

    /**
     * Traces every adaptation decision so the MediaTek VP9 guard above can be confirmed to fire on
     * the affected hardware. Debug builds only: {@code Log.d} is not compiled out in release, and a
     * release build has no use for it.
     */
    private void logAdaptation(
            MediaCodecInfo codecInfo, Format oldFormat, Format newFormat,
            int result, boolean guardApplied) {
        long nowMs = SystemClock.elapsedRealtime();
        long sinceLastMs = mLastAdaptationMs == C.TIME_UNSET ? -1 : nowMs - mLastAdaptationMs;
        mLastAdaptationMs = nowMs;

        if (!BuildConfig.DEBUG) {
            return;
        }

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
     * Frame drop fixes on Sony Bravia.
     * https://github.com/google/ExoPlayer/issues/6348#issuecomment-718986083
     *
     * <p>The upstream lateness checks ({@code isBufferLate} / {@code isBufferVeryLate}) are
     * {@code private static}, so they cannot be overridden — the vendored fork used to widen them
     * to {@code protected}, which is not an option against a published artifact. Instead the
     * decision is intercepted one level up, in {@code shouldDropOutputBuffer} and friends, by
     * short-circuiting on the Sony thresholds before deferring to the default behaviour.
     *
     * <p>The thresholds are the fork's: a buffer counts as late past 1s and very late past 1.5s,
     * far more permissive than upstream's 30ms/500ms, which is what stops these panels from
     * dropping frames they could still have displayed.
     */
    private static final long SONY_BUFFER_LATE_US = -1_000_000;
    private static final long SONY_BUFFER_VERY_LATE_US = -1_500_000;

    @Override
    protected boolean shouldDropOutputBuffer(long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
        if (mIsFrameDropSonyFixEnabled) {
            return earlyUs < SONY_BUFFER_LATE_US && !isLastBuffer;
        }

        return super.shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs, isLastBuffer);
    }

    @Override
    protected boolean shouldDropBuffersToKeyframe(long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
        if (mIsFrameDropSonyFixEnabled) {
            return earlyUs < SONY_BUFFER_VERY_LATE_US && !isLastBuffer;
        }

        return super.shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs, isLastBuffer);
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

    public void skipProfileLevelCheck(boolean skip) {
        mIsProfileLevelCheckSkipped = skip;
    }

    /**
     * Replaces the Amazon port's {@code AmazonQuirks.skipProfileLevelCheck}.
     *
     * <p>Some devices under-report the profile and level they can actually decode, so a format that
     * would play fine is rejected outright. The quirk made the capability check pass
     * unconditionally; upstream has an equivalent in {@code getDecoderInfosSoftMatch}, which returns
     * decoders matching the mime type without requiring the format's profile and level to be
     * advertised.
     */
    @Override
    protected List<MediaCodecInfo> getDecoderInfos(
            MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
            throws MediaCodecUtil.DecoderQueryException {
        if (mIsProfileLevelCheckSkipped) {
            Log.d(TAG, "Skipping codec profile/level check for %s", format.codecs);
            return MediaCodecUtil.getDecoderInfosSoftMatch(
                    mediaCodecSelector, format, requiresSecureDecoder, /* requiresTunnelingDecoder= */ false);
        }

        return super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder);
    }
}
