package net.rasanovum.roxy.legacy.loader;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/** Installs only the legacy render-phase handoff; Voxy remains external. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({ "net.rasanovum.roxy.legacy.loader." })
public final class RoxyCorePlugin implements IFMLLoadingPlugin {

    public String[] getASMTransformerClass() {
        return new String[] { RenderPhaseTransformer.class.getName(), ChunkArrivalTransformer.class.getName(),
            ShaderPhaseTransformer.class.getName() };
    }

    public String getModContainerClass() {
        return null;
    }

    public String getSetupClass() {
        return null;
    }

    public void injectData(Map<String, Object> data) {}

    public String getAccessTransformerClass() {
        return null;
    }
}
