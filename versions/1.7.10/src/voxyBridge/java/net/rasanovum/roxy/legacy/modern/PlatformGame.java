package net.rasanovum.roxy.legacy.modern;

import java.nio.file.Path;
import java.util.Optional;

import org.joml.Matrix4f;

/** Client platform snapshot consumed by the original engine, updated by the legacy client thread. */
public final class PlatformGame {

    public static int renderDistance = 8;
    public static int mipLevels;
    public static float partialTick;
    public static float fov = 70;
    public static Matrix4f projection = new Matrix4f();
    public static Path gameDirectory;
    private static final PlatformGame INSTANCE = new PlatformGame();
    public final World field_1687 = new World();
    public final Options field_1690 = new Options();
    public final Renderer field_1773 = new Renderer();
    public final LevelRenderer field_1769 = new LevelRenderer();

    public static PlatformGame method_1551() {
        return INSTANCE;
    }

    public Textures method_1531() {
        return new Textures();
    }

    public Colours method_1505() {
        return new Colours();
    }

    public Chat method_44714() {
        return new Chat();
    }

    public int method_47599() {
        return 60;
    }

    public Timer method_61966() {
        return paused -> partialTick;
    }

    public static final class Options {

        public int method_38521() {
            return renderDistance;
        }

        public Option method_42503() {
            return new Option();
        }
    }

    public static final class Option {

        public Object method_41753() {
            return renderDistance;
        }
    }

    public interface Timer {

        float method_60637(boolean paused);
    }

    public static final class Camera {
    }

    public static final class LevelRenderer {
    }

    public static final class Renderer {

        public Camera method_19418() {
            return new Camera();
        }

        public Matrix4f method_22973(float ignoredFov) {
            return new Matrix4f(projection);
        }

        public float method_3196(Camera camera, float tick, boolean changing) {
            return fov;
        }

        public float method_3193() {
            return renderDistance * 16;
        }
    }

    public static final class World {

        public int method_31597() {
            return 16;
        }

        public int method_32891() {
            return 0;
        }

        public RegistryAccess method_30349() {
            return key -> new BiomeRegistry();
        }

        public float method_24852(PlatformTypes.Direction direction, boolean shade) {
            if (!shade) return 1;
            return switch (direction) {
                case DOWN -> .5f;
                case UP -> 1;
                case NORTH, SOUTH -> .8f;
                case EAST, WEST -> .6f;
            };
        }
    }

    public static class Texture {
    }

    public static final class Atlas extends Texture {

        public final int field_64244 = mipLevels;
    }

    public static final class Textures {

        public Texture method_4619(ResourceId id) {
            return new Atlas();
        }
    }

    public interface Text {

        static PlainText method_43470(String value) {
            return new PlainText(value);
        }
    }

    public record PlainText(String value) implements Text {}

    public static final class Chat {

        public void method_44736(Text text, boolean overlay) {
            PlatformLog.warn(text);
        }
    }

    public record Key(String name) {}

    public record Biome(int id) {}

    public static final class Biomes {

        public static final Key field_9451 = new Key("minecraft:plains");
    }

    public static final class Registries {

        public static final Key field_41236 = new Key("minecraft:worldgen/biome");
    }

    public record Holder(Object value) {

        public Object comp_349() {
            return value;
        }
    }

    public interface Registry {

        Object method_29107(Key key);

        Optional<Holder> method_10223(ResourceId name);
    }

    public interface RegistryAccess {

        Registry method_30530(Key key);
    }

    public static final class BiomeRegistry implements Registry {

        public Object method_29107(Key key) {
            return new Biome(1);
        }

        public Optional<Holder> method_10223(ResourceId name) {
            if (name.toString()
                .equals("minecraft:plains")) return Optional.of(new Holder(new Biome(1)));
            if (name.namespace()
                .equals("gtnh")
                && name.path()
                    .startsWith("biome/"))
                return Optional.of(
                    new Holder(
                        new Biome(
                            Integer.parseInt(
                                name.path()
                                    .substring(6)))));
            return Optional.empty();
        }
    }

    public interface BlockRegistry {

        int method_10206(Object block);
    }

    public static final class Builtins {

        public static final BlockRegistry field_41175 = block -> 0;
    }

    public static final class Colours {

        public final ColourIds field_1995 = new ColourIds();
    }

    public static final class ColourIds {

        public Object method_10200(int id) {
            return (PlatformTypes.BlockColor) (state, world, position, tint) -> world.method_23752(
                position,
                (biome, x, z) -> state.input()
                    .colour(biome.id()));
        }
    }
}
