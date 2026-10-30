package net.rasanovum.roxy.legacy.bridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable model input crossing the legacy/modern class-loader boundary. */
public final class ModelSnapshot {

    public final float[] vertices;
    public final float[][] fluidFaces;
    public final boolean fluid;
    public final boolean leaves;
    public final boolean translucent;
    public final int emission;
    public final int colour;
    private final Map<Integer, Integer> biomeColours;

    public ModelSnapshot(float[] vertices, boolean fluid, boolean leaves, boolean translucent, int emission,
        int colour) {
        this(vertices, fluid, leaves, translucent, emission, colour, Collections.<Integer, Integer>emptyMap());
    }

    public ModelSnapshot(float[] vertices, boolean fluid, boolean leaves, boolean translucent, int emission, int colour,
        Map<Integer, Integer> biomeColours) {
        this(vertices, fluid, leaves, translucent, emission, colour, biomeColours, null);
    }

    public ModelSnapshot(float[] vertices, boolean fluid, boolean leaves, boolean translucent, int emission, int colour,
        Map<Integer, Integer> biomeColours, float[][] fluidFaces) {
        if (vertices.length % 24 != 0) throw new IllegalArgumentException("Expected complete quads");
        this.vertices = vertices.clone();
        this.fluidFaces = fluidFaces == null ? null : new float[6][];
        if (fluidFaces != null) for (int face = 0; face < 6; face++) this.fluidFaces[face] = fluidFaces[face].clone();
        this.fluid = fluid;
        this.leaves = leaves;
        this.translucent = translucent;
        this.emission = emission;
        this.colour = colour;
        this.biomeColours = Collections.unmodifiableMap(new HashMap<Integer, Integer>(biomeColours));
    }

    public int colour(int biome) {
        Integer value = biomeColours.get(biome);
        return value == null ? colour : value;
    }
}
