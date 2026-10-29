package net.rasanovum.roxy.legacy.terrain;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.IIcon;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** 1.7.10 draw backend for externally reduced voxels, using the pack's block atlas and lightmap. */
public final class LegacyTerrainRenderer {

    private static final int[][] DIRECTIONS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    private static final float[] SHADE = { 0.5f, 1, 0.8f, 0.8f, 0.6f, 0.6f };
    private final Map<Long, Mesh> meshes = new HashMap<Long, Mesh>();
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    public int drawnChunks;
    public int drawnQuads;
    private boolean reload;

    private static final class Mesh {

        private final LodChunk chunk;
        private final int displayList;
        private final int quads;

        private Mesh(LodChunk chunk, int displayList, int quads) {
            this.chunk = chunk;
            this.displayList = displayList;
            this.quads = quads;
        }
    }

    public void reload() {
        reload = true;
    }

    public void clear() {
        for (Mesh mesh : meshes.values()) GL11.glDeleteLists(mesh.displayList, 1);
        meshes.clear();
        drawnChunks = 0;
        drawnQuads = 0;
    }

    public static boolean hasEnvironmentalFog(EntityLivingBase camera) {
        return camera.isInsideOfMaterial(Material.water) || camera.isInsideOfMaterial(Material.lava)
            || camera.isPotionActive(Potion.blindness);
    }

    public void render(Map<Long, LodChunk> chunks, int distance, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase camera = mc.renderViewEntity;
        drawnChunks = 0;
        drawnQuads = 0;
        if (camera == null || hasEnvironmentalFog(camera)) return;
        if (reload) {
            clear();
            reload = false;
        }
        for (Iterator<Map.Entry<Long, Mesh>> it = meshes.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<Long, Mesh> entry = it.next();
            if (chunks.get(entry.getKey()) != entry.getValue().chunk) {
                GL11.glDeleteLists(entry.getValue().displayList, 1);
                it.remove();
            }
        }
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        int cameraChunkX = (int) Math.floor(cx / 16);
        int cameraChunkZ = (int) Math.floor(cz / 16);
        int near = mc.gameSettings.renderDistanceChunks;
        int compiled = 0;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        projection.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        float n = projection.get(14) / (projection.get(10) - 1);
        float far = Math.max(distance * 32, near * 32);
        projection.put(10, -(far + n) / (far - n));
        projection.put(14, -2 * far * n / (far - n));
        GL11.glLoadMatrix(projection);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            Frustrum frustum = new Frustrum();
            frustum.setPosition(cx, cy, cz);
            mc.entityRenderer.enableLightmap(partialTicks);
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glEnable(GL11.GL_FOG);
            GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
            GL11.glFogf(GL11.GL_FOG_START, distance * 16 * 0.75f);
            GL11.glFogf(GL11.GL_FOG_END, distance * 16);
            GL11.glTranslated(-cx, -cy, -cz);
            for (Map.Entry<Long, LodChunk> entry : chunks.entrySet()) {
                LodChunk chunk = entry.getValue();
                int range = Math.max(Math.abs(chunk.x - cameraChunkX), Math.abs(chunk.z - cameraChunkZ));
                if (range < near || range > distance) continue;
                if (!frustum.isBoxInFrustum(chunk.x * 16, 0, chunk.z * 16, chunk.x * 16 + 16, 256, chunk.z * 16 + 16))
                    continue;
                Mesh mesh = meshes.get(entry.getKey());
                if (mesh == null) {
                    if (compiled++ >= 2) continue;
                    mesh = compile(chunk);
                    meshes.put(entry.getKey(), mesh);
                }
                GL11.glPushMatrix();
                GL11.glTranslated(chunk.x * 16, 0, chunk.z * 16);
                GL11.glCallList(mesh.displayList);
                GL11.glPopMatrix();
                drawnChunks++;
                drawnQuads += mesh.quads;
            }
        } finally {
            mc.entityRenderer.disableLightmap(partialTicks);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
        }
    }

    private Mesh compile(LodChunk chunk) {
        IIcon[][] icons = new IIcon[chunk.names.length][6];
        for (int i = 1; i < icons.length; i++) {
            Block block = Block.getBlockFromName(chunk.names[i]);
            for (int side = 0; side < 6; side++) {
                try {
                    icons[i][side] = block == null ? null : block.getIcon(side, chunk.metadata[i]);
                } catch (RuntimeException ignored) {
                    // Special renderers without a block icon fall back to the atlas missing sprite.
                }
                if (icons[i][side] == null) {
                    icons[i][side] = Minecraft.getMinecraft()
                        .getTextureMapBlocks()
                        .getAtlasSprite("missingno");
                }
            }
        }
        int list = GL11.glGenLists(1);
        int quads = 0;
        GL11.glNewList(list, GL11.GL_COMPILE);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int y = 0; y < LodChunk.HEIGHT; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int index = LodChunk.index(x, y, z);
                    int state = chunk.states[index];
                    if (state == 0) continue;
                    for (int side = 0; side < 6; side++) {
                        int[] direction = DIRECTIONS[side];
                        if (chunk.state(x + direction[0], y + direction[1], z + direction[2]) != 0) continue;
                        int light = chunk.faceLight(x, y, z, x + direction[0], y + direction[1], z + direction[2]);
                        t.setBrightness(((light & 15) << 20) | ((light >>> 4) << 4));
                        int color = chunk.colors[index];
                        float shade = SHADE[side];
                        t.setColorOpaque_F(
                            ((color >>> 16) & 255) * shade / 255,
                            ((color >>> 8) & 255) * shade / 255,
                            (color & 255) * shade / 255);
                        face(t, x * 4, y * 4, z * 4, side, icons[state][side]);
                        quads++;
                    }
                }
            }
        }
        t.draw();
        GL11.glEndList();
        return new Mesh(chunk, list, quads);
    }

    private static void face(Tessellator t, double x, double y, double z, int side, IIcon icon) {
        double a = x + 4, b = y + 4, c = z + 4;
        double u = icon.getMinU(), v = icon.getMinV(), U = icon.getMaxU(), V = icon.getMaxV();
        switch (side) {
            case 0:
                t.addVertexWithUV(x, y, z, u, v);
                t.addVertexWithUV(a, y, z, U, v);
                t.addVertexWithUV(a, y, c, U, V);
                t.addVertexWithUV(x, y, c, u, V);
                break;
            case 1:
                t.addVertexWithUV(x, b, z, u, v);
                t.addVertexWithUV(x, b, c, u, V);
                t.addVertexWithUV(a, b, c, U, V);
                t.addVertexWithUV(a, b, z, U, v);
                break;
            case 2:
                t.addVertexWithUV(x, y, z, u, V);
                t.addVertexWithUV(x, b, z, u, v);
                t.addVertexWithUV(a, b, z, U, v);
                t.addVertexWithUV(a, y, z, U, V);
                break;
            case 3:
                t.addVertexWithUV(x, y, c, u, V);
                t.addVertexWithUV(a, y, c, U, V);
                t.addVertexWithUV(a, b, c, U, v);
                t.addVertexWithUV(x, b, c, u, v);
                break;
            case 4:
                t.addVertexWithUV(x, y, z, u, V);
                t.addVertexWithUV(x, y, c, U, V);
                t.addVertexWithUV(x, b, c, U, v);
                t.addVertexWithUV(x, b, z, u, v);
                break;
            default:
                t.addVertexWithUV(a, y, z, u, V);
                t.addVertexWithUV(a, b, z, u, v);
                t.addVertexWithUV(a, b, c, U, v);
                t.addVertexWithUV(a, y, c, U, V);
                break;
        }
    }
}
