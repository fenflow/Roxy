package net.rasanovum.roxy.legacy;

import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.rasanovum.roxy.legacy.bridge.LegacyChunkCapture;
import net.rasanovum.roxy.legacy.bridge.LegacyPalette;
import net.rasanovum.roxy.legacy.loader.VoxyRuntime;

/** Explicit diagnostic only: does not enable an incomplete renderer or alter world data. */
public final class RoxyProbeCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "roxy";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/roxy probe - test the Voxy chunk bridge (no LOD rendering yet)";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        if (arguments.length != 1 || !"probe".equals(arguments[0])) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            sender.addChatMessage(new ChatComponentText("Roxy: enter a world first."));
            return;
        }
        Path original = mc.mcDataDir.toPath()
            .resolve("roxy")
            .resolve("voxy.jar");
        try (VoxyRuntime runtime = new VoxyRuntime(original)) {
            int x = MathHelper.floor_double(mc.thePlayer.posX) >> 4;
            int z = MathHelper.floor_double(mc.thePlayer.posZ) >> 4;
            int y = Math.max(0, Math.min(15, MathHelper.floor_double(mc.thePlayer.posY) >> 4));
            Chunk chunk = mc.theWorld.getChunkFromChunkCoords(x, z);
            LegacyPalette palette = new LegacyPalette();
            long[] snapshot = LegacyChunkCapture.capture(mc.theWorld, chunk, y, palette);
            Object section = runtime.convert(x, y, z, snapshot, palette.snapshotMapper());
            sender.addChatMessage(
                new ChatComponentText(
                    "Roxy bridge passed: " + runtime.copyData(section).length
                        + " voxels across 5 LOD levels, "
                        + palette.size()
                        + " legacy states. Renderer not implemented yet."));
        } catch (Exception | LinkageError error) {
            sender.addChatMessage(
                new ChatComponentText(
                    "Roxy probe failed: " + error.getClass()
                        .getSimpleName()
                        + ": "
                        + error.getMessage()
                        + ". Requires Java 21+ and original Voxy H3w2nVdU at roxy/voxy.jar."));
        }
    }
}
