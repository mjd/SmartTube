package android.net;

/**
 * Minimal stand-in for {@code android.net.Uri}, for plain-JUnit tests.
 *
 * <p>Unit tests run against AGP's "mockable" android.jar. With
 * {@code testOptions.unitTests.returnDefaultValues = true}, {@code Uri.parse()} returns
 * {@code null}, so anything that builds a {@code DataSpec} fails with "The uri must be set" before
 * reaching the code under test. Test sources precede the mockable jar on the classpath, so this
 * class shadows the stub.
 *
 * <p>Deliberately a thin wrapper over the original string rather than a parser. Nothing under test
 * inspects a URI's components — the player treats them as opaque handles and only ever compares or
 * prints them. Keep it that way: if a test ever needs real parsing, that is a sign it wants an
 * instrumented test, not a richer fake here.
 *
 * <p>The real class is abstract and implements {@code Parcelable}; neither matters to a test that
 * only constructs and compares. Add members here only when a test actually needs them, and keep the
 * semantics identical to the platform's.
 */
public final class Uri implements Comparable<Uri> {
    /** Platform-equivalent constant: a URI with an empty string representation. */
    public static final Uri EMPTY = new Uri("");

    private final String mUriString;

    private Uri(String uriString) {
        mUriString = uriString;
    }

    /** Platform semantics: never returns null, and throws on a null argument. */
    public static Uri parse(String uriString) {
        if (uriString == null) {
            throw new NullPointerException("uriString");
        }

        return new Uri(uriString);
    }

    @Override
    public String toString() {
        return mUriString;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Uri)) {
            return false;
        }

        return mUriString.equals(((Uri) other).mUriString);
    }

    @Override
    public int hashCode() {
        return mUriString.hashCode();
    }

    @Override
    public int compareTo(Uri other) {
        return mUriString.compareTo(other.mUriString);
    }
}
