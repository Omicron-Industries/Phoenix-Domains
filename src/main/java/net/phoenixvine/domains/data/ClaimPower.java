package net.phoenixvine.domains.data;

import net.minecraft.nbt.CompoundTag;

/**
 * A resource pool for one owner token (guild or solo player UUID). Tracks two
 * independent budgets — claim blocks (spent claiming chunks) and chunkload blocks
 * (spent keeping claimed chunks force-loaded) — each built from three sources:
 * a config base, passive real-time accrual (capped), and admin/API grants.
 *
 * This class does no config lookups itself; callers pass in the current config
 * base/cap so the pool stays decoupled from {@code DomainsConfig}.
 */
public class ClaimPower {

    private double accruedClaim;
    private long grantedClaim;
    private int claimsUsed;

    private double accruedChunkload;
    private long grantedChunkload;
    private int chunkloadsUsed;

    // ── Claim power ──────────────────────────────────────────────────────────

    public void accrueClaimPower(double amount, double accrualCap) {
        accruedClaim = Math.min(accruedClaim + amount, accrualCap);
    }

    public void grantClaimPower(long amount) {
        grantedClaim = Math.max(0, grantedClaim + amount);
    }

    public long getEarnedClaimBlocks(long configBase) {
        return configBase + (long) Math.floor(accruedClaim) + grantedClaim;
    }

    public long getAvailableClaimBlocks(long configBase) {
        return Math.max(0, getEarnedClaimBlocks(configBase) - claimsUsed);
    }

    public int getClaimsUsed() {
        return claimsUsed;
    }

    public void incrementClaimsUsed() {
        claimsUsed++;
    }

    public void decrementClaimsUsed() {
        claimsUsed = Math.max(0, claimsUsed - 1);
    }

    // ── Chunkload power ──────────────────────────────────────────────────────

    public void accrueChunkloadPower(double amount, double accrualCap) {
        accruedChunkload = Math.min(accruedChunkload + amount, accrualCap);
    }

    public void grantChunkloadPower(long amount) {
        grantedChunkload = Math.max(0, grantedChunkload + amount);
    }

    public long getEarnedChunkloadBlocks(long configBase) {
        return configBase + (long) Math.floor(accruedChunkload) + grantedChunkload;
    }

    public long getAvailableChunkloadBlocks(long configBase) {
        return Math.max(0, getEarnedChunkloadBlocks(configBase) - chunkloadsUsed);
    }

    public int getChunkloadsUsed() {
        return chunkloadsUsed;
    }

    public void incrementChunkloadsUsed() {
        chunkloadsUsed++;
    }

    public void decrementChunkloadsUsed() {
        chunkloadsUsed = Math.max(0, chunkloadsUsed - 1);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("accruedClaim", accruedClaim);
        tag.putLong("grantedClaim", grantedClaim);
        tag.putInt("claimsUsed", claimsUsed);
        tag.putDouble("accruedChunkload", accruedChunkload);
        tag.putLong("grantedChunkload", grantedChunkload);
        tag.putInt("chunkloadsUsed", chunkloadsUsed);
        return tag;
    }

    public static ClaimPower deserialize(CompoundTag tag) {
        ClaimPower power = new ClaimPower();
        power.accruedClaim = tag.getDouble("accruedClaim");
        power.grantedClaim = tag.getLong("grantedClaim");
        power.claimsUsed = tag.getInt("claimsUsed");
        power.accruedChunkload = tag.getDouble("accruedChunkload");
        power.grantedChunkload = tag.getLong("grantedChunkload");
        power.chunkloadsUsed = tag.getInt("chunkloadsUsed");
        return power;
    }
}
