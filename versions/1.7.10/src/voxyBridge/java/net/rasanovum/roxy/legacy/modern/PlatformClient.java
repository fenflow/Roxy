package net.rasanovum.roxy.legacy.modern;

import static org.lwjgl.opengl.GL45C.*;

import java.nio.file.Path;

/** Fabric client services adapted to the legacy test client. */
public final class PlatformClient {

    private PlatformClient() {}

    public static int getOcclusionDebugState() {
        return 0;
    }

    public static boolean disableSodiumChunkRender() {
        return false;
    }

    public static boolean isFrexActive() {
        return false;
    }

    public static final class Iris {

        public static final boolean IRIS_INSTALLED = false;
        public static final boolean SHADER_SUPPORT = false;

        public static boolean irisShadowActive() {
            return false;
        }

        public static void clearIrisSamplers() {}

        public static void disableIrisShaders() {
            throw new UnsupportedOperationException("GTNH shader integration is not implemented");
        }
    }

    public interface Fabric {

        static Fabric getInstance() {
            return new Fabric() {};
        }

        default boolean isModLoaded(String mod) {
            return false;
        }

        default Path getConfigDir() {
            return PlatformGame.gameDirectory.resolve("config");
        }
    }

    public static final class GLState {

        public static void _activeTexture(int texture) {
            glActiveTexture(texture);
        }

        public static void _bindTexture(int texture) {
            glBindTexture(GL_TEXTURE_2D, texture);
        }

        public static void _glBindVertexArray(int array) {
            glBindVertexArray(array);
        }
    }

    public record Fog(float red, float green, float blue, float alpha, float environmentalStart,
        float environmentalEnd) {}
}
