package net.rasanovum.roxy.legacy.modern;

/** Platform switches normally supplied by Fabric's entrypoint. */
public final class PlatformFlags {

    public static final boolean IS_MINE_IN_ABYSS = false;
    public static final boolean IS_IN_MINECRAFT = true;
    public static final boolean IS_DEDICATED_SERVER = false;
    public static final String MOD_VERSION = "0.2.16-beta / Roxy GTNH bridge";

    private PlatformFlags() {}

    public static boolean isVerificationFlagOn(String name) {
        return isVerificationFlagOn(name, false);
    }

    public static boolean isVerificationFlagOn(String name, boolean fallback) {
        return Boolean.parseBoolean(System.getProperty("roxy.voxy.verify." + name, Boolean.toString(fallback)));
    }

    public static void breakpoint() {}
}
