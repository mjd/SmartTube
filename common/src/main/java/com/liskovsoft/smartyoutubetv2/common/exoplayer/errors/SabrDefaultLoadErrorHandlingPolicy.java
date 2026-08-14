package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;

/**
 * SABR load-error policy: identical to the DASH one apart from honouring the server's explicit
 * back-off request.
 *
 * <p>The blacklist override that used to sit here only delegated to the parent, so it is dropped
 * rather than ported to {@code getFallbackSelectionFor}.
 */
public class SabrDefaultLoadErrorHandlingPolicy extends DashDefaultLoadErrorHandlingPolicy {
    @Override
    public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
        if (loadErrorInfo.exception != null
                && Helpers.contains(loadErrorInfo.exception.getMessage(), "Wait 5 sec")) {
            return 5_000;
        }

        return super.getRetryDelayMsFor(loadErrorInfo);
    }
}
