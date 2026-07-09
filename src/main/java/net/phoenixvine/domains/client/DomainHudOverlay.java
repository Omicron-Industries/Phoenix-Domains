package net.phoenixvine.domains.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.client.map.ClaimMapScreen;
import net.phoenixvine.domains.config.DomainsClientConfig;
import net.phoenixvine.domains.integration.solaris.DomainsSolarisIntegration;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

/**
 * Rough v1 HUD: a single centered top-of-screen line naming whoever owns the chunk
 * the player is standing in (or "Wilderness"). Also drives the periodic sync request
 * that keeps {@link ClientDomainCache} populated for both this and the claim map
 * screen(s), and — when Solaris is installed — registers/refreshes the claim-tint
 * overlay and opens {@code SolarisClaimMapScreen} (real terrain + chunk grid) for the
 * map keybind instead of the vanilla-only {@link ClaimMapScreen} fallback.
 */
@Mod.EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DomainHudOverlay {

    private static final int SYNC_INTERVAL_TICKS = 40; // 2 real-time seconds
    private static final int SYNC_RADIUS = 8;

    private static int tickCounter = 0;
    private static int lastSeenCacheVersion = -1;

    // Set once if a Solaris call throws, so a stale/mismatched Solaris jar (ModList says
    // present, but a class we need is actually missing/incompatible) logs once and stops
    // retrying the overlay registration, instead of retrying — and potentially
    // re-throwing — on every single client tick.
    private static boolean solarisBroken = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean solarisAvailable = !solarisBroken && DomainsSolarisIntegration.isAvailable();
        if (solarisAvailable) {
            try {
                DomainsSolarisIntegration.init();
                if (ClientDomainCache.version != lastSeenCacheVersion) {
                    lastSeenCacheVersion = ClientDomainCache.version;
                    DomainsSolarisIntegration.requestRefresh();
                }
            } catch (Throwable t) {
                solarisBroken = true;
                solarisAvailable = false;
                PhoenixDomains.LOGGER.error("Solaris is present but its integration failed — falling back to" +
                        " Domains' own claim map for the rest of this session.", t);
            }
        }

        while (DomainKeybinds.TOGGLE_HUD.consumeClick()) {
            DomainsClientConfig.SHOW_HUD.set(!DomainsClientConfig.SHOW_HUD.get());
            DomainsClientConfig.SHOW_HUD.save();
        }

        while (DomainKeybinds.OPEN_MAP.consumeClick()) {
            if (mc.screen == null) {
                Screen mapScreen = null;
                if (solarisAvailable) {
                    try {
                        mapScreen = DomainsSolarisIntegration.openClaimMapScreen();
                    } catch (Throwable t) {
                        solarisBroken = true;
                        PhoenixDomains.LOGGER.error(
                                "Failed to open the Solaris-backed claim map — falling back to Domains'" +
                                        " vanilla-only claim map.",
                                t);
                    }
                }
                mc.setScreen(mapScreen != null ? mapScreen : new ClaimMapScreen());
            }
        }

        if (++tickCounter < SYNC_INTERVAL_TICKS) return;
        tickCounter = 0;
        DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.requestSync(SYNC_RADIUS));
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!DomainsClientConfig.SHOW_HUD.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int chunkX = mc.player.blockPosition().getX() >> 4;
        int chunkZ = mc.player.blockPosition().getZ() >> 4;
        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(chunkX, chunkZ);

        Component text = entry == null ? Component.translatable("domains.hud.wilderness") :
                Component.translatable("domains.hud.owner", entry.ownerName());

        Font font = mc.font;
        int width = font.width(text);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = (screenW - width) / 2;
        int y = 4;
        int color = entry == null ? 0xFFAAAAAA : entry.color();

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.drawString(font, text, x + 1, y + 1, 0xFF000000, false);
        graphics.drawString(font, text, x, y, color, false);
    }
}
