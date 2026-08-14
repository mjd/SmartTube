package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Locks the on-disk wire format of {@link ExoFormatItem}.
 *
 * <p>{@link ExoFormatItem#toString()} emits an 11-field comma-separated string that is
 * <b>persisted in user preferences</b> (the saved video/audio/subtitle quality presets), and
 * {@link ExoFormatItem#from(String)} parses it back. The field order is positional, so inserting,
 * reordering or retyping a field silently invalidates every preference already saved on every
 * user's device — they would simply lose their quality settings.
 *
 * <p>These tests exist to make that breakage loud. They are the gate on the planned {@code Format}
 * de-patch (which replaces the deleted {@code Format.createXSampleFormat} factories with
 * {@code Format.Builder}) and on the Media3 migration generally: the internals may change freely,
 * this string may not.
 *
 * <p>Deliberately plain JUnit — the Robolectric bundled with this project is from 2019 and cannot
 * run under the JDK 17 the build requires, so the whole Robolectric suite is inert. This relies on
 * {@code testOptions.unitTests.returnDefaultValues} for the handful of {@code android.text.TextUtils}
 * calls on the parse path.
 */
public class ExoFormatItemWireCompatTest {
    /** type, rendererIndex, id, codecs, width, height, frameRate, language, isPreset, bitrate, isDrc */
    private static final int FIELD_COUNT = 11;

    private static final String VIDEO_SPEC = "0,0,247,vp9,1280,720,30.0,null,false,1648597,false";
    private static final String VIDEO_4K_SPEC = "0,0,401,av01,3840,2160,60.0,null,false,15000000,false";
    private static final String AUDIO_SPEC = "1,1,251,opus,-1,-1,-1.0,en,false,160000,false";
    private static final String AUDIO_DRC_SPEC = "1,1,251-drc,opus,-1,-1,-1.0,en,false,160000,true";
    private static final String SUBTITLE_SPEC = "2,2,en,null,-1,-1,-1.0,en,false,-1,false";

    @Test
    public void toString_emitsExactlyElevenCommaSeparatedFields() {
        for (String spec : new String[] {VIDEO_SPEC, VIDEO_4K_SPEC, AUDIO_SPEC, AUDIO_DRC_SPEC, SUBTITLE_SPEC}) {
            ExoFormatItem item = ExoFormatItem.from(spec);
            assertNotNull("spec should parse: " + spec, item);
            assertEquals("field count changed for: " + spec,
                    FIELD_COUNT, item.toString().split(",", -1).length);
        }
    }

    @Test
    public void from_thenToString_isStableAcrossASecondRoundTrip() {
        // The first serialization may normalize (e.g. "null" for absent values); what must hold is
        // that re-parsing its own output is a fixed point. Otherwise a preference would drift every
        // time it is read and written back.
        for (String spec : new String[] {VIDEO_SPEC, VIDEO_4K_SPEC, AUDIO_SPEC, AUDIO_DRC_SPEC, SUBTITLE_SPEC}) {
            String once = ExoFormatItem.from(spec).toString();
            String twice = ExoFormatItem.from(once).toString();
            assertEquals("round trip is not a fixed point for: " + spec, once, twice);
        }
    }

    @Test
    public void from_preservesTheIdentifyingFields() {
        // id and codecs are what the track matcher keys on; width/height/frameRate drive the
        // preset comparison. If any of these move position, the wrong track gets selected.
        String out = ExoFormatItem.from(VIDEO_SPEC).toString();
        String[] f = out.split(",", -1);

        assertEquals("type", "0", f[0]);
        assertEquals("rendererIndex", "0", f[1]);
        assertEquals("id", "247", f[2]);
        assertEquals("codecs", "vp9", f[3]);
        assertEquals("width", "1280", f[4]);
        assertEquals("height", "720", f[5]);
        assertEquals("frameRate", "30.0", f[6]);
    }

    @Test
    public void from_tenFieldLegacySpec_isUpgradedWithIsDrcFalse() {
        // Pre-DRC preferences: 10 fields, isDrc appended as false.
        String legacy = "0,0,247,vp9,1280,720,30.0,null,false,1648597";
        ExoFormatItem item = ExoFormatItem.from(legacy);

        assertNotNull("10-field legacy spec must still parse", item);
        String[] f = item.toString().split(",", -1);
        assertEquals(FIELD_COUNT, f.length);
        assertEquals("isDrc should default to false", "false", f[10]);
    }

    @Test
    public void from_malformedSpec_returnsNullRatherThanThrowing() {
        assertNull(ExoFormatItem.from("0,0,247"));
        assertNull(ExoFormatItem.from((String) null));
    }

    /**
     * Documents a defect rather than blessing it.
     *
     * <p>{@code from(String)} intends to upgrade both 9- and 10-field legacy specs, but the padding
     * is an {@code if/else if} on the <i>original</i> length: a 9-field spec has one element
     * appended, reaches 10, and then fails the {@code length != 11} check — so it returns null and
     * the saved preference is silently discarded. A 9-field spec needs two appended fields
     * (bitrate and isDrc), not one.
     *
     * <p>Asserted as-is so the behaviour is pinned while the wire format is being changed around it.
     * If the padding is fixed, this test should be inverted to assert a successful parse.
     */
    @Test
    public void from_nineFieldLegacySpec_returnsNull_knownDefect() {
        String nineField = "0,0,247,vp9,1280,720,30.0,null,false";

        assertEquals("precondition: the spec really has 9 fields", 9, nineField.split(",", -1).length);
        assertNull("9-field legacy specs are currently dropped (see javadoc)",
                ExoFormatItem.from(nineField));
    }
}
