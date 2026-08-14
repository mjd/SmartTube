package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins subtitle-language matching against a corpus of real YouTube caption labels.
 *
 * <p>SmartTube does not match subtitles on language <i>codes</i>. It matches on the human-readable
 * labels YouTube returns — {@code "English"}, {@code "English (United Kingdom)"},
 * {@code "Portuguese (Portugal)"} — with a trailing {@code "*"} appended to mark an auto-translated
 * track. All of the trimming and comparison logic in {@link SubtitleTrack} is string surgery over
 * those labels.
 *
 * <p>That is fragile in a specific way that matters for the player migration: Media3 normalizes
 * {@code Format.language} to BCP-47 inside {@code Format.Builder.build()}. A display label pushed
 * through that normalization does not survive as itself, so every one of these matches would start
 * failing — and it would fail <i>silently</i>. Nothing throws; users simply find their subtitle
 * preference is no longer remembered, and the cause is several layers away from the symptom.
 *
 * <p>These are characterization tests: they record what the code does today, including the cases
 * where the current behaviour is imperfect (see the regex-collision tests at the bottom, which the
 * production code itself flags in comments). If the migration changes any of these outcomes, that
 * should be a deliberate decision rather than a discovery made from a bug report.
 */
public class SubtitleLanguageCorpusTest {
    private static final String MARKER = "*";

    // ---------------------------------------------------------------- auto-translated detection

    @Test
    public void isAuto_isDrivenPurelyByTheTrailingMarker() {
        assertTrue("a trailing marker means auto-translated", SubtitleTrack.isAuto("English" + MARKER));
        assertTrue(SubtitleTrack.isAuto("Portuguese (Brazil)" + MARKER));
        assertTrue(SubtitleTrack.isAuto("Chinese (Simplified)" + MARKER));

        assertFalse("no marker means an original track", SubtitleTrack.isAuto("English"));
        assertFalse(SubtitleTrack.isAuto("English (United Kingdom)"));
        assertFalse(SubtitleTrack.isAuto(null));
    }

    @Test
    public void isAuto_ignoresAMarkerThatIsNotTrailing() {
        // Only the suffix position is meaningful.
        assertFalse(SubtitleTrack.isAuto("Eng*lish"));
        assertFalse(SubtitleTrack.isAuto(MARKER + "English"));
    }

    // ------------------------------------------------------------------------------------ trim()

    @Test
    public void trim_stripsTheAutoTranslateMarker() {
        assertEquals("English", SubtitleTrack.trim("English" + MARKER));
        assertEquals("Japanese", SubtitleTrack.trim("Japanese" + MARKER));
    }

    @Test
    public void trim_stripsASourceLanguageSuffix() {
        // YouTube labels an auto-translated track as "<target> - <source>".
        assertEquals("Spanish", SubtitleTrack.trim("Spanish - English"));
        assertEquals("German", SubtitleTrack.trim("German - English" + MARKER));
    }

    @Test
    public void trim_stripsAParentheticalQualifierOnlyWhenASourceSuffixFollows() {
        // TRIM_PATTERN1 (" (...) - ...") requires the " - " part; a bare parenthetical is kept,
        // which is what preserves regional variants as distinct tracks.
        assertEquals("English", SubtitleTrack.trim("English (United Kingdom) - English"));
        assertEquals("English (United Kingdom)", SubtitleTrack.trim("English (United Kingdom)"));
        assertEquals("Portuguese (Portugal)", SubtitleTrack.trim("Portuguese (Portugal)"));
    }

    @Test
    public void trim_isNullSafe() {
        assertNull(SubtitleTrack.trim(null));
    }

    @Test
    public void trim_leavesAPlainLabelUntouched() {
        for (String label : new String[] {"English", "Spanish", "Ukrainian", "Chinese (Simplified)"}) {
            assertEquals(label, SubtitleTrack.trim(label));
        }
    }

    // ------------------------------------------------------------------------------- trimIfAuto()

    @Test
    public void trimIfAuto_onlyTrimsMarkedTracks() {
        // Original tracks must keep their full label — that is what makes "English (United Kingdom)"
        // selectable as something distinct from "English".
        assertEquals("English (United Kingdom)", SubtitleTrack.trimIfAuto("English (United Kingdom)"));
        assertEquals("English", SubtitleTrack.trimIfAuto("English" + MARKER));
        assertEquals("Spanish - English", SubtitleTrack.trimIfAuto("Spanish - English"));
    }

    // ----------------------------------------------------------- known imperfections, pinned as-is

    /**
     * The production code carries the comment "May have mismatches e.g. 'English (United Kingdom)'"
     * on its auto-detect pattern, and "NOTE: Breaks Portuguese (Portugal)" on the trimming variant.
     * Recorded here so the collision is visible rather than folklore: a parenthetical regional
     * variant is indistinguishable, by shape alone, from a parenthetical qualifier like
     * "(auto-generated)".
     *
     * <p>Detection is therefore driven by the trailing marker, not by the parentheses — which is
     * why {@link SubtitleTrack#isAuto} looks only at the suffix.
     */
    @Test
    public void regionalVariantsAreNotMistakenForAutoTranslations() {
        assertFalse(SubtitleTrack.isAuto("English (United Kingdom)"));
        assertFalse(SubtitleTrack.isAuto("Portuguese (Portugal)"));
        assertFalse(SubtitleTrack.isAuto("Chinese (Traditional)"));

        // ...and they survive trimming intact, so they stay individually selectable.
        assertEquals("English (United Kingdom)", SubtitleTrack.trim("English (United Kingdom)"));
        assertEquals("Portuguese (Portugal)", SubtitleTrack.trim("Portuguese (Portugal)"));
    }

    /**
     * {@code Helpers.replace} uses {@code replaceFirst}, so trimming removes one occurrence only.
     * Harmless for real labels, but pinned because switching it to {@code replaceAll} during a
     * refactor would quietly change matching.
     */
    @Test
    public void trim_removesOnlyTheFirstMatch() {
        assertEquals("A", SubtitleTrack.trim("A - B - C"));
    }
}
