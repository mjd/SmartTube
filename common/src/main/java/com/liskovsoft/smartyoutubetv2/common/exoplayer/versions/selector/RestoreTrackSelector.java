package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector;

import android.content.Context;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection.Definition;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection.Factory;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

public class RestoreTrackSelector extends DefaultTrackSelector {
    private static final String TAG = RestoreTrackSelector.class.getSimpleName();
    private TrackSelectorCallback mCallback;

    public interface TrackSelectorCallback {
        Pair<Definition, MediaTrack> onSelectVideoTrack(TrackGroupArray groups, Parameters params);
        Pair<Definition, MediaTrack> onSelectAudioTrack(TrackGroupArray groups, Parameters params);
        Pair<Definition, MediaTrack> onSelectSubtitleTrack(TrackGroupArray groups, Parameters params);
        void updateVideoTrackSelection(TrackGroupArray groups, Parameters params, Definition definition);
        void updateAudioTrackSelection(TrackGroupArray groups, Parameters params, Definition definition);
        void updateSubtitleTrackSelection(TrackGroupArray groups, Parameters params, Definition definition);
    }

    /**
     * A {@link Context} is now required: {@code DefaultTrackSelector} uses it to derive
     * device-dependent defaults such as the display size, so the factory-only constructor was
     * removed upstream.
     */
    public RestoreTrackSelector(Context context, Factory trackSelectionFactory) {
        super(context, trackSelectionFactory);
        // Could help with Shield resolution bug?
        //setParameters(buildUponParameters().setForceHighestSupportedBitrate(true));
    }

    public void setOnTrackSelectCallback(TrackSelectorCallback callback) {
        mCallback = callback;
    }

    // Exo 2.9
    //@Nullable
    //@Override
    //protected TrackSelection selectVideoTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports,
    //                                          Parameters params, @Nullable Factory adaptiveTrackSelectionFactory) throws ExoPlaybackException {
    //    if (mCallback != null) {
    //        Pair<Definition, MediaTrack> resultPair = mCallback.onSelectVideoTrack(groups, params);
    //
    //        if (resultPair != null) {
    //            Log.d(TAG, "selectVideoTrack: choose custom video processing");
    //            return resultPair.first.toSelection();
    //        }
    //    }
    //
    //    Log.d(TAG, "selectVideoTrack: choose default video processing");
    //
    //    TrackSelection trackSelection = super.selectVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, adaptiveTrackSelectionFactory);
    //
    //    // Don't invoke if track already has been selected by the app
    //    if (mCallback != null && trackSelection != null && !params.hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_VIDEO, groups)) {
    //        mCallback.updateVideoTrackSelection(groups, params, Definition.from(trackSelection));
    //    }
    //
    //    return trackSelection;
    //}

    // Exo 2.9
    //@Nullable
    //@Override
    //protected Pair<TrackSelection, AudioTrackScore> selectAudioTrack(TrackGroupArray groups, int[][] formatSupports,
    //                                                                 int mixedMimeTypeAdaptationSupports, Parameters params,
    //                                                                 @Nullable Factory adaptiveTrackSelectionFactory) throws ExoPlaybackException {
    //    if (mCallback != null) {
    //        Pair<Definition, MediaTrack> resultPair = mCallback.onSelectAudioTrack(groups, params);
    //        if (resultPair != null) {
    //            Log.d(TAG, "selectVideoTrack: choose custom audio processing");
    //            return new Pair<>(resultPair.first.toSelection(), new AudioTrackScore(resultPair.second.format, params, RendererCapabilities.FORMAT_HANDLED));
    //        }
    //    }
    //
    //    Log.d(TAG, "selectAudioTrack: choose default audio processing");
    //
    //    Pair<TrackSelection, AudioTrackScore> selectionPair =
    //            super.selectAudioTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, adaptiveTrackSelectionFactory);
    //
    //    // Don't invoke if track already has been selected by the app
    //    if (mCallback != null && selectionPair != null && !params.hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_AUDIO, groups)) {
    //        mCallback.updateAudioTrackSelection(groups, params, Definition.from(selectionPair.first));
    //    }
    //
    //    return selectionPair;
    //}

    // Exo 2.9
    //@Nullable
    //@Override
    //protected Pair<TrackSelection, Integer> selectTextTrack(TrackGroupArray groups, int[][] formatSupport, Parameters params) throws ExoPlaybackException {
    //    if (mCallback != null) {
    //        Pair<Definition, MediaTrack> resultPair = mCallback.onSelectSubtitleTrack(groups, params);
    //        if (resultPair != null) {
    //            Log.d(TAG, "selectTextTrack: choose custom text processing");
    //            return new Pair<>(resultPair.first.toSelection(), 10);
    //        }
    //    }
    //
    //    Log.d(TAG, "selectTextTrack: choose default text processing");
    //
    //    Pair<TrackSelection, Integer> selectionPair = super.selectTextTrack(groups, formatSupport, params);
    //
    //    // Don't invoke if track already has been selected by the app
    //    if (mCallback != null && selectionPair != null && !params.hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_SUBTITLE, groups)) {
    //        mCallback.updateSubtitleTrackSelection(groups, params, Definition.from(selectionPair.first));
    //    }
    //
    //    return selectionPair;
    //}

