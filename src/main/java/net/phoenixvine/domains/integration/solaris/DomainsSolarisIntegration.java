package net.phoenixvine.domains.integration.solaris;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.solaris.api.SolarisFeatureState;

/**
 * Optional integration with Solaris. {@code phoenix_solaris} is declared as a
 * non-mandatory, client-only {@code mods.toml} dependency, so nothing in this class
 * may be touched unless {@link #isAvailable()} is true first — and that check, plus
 * every call into this class, must happen from code that already only ever runs on
 * the physical client (never from a class Forge loads on a dedicated server), or the
 * JVM will try to resolve Solaris's classes and crash on a server that doesn't have
 * it installed. Mirrors {@code DomainsChroniclesIntegration} /
 * {@code net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat}.
 *
 * Registers a claim-tint overlay on Solaris's own terrain map (so claims show up there
 * too), and hands out {@link SolarisClaimMapScreen} — a Domains-specific screen that
 * reuses Solaris's real terrain rendering with a chunk grid and claim interactions laid
 * over it, in place of Domains' own vanilla-only fallback ({@code ClaimMapScreen}).
 */
public final class DomainsSolarisIntegration {

    public static final String SOLARIS_MOD_ID = "phoenix_solaris";

    /**
     * Feature id this mod registers under Solaris's generic per-player/team tri-state system
     * ({@code SolarisFeatureState}) — {@code DISABLED} hides the claim-map keybind's screen
     * entirely, {@code VISIBLE} allows browsing but refuses claim/unclaim/chunkload clicks
     * client-side, {@code ENABLED} allows full management. Rides entirely on Solaris's own
     * team-shared persistence/sync; Domains stores no state of its own for this.
     */
    public static final String FEATURE_CLAIM_MAP = "domains_claim_map";

    private static boolean registered = false;

    private DomainsSolarisIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(SOLARIS_MOD_ID);
    }

    /** Call only after {@link #isAvailable()} has returned true. Idempotent. */
    public static void init() {
        if (registered) return;
        registered = true;
        net.phoenixvine.solaris.api.SolarisAPI.registerOverlay(new DomainClaimOverlay());
    }

    /** Call only after {@link #isAvailable()} has returned true. */
    public static void requestRefresh() {
        net.phoenixvine.solaris.api.SolarisAPI.requestRefresh();
    }

    /** Call only after {@link #isAvailable()} has returned true. */
    public static Screen openClaimMapScreen() {
        return new SolarisClaimMapScreen();
    }

    /** Call only after {@link #isAvailable()} has returned true. */
    public static SolarisFeatureState claimMapState(ResourceLocation dimension) {
        return net.phoenixvine.solaris.api.SolarisAPI.getFeatureState(FEATURE_CLAIM_MAP, dimension);
    }
}
