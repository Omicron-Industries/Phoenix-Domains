package net.phoenixvine.domains.integration.solaris;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;

import java.util.Optional;

/**
 * Tints claimed chunks on Solaris's map, reusing whatever {@link ClientDomainCache} already
 * has synced for the HUD/{@code ClaimMapScreen} — no new network traffic. Semi-transparent so
 * terrain stays visible underneath.
 *
 * Chunkload is a property of an existing claim, not an independent state — you can't
 * chunkload a chunk you haven't claimed. The color should carry that dependency: a plain
 * claim shows the owner's chosen color, but chunkloading it overrides that with a fixed gold,
 * so "claimed" and "claimed + chunkloaded" are visually distinct at a glance instead of both
 * just being "owner's color" (which is what the old flat {@code entry.color()} tint did,
 * indistinguishably, regardless of chunkload state).
 */
public class DomainClaimOverlay implements SolarisOverlay {

    private static final int TINT_ALPHA = 0x99;
    private static final int CHUNKLOADED_RGB = 0xFFD700;

    @Override
    public Optional<Integer> colorAt(ResourceLocation dimension, int chunkX, int chunkZ) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(dimension)) return Optional.empty();

        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(chunkX, chunkZ);
        if (entry == null) return Optional.empty();

        int rgb = entry.chunkloaded() ? CHUNKLOADED_RGB : (entry.color() & 0xFFFFFF);
        return Optional.of((TINT_ALPHA << 24) | rgb);
    }
}
