package net.phoenixvine.domains.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide claim storage. A single instance is anchored to the overworld's
 * {@code DataStorage} (mirrors {@code GuildManager}) so the claim-power economy
 * stays a single shared pool per owner token regardless of which dimension a
 * chunk was claimed in.
 */
public class DomainManager extends SavedData {

    private static final String SAVE_KEY = "phoenix_domains";

    private final Map<ChunkKey, Claim> claims = new LinkedHashMap<>();
    private final Map<UUID, Set<ChunkKey>> claimsByOwner = new HashMap<>();
    private final Map<UUID, ClaimPower> powers = new HashMap<>();

    // ── SavedData factory ─────────────────────────────────────────────────────

    public static DomainManager get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(DomainManager::load, DomainManager::new, SAVE_KEY);
    }

    private static DomainManager load(CompoundTag tag) {
        DomainManager mgr = new DomainManager();

        ListTag claimList = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimList.size(); i++) {
            Claim claim = Claim.deserialize(claimList.getCompound(i));
            mgr.claims.put(claim.getKey(), claim);
            mgr.claimsByOwner.computeIfAbsent(claim.getOwner(), k -> new HashSet<>()).add(claim.getKey());
        }

        ListTag powerList = tag.getList("powers", Tag.TAG_COMPOUND);
        for (int i = 0; i < powerList.size(); i++) {
            CompoundTag p = powerList.getCompound(i);
            mgr.powers.put(p.getUUID("owner"), ClaimPower.deserialize(p));
        }

        return mgr;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag claimList = new ListTag();
        for (Claim claim : claims.values()) claimList.add(claim.serialize());
        tag.put("claims", claimList);

        ListTag powerList = new ListTag();
        for (Map.Entry<UUID, ClaimPower> e : powers.entrySet()) {
            CompoundTag p = e.getValue().serialize();
            p.putUUID("owner", e.getKey());
            powerList.add(p);
        }
        tag.put("powers", powerList);

        return tag;
    }

    // ── Claim query ───────────────────────────────────────────────────────────

    public Optional<Claim> getClaim(ChunkKey key) {
        return Optional.ofNullable(claims.get(key));
    }

    public boolean isClaimed(ChunkKey key) {
        return claims.containsKey(key);
    }

    public Set<ChunkKey> getClaimsForOwner(UUID token) {
        return claimsByOwner.getOrDefault(token, Set.of());
    }

    public int getClaimCount() {
        return claims.size();
    }

    // ── Claim mutation ───────────────────────────────────────────────────────

    /** Claims the chunk for the given owner token. Returns false if already claimed. */
    public boolean claim(ChunkKey key, UUID owner) {
        if (claims.containsKey(key)) return false;
        Claim claim = new Claim(key, owner);
        claims.put(key, claim);
        claimsByOwner.computeIfAbsent(owner, k -> new HashSet<>()).add(key);
        getOrCreatePower(owner).incrementClaimsUsed();
        setDirty();
        return true;
    }

    /** Unclaims the chunk. Returns false if it wasn't claimed. */
    public boolean unclaim(ChunkKey key) {
        Claim claim = claims.remove(key);
        if (claim == null) return false;

        Set<ChunkKey> owned = claimsByOwner.get(claim.getOwner());
        if (owned != null) owned.remove(key);

        ClaimPower power = powers.get(claim.getOwner());
        if (power != null) {
            power.decrementClaimsUsed();
            if (claim.isChunkloaded()) power.decrementChunkloadsUsed();
        }

        setDirty();
        return true;
    }

    /** Toggles chunkloading for an already-claimed chunk. Returns false if not claimed. */
    public boolean setChunkloaded(ChunkKey key, boolean chunkloaded) {
        Claim claim = claims.get(key);
        if (claim == null || claim.isChunkloaded() == chunkloaded) return false;

        claim.setChunkloaded(chunkloaded);
        ClaimPower power = getOrCreatePower(claim.getOwner());
        if (chunkloaded) power.incrementChunkloadsUsed();
        else power.decrementChunkloadsUsed();

        setDirty();
        return true;
    }

    public void setFlag(ChunkKey key, ClaimFlag flag, boolean value) {
        Claim claim = claims.get(key);
        if (claim == null) return;
        claim.setFlag(flag, value);
        setDirty();
    }

    // ── Power ─────────────────────────────────────────────────────────────────

    public ClaimPower getOrCreatePower(UUID token) {
        return powers.computeIfAbsent(token, k -> new ClaimPower());
    }

    public Optional<ClaimPower> getPower(UUID token) {
        return Optional.ofNullable(powers.get(token));
    }
}
