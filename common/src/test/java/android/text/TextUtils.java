package android.text;

/**
 * Real implementation of the two {@code TextUtils} methods the player-selector code uses, for
 * plain-JUnit tests.
 *
 * <p>Unit tests run against AGP's "mockable" android.jar, whose methods are stubbed. With
 * {@code testOptions.unitTests.returnDefaultValues = true} those stubs return type defaults, which
 * makes {@code isEmpty(null)} return {@code false} — the exact inverse of the truth. Code that
 * (correctly) guards with {@code isEmpty} before dereferencing then walks straight into a
 * NullPointerException, so the tests fail for a reason that has nothing to do with the code under
 * test.
 *
 * <p>Test sources precede the mockable android.jar on the classpath, so this class shadows the stub.
 * Keep it minimal: add a method here only when a test actually needs it, and keep the semantics
 * identical to the platform's.
 */
public class TextUtils {
    private TextUtils() {
    }

    /** Platform semantics: true when the sequence is null or zero-length. */
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    /** Platform semantics: concatenates in order; a single element is returned unchanged. */
    public static CharSequence concat(CharSequence... text) {
        if (text == null || text.length == 0) {
            return "";
        }

        if (text.length == 1) {
            return text[0];
        }

        StringBuilder result = new StringBuilder();

        for (CharSequence piece : text) {
            if (piece != null) {
                result.append(piece);
            }
        }

        return result.toString();
    }
}
