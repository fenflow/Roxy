package net.rasanovum.roxy.legacy.terrain;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IWorldAccess;

/** Receives the same block and lighting invalidations as the normal chunk renderer. */
final class WorldChanges implements IWorldAccess {

    private final ModernTerrain terrain;

    WorldChanges(ModernTerrain terrain) {
        this.terrain = terrain;
    }

    public void markBlockForUpdate(int x, int y, int z) {
        terrain.changed(x, y, z, x, y, z);
    }

    public void markBlockForRenderUpdate(int x, int y, int z) {
        terrain.changed(x, y, z, x, y, z);
    }

    public void markBlockRangeForRenderUpdate(int x, int y, int z, int maxX, int maxY, int maxZ) {
        terrain.changed(x, y, z, maxX, maxY, maxZ);
    }

    public void playSound(String sound, double x, double y, double z, float volume, float pitch) {}

    public void playSoundToNearExcept(EntityPlayer player, String sound, double x, double y, double z, float volume,
        float pitch) {}

    public void spawnParticle(String particle, double x, double y, double z, double vx, double vy, double vz) {}

    public void onEntityCreate(Entity entity) {}

    public void onEntityDestroy(Entity entity) {}

    public void playRecord(String record, int x, int y, int z) {}

    public void broadcastSound(int sound, int x, int y, int z, int data) {}

    public void playAuxSFX(EntityPlayer player, int effect, int x, int y, int z, int data) {}

    public void destroyBlockPartially(int entity, int x, int y, int z, int progress) {}

    public void onStaticEntitiesChanged() {}
}
