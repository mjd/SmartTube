package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader.UnexpectedLoaderException;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.FallbackOptions;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.FallbackSelection;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Load-error policy for DASH.
 *
 * <p>The upstream interface was reshaped: the loose {@code (dataType, loadDurationMs, exception,
 * errorCount)} parameter lists became a single {@link LoadErrorInfo}, and blacklisting became a
 * {@link FallbackSelection} describing both what to exclude (a track or a whole location) and for
 * how long. The policy expressed here is unchanged — exclude a track on 404/410, and do not retry
 * errors that re-requesting cannot fix.
 */
public class DashDefaultLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
    @Nullable
    @Override
    public FallbackSelection getFallbackSelectionFor(FallbackOptions fallbackOptions, LoadErrorInfo loadErrorInfo) {
        IOException exception = loadErrorInfo.exception;

        if (exception instanceof InvalidResponseCodeException) {
            int responseCode = ((InvalidResponseCodeException) exception).responseCode;

            if ((responseCode == 404 // HTTP 404 Not Found.
                    || responseCode == 410) // HTTP 410 Gone.
                    && fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK)) {
                return new FallbackSelection(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK, DEFAULT_TRACK_EXCLUSION_MS);
            }
        }

        return null; // no fallback
    }

    @Override
    public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
        IOException exception = loadErrorInfo.exception;

        return exception instanceof ParserException
                || exception instanceof FileNotFoundException
                || exception instanceof UnexpectedLoaderException
                ? C.TIME_UNSET
                : Math.min((loadErrorInfo.errorCount - 1) * 1000, 5000);
    }
}
