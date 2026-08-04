package com.liskovsoft.smartyoutubetv2.common.exoplayer.dash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSpec;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Pins how the YouTube visitor cookie is attached to DASH requests.
 *
 * <p>Subtitle segments are bot-checked: without the cookie the request is rejected and the track
 * silently fails to load — no error, just missing subtitles. That silence is why this is worth
 * pinning; a regression here does not announce itself.
 *
 * <p>The vendored player carried the cookie as an extra field on {@code DashManifest} and applied it
 * inside {@code DefaultDashChunkSource}. Neither is possible against a published artifact, so it is
 * now a {@code ResolvingDataSource.Resolver} that sets the header on the way to the data source.
 * That deliberately widens the scope from the fork's subtitle-only patch to every DASH request,
 * which is correct: the cookie identifies the session, not the track.
 */
public class VisitorCookieResolverTest {
    private static final String COOKIE = "VISITOR_INFO1_LIVE=Ab1cD2eF3gH";
    private static final Uri URI = Uri.parse("https://rr3---sn-vgqs.googlevideo.com/videoplayback");

    @Test
    public void attachesCookieWhenNoHeadersPresent() {
        DataSpec resolved = new VisitorCookieResolver(COOKIE).resolveDataSpec(specWithHeaders(null));

        assertEquals(COOKIE, resolved.httpRequestHeaders.get("Cookie"));
        assertEquals(1, resolved.httpRequestHeaders.size());
    }

    /**
     * The branch most likely to be broken by a "simplification". Replacing the whole header map
     * instead of merging would silently drop headers the caller set — including the Range header
     * that makes segmented playback work at all.
     */
    @Test
    public void preservesExistingHeaders() {
        Map<String, String> existing = new HashMap<>();
        existing.put("Range", "bytes=0-1023");
        existing.put("User-Agent", "SmartTube");

        DataSpec resolved =
                new VisitorCookieResolver(COOKIE).resolveDataSpec(specWithHeaders(existing));

        assertEquals(COOKIE, resolved.httpRequestHeaders.get("Cookie"));
        assertEquals("bytes=0-1023", resolved.httpRequestHeaders.get("Range"));
        assertEquals("SmartTube", resolved.httpRequestHeaders.get("User-Agent"));
        assertEquals(3, resolved.httpRequestHeaders.size());
    }

    /** The resolver is the authority on this header, so an existing value is replaced, not kept. */
    @Test
    public void overwritesAnExistingCookieHeader() {
        DataSpec resolved = new VisitorCookieResolver(COOKIE)
                .resolveDataSpec(specWithHeaders(Collections.singletonMap("Cookie", "stale=1")));

        assertEquals(COOKIE, resolved.httpRequestHeaders.get("Cookie"));
        assertEquals(1, resolved.httpRequestHeaders.size());
    }

    /**
     * No cookie is a normal state, not an error — it is absent until the session provides one, and
     * playback has to work meanwhile. The spec is passed through untouched rather than gaining an
     * empty or null header, which servers treat differently from no header at all.
     */
    @Test
    public void passesSpecThroughUnchangedWhenCookieIsNull() {
        DataSpec original = specWithHeaders(Collections.singletonMap("Range", "bytes=0-99"));

        DataSpec resolved = new VisitorCookieResolver(null).resolveDataSpec(original);

        assertSame(original, resolved);
        assertNull(resolved.httpRequestHeaders.get("Cookie"));
    }

    /**
     * {@code DataSpec} is shared and reused by the loader, so resolving must not mutate the input.
     * A resolver that edited the original in place would leak the cookie into unrelated requests.
     */
    @Test
    public void doesNotMutateTheOriginalSpec() {
        DataSpec original = specWithHeaders(Collections.singletonMap("Range", "bytes=0-99"));

        new VisitorCookieResolver(COOKIE).resolveDataSpec(original);

        assertNull(original.httpRequestHeaders.get("Cookie"));
        assertEquals(1, original.httpRequestHeaders.size());
    }

    /** The rest of the spec has to survive; only headers are the resolver's business. */
    @Test
    public void leavesTheRequestItselfIntact() {
        DataSpec original = new DataSpec.Builder()
                .setUri(URI)
                .setPosition(2048)
                .setLength(4096)
                .build();

        DataSpec resolved = new VisitorCookieResolver(COOKIE).resolveDataSpec(original);

        assertEquals(URI, resolved.uri);
        assertEquals(2048, resolved.position);
        assertEquals(4096, resolved.length);
        assertTrue(resolved.httpRequestHeaders.containsKey("Cookie"));
    }

    private static DataSpec specWithHeaders(Map<String, String> headers) {
        DataSpec.Builder builder = new DataSpec.Builder().setUri(URI);

        if (headers != null) {
            builder.setHttpRequestHeaders(headers);
        }

        return builder.build();
    }
}
