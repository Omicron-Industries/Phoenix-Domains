package net.phoenixvine.domains.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Identifies a single chunk within a specific dimension. */
public record ChunkKey(ResourceLocation dimension, int x, int z) {

    public static ChunkKey of(ResourceLocation dimension, ChunkPos pos) {
        return new ChunkKey(dimension, pos.x, pos.z);
    }

    public static ChunkKey of(Level level, ChunkPos pos) {
        return new ChunkKey(level.dimension().location(), pos.x, pos.z);
    }

    public static ChunkKey of(Level level, int blockX, int blockZ) {
        return new ChunkKey(level.dimension().location(), blockX >> 4, blockZ >> 4);
    }

    public ChunkPos toChunkPos() {
        return new ChunkPos(x, z);
    }

    @Override
    public String toString() {
        return dimension + "@" + x + "," + z;
    }
}
