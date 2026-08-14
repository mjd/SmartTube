package com.liskovsoft.smartyoutubetv2.player.extras;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.metadata.Metadata;

import org.junit.Test;

/**
 * Pins the carrier that replaced the vendored player's patched {@code Format} fields.
 *
 * <p>The fork added {@code isDrc} and {@code lastModified} as extra public fields on core
 * {@code Format}, which is not something a Maven-consumed player can be given. Both now travel as a
 * single {@link Metadata.Entry} attached to {@code Format.metadata} — the supported extension point,
 * and the only one that survives track selection, which is where both values are actually read.
 *
 * <p>The static lookups are the whole public contract, and both are called on formats that carry no
 * metadata at all: {@code lastModified} is read for every SABR playback request, {@code isDrc} for
 * every audio track in the selection UI. Returning a wrong default is worse than throwing, because
 * a bad {@code lmt} is echoed to YouTube in a {@code FormatId} and simply fails to play.
 */
public class FormatExtrasTest {
    private static final long LMT = 1_701_234_567_890L;
    private static final long FALLBACK = -1L;

    @Test
    public void findsDrcMarkerInMetadata() {
        Metadata metadata = new Metadata(new FormatExtras(true, LMT));

        assertTrue(FormatExtras.isDrc(metadata));
    }

    @Test
    public void findsLastModifiedInMetadata() {
        Metadata metadata = new Metadata(new FormatExtras(false, LMT));

        assertEquals(LMT, FormatExtras.getLastModified(metadata, FALLBACK));
    }

    /**
     * The common case on every path that does not attach the marker. A format with no metadata is
     * ordinary, not exceptional.
     */
    @Test
    public void nullMetadataFallsBack() {
        assertFalse(FormatExtras.isDrc(null));
        assertEquals(FALLBACK, FormatExtras.getLastModified(null, FALLBACK));
    }

    /**
     * {@code Metadata} is a general-purpose bag the player also writes to, so "has metadata" is not
     * the same as "has our marker".
     */
    @Test
    public void metadataWithoutOurEntryFallsBack() {
        Metadata metadata = new Metadata(new UnrelatedEntry());

        assertFalse(FormatExtras.isDrc(metadata));
        assertEquals(FALLBACK, FormatExtras.getLastModified(metadata, FALLBACK));
    }

    /**
     * The entry has to be found among unrelated neighbours rather than only at index 0 — the player
     * appends its own entries, and their order is not ours to control.
     */
    @Test
    public void findsEntryAmongUnrelatedEntries() {
        Metadata metadata = new Metadata(
                new UnrelatedEntry(), new FormatExtras(true, LMT), new UnrelatedEntry());

        assertTrue(FormatExtras.isDrc(metadata));
        assertEquals(LMT, FormatExtras.getLastModified(metadata, FALLBACK));
    }

    /** An explicit {@code false} marker must be distinguishable from an absent one. */
    @Test
    public void explicitFalseIsNotConfusedWithAbsent() {
        Metadata metadata = new Metadata(new FormatExtras(false, LMT));

        assertFalse(FormatExtras.isDrc(metadata));
        // ...but the entry was found, proving false came from the marker rather than the fallback.
        assertEquals(LMT, FormatExtras.getLastModified(metadata, FALLBACK));
    }

    /** A zero lmt is a real value that YouTube does send, not a "missing" sentinel. */
    @Test
    public void zeroLastModifiedIsAValueNotAFallback() {
        Metadata metadata = new Metadata(new FormatExtras(false, 0L));

        assertEquals(0L, FormatExtras.getLastModified(metadata, FALLBACK));
    }

    /**
     * Both fields participate in equality. Formats are compared during track selection, so an
     * identity that ignored a field would make a DRC and non-DRC track look interchangeable.
     */
    @Test
    public void equalityCoversBothFields() {
        assertEquals(new FormatExtras(true, LMT), new FormatExtras(true, LMT));
        assertNotEquals(new FormatExtras(true, LMT), new FormatExtras(false, LMT));
        assertNotEquals(new FormatExtras(true, LMT), new FormatExtras(true, LMT + 1));
    }

    @Test
    public void equalInstancesShareHashCode() {
        assertEquals(new FormatExtras(true, LMT).hashCode(), new FormatExtras(true, LMT).hashCode());
    }

    /** A stand-in for the entries the player itself attaches. */
    private static final class UnrelatedEntry implements Metadata.Entry {
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
        }
    }
}
