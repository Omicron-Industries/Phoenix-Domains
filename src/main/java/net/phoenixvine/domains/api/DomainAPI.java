package net.phoenixvine.domains.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenixvine.domains.chunkload.ClaimChunkLoader;
import net.phoenixvine.domains.config.DomainsConfig;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;
import net.phoenixvine.domains.data.ClaimPower;
import net.phoenixvine.domains.data.DomainManager;
import net.phoenixvine.domains.ownership.ClaimPermissions;
import net.phoenixvine.domains.ownership.DomainOwnership;

import java.util.Optional;
import java.util.UUID;

/**
 * Stable public API for Phoenix Domains — the single entry point for other mods
 * (and this mod's own commands/network handlers) to query or mutate claims.
 * Mirrors the {@code GuildAPI} / {@code QuestAPI} house style: a final class,
 * private constructor, static methods, safe to call from any server-side code.
 */
public final class DomainAPI {

    private DomainAPI() {}

    // ── Query ─────────────────────────────────────────────────────────────────

    public static Optional<UUID> getOwner(MinecraftServer server, ChunkKey key) {
        return manager(server).getClaim(key).map(Claim::getOwner);
    }

    public static boolean isClaimed(MinecraftServer server, ChunkKey key) {
        return manager(server).isClaimed(key);
    }

    /** True if the player may break/place/build at this position — also true when unclaimed. */
    public static boolean canInteract(ServerPlayer player, BlockPos pos) {
        ChunkKey key = ChunkKey.of(player.level(), pos.getX(), pos.getZ());
        return manager(player.getServer()).getClaim(key)
                .map(claim -> ClaimPermissions.canBuild(player, claim))
                .orElse(true);
    }

    public static long getAvailableClaimBlocks(MinecraftServer server, UUID token) {
        return manager(server).getOrCreatePower(token).getAvailableClaimBlocks(DomainsConfig.CLAIM_POWER_BASE.get());
    }

    public static long getAvailableChunkloadBlocks(MinecraftServer server, UUID token) {
        return manager(server).getOrCreatePower(token)
                .getAvailableChunkloadBlocks(DomainsConfig.CHUNKLOAD_POWER_BASE.get());
    }

    // ── Claim actions ────────────────────────────────────────────────────────
    // Return a result key: "ok" on success, or an error key for the caller to
    // translate/display — mirrors GuildManager's promote/demote/ally result style.

    public static String claim(ServerPlayer player, ChunkKey key) {
        DomainManager manager = manager(player.getServer());
        if (manager.isClaimed(key)) return "already_claimed";

        // The claim map lets a player pan around and see chunks far past where they're
        // actually standing — without this, that'd let anyone claim land anywhere they've
        // simply looked at, not just land they've traveled to.
        int maxDistance = DomainsConfig.MAX_CLAIM_DISTANCE_CHUNKS.get();
        if (maxDistance > 0) {
            int playerChunkX = player.blockPosition().getX() >> 4;
            int playerChunkZ = player.blockPosition().getZ() >> 4;
            int distance = Math.max(Math.abs(playerChunkX - key.x()), Math.abs(playerChunkZ - key.z()));
            if (distance > maxDistance) return "too_far";
        }

        UUID token = DomainOwnership.tokenFor(player.getUUID());
        ClaimPower power = manager.getOrCreatePower(token);
        if (power.getAvailableClaimBlocks(DomainsConfig.CLAIM_POWER_BASE.get()) <= 0) return "no_power";

        manager.claim(key, token);
        return "ok";
    }

    public static String unclaim(ServerPlayer player, ChunkKey key) {
        DomainManager manager = manager(player.getServer());
        Optional<Claim> claimOpt = manager.getClaim(key);
        if (claimOpt.isEmpty()) return "not_claimed";
        Claim claim = claimOpt.get();
        if (!ClaimPermissions.canManage(player, claim.getOwner())) return "no_permission";

        if (claim.isChunkloaded()) {
            ServerLevel level = levelFor(player.getServer(), key.dimension());
            if (level != null) ClaimChunkLoader.setChunkForced(level, key, false);
        }
        manager.unclaim(key);
        return "ok";
    }

    public static String setChunkloaded(ServerPlayer player, ChunkKey key, boolean chunkloaded) {
        DomainManager manager = manager(player.getServer());
        Optional<Claim> claimOpt = manager.getClaim(key);
        if (claimOpt.isEmpty()) return "not_claimed";
        Claim claim = claimOpt.get();
        if (!ClaimPermissions.canManage(player, claim.getOwner())) return "no_permission";
        if (claim.isChunkloaded() == chunkloaded) return "no_change";

        if (chunkloaded) {
            ClaimPower power = manager.getOrCreatePower(claim.getOwner());
            if (power.getAvailableChunkloadBlocks(DomainsConfig.CHUNKLOAD_POWER_BASE.get()) <= 0) {
                return "no_chunkload_power";
            }
        }

        ServerLevel level = levelFor(player.getServer(), key.dimension());
        if (level == null) return "dimension_not_loaded";

        manager.setChunkloaded(key, chunkloaded);
        ClaimChunkLoader.setChunkForced(level, key, chunkloaded);
        return "ok";
    }

    public static String setFlag(ServerPlayer player, ChunkKey key, ClaimFlag flag, boolean value) {
        DomainManager manager = manager(player.getServer());
        Optional<Claim> claimOpt = manager.getClaim(key);
        if (claimOpt.isEmpty()) return "not_claimed";
        if (!ClaimPermissions.canManage(player, claimOpt.get().getOwner())) return "no_permission";

        manager.setFlag(key, flag, value);
        return "ok";
    }

    // ── Admin / external grants ─────────────────────────────────────────────

    /** Grants extra claim power to an owner token (guild or solo player UUID). No-op if the server isn't running. */
    public static void grantClaimPower(UUID token, long amount) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || token == null) return;
        DomainManager manager = manager(server);
        manager.getOrCreatePower(token).grantClaimPower(amount);
        manager.setDirty();
    }

    /**
     * Grants extra chunkload power to an owner token (guild or solo player UUID). No-op if the server isn't running.
     */
    public static void grantChunkloadPower(UUID token, long amount) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || token == null) return;
        DomainManager manager = manager(server);
        manager.getOrCreatePower(token).grantChunkloadPower(amount);
        manager.setDirty();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static DomainManager manager(MinecraftServer server) {
        return DomainManager.get(server.overworld());
    }

    private static ServerLevel levelFor(MinecraftServer server, ResourceLocation dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }
}
