package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import net.rasanovum.roxy.legacy.bridge.LegacyFaceLighting;

import org.junit.Test;

public class LegacyFaceLightingTest {

    @Test
    public void onlyCompleteTranslucentCubesUseAdjacentLighting() {
        float[] cube = new float[144];
        for (int face = 0; face < 6; face++) for (int vertex = 0; vertex < 4; vertex++) {
            int base = face * 24 + vertex * 6;
            int axis = face / 2;
            cube[base + axis] = face & 1;
            cube[base + (axis + 1) % 3] = vertex & 1;
            cube[base + (axis + 2) % 3] = vertex >>> 1;
        }
        long ice = 0x230e0e0e0e0e0eL;
        assertEquals(0x23060606060606L, LegacyFaceLighting.adapt(ice, cube));
        long fluid = 0x330e0e0e0e0e0eL;
        assertEquals(fluid, LegacyFaceLighting.adapt(fluid, cube));
        assertEquals(0x63070707070707L, LegacyFaceLighting.adapt(0x63070707070707L, cube));
        cube[0] = .125f;
        assertEquals("Inset/partial geometry keeps original self lighting", ice, LegacyFaceLighting.adapt(ice, cube));
    }
}
