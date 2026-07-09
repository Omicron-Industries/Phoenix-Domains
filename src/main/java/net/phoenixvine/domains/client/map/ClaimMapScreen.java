package net.phoenixvine.domains.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

/**
 * Claim grid: a top-down chunk grid centered on the player. Deliberately grid-based
 * rather than a pannable/zoomable terrain view — claiming is an inherently per-chunk
 * action, so a literal chunk grid with a side info panel reads better than scrolling
 * around Solaris's full map to poke at one square. Solaris is only a soft/optional
 * dependency of this mod (its claim-tint overlay lives in {@code integration.solaris},
 * applied to Solaris's own map screen when present) — this screen is always what
 * Domains' own map keybind opens, so it must render with vanilla-only primitives and
 * never reach for Solaris chrome.
 *
 * Controls: left-click claims an unclaimed chunk, right-click unclaims your own — separate
 * dedicated buttons rather than one toggle, specifically so you can hold the button down and
 * drag across many chunks to mass-claim/unclaim without worrying about a chunk you're just
 * passing over toggling to the opposite of what you want. Shift+left-click claims AND
 * chunkloads a chunk in one action (or just turns chunkload on if you already own it);
 * shift+right-click turns chunkload off without unclaiming. The server is the actual
 * authority on all of this (ownership/permission/power checks, plus a max-claim-distance
 * limit) — clicks just fire the same {@code C2SDomainActionPacket} actions it already
 * validates.
 */
@OnlyIn(Dist.CLIENT)
public class ClaimMapScreen extends Screen {

    private static final int RADIUS = 16;
    private static final int MIN_CELL = 10;
    private static final int SIDEBAR_W = 150;
    private static final int MARGIN = 12;
    private static final int LINE_H = 10;

    private int centerChunkX;
    private int centerChunkZ;
    private int cell;
    private int gridPx;
    private int gridPy;
    private int gridSize;

    private int hoveredX = Integer.MIN_VALUE;
    private int hoveredZ;

    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private int lastActionChunkX = Integer.MIN_VALUE;
    private int lastActionChunkZ = Integer.MIN_VALUE;