    //@Override
    //public void setParameters(Parameters parameters) {
    //    // Fix dropping to 144p by disabling any overrides.
    //    invalidate();
    //}

    @Nullable
    @Override
    protected Pair<Definition, Integer> selectVideoTrack(MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports,
                                                         int[] rendererMixedMimeTypeAdaptationSupports,
                                                         Parameters params) throws ExoPlaybackException {
        int rendererIndex = findRendererIndex(mappedTrackInfo, C.TRACK_TYPE_VIDEO);

        if (mCallback != null && rendererIndex != C.INDEX_UNSET) {
            TrackGroupArray groups = mappedTrackInfo.getTrackGroups(rendererIndex);
            Pair<Definition, MediaTrack> resultPair = mCallback.onSelectVideoTrack(groups, params);

            if (resultPair != null) {
                Log.d(TAG, "selectVideoTrack: choose custom video processing");
                return Pair.create(resultPair.first, rendererIndex);
            } else {
                return null; // video disabled
            }
        }

        Log.d(TAG, "selectVideoTrack: choose default video processing");

        Pair<Definition, Integer> result = super.selectVideoTrack(
                mappedTrackInfo, rendererFormatSupports, rendererMixedMimeTypeAdaptationSupports, params);

        // Don't invoke if track already has been selected by the app
        if (mCallback != null && result != null) {
            mCallback.updateVideoTrackSelection(mappedTrackInfo.getTrackGroups(result.second), params, result.first);
        }

        return result;
    }

    @Nullable
    @Override
    protected Pair<Definition, Integer> selectAudioTrack(MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports,
                                                         int[] rendererMixedMimeTypeAdaptationSupports,
                                                         Parameters params) throws ExoPlaybackException {
        int rendererIndex = findRendererIndex(mappedTrackInfo, C.TRACK_TYPE_AUDIO);

        if (mCallback != null && rendererIndex != C.INDEX_UNSET) {
            TrackGroupArray groups = mappedTrackInfo.getTrackGroups(rendererIndex);
            Pair<Definition, MediaTrack> resultPair = mCallback.onSelectAudioTrack(groups, params);
            if (resultPair != null) {
                Log.d(TAG, "selectAudioTrack: choose custom audio processing");
                return Pair.create(resultPair.first, rendererIndex);
            } else {
                return null; // audio disabled
            }
        }

        Log.d(TAG, "selectAudioTrack: choose default audio processing");

        Pair<Definition, Integer> result = super.selectAudioTrack(
                mappedTrackInfo, rendererFormatSupports, rendererMixedMimeTypeAdaptationSupports, params);

        // Don't invoke if track already has been selected by the app
        if (mCallback != null && result != null) {
            mCallback.updateAudioTrackSelection(mappedTrackInfo.getTrackGroups(result.second), params, result.first);
        }

        return result;
    }

    @Nullable
    @Override
    protected Pair<Definition, Integer> selectTextTrack(MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports,
                                                        Parameters params,
                                                        @Nullable String selectedAudioLanguage) throws ExoPlaybackException {
        int rendererIndex = findRendererIndex(mappedTrackInfo, C.TRACK_TYPE_TEXT);

        if (mCallback != null && rendererIndex != C.INDEX_UNSET) {
            TrackGroupArray groups = mappedTrackInfo.getTrackGroups(rendererIndex);
            Pair<Definition, MediaTrack> resultPair = mCallback.onSelectSubtitleTrack(groups, params);
            if (resultPair != null) {
                Log.d(TAG, "selectTextTrack: choose custom text processing");
                return Pair.create(resultPair.first, rendererIndex);
            }
            // NOTE: unlike video and audio, a null result falls through to the default selector
            // rather than disabling the renderer. Preserved from the pre-migration behaviour.
        }

        Log.d(TAG, "selectTextTrack: choose default text processing");

        Pair<Definition, Integer> result = super.selectTextTrack(
                mappedTrackInfo, rendererFormatSupports, params, selectedAudioLanguage);

        // Don't invoke if track already has been selected by the app
        if (mCallback != null && result != null) {
            mCallback.updateSubtitleTrackSelection(mappedTrackInfo.getTrackGroups(result.second), params, result.first);
        }

        return result;
    }

    /**
     * Finds the renderer handling a given track type.
     *
     * <p>The selection hooks now receive {@link MappedTrackInfo} covering every renderer rather than
     * one renderer's track groups, so the index has to be resolved here. Derived from the renderer
     * types rather than assuming the app's fixed 0/1/2 ordering, which only happens to hold because
     * of the order {@code DefaultRenderersFactory} builds them in.
     */
    private static int findRendererIndex(MappedTrackInfo mappedTrackInfo, int trackType) {
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (mappedTrackInfo.getRendererType(i) == trackType
                    && mappedTrackInfo.getTrackGroups(i).length > 0) {
                return i;
            }
        }

        return C.INDEX_UNSET;
    }
}
