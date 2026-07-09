package net.phoenixvine.domains.integration.chronicles;

import net.minecraft.server.MinecraftServer;
import net.phoenixvine.chronicles.flag.FlagExpression;
import net.phoenixvine.chronicles.flag.QuestFlagProvider;
import net.phoenixvine.domains.data.DomainManager;

import javax.annotation.Nullable;

/**
 * Quest SNBT: {@code enable_if: "domain:..."}.
 *
 * {@link QuestFlagProvider#evaluate} has no player parameter — it's a server-wide
 * check, not a per-player one — so these expressions read the global claim count
 * rather than "does the querying player own land" (that would need a different,
 * player-aware hook that Chronicles doesn't currently expose):
 *
 * <pre>
 * enable_if: "domain:any_claims"    // at least one chunk is claimed anywhere
 * enable_if: "domain:claims>=5"     // numeric comparison against total claim count
 * </pre>
 */
public class DomainQuestFlagProvider implements QuestFlagProvider {

    @Override
    public String prefix() {
        return "domain";
    }

    @Override
    public boolean evaluate(String expression, @Nullable MinecraftServer server) {
        if (server == null) return true;

        FlagExpression expr = FlagExpression.parse(expression);
        int claimCount = DomainManager.get(server.overworld()).getClaimCount();

        return switch (expr.key) {
            case "any_claims" -> expr.test(String.valueOf(claimCount > 0));
            case "claims" -> expr.test(String.valueOf(claimCount));
            default -> false;
        };
    }
}
