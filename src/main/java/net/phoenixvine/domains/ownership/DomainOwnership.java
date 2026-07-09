package net.phoenixvine.domains.ownership;

import net.phoenixvine.guilds.GuildAPI;

import java.awt.Color;
import java.util.UUID;

/**
 * Resolves the "owner token" for a player — their guild's UUID if they're in a
 * Phoenix Guild, otherwise their own player UUID. A {@link net.phoenixvine.domains.data.Claim}
 * always stores one of these tokens as its owner, exactly like the ownership-token
 * pattern used elsewhere in this mod family (see {@code GuildAPI.getGuildIdOrPlayerFallback}
 * and {@code TeamUtils.getTeamIdOrPlayerFallback}).
 */
public final class DomainOwnership {

    private DomainOwnership() {}

    /** The token a new claim by this player would be owned by. */
    public static UUID tokenFor(UUID playerUUID) {
        return GuildAPI.getGuildIdOrPlayerFallback(playerUUID);
    }

    /** True if the player is a member of the guild behind {@code token}, or is the solo owner. */
    public static boolean isMemberOrSelf(UUID playerUUID, UUID token) {
        return GuildAPI.isPlayerInGuildOrIs(playerUUID, token);
    }

    /** Human-readable name for a claim owner token — guild name, or a short player-UUID fallback. */
    public static String displayName(UUID token) {
        return GuildAPI.getDisplayName(token);
    }

    /**
     * A deterministic, opaque color for a claim owner token — used to tell claims apart on the
     * (deliberately rough, v1) claim map before a proper guild-flag-derived palette exists.
     */
    public static int colorFor(UUID token) {
        if (token == null) return 0xFFAAAAAA;
        float hue = (token.hashCode() & 0xFFFF) / 65536f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 0.55f, 0.9f) & 0xFFFFFF);
    }
}
