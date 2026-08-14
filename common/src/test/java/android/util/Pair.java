package android.util;

/**
 * Minimal stand-in for {@code android.util.Pair}, for plain-JUnit tests.
 *
 * <p>Unit tests run against AGP's "mockable" android.jar. With
 * {@code testOptions.unitTests.returnDefaultValues = true}, {@code Pair.create()} returns
 * {@code null}, and {@link com.liskovsoft.smartyoutubetv2.common.exoplayer.dash.DashManifestParser2}
 * carries its parsed period back through one — so the manifest comes out with no periods at all and
 * every assertion fails for a reason unrelated to the code under test. Test sources precede the
 * mockable jar on the classpath, so this class shadows the stub.
 *
 * <p>Keep the semantics identical to the platform's, and add members only when a test needs them.
 */
public class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public static <A, B> Pair<A, B> create(A a, B b) {
        return new Pair<>(a, b);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Pair)) {
            return false;
        }

        Pair<?, ?> that = (Pair<?, ?>) other;

        return equal(first, that.first) && equal(second, that.second);
    }

    private static boolean equal(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public int hashCode() {
        return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode());
    }

    @Override
    public String toString() {
        return "Pair{" + first + " " + second + "}";
    }
}
