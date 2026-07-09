package net.phoenixvine.domains.ownership;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;
import net.phoenixvine.guilds.GuildAPI;
import net.phoenixvine.guilds.data.Guild;
import net.phoenixvine.guilds.data.GuildManager;
import net.phoenixvine.guilds.data.GuildRank;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves what a player may do inside a claim: full trust for the owning
 * guild's members (or the solo owner), reduced access for allies gated by the
 * claim's {@code ALLY_*} flags, and no access otherwise.
 *
 * Claim *management* (unclaim, flag toggles, chunkload toggle) is gated
 * separately by {@link #canManage} — officer-or-above for guild claims, mirroring
 * {@code GuildManager}'s own OFFICER-gated settings mutations.
 */
public final class ClaimPermissions {

    private ClaimPermissions() {}

    /** True if the player belongs to the claim's owning guild, or is its solo owner. */
    public static boolean isTrusted(ServerPlayer player, Claim claim) {
        return DomainOwnership.isMemberOrSelf(player.getUUID(), claim.getOwner());
    }

    /** True if the player's guild is allied with the claim's owning guild (never true for solo claims). */
    public static boolean isAllied(ServerPlayer player, Claim claim) {
        GuildManager mgr = GuildManager.get(player.getServer().overworld());
        Optional<Guild> ownerGuild = mgr.getGuildById(claim.getOwner());
        if (ownerGuild.isEmpty()) return false;
        Optional<Guild> playerGuild = mgr.getGuildFor(player.getUUID());
        return playerGuild.isPresent() && ownerGuild.get().isAlly(playerGuild.get().getId());
    }

    public static boolean canBuild(ServerPlayer player, Claim claim) {
        if (isTrusted(player, claim)) return true;
        return isAllied(player, claim) && claim.getFlag(ClaimFlag.ALLY_BUILD);
    }

    public static boolean canInteract(ServerPlayer player, Claim claim) {
        if (isTrusted(player, claim)) return true;
        return isAllied(player, claim) && claim.getFlag(ClaimFlag.ALLY_INTERACT);
    }

    public static boolean canOpenContainer(ServerPlayer player, Claim claim) {
        if (isTrusted(player, claim)) return true;
        return isAllied(player, claim) && claim.getFlag(ClaimFlag.ALLY_CONTAINERS);
    }

    /** PvP is a plain environmental flag — it isn't gated by trust/ally, just on or off. */
    public static boolean canPvp(Claim claim) {
        return claim.getFlag(ClaimFlag.PVP);
    }

    /** Officer-or-above (or the solo owner) may unclaim, retag flags, or toggle chunkloading. */
    public static boolean canManage(ServerPlayer player, UUID ownerToken) {
        UUID myToken = DomainOwnership.tokenFor(player.getUUID());
        if (!myToken.equals(ownerToken)) return false;
        if (player.getUUID().equals(ownerToken)) return true; // solo owner
        return GuildAPI.hasRank(player.getUUID(), GuildRank.OFFICER);
    }
}
