package net.phoenixvine.domains.integration.chronicles;

import net.minecraftforge.fml.ModList;

/**
 * Optional integration with Phoenix Chronicles. {@code phoenix_chronicles} is declared as a
 * non-mandatory {@code mods.toml} dependency, so this class must contain ONLY the availability
 * check - zero imports or references to anything in {@code net.phoenixvine.chronicles.*}.
 *
 * Calling ANY static method on a class forces the JVM to load and verify that class's entire
 * bytecode, not just the method being invoked - so if the actual Chronicles-referencing code
 * (registering {@link ChroniclesQuestFlagRegistrar}) lived in this same class file, merely
 * calling {@link #isAvailable()} to check would already try to resolve Chronicles' types and
 * throw ClassNotFoundException on a server that doesn't have it installed, regardless of what
 * the check itself returns. The registration logic lives in {@link ChroniclesQuestFlagRegistrar}
 * instead, which callers must only ever reference AFTER this check has already returned true.
 */
public final class DomainsChroniclesIntegration {

    public static final String CHRONICLES_MOD_ID = "phoenix_chronicles";

    private DomainsChroniclesIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(CHRONICLES_MOD_ID);
    }
}
