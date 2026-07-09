package net.phoenixvine.domains.integration.chronicles;

/**
 * Actually touches Phoenix Chronicles' classes - callers MUST only reference this class after
 * {@link DomainsChroniclesIntegration#isAvailable()} has already returned true. Split out of
 * that class specifically so the availability check itself never forces the JVM to resolve
 * Chronicles' types (see {@link DomainsChroniclesIntegration}'s javadoc for why that matters).
 */
public final class ChroniclesQuestFlagRegistrar {

    private ChroniclesQuestFlagRegistrar() {}

    public static void register() {
        net.phoenixvine.chronicles.flag.PhoenixQuestFlags.registerProvider(new DomainQuestFlagProvider());
    }
}
