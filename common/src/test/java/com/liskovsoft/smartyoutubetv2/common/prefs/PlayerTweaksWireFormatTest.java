package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards the positional wire format of the persisted player tweaks.
 *
 * <p>{@code PlayerTweaksData} persists ~60 settings as one delimited string. Position <b>is</b> the
 * identity: {@code restoreData()} reads slot N by index, {@code persistDataInt()} writes slot N by
 * argument order, and nothing records which setting a slot belongs to. Insert or delete one value in
 * the middle and every later setting silently becomes a different setting on every device that has
 * ever saved preferences — no crash, no migration, no way to tell afterwards which value meant what.
 *
 * <p>This is checked by reading the source rather than by exercising the class, which needs a
 * {@code Context} and real preferences. That is an unusual thing for a unit test to do, and it is
 * chosen deliberately: the invariant is a property of how the two methods are written, and it is
 * far cheaper to assert here than to discover from a corrupted install. The repo already takes this
 * approach in CI with the reflection ratchet.
 *
 * <p>Note that slot numbering is <b>not</b> contiguous — retired settings leave {@code null}
 * placeholders on the write side and are simply absent on the read side. Gaps are correct and
 * expected; what must never happen is a slot moving.
 */
public class PlayerTweaksWireFormatTest {
    private static final String SOURCE_PATH =
            "src/main/java/com/liskovsoft/smartyoutubetv2/common/prefs/PlayerTweaksData.java";

    /** The retired vsync setting, kept solely to hold its slot. See {@code restoreData()}. */
    private static final String RESERVED_SLOT_2_FIELD = "mIsSnapToVsyncDisabled";

    /** Sanity floors, so a regex that silently stops matching fails loudly instead of passing. */
    private static final int MIN_EXPECTED_SLOTS = 50;

    /**
     * The invariant, stated directly: the index a field is <i>read</i> from equals the position it
     * is <i>written</i> at.
     */
    @Test
    public void everyFieldIsWrittenAtTheSlotItIsReadFrom() throws IOException {
        Map<String, Integer> reads = parseReadSlots();
        List<String> writes = parseWriteOrder();

        assertTrue("Parsed only " + reads.size() + " read slots -- the regex has likely rotted",
                reads.size() >= MIN_EXPECTED_SLOTS);
        assertTrue("Parsed only " + writes.size() + " write slots -- the regex has likely rotted",
                writes.size() >= MIN_EXPECTED_SLOTS);

        List<String> mismatches = new ArrayList<>();

        for (Map.Entry<String, Integer> read : reads.entrySet()) {
            String field = read.getKey();
            int readIndex = read.getValue();
            int writeIndex = writes.indexOf(field);

            if (writeIndex != readIndex) {
                mismatches.add(String.format(
                        "%s is read from slot %d but written at slot %s",
                        field, readIndex, writeIndex < 0 ? "(never written)" : writeIndex));
            }
        }

        if (!mismatches.isEmpty()) {
            fail("Persisted tweak slots have shifted -- this silently reassigns saved settings on "
                    + "every device:\n  " + String.join("\n  ", mismatches));
        }
    }

    /**
     * No two settings may share a slot. A duplicate index means one setting overwrites the other on
     * write and both read the same value back.
     */
    @Test
    public void noTwoFieldsShareASlot() throws IOException {
        Map<String, Integer> reads = parseReadSlots();
        Map<Integer, String> bySlot = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> read : reads.entrySet()) {
            String clash = bySlot.put(read.getValue(), read.getKey());

            if (clash != null) {
                fail("Slot " + read.getValue() + " is read by both " + clash + " and "
                        + read.getKey());
            }
        }
    }

    /**
     * The specific slot this branch retired. The vsync setting was removed from the UI and does
     * nothing, which makes its field look like dead code worth deleting -- deleting it would shift
     * the ~58 settings after it. Kept as its own test so the failure names the reason.
     */
    @Test
    public void reservedVsyncSlotIsStillHeld() throws IOException {
        List<String> writes = parseWriteOrder();

        assertEquals(
                "Slot 2 is reserved by the retired vsync setting. Removing it shifts every later "
                        + "slot and invalidates all saved tweaks -- leave the field, remove the UI.",
                RESERVED_SLOT_2_FIELD, writes.get(2));

        assertEquals("The reserved slot must still be read back, not just written",
                Integer.valueOf(2), parseReadSlots().get(RESERVED_SLOT_2_FIELD));
    }

    /** {@code mField = Helpers.parseXxx(split, N, ...)} -> field name to slot index. */
    private static Map<String, Integer> parseReadSlots() throws IOException {
        String body = extractBlock(readSource(), "private void restoreData\\(\\) \\{");
        Matcher matcher = Pattern
                .compile("(m\\w+)\\s*=\\s*Helpers\\.parse\\w+\\(\\s*split\\s*,\\s*(\\d+)")
                .matcher(body);

        Map<String, Integer> slots = new LinkedHashMap<>();

        while (matcher.find()) {
            slots.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }

        return slots;
    }

    /** The {@code Helpers.mergeData(...)} argument list, in order. {@code null} marks a dead slot. */
    private static List<String> parseWriteOrder() throws IOException {
        String body = extractBlock(readSource(), "private void persistDataInt\\(\\) \\{");
        int start = body.indexOf("Helpers.mergeData(");

        if (start < 0) {
            fail("Could not find Helpers.mergeData( in persistDataInt()");
        }

        String args = body.substring(start + "Helpers.mergeData(".length());
        int end = args.indexOf("));");
        args = end < 0 ? args : args.substring(0, end);

        List<String> order = new ArrayList<>();

        for (String arg : args.split(",")) {
            String trimmed = arg.trim();

            if (!trimmed.isEmpty()) {
                order.add(trimmed);
            }
        }

        return order;
    }

    /** Returns the text of a method body, with line comments stripped. */
    private static String extractBlock(String source, String signatureRegex) {
        Matcher matcher = Pattern.compile(signatureRegex).matcher(source);

        if (!matcher.find()) {
            fail("Could not locate " + signatureRegex + " -- has the method been renamed?");
        }

        // Methods here are one indent level in, so the first "\n    }" closes the block.
        int start = matcher.end();
        int end = source.indexOf("\n    }", start);

        return stripLineComments(source.substring(start, end < 0 ? source.length() : end));
    }

    private static String stripLineComments(String block) {
        StringBuilder result = new StringBuilder();

        for (String line : block.split("\n")) {
            int comment = line.indexOf("//");
            result.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
        }

        return result.toString();
    }

    /** Resolves whether the test runs with the module or the repo root as working directory. */
    private static String readSource() throws IOException {
        File file = new File(SOURCE_PATH);

        if (!file.exists()) {
            file = new File("common", SOURCE_PATH);
        }

        assertTrue("Cannot find PlayerTweaksData.java from " + new File(".").getAbsolutePath(),
                file.exists());

        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
