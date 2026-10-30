package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import net.rasanovum.roxy.legacy.bridge.LegacyVertexColour;

import org.junit.Test;

public class LegacyVertexColourTest {

    @Test
    public void detectsShadeAlreadyAppliedByRenderBlocks() {
        assertTrue(LegacyVertexColour.hasDirectionalShade(0xff7f7f7f, 0xffffff));
        assertTrue(LegacyVertexColour.hasDirectionalShade(0xff999999, 0xffffff));
        assertTrue(LegacyVertexColour.hasDirectionalShade(0xffcccccc, 0xffffff));
        assertTrue(LegacyVertexColour.hasDirectionalShade(0xff203040, 0x80_60_40));
        assertFalse(LegacyVertexColour.hasDirectionalShade(0xffffffff, 0xffffff));
        assertFalse(LegacyVertexColour.hasDirectionalShade(0xff406080, 0x80_60_40));
        assertFalse(LegacyVertexColour.hasDirectionalShade(0xff000000, 0x000000));
    }
}
