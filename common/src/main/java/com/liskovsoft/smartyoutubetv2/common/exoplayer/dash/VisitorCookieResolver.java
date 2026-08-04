package com.liskovsoft.smartyoutubetv2.common.exoplayer.dash;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ResolvingDataSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Attaches YouTube's visitor cookie to DASH segment requests.
 *
 * <p>Subtitle segments are bot-checked: without the cookie the request is rejected and the track
 * silently fails to load. The vendored player carried the cookie as an extra field on
 * {@code DashManifest} and applied it inside {@code DefaultDashChunkSource}; neither is possible
 * against a published artifact.
 *
 * <p>A cookie is an HTTP concern rather than a manifest field, so it belongs here — resolving the
 * {@link DataSpec} on its way to the data source. That also makes it apply to every DASH request
 * rather than only the subtitle path the fork happened to patch, which is the correct behaviour:
 * the cookie identifies the session, not the track.
 */
public class VisitorCookieResolver implements ResolvingDataSource.Resolver {
    private static final String COOKIE_HEADER = "Cookie";
    private final String mVisitorCookie;

    public VisitorCookieResolver(String visitorCookie) {
        mVisitorCookie = visitorCookie;
    }

    @Override
    public DataSpec resolveDataSpec(DataSpec dataSpec) {
        if (mVisitorCookie == null) {
            return dataSpec;
        }

        if (dataSpec.httpRequestHeaders.isEmpty()) {
            return dataSpec.withRequestHeaders(Collections.singletonMap(COOKIE_HEADER, mVisitorCookie));
        }

        // Preserve anything already set rather than replacing the whole map.
        Map<String, String> headers = new HashMap<>(dataSpec.httpRequestHeaders);
        headers.put(COOKIE_HEADER, mVisitorCookie);

        return dataSpec.withRequestHeaders(headers);
    }
}
