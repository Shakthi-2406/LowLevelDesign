package app;

/** Minimal assertion helper so tests run with plain javac/java. */
public final class Check {
    private static int passed = 0;
    private static int failed = 0;

    private Check() {}

    public static void eq(Object actual, Object expected, String what) {
        boolean ok = actual == null ? expected == null : actual.equals(expected);
        record(ok, what + " | expected=" + expected + " actual=" + actual);
    }

    public static void isTrue(boolean cond, String what) {
        record(cond, what);
    }

    public static void throwsEx(Runnable r, Class<? extends Throwable> type, String what) {
        try {
            r.run();
            record(false, what + " | expected " + type.getSimpleName() + ", nothing thrown");
        } catch (Throwable t) {
            record(type.isInstance(t), what + " | expected " + type.getSimpleName() + ", got " + t);
        }
    }

    private static void record(boolean ok, String msg) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + msg);
        }
    }

    public static void summary() {
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
