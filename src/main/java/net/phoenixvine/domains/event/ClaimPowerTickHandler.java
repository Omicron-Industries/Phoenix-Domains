package net.phoenixvine.domains.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.config.DomainsConfig;
import net.phoenixvine.domains.data.ClaimPower;
import net.phoenixvine.domains.data.DomainManager;
import net.phoenixvine.domains.ownership.DomainOwnership;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Passively accrues claim/chunkload power once per real-time minute for every
 * owner token (guild or solo player) that has at least one member online —
 * this is the "config default, mutable, plus API/admin grants" accrual leg of
 * the claim economy described in the plan; {@link DomainsConfig} supplies rate
 * and cap, {@link ClaimPower} tracks the running total per token.
 */
@Mod.EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClaimPowerTickHandler {

    private static final int ACCRUAL_INTERVAL_TICKS = 1200; // 20 ticks/sec * 60 sec

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (++tickCounter < ACCRUAL_INTERVAL_TICKS) return;
        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Set<UUID> onlineTokens = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlineTokens.add(DomainOwnership.tokenFor(player.getUUID()));
        }
        if (onlineTokens.isEmpty()) return;

        DomainManager manager = DomainManager.get(server.overworld());

        double claimPerMinute = DomainsConfig.CLAIM_POWER_PER_HOUR.get() / 60.0;
        double claimCap = DomainsConfig.CLAIM_POWER_MAX_ACCRUAL.get();
        double chunkloadPerMinute = DomainsConfig.CHUNKLOAD_POWER_PER_HOUR.get() / 60.0;
        double chunkloadCap = DomainsConfig.CHUNKLOAD_POWER_MAX_ACCRUAL.get();

        for (UUID token : onlineTokens) {
            ClaimPower power = manager.getOrCreatePower(token);
            power.accrueClaimPower(claimPerMinute, claimCap);
            power.accrueChunkloadPower(chunkloadPerMinute, chunkloadCap);
        }
        manager.setDirty();
    }
}
