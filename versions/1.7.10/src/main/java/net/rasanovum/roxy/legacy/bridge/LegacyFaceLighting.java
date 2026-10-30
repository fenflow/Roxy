package net.rasanovum.roxy.legacy.bridge;

/** Adapts the model-lighting convention for legacy translucent full cubes such as ice. */
public final class LegacyFaceLighting {

    private LegacyFaceLighting() {}

    public static long adapt(long metadata, float[] vertices) {
        // Voxy's model header identifies translucent models and actual fluids separately.
        if (((metadata >>> 48) & 18) != 2 || vertices.length != 144) return metadata;
        int faces = 0;
        for (int quad = 0; quad < 6; quad++) {
            int base = quad * 24;
            int face = -1;
            for (int axis = 0; axis < 3; axis++) {
                float plane = vertices[base + axis];
                if (plane != 0 && plane != 1) continue;
                int corners = 0;
                boolean boundary = true;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = base + vertex * 6;
                    float u = vertices[offset + (axis + 1) % 3];
                    float v = vertices[offset + (axis + 2) % 3];
                    if (vertices[offset + axis] != plane || (u != 0 && u != 1) || (v != 0 && v != 1)) {
                        boundary = false;
                        break;
                    }
                    corners |= 1 << ((int) u + 2 * (int) v);
                }
                if (boundary && corners == 15) face = axis * 2 + (int) plane;
            }
            if (face < 0 || (faces & (1 << face)) != 0) return metadata;
            faces |= 1 << face;
        }
        // The original mesher then samples the adjacent cell on each face, as RenderBlocks does.
        return faces == 63 ? metadata & ~0x080808080808L : metadata;
    }
}
