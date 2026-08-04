package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.BlacklistMediaCodecSelector;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import java.util.ArrayList;

/**
 * Main intent: override audio delay
 */
public class CustomOverridesRenderersFactory extends CustomRenderersFactoryBase {
    private static final String TAG = CustomOverridesRenderersFactory.class.getSimpleName();
    private static final String[] FRAME_DROP_FIX_LIST = {
            "T95ZPLUS (q201_3GB)",
            "UGOOS (UGOOS)",
            "55UC30G (ctl_iptv_mrvl)" // Kivi 55uc30g
    };
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;
    // 2.12, 2.13
    //private int mOperationMode = MediaCodecRenderer.OPERATION_MODE_SYNCHRONOUS;

    // 2.9, 2.10, 2.11
    public CustomOverridesRenderersFactory(Context activity) {
        super(activity);

        mPlayerData = PlayerData.instance(activity);
        mPlayerTweaksData = PlayerTweaksData.instance(activity);

        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
        // setEnableDecoderFallback(true); // Exo 2.10 and up

        if (mPlayerTweaksData.isSWDecoderForced()) {
            setMediaCodecSelector(new BlacklistMediaCodecSelector());
        }

        // AmazonQuirks was an Amazon-port-only class that does not exist upstream, so its two
        // settings had to find new homes.
        //
        // Profile/level check skipping is reimplemented in TweaksMediaCodecVideoRenderer via
        // MediaCodecUtil.getDecoderInfosSoftMatch, which is upstream's equivalent.
        //
        // Vsync snapping has no equivalent: the quirk swapped in a Context-less
        // VideoFrameReleaseTimeHelper, and its successor VideoFrameReleaseHelper is constructed
        // internally by MediaCodecVideoRenderer with no hook to replace it. That setting is
        // currently inert -- see MIGRATION_STATUS.md.
    }

    // 2.12, 2.13
    //public CustomOverridesRenderersFactory(Context activity) {
    //    super(activity);
    //
    //    mPlayerData = PlayerData.instance(activity);
    //    mPlayerTweaksData = PlayerTweaksData.instance(activity);
    //
    //    // Exo 2.12 (Exclusive experimental tweaks)
    //    //mOperationMode = MediaCodecRenderer.OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD_ASYNCHRONOUS_QUEUEING;
    //    //experimentalSetMediaCodecOperationMode(mOperationMode);
    //
    //    setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
    //    // setEnableDecoderFallback(true); // Exo 2.10 and up
    //
    //    if (mPlayerTweaksData.isSWDecoderForced()) {
    //        setMediaCodecSelector(new BlacklistMediaCodecSelector());
    //    }
    //
    //    AmazonQuirks.skipProfileLevelCheck(mPlayerTweaksData.isProfileLevelCheckSkipped());
    //}

    // Exo 2.9
    //@Override
    //protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
    //                                   @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                   AudioProcessor[] audioProcessors, Handler eventHandler, AudioRendererEventListener eventListener,
    //                                   ArrayList<Renderer> out) {
    //    super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys,
    //            audioProcessors, eventHandler, eventListener, out);
    //
    //    CustomMediaCodecAudioRenderer audioRenderer = null;
    //
    //    if (mPlayerData.getAudioDelayMs() != 0) {
    //        audioRenderer =
    //                new CustomMediaCodecAudioRenderer(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler,
    //                        eventListener, new DefaultAudioSink(AudioCapabilities.getCapabilities(context), audioProcessors));
    //
    //        audioRenderer.setAudioDelayMs(mPlayerData.getAudioDelayMs());
    //    }
    //
    //    replaceAudioRenderer(out, audioRenderer);
    //}

