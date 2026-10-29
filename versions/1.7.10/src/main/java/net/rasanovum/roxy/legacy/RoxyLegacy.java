package net.rasanovum.roxy.legacy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mod(
    modid = "roxy",
    name = "Roxy GTNH Bridge Probe",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public final class RoxyLegacy {

    @Mod.EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new RoxyProbeCommand());
        org.apache.logging.log4j.LogManager.getLogger("Roxy")
            .info(
                "Roxy 1.7.10 bridge probe initialized. LOD rendering is not implemented; use /roxy probe for diagnostics.");
    }
}
