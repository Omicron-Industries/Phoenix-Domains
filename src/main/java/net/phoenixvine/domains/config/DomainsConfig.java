package net.phoenixvine.domains.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Server-side claim economy tuning. Every owner token's {@code ClaimPower} is
 * built from three sources at query time: this config's base, real-time passive
 * accrual (rate/cap also from here), and admin/API grants stored on the pool
 * itself — see {@link net.phoenixvine.domains.data.ClaimPower}.
 */
public final class DomainsConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue CLAIM_POWER_BASE;
    public static final ForgeConfigSpec.DoubleValue CLAIM_POWER_PER_HOUR;
    public static final ForgeConfigSpec.DoubleValue CLAIM_POWER_MAX_ACCRUAL;

    public static final ForgeConfigSpec.IntValue CHUNKLOAD_POWER_BASE;
    public static final ForgeConfigSpec.DoubleValue CHUNKLOAD_POWER_PER_HOUR;
    public static final ForgeConfigSpec.DoubleValue CHUNKLOAD_POWER_MAX_ACCRUAL;

    public static final ForgeConfigSpec.IntValue MAX_CLAIM_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAX_CLAIMED_CHUNKS_PER_OWNER;
    public static final ForgeConfigSpec.IntValue MAX_FORCELOADED_CHUNKS_PER_OWNER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("claim_power");
        CLAIM_POWER_BASE = builder
                .comment("Claim blocks every owner token (guild or solo player) starts with, before accrual or grants.")
                .defineInRange("base", 64, 0, Integer.MAX_VALUE);
        CLAIM_POWER_PER_HOUR = builder.comment(
                "Claim blocks passively accrued per hour, for every hour at least one member of the owner token is online.")
                .defineInRange("perHour", 4.0, 0.0, Double.MAX_VALUE);
        CLAIM_POWER_MAX_ACCRUAL = builder
                .comment("Cap on passively-accrued claim blocks (does not cap base or admin-granted blocks).")
                .defineInRange("maxAccrual", 500.0, 0.0, Double.MAX_VALUE);
        builder.pop();

        builder.push("chunkload_power");
        CHUNKLOAD_POWER_BASE = builder
                .comment("Chunkload blocks every owner token starts with, before accrual or grants.")
                .defineInRange("base", 4, 0, Integer.MAX_VALUE);
        CHUNKLOAD_POWER_PER_HOUR = builder.comment(
                "Chunkload blocks passively accrued per hour, for every hour at least one member of the owner token is online.")
                .defineInRange("perHour", 0.5, 0.0, Double.MAX_VALUE);
        CHUNKLOAD_POWER_MAX_ACCRUAL = builder
                .comment("Cap on passively-accrued chunkload blocks (does not cap base or admin-granted blocks).")
                .defineInRange("maxAccrual", 50.0, 0.0, Double.MAX_VALUE);
        builder.pop();

        builder.push("restrictions");
        MAX_CLAIM_DISTANCE_CHUNKS = builder
                .comment("Maximum distance, in chunks, a player can be from a chunk to claim it — the " +
                        "Solaris-backed claim map lets you pan around and see chunks far past where you're " +
                        "actually standing, so without a limit here players could claim land anywhere " +
                        "they've simply looked at, not just land they've traveled to. 0 = no limit.")
                .defineInRange("maxClaimDistanceChunks", 32, 0, Integer.MAX_VALUE);
        MAX_CLAIMED_CHUNKS_PER_OWNER = builder
                .comment("Flat hard ceiling on how many chunks a single owner token (guild or solo player) may " +
                        "have claimed at once, independent of the claim-power economy — a config admins can use " +
                        "even if they don't want to touch the power-pool system at all. 0 = no limit.")
                .defineInRange("maxClaimedChunksPerOwner", 0, 0, Integer.MAX_VALUE);
        MAX_FORCELOADED_CHUNKS_PER_OWNER = builder
                .comment("Flat hard ceiling on how many of an owner token's claimed chunks may be forceloaded " +
                        "at once, independent of the chunkload-power economy. 0 = no limit.")
                .defineInRange("maxForceloadedChunksPerOwner", 0, 0, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private DomainsConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}