    public ClaimMapScreen() {
        super(Component.translatable("domains.map.title"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            centerChunkX = mc.player.blockPosition().getX() >> 4;
            centerChunkZ = mc.player.blockPosition().getZ() >> 4;
        }

        int span = RADIUS * 2 + 1;
        int available = width - SIDEBAR_W - MARGIN * 3;
        gridSize = Math.min(available, height - MARGIN * 2);
        cell = Math.max(MIN_CELL, gridSize / span);
        gridSize = cell * span;
        gridPx = MARGIN;
        gridPy = (height - gridSize) / 2;

        DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.requestSync(RADIUS));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        g.fill(gridPx - 1, gridPy - 1, gridPx + gridSize + 1, gridPy + gridSize + 1, 0xFF808080);

        hoveredX = Integer.MIN_VALUE;
        S2CDomainSyncPacket.ClaimEntry hoveredEntry = null;

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                int x0 = gridPx + (dx + RADIUS) * cell;
                int y0 = gridPy + (dz + RADIUS) * cell;

                S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(cx, cz);
                // Chunkload is a property of an existing claim, not an independent state — a
                // chunkloaded claim is solid gold rather than owner-color-plus-a-strip, so
                // "claimed" and "claimed + chunkloaded" read as genuinely distinct at a glance.
                int rgb = entry == null ? 0xFFFFFF : entry.chunkloaded() ? 0xFFD700 : (entry.color() & 0xFFFFFF);
                int fillColor = entry == null ? (0x33000000 | rgb) : (0xCC000000 | rgb);
                g.fill(x0 + 1, y0 + 1, x0 + cell - 1, y0 + cell - 1, fillColor);

                if (dx == 0 && dz == 0) {
                    g.renderOutline(x0, y0, cell, cell, 0xFFFFFFFF);
                }

                if (mx >= x0 && mx < x0 + cell && my >= y0 && my < y0 + cell) {
                    hoveredX = cx;
                    hoveredZ = cz;
                    hoveredEntry = entry;
                    g.fill(x0, y0, x0 + cell, y0 + cell, 0x55FFFFFF);
                }
            }
        }

        renderSidebar(g, hoveredEntry);

        super.render(g, mx, my, partialTick);
    }

    private void renderSidebar(GuiGraphics g, S2CDomainSyncPacket.ClaimEntry hoveredEntry) {
        int x = width - SIDEBAR_W - MARGIN;
        int y = MARGIN;
        int w = SIDEBAR_W;
        int bottom = height - MARGIN;

        g.fill(x, y, x + w, bottom, 0xCC101010);
        g.renderOutline(x, y, w, bottom - y, 0xFF808080);

        int tx = x + 8;
        int maxTextW = w - 16;
        int ty = y + 8;

        g.drawString(font, title, tx, ty, 0xFFFFFF, false);
        ty += LINE_H + 4;

        g.drawString(font, Component.translatable("domains.map.claim_blocks", ClientDomainCache.usedClaimBlocks,
                ClientDomainCache.availableClaimBlocks), tx, ty, 0xAAFFAA, false);
        ty += LINE_H;
        g.drawString(font, Component.translatable("domains.map.chunkload_blocks",
                ClientDomainCache.usedChunkloadBlocks, ClientDomainCache.availableChunkloadBlocks), tx, ty, 0xFFD780,
                false);
        ty += LINE_H + 6;

        ty = divider(g, tx, ty, x + w - 8);

        if (hoveredX != Integer.MIN_VALUE) {
            g.drawString(font, "Chunk " + hoveredX + ", " + hoveredZ, tx, ty, 0xFFFFFF, false);
            ty += LINE_H;
            if (hoveredEntry == null) {
                g.drawString(font, Component.translatable("domains.hud.wilderness"), tx, ty, 0xAAAAAA, false);
                ty += LINE_H;
            } else {
                g.drawString(font, Component.literal(hoveredEntry.ownerName()), tx, ty, hoveredEntry.color(), false);
                ty += LINE_H;
                Component chunkloadText = Component.translatable(
                        hoveredEntry.chunkloaded() ? "domains.map.chunkloaded" : "domains.map.not_chunkloaded");
                g.drawString(font, chunkloadText, tx, ty, hoveredEntry.chunkloaded() ? 0xFFD700 : 0x888888, false);
                ty += LINE_H;
                if (isMine(hoveredEntry)) {
                    g.drawString(font, Component.translatable("domains.map.yours"), tx, ty, 0x55FF55, false);
                    ty += LINE_H;
                }
            }
        } else {
            g.drawString(font, Component.translatable("domains.map.hover_hint"), tx, ty, 0x888888, false);
            ty += LINE_H;
        }
        ty += 6;

        ty = divider(g, tx, ty, x + w - 8);

        ty = drawWrapped(g, Component.translatable("domains.map.hint_claim"), tx, ty, maxTextW);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_unclaim"), tx, ty, maxTextW);
        drawWrapped(g, Component.translatable("domains.map.hint_chunkload"), tx, ty, maxTextW);
    }

    private int divider(GuiGraphics g, int x1, int y, int x2) {
        g.fill(x1, y, x2, y + 1, 0xFF404040);
        return y + 8;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, y, 0xCCCCCC, false);
            y += LINE_H;
        }
        return y + 2;
    }

    private boolean isMine(S2CDomainSyncPacket.ClaimEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entry.ownerName().equals(mc.player.getName().getString());
    }

    /**
     * Claims (or shift: claims+chunkloads / turns chunkload on) whatever's currently hovered
     * — no-ops if we already acted on this exact chunk since the button went down, so holding
     * the button and dragging across many chunks claims each one once, not every frame.
     */
    private void performClaimAction(boolean shift) {
        if (hoveredX == Integer.MIN_VALUE || (hoveredX == lastActionChunkX && hoveredZ == lastActionChunkZ)) return;
        lastActionChunkX = hoveredX;
        lastActionChunkZ = hoveredZ;

        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(hoveredX, hoveredZ);
        if (shift) {
            if (entry == null) {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(hoveredX, hoveredZ));
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(hoveredX, hoveredZ, true));
            } else {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(hoveredX, hoveredZ, true));
            }
        } else if (entry == null) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(hoveredX, hoveredZ));
        }
    }

    /**
     * Unclaims (or shift: just removes chunkload, keeping the claim) whatever's hovered — same
     * once-per-chunk-per-drag guard as {@link #performClaimAction}.
     */
    private void performUnclaimAction(boolean shift) {
        if (hoveredX == Integer.MIN_VALUE || (hoveredX == lastActionChunkX && hoveredZ == lastActionChunkZ)) return;
        lastActionChunkX = hoveredX;
        lastActionChunkZ = hoveredZ;

        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(hoveredX, hoveredZ);
        if (entry == null) return;
        if (shift) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(hoveredX, hoveredZ, false));
        } else {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.unclaim(hoveredX, hoveredZ));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            leftHeld = true;
            lastActionChunkX = Integer.MIN_VALUE;
            lastActionChunkZ = Integer.MIN_VALUE;
            performClaimAction(hasShiftDown());
            return true;
        }
        if (button == 1) {
            rightHeld = true;
            lastActionChunkX = Integer.MIN_VALUE;
            lastActionChunkZ = Integer.MIN_VALUE;
            performUnclaimAction(hasShiftDown());
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (leftHeld) {
            performClaimAction(hasShiftDown());
            return true;
        }
        if (rightHeld) {
            performUnclaimAction(hasShiftDown());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) leftHeld = false;
        if (button == 1) rightHeld = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
