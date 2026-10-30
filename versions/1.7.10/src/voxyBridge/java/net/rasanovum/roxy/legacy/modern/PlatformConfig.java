package net.rasanovum.roxy.legacy.modern;

import me.cortex.voxy.client.core.SSAO;

/** Legacy settings exposed through the original engine configuration ABI. */
public final class PlatformConfig {

    public static final PlatformConfig CONFIG = new PlatformConfig();
    public boolean enabled = true;
    public boolean enableRendering = true;
    public boolean ingestEnabled = true;
    public float sectionRenderDistance = 2;
    public int serviceThreads = 2;
    public float subDivisionSize = 32;
    public boolean useEnvironmentalFog = false;
    public boolean dontUseSodiumBuilderThreads = true;

    public SSAO.SSAOMode getSSAOMode() {
        return SSAO.SSAOMode.AUTO;
    }

    public boolean isRenderingEnabled() {
        return enabled && enableRendering;
    }
}
