package net.rasanovum.roxy.legacy.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LegacyMapperTest {

    @Test
    public void preservesFullStateAndBiomeRangesAndUnsignedLighting() {
        long voxel = LegacyMapper.pack(0xFFFFF, 511, 3, 15);
        assertEquals(0xFFFFF, LegacyMapper.blockId(voxel));
        assertEquals(511, (voxel >>> 47) & 511);
        assertEquals(0xF3, LegacyMapper.getLightId(voxel));
        assertFalse(LegacyMapper.isAir(voxel));
        long relit = LegacyMapper.withLight(voxel, 0x1E);
        assertEquals(0x1E, LegacyMapper.getLightId(relit));
        assertEquals(voxel & 0x00FFFFFFFFFFFFFFL, relit & 0x00FFFFFFFFFFFFFFL);
        assertTrue(LegacyMapper.isAir(LegacyMapper.pack(0, 0, 15, 0)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesPaletteOverflowInsteadOfCorruptingLighting() {
        LegacyMapper.pack(1 << 20, 0, 0, 0);
    }

    @Test
    public void keepsMetadataAndPositionDependentOpacityDistinct() {
        LegacyPalette palette = new LegacyPalette();
        int stone = palette.register("minecraft:stone", 0, 255);
        int ore = palette.register("gregtech:gt.blockores", 15, 255);
        int transparent = palette.register("gregtech:gt.blockores", 15, 0);
        assertEquals(stone, palette.register("minecraft:stone", 0, 255));
        assertTrue(ore != transparent);
        assertEquals(4, palette.size());
        LegacyMapper mapper = palette.snapshotMapper();
        assertEquals(15, mapper.getBlockStateOpacity(LegacyMapper.pack(ore, 0, 0, 0)));
        assertEquals(0, mapper.getBlockStateOpacity(LegacyMapper.pack(transparent, 0, 0, 0)));
    }
}