    // Exo 2.9
    //@Override
    //protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
    //                                   @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                   Handler eventHandler, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs,
    //                                   ArrayList<Renderer> out) {
    //    super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler,
    //            eventListener, allowedVideoJoiningTimeMs, out);
    //
    //    CustomMediaCodecVideoRenderer videoRenderer = null;
    //
    //    if (mPlayerTweaksData.isFrameDropFixEnabled() || mPlayerTweaksData.isAmlogicFixEnabled()) {
    //        videoRenderer = new CustomMediaCodecVideoRenderer(context, mediaCodecSelector, allowedVideoJoiningTimeMs, drmSessionManager,
    //                playClearSamplesWithoutKeys, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    //
    //        videoRenderer.enableFrameDropFix(mPlayerTweaksData.isFrameDropFixEnabled());
    //        videoRenderer.enableAmlogicFix(mPlayerTweaksData.isAmlogicFixEnabled());
    //    }
    //
    //    replaceVideoRenderer(out, videoRenderer);
    //}

    // 2.12+ (DRM parameters and the AudioProcessor[] were dropped upstream; the audio sink is now
    // passed in directly and built with DefaultAudioSink.Builder)
    @Override
    protected void buildAudioRenderers(Context context, @ExtensionRendererMode int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
                                       boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler,
                                       AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector,
                enableDecoderFallback, audioSink, eventHandler, eventListener, out);

        if ((mPlayerData.getAudioDelayMs() == 0 || !mPlayerData.isAudioDelayEnabled()) && !mPlayerTweaksData.isAudioSyncFixEnabled()) {
            // Improve performance a bit by eliminating calculations presented in custom renderer.

            return;
        }

        DelayMediaCodecAudioRenderer audioRenderer =
                new DelayMediaCodecAudioRenderer(context, mediaCodecSelector, enableDecoderFallback,
                        eventHandler, eventListener, audioSink);

        audioRenderer.setAudioDelayMs(mPlayerData.isAudioDelayEnabled() ? mPlayerData.getAudioDelayMs() : 0);
        audioRenderer.enableAudioSyncFix(mPlayerTweaksData.isAudioSyncFixEnabled());

