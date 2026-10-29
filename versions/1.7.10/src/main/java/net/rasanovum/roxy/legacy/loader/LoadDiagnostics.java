package net.rasanovum.roxy.legacy.loader;

/** Reports the actual failure instead of suggesting a Java upgrade for every exception. */
public final class LoadDiagnostics {

    private LoadDiagnostics() {}

    public static String describe(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 16; depth++) {
            if (cause instanceof UnsupportedClassVersionError) {
                return "The JVM rejected a class version. External Voxy requires Java 21+. This game reports Java "
                    + System.getProperty("java.version")
                    + ". "
                    + detail(cause);
            }
            if (cause.getCause() == cause) break;
            cause = cause.getCause();
        }
        String result = detail(error);
        // Preserve an explanatory outer message (including the checked JAR path), and expose
        // the root cause when reflection or a class loader has wrapped the actual failure.
        cause = error;
        for (int depth = 0; cause.getCause() != null && cause.getCause() != cause && depth < 16; depth++) {
            cause = cause.getCause();
        }
        if (cause != error) result += " Cause: " + detail(cause);
        return result;
    }

    public static String environment() {
        return "Java " + System.getProperty("java.version")
            + " ("
            + System.getProperty("java.vm.name")
            + "); java.home="
            + System.getProperty("java.home");
    }

    private static String detail(Throwable error) {
        String message = error.getMessage();
        return error.getClass()
            .getSimpleName()
            + (message == null ? ""
                : ": " + message.replace('\n', ' ')
                    .replace('\r', ' '));
    }
}
