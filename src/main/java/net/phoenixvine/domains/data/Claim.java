package net.phoenixvine.domains.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** A single claimed chunk. Owner is a "token" — a guild UUID or a solo player's own UUID. */
public class Claim {

    private final ChunkKey key;
    private UUID owner;
    private long claimedAt;
    private boolean chunkloaded;
    private final Map<ClaimFlag, Boolean> flagOverrides = new EnumMap<>(ClaimFlag.class);

    public Claim(ChunkKey key, UUID owner) {
        this.key = key;
        this.owner = owner;
        this.claimedAt = System.currentTimeMillis();
    }

    public ChunkKey getKey() {
        return key;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public long getClaimedAt() {
        return claimedAt;
    }

    public boolean isChunkloaded() {
        return chunkloaded;
    }

    public void setChunkloaded(boolean chunkloaded) {
        this.chunkloaded = chunkloaded;
    }

    public boolean getFlag(ClaimFlag flag) {
        return flagOverrides.getOrDefault(flag, flag.defaultValue());
    }

    public void setFlag(ClaimFlag flag, boolean value) {
        if (value == flag.defaultValue()) {
            flagOverrides.remove(flag);
        } else {
            flagOverrides.put(flag, value);
        }
    }

    public void clearFlag(ClaimFlag flag) {
        flagOverrides.remove(flag);
    }

    public Map<ClaimFlag, Boolean> getFlagOverrides() {
        return flagOverrides;
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", key.dimension().toString());
        tag.putInt("x", key.x());
        tag.putInt("z", key.z());
        tag.putUUID("owner", owner);
        tag.putLong("claimedAt", claimedAt);
        tag.putBoolean("chunkloaded", chunkloaded);

        CompoundTag flags = new CompoundTag();
        for (Map.Entry<ClaimFlag, Boolean> e : flagOverrides.entrySet()) {
            flags.putBoolean(e.getKey().name(), e.getValue());
        }
        tag.put("flags", flags);

        return tag;
    }

    public static Claim deserialize(CompoundTag tag) {
        ResourceLocation dim = new ResourceLocation(tag.getString("dim"));
        ChunkKey key = new ChunkKey(dim, tag.getInt("x"), tag.getInt("z"));
        Claim claim = new Claim(key, tag.getUUID("owner"));
        claim.claimedAt = tag.getLong("claimedAt");
        claim.chunkloaded = tag.getBoolean("chunkloaded");

        CompoundTag flags = tag.getCompound("flags");
        for (String name : flags.getAllKeys()) {
            ClaimFlag flag = ClaimFlag.byName(name);
            if (flag != null) claim.flagOverrides.put(flag, flags.getBoolean(name));
        }

        return claim;
    }
}
