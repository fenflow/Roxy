package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import net.rasanovum.roxy.legacy.shader.ShaderBiomeAliases;

import org.junit.Test;

public class ShaderBiomeAliasesTest {

    @Test
    public void renamedBiomesUseHostIdsAndAbsentBiomesNeverMatchLegacyWorlds() {
        Map<String, String> host = new HashMap<String, String>();
        host.put("BIOME_MESA", "37");
        host.put("BIOME_ICE_PLAINS", "12");
        host.put("BIOME_HELL", "8");
        host.put("BIOME_NETHER_WASTES", "-1000");
        host.put("BIOME_GROVE", "93");
        Map<String, String> result = ShaderBiomeAliases.extend(host);
        assertEquals("37", result.get("BIOME_BADLANDS"));
        assertEquals("165", result.get("BIOME_ERODED_BADLANDS"));
        assertEquals("140", result.get("BIOME_ICE_SPIKES"));
        assertEquals("8", result.get("BIOME_NETHER_WASTES"));
        assertEquals("93", result.get("BIOME_GROVE"));
        assertTrue(Integer.parseInt(result.get("BIOME_JAGGED_PEAKS")) < 0);
        assertFalse(host.containsKey("BIOME_BADLANDS"));
    }
}
