package net.rasanovum.roxy.legacy.modern;

import net.rasanovum.roxy.legacy.bridge.LegacyModelInputs;
import net.rasanovum.roxy.legacy.bridge.ModelSnapshot;

/** Stable legacy state identity; full metadata is retained, including EndlessIDs extensions. */
public record BlockState(String name, int metadata, int opacity) {

    public BlockState {
        if (name == null || name.isEmpty()
            || name.length() > 256
            || metadata < 0
            || metadata > 65535
            || opacity < 0
            || opacity > 15) throw new IllegalArgumentException("Invalid legacy state");
    }

    public boolean isAir() {
        return name.equals("minecraft:air");
    }

    public ModelSnapshot input() {
        return LegacyModelInputs.get(name, metadata);
    }

    public PlatformTypes.Block method_26204() {
        ModelSnapshot input = input();
        if (input.fluid) return new PlatformTypes.LiquidBlock(this);
        if (input.leaves) return new PlatformTypes.LeavesBlock(this);
        return new PlatformTypes.Block(this);
    }

    public PlatformTypes.Fluid method_26227() {
        return new PlatformTypes.Fluid(this);
    }

    public boolean method_31709() {
        return false;
    }

    public boolean method_26208(PlatformTypes.BlockGetter world, PlatformTypes.Position position) {
        return false;
    }

    public int method_26213() {
        return input().emission;
    }

    public boolean method_26187(BlockState adjacent, PlatformTypes.Direction direction) {
        return equals(adjacent) && (input().fluid || input().translucent);
    }
}
