package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader.UnexpectedLoaderException;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.FallbackOptions;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.FallbackSelection;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;

import org.junit.Test;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;

/**
 * Pins the two load-error decisions the migration had to re-express.
 *
 * <p>Upstream replaced the loose {@code (dataType, loadDurationMs, exception, errorCount)} parameter
 * lists with a single {@link LoadErrorInfo}, and turned blacklisting into a {@link FallbackSelection}
 * that names both what to exclude and for how long. Both halves fail quietly if they are wrong: drop
 * the 404 exclusion and a live stream whose last audio segment 404s never switches away from the
 * broken track, and retry something a re-request cannot fix and the player loops instead of
 * surfacing the error.
 */
public class LoadErrorHandlingPolicyTest {
    private static final DataSpec DATA_SPEC =
            new DataSpec(Uri.parse("https://rr3---sn-vgqs.googlevideo.com/videoplayback?itag=140"));
    /** A fallback is available when there is more than one track and none excluded yet. */
    private static final FallbackOptions TRACK_AVAILABLE =
            new FallbackOptions(1, 0, 2, 0);
    private static final FallbackOptions NO_TRACK_LEFT =
            new FallbackOptions(1, 0, 1, 1);

    private final DashDefaultLoadErrorHandlingPolicy mDash = new DashDefaultLoadErrorHandlingPolicy();
    private final SabrDefaultLoadErrorHandlingPolicy mSabr = new SabrDefaultLoadErrorHandlingPolicy();

    /** The case this policy exists for: the last segment of a live stream 404s. */
    @Test
    public void excludesTheTrackOn404() {
        FallbackSelection selection =
                mDash.getFallbackSelectionFor(TRACK_AVAILABLE, errorInfo(httpError(404), 1));

        assertNotNull(selection);
        assertEquals(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK, selection.type);
        assertEquals(DashDefaultLoadErrorHandlingPolicy.DEFAULT_TRACK_EXCLUSION_MS,
                selection.exclusionDurationMs);
    }

    @Test
    public void excludesTheTrackOn410() {
        assertNotNull(mDash.getFallbackSelectionFor(TRACK_AVAILABLE, errorInfo(httpError(410), 1)));
    }

    /**
     * 403 and 503 are not "this track is gone". Excluding on them would throw away a working track
     * over a transient or authorization problem that affects every track equally.
     */
    @Test
    public void doesNotExcludeTheTrackOnOtherResponseCodes() {
        assertNull(mDash.getFallbackSelectionFor(TRACK_AVAILABLE, errorInfo(httpError(403), 1)));
        assertNull(mDash.getFallbackSelectionFor(TRACK_AVAILABLE, errorInfo(httpError(500), 1)));
        assertNull(mDash.getFallbackSelectionFor(TRACK_AVAILABLE, errorInfo(httpError(503), 1)));
    }

    @Test
    public void doesNotExcludeOnANonHttpError() {
        assertNull(mDash.getFallbackSelectionFor(TRACK_AVAILABLE, errorInfo(new EOFException(), 1)));
    }

    /** Nothing to fall back to: excluding the only track left would strand playback. */
    @Test
    public void doesNotExcludeWhenNoTrackRemains() {
        assertNull(mDash.getFallbackSelectionFor(NO_TRACK_LEFT, errorInfo(httpError(404), 1)));
    }

    /** C.TIME_UNSET means "do not retry" -- re-requesting cannot fix any of these. */
    @Test
    public void refusesToRetryUnfixableErrors() {
        assertEquals(C.TIME_UNSET, mDash.getRetryDelayMsFor(errorInfo(new FileNotFoundException(), 1)));
        assertEquals(C.TIME_UNSET,
                mDash.getRetryDelayMsFor(errorInfo(new UnexpectedLoaderException(new RuntimeException()), 1)));
        assertEquals(C.TIME_UNSET, mDash.getRetryDelayMsFor(
                errorInfo(ParserException.createForMalformedContainer("bad box", null), 1)));
    }

    /** Everything else backs off a second per attempt, capped at five. */
    @Test
    public void backsOffOneSecondPerAttemptUpToFive() {
        assertEquals(0, mDash.getRetryDelayMsFor(errorInfo(httpError(500), 1)));
        assertEquals(1_000, mDash.getRetryDelayMsFor(errorInfo(httpError(500), 2)));
        assertEquals(4_000, mDash.getRetryDelayMsFor(errorInfo(httpError(500), 5)));
        assertEquals(5_000, mDash.getRetryDelayMsFor(errorInfo(httpError(500), 6)));
        assertEquals(5_000, mDash.getRetryDelayMsFor(errorInfo(httpError(500), 50)));
    }

    /** SABR differs in one respect only: the server can ask for a five second wait, and gets it. */
    @Test
    public void sabrHonoursTheServersBackOffRequest() {
        assertEquals(5_000,
                mSabr.getRetryDelayMsFor(errorInfo(new IOException("Stream busy. Wait 5 sec."), 1)));
    }

    @Test
    public void sabrOtherwiseBehavesLikeDash() {
        assertEquals(1_000, mSabr.getRetryDelayMsFor(errorInfo(httpError(500), 2)));
        assertEquals(C.TIME_UNSET, mSabr.getRetryDelayMsFor(errorInfo(new FileNotFoundException(), 1)));
        assertNotNull(mSabr.getFallbackSelectionFor(TRACK_AVAILABLE, errorInfo(httpError(404), 1)));
    }

    /** A null message must not blow up the SABR message check. */
    @Test
    public void sabrToleratesAnErrorWithNoMessage() {
        assertEquals(0, mSabr.getRetryDelayMsFor(errorInfo(new IOException(), 1)));
    }

    private static InvalidResponseCodeException httpError(int responseCode) {
        return new InvalidResponseCodeException(
                responseCode,
                /* responseMessage= */ null,
                /* cause= */ null,
                Collections.emptyMap(),
                DATA_SPEC,
                new byte[0]);
    }

    private static LoadErrorInfo errorInfo(IOException exception, int errorCount) {
        return new LoadErrorInfo(
                /* loadEventInfo= */ null, /* mediaLoadData= */ null, exception, errorCount);
    }
}
