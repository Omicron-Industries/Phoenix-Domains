package net.phoenixvine.domains.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Client-only display tuning — separate from {@link DomainsConfig} since that's server-side claim economy tuning. */
public final class DomainsClientConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("hud");
        SHOW_HUD = builder
                .comment("Show the top-of-screen line naming whoever owns the chunk you're standing in. " +
                        "Can also be toggled in-game with the keybind (default unbound).")
                .define("showHud", true);
        builder.pop();

        SPEC = builder.build();
    }

    private DomainsClientConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
