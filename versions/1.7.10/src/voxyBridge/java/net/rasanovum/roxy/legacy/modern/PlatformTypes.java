package net.rasanovum.roxy.legacy.modern;

import net.rasanovum.roxy.legacy.bridge.ModelSnapshot;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

/** Small platform values used by the original Voxy model baker's Minecraft-facing ABI. */
public final class PlatformTypes {

    public static final class MathHelper {

        public static int method_15357(double value) {
            return (int) Math.floor(value);
        }
    }

    private PlatformTypes() {}

    public interface VertexConsumer {
    }

    public record Position(int x, int y, int z) {

        public static final Position field_10980 = new Position(0, 0, 0);
    }

    public interface BlockGetter {
    }

    public interface TintGetter extends BlockGetter {

        int method_23752(Position position, ColorResolver resolver);
    }

    public interface ColorResolver {

        int getColor(PlatformGame.Biome biome, double x, double z);
    }

    public interface BlockColor {

        int getColor(BlockState state, TintGetter world, Position position, int tint);
    }

    public static final class BlockEntity {
    }

    public static final class LightEngine {
    }

    public static final class LightLayer {
    }

    public enum Layer {

        SOLID,
        CUTOUT_MIPPED,
        CUTOUT,
        TRANSLUCENT,
        TRIPWIRE;

        public static final Layer field_60923 = SOLID;
        public static final Layer field_60925 = CUTOUT;
        public static final Layer field_60926 = TRANSLUCENT;
        public static final Layer field_60927 = TRIPWIRE;
    }

    public static class Block {

        public final BlockState state;

        public Block(BlockState state) {
            this.state = state;
        }

        public BlockState method_9564() {
            return state;
        }

        public BlockState method_34725(BlockState other) {
            return other;
        }
    }

    public static final class LiquidBlock extends Block {

        public LiquidBlock(BlockState state) {
            super(state);
        }
    }

    public static final class LeavesBlock extends Block {

        public LeavesBlock(BlockState state) {
            super(state);
        }
    }

    public static final class StairBlock extends Block {

        public final BlockState field_11574;

        public StairBlock(BlockState state) {
            super(state);
            field_11574 = state;
        }
    }

    public record Fluid(BlockState state) {

        public boolean method_15769() {
            return !state.input().fluid;
        }

        public BlockState method_15759() {
            return state;
        }
    }

    public static final class Layers {

        private Layers() {}

        public static Layer method_23679(BlockState state) {
            ModelSnapshot input = state.input();
            return input.translucent ? Layer.TRANSLUCENT : state.opacity() == 15 ? Layer.SOLID : Layer.CUTOUT;
        }

        public static Layer method_23680(Fluid fluid) {
            return method_23679(fluid.state());
        }
    }

    public enum Direction {

        DOWN,
        UP,
        NORTH,
        SOUTH,
        WEST,
        EAST;

        public static final Direction field_11033 = DOWN;
        public static final Direction field_11036 = UP;
        public static final Direction field_11043 = NORTH;
        public static final Direction field_11034 = EAST;

        public int method_10146() {
            return ordinal();
        }
    }

    public static final class PoseStack {

        private final Pose pose = new Pose();

        public void method_46416(float x, float y, float z) {
            pose.matrix.translate(x, y, z);
        }

        public void method_22907(Quaternionfc rotation) {
            pose.matrix.rotate(rotation);
            // Cardinal face rotations must stay exact; tiny quaternion errors leave diagonal pixel cracks.
            float[] values = pose.matrix.get(new float[16]);
            for (int i = 0; i < 12; i++) {
                if (Math.abs(values[i]) < 0.000001f) values[i] = 0;
                else if (Math.abs(values[i] - 1) < 0.000001f) values[i] = 1;
                else if (Math.abs(values[i] + 1) < 0.000001f) values[i] = -1;
            }
            pose.matrix.set(values);
        }

        public void method_34425(Matrix4fc matrix) {
            pose.matrix.mul(matrix);
        }

        public Pose method_23760() {
            return pose;
        }
    }

    public static final class Pose {

        private final Matrix4f matrix = new Matrix4f();

        public Matrix4f method_23761() {
            return matrix;
        }
    }

    public static final class ColorMixer {

        private ColorMixer() {}

        public static int mix(int a, int b, int weight) {
            int result = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int channel = (((a >>> shift) & 255) * weight + ((b >>> shift) & 255) * (256 - weight)) >>> 8;
                result |= channel << shift;
            }
            return result;
        }
    }
}
