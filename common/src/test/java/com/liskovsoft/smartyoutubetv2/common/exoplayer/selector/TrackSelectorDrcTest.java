package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.Metadata;
import com.liskovsoft.smartyoutubetv2.player.extras.FormatExtras;

import org.junit.Test;

/**
 * Pins DRC (dynamic range compressed) audio detection across <b>both</b> of its carriers.
 *
 * <p>DRC is the one format attribute whose producers disagree, and the disagreement is not
 * cosmetic. {@code YouTubeMPDBuilder} encodes it into the format id as {@code <itag>-drc}, while
 * {@code DashManifestParser2} and {@code SabrManifestParser} leave the id alone and attach a
 * {@link FormatExtras} marker to {@code Format.metadata}. {@code TrackSelectorUtil.isDrc} is
 * therefore an OR over two unrelated carriers, and a "simplification" that drops either one silently
 * breaks DRC detection on the paths that use it — including the DASH path, which is the active one
 * on real hardware.
 *
 * <p>The obvious tidy-up — make every parser append {@code -drc} so the id alone suffices — is the
 * trap these tests exist to prevent. {@code Format.id} is <b>field 3 of the persisted
 * {@code ExoFormatItem} preference string</b>, so rewriting it would invalidate every saved audio
 * selection on every device. The two carriers have to stay.
 *
 * <p>The vendored player avoided all of this with an extra {@code Format.isDrc} public field, which
 * is not something a Maven-consumed player can be given.
 */
public class TrackSelectorDrcTest {
    private static final long NO_LMT = 0L;

    /** The MPD-builder carrier: DRC encoded as an id suffix. */
    @Test
    public void detectsDrcFromIdSuffix() {
        assertTrue(TrackSelectorUtil.isDrc(formatWithId("251-drc")));
    }

    /** The DASH/SABR parser carrier: plain id, marker in metadata. */
    @Test
    public void detectsDrcFromMetadataMarker() {
        assertTrue(TrackSelectorUtil.isDrc(formatWithExtras("251", true)));
    }

    /**
     * Neither carrier present. This is the ordinary case — most audio formats are not DRC — so a
     * false positive here would mark every track "DRC" in the UI.
     */
    @Test
    public void plainFormatIsNotDrc() {
        assertFalse(TrackSelectorUtil.isDrc(formatWithId("251")));
    }

    /**
     * A marker that explicitly says "not DRC" must not be read as merely "a marker is present".
     * {@link FormatExtras} also carries {@code lastModified}, so it is attached to formats that are
     * not DRC at all — the DASH parser attaches it unconditionally.
     */
    @Test
    public void metadataMarkerSayingFalseIsNotDrc() {
        assertFalse(TrackSelectorUtil.isDrc(formatWithExtras("251", false)));
    }

    /** Guards the OR: either carrier alone is sufficient, so the id may be absent entirely. */
    @Test
    public void metadataMarkerAloneIsEnoughWithNullId() {
        Format format = new Format.Builder()
                .setMetadata(new Metadata(new FormatExtras(true, NO_LMT)))
                .build();

        assertTrue(TrackSelectorUtil.isDrc(format));
    }

    /**
     * The id carrier is a <b>suffix</b> match, not a substring one. A format id that merely contains
     * "drc" elsewhere is not DRC, and treating it as such would mislabel unrelated tracks.
     *
     * <p>Only the leading-substring case is asserted. A bare id of {@code "drc"} does match, since
     * a string trivially ends with itself, but real ids are numeric itags optionally suffixed with
     * {@code -drc}, so that input never occurs and pinning it would over-specify the contract.
     */
    @Test
    public void idContainingDrcElsewhereIsNotDrc() {
        assertFalse(TrackSelectorUtil.isDrc(formatWithId("drc251")));
        assertFalse(TrackSelectorUtil.isDrc(formatWithId("251drc9")));
    }

    /**
     * Metadata carrying unrelated entries must not be mistaken for a DRC marker. {@code Metadata} is
     * a general-purpose bag and the player itself puts things in it.
     */
    @Test
    public void unrelatedMetadataEntryIsNotDrc() {
        Format format = new Format.Builder()
                .setId("251")
                .setMetadata(new Metadata(new FormatExtras(false, 1234L)))
                .build();

        assertFalse(TrackSelectorUtil.isDrc(format));
    }

    /** Track selection runs over formats that can legitimately be null; it must not throw. */
    @Test
    public void nullFormatIsNotDrc() {
        assertFalse(TrackSelectorUtil.isDrc(null));
    }

    /** The UI mark is derived from the same predicate, so it inherits both carriers. */
    @Test
    public void drcMarkFollowsBothCarriers() {
        assertEquals("DRC", TrackSelectorUtil.buildDrcMark(formatWithId("251-drc")));
        assertEquals("DRC", TrackSelectorUtil.buildDrcMark(formatWithExtras("251", true)));
        assertEquals("", TrackSelectorUtil.buildDrcMark(formatWithId("251")));
    }

    private static Format formatWithId(String id) {
        return new Format.Builder().setId(id).build();
    }

    private static Format formatWithExtras(String id, boolean isDrc) {
        return new Format.Builder()
                .setId(id)
                .setMetadata(new Metadata(new FormatExtras(isDrc, NO_LMT)))
                .build();
    }
}