        replaceAudioRenderer(out, audioRenderer);
    }

    // 2.12+ (DRM parameters dropped upstream)
    @Override
    protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
                                       boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener,
                                       long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector,
                enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
        
        if (!mPlayerTweaksData.isAmazonFrameDropFixEnabled() && !mPlayerTweaksData.isSonyFrameDropFixEnabled() && !mPlayerTweaksData.isAmlogicFixEnabled()
                && !mPlayerTweaksData.isMtkVp9AdaptationFixEnabled()
                && !mPlayerTweaksData.isProfileLevelCheckSkipped()) {
            // Improve performance a bit by eliminating some if conditions presented in tweaks.
            // But we need to obtain codec real name somehow. So use interceptor below.

            DebugInfoMediaCodecVideoRenderer videoRenderer =
                    new DebugInfoMediaCodecVideoRenderer(context, mediaCodecSelector, allowedVideoJoiningTimeMs,
                        enableDecoderFallback, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);

            videoRenderer.enableSetOutputSurfaceWorkaround(true); // Force enable?

            replaceVideoRenderer(out, videoRenderer);

            return;
        }

        TweaksMediaCodecVideoRenderer videoRenderer =
                new TweaksMediaCodecVideoRenderer(context, mediaCodecSelector, allowedVideoJoiningTimeMs,
                        enableDecoderFallback, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);

        videoRenderer.enableFrameDropFix(mPlayerTweaksData.isAmazonFrameDropFixEnabled());
        videoRenderer.enableFrameDropSonyFix(mPlayerTweaksData.isSonyFrameDropFixEnabled());
        videoRenderer.enableAmlogicFix(mPlayerTweaksData.isAmlogicFixEnabled());
        videoRenderer.enableMtkVp9AdaptationFix(mPlayerTweaksData.isMtkVp9AdaptationFixEnabled());
        videoRenderer.skipProfileLevelCheck(mPlayerTweaksData.isProfileLevelCheckSkipped());
        videoRenderer.enableSetOutputSurfaceWorkaround(true); // Force enable?

        replaceVideoRenderer(out, videoRenderer);
    }

    // Exo 2.12, 2.13
    //@Override
    //protected void buildAudioRenderers(Context context,
    //                                   int extensionRendererMode,
    //                                   MediaCodecSelector mediaCodecSelector,
    //                                   boolean enableDecoderFallback,
    //                                   AudioSink audioSink,
    //                                   Handler eventHandler,
    //                                   AudioRendererEventListener eventListener,
    //                                   ArrayList<Renderer> out) {
    //    super.buildAudioRenderers(
    //            context,
    //            extensionRendererMode,
    //            mediaCodecSelector,
    //            enableDecoderFallback,
    //            audioSink,
    //            eventHandler,
    //            eventListener,
    //            out);
    //
    //    if (mPlayerData.getAudioDelayMs() == 0) {
    //        // Improve performance a bit by eliminating calculations presented in custom renderer.
    //
    //        return;
    //    }
    //
    //    DelayMediaCodecAudioRenderer audioRenderer =
    //            new DelayMediaCodecAudioRenderer(context,
    //                    mediaCodecSelector,
    //                    enableDecoderFallback,
    //                    eventHandler,
    //                    eventListener,
    //                    audioSink);
    //
    //    audioRenderer.setAudioDelayMs(mPlayerData.getAudioDelayMs());
    //
    //    // Restore global operation mode (needed for stability)
    //    //audioRenderer.experimentalSetMediaCodecOperationMode(mOperationMode);
    //
    //    replaceAudioRenderer(out, audioRenderer);
    //}

    // Exo 2.12, 2.13
    //@Override
    //protected void buildVideoRenderers(Context context,
    //                                   int extensionRendererMode,
    //                                   MediaCodecSelector mediaCodecSelector,
    //                                   boolean enableDecoderFallback,
    //                                   Handler eventHandler,
    //                                   VideoRendererEventListener eventListener,
    //                                   long allowedVideoJoiningTimeMs,
    //                                   ArrayList<Renderer> out) {
    //    super.buildVideoRenderers(
    //            context,
    //            extensionRendererMode,
    //            mediaCodecSelector,
    //            enableDecoderFallback,
    //            eventHandler,
    //            eventListener,
    //            allowedVideoJoiningTimeMs,
    //            out);
    //
    //
    //    if (!mPlayerTweaksData.isFrameDropFixEnabled() && !mPlayerTweaksData.isAmlogicFixEnabled()) {
    //        // Improve performance a bit by eliminating some if conditions presented in tweaks.
    //        // But we need to obtain codec real name somehow. So use interceptor below.
    //
    //        DebugInfoMediaCodecVideoRenderer videoRenderer =
    //                new DebugInfoMediaCodecVideoRenderer(context,
    //                        mediaCodecSelector,
    //                        allowedVideoJoiningTimeMs,
    //                        enableDecoderFallback,
    //                        eventHandler,
    //                        eventListener,
    //                        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    //
    //        // Restore global operation mode (needed for stability)
    //        //videoRenderer.experimentalSetMediaCodecOperationMode(mOperationMode);
    //        videoRenderer.enableSetOutputSurfaceWorkaround(mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled());
    //
    //        replaceVideoRenderer(out, videoRenderer);
    //
    //        return;
    //    }
    //
    //    TweaksMediaCodecVideoRenderer videoRenderer =
    //            new TweaksMediaCodecVideoRenderer(context,
    //                    mediaCodecSelector,
    //                    allowedVideoJoiningTimeMs,
    //                    enableDecoderFallback,
    //                    eventHandler,
    //                    eventListener,
    //                    MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    //
    //    videoRenderer.enableFrameDropFix(mPlayerTweaksData.isFrameDropFixEnabled());
    //    videoRenderer.enableAmlogicFix(mPlayerTweaksData.isAmlogicFixEnabled());
    //    videoRenderer.enableSetOutputSurfaceWorkaround(mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled());
    //
    //    // Restore global operation mode (needed for stability)
    //    //videoRenderer.experimentalSetMediaCodecOperationMode(mOperationMode);
    //
    //    replaceVideoRenderer(out, videoRenderer);
    //}
}
