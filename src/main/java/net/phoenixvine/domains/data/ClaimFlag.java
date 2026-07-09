package net.phoenixvine.domains.data;

/**
 * A togglable protection setting on a {@link Claim}. Every flag has a mod-wide
 * default; a claim only stores an override when it differs from that default.
 *
 * Rank-gated access (who may break/place/open containers) is NOT a flag — that's
 * resolved from guild rank via {@code ClaimPermissions}. Flags here are the
 * environmental/relationship toggles an owner can turn on or off per claim.
 */
public enum ClaimFlag {

    /** Creepers, endermen, withers etc. may alter terrain inside the claim. */
    MOB_GRIEFING(false, "Mob Griefing"),
    /** Explosions (TNT, creepers, beds, ghasts) damage blocks inside the claim. */
    EXPLOSIONS(false, "Explosions"),
    /** Fire may spread onto/through blocks inside the claim. */
    FIRE_SPREAD(false, "Fire Spread"),
    /** Fluids (water/lava source placement, flow) may alter blocks inside the claim. */
    FLUID_FLOW(true, "Fluid Flow"),
    /** Hostile mobs may naturally spawn inside the claim. */
    HOSTILE_SPAWNING(true, "Hostile Spawning"),
    /** Passive mobs may naturally spawn inside the claim. */
    PASSIVE_SPAWNING(true, "Passive Spawning"),
    /** Players may deal damage to each other inside the claim. */
    PVP(false, "PvP"),
    /** Allied guilds' members get member-level build access inside the claim. */
    ALLY_BUILD(false, "Ally Build"),
    /** Allied guilds' members may interact with non-container blocks (doors, levers...). */
    ALLY_INTERACT(true, "Ally Interact"),
    /** Allied guilds' members may open containers (chests, furnaces...) inside the claim. */
    ALLY_CONTAINERS(false, "Ally Containers");

    private final boolean defaultValue;
    private final String label;

    ClaimFlag(boolean defaultValue, String label) {
        this.defaultValue = defaultValue;
        this.label = label;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public String label() {
        return label;
    }

    public static ClaimFlag byName(String name) {
        for (ClaimFlag flag : values()) {
            if (flag.name().equalsIgnoreCase(name)) return flag;
        }
        return null;
    }
}
