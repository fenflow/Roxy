package net.rasanovum.roxy.legacy.modern;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps external diagnostics available without loading modern Minecraft GUI classes. */
public final class PlatformLog {

    private static final Logger LOG = Logger.getLogger("Roxy/Voxy");

    private PlatformLog() {}

    private static String write(Level level, Object... parts) {
        StringBuilder message = new StringBuilder();
        Throwable cause = null;
        for (Object part : parts) {
            if (part instanceof Throwable error) cause = error;
            if (!message.isEmpty()) message.append(' ');
            message.append(part);
        }
        LOG.log(level, message.toString(), cause);
        return message.toString();
    }

    public static String info(Object... parts) {
        return write(Level.INFO, parts);
    }

    public static void warn(Object... parts) {
        write(Level.WARNING, parts);
    }

    public static void error(Object... parts) {
        write(Level.SEVERE, parts);
    }

    public static void showInHUD(String message) {
        warn(message);
    }
}
