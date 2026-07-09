package net.phoenixvine.domains.integration.solaris;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.render.MapViewport;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.config.SolarisConfig;

/**
 * The claim screen used when Solaris is installed: real sampled terrain (same texture/
 * viewport pipeline as {@code SolarisMapScreen}) with a chunk grid drawn over it and
 * Domains' own claim interactions instead of Solaris's waypoint/theme context menu — this
 * screen is claim-focused, not general-purpose, so it doesn't carry Solaris's waypoint or
 * theme UI at all. Claimed chunks are tinted via
 * {@link DomainClaimOverlay}, which is a normal {@code SolarisOverlay} registered through
 * {@link DomainsSolarisIntegration#init()} — that overlay applies to every live
 * {@code SolarisTexture}, including this screen's own, with no extra plumbing needed here.
 *
 * Lives in {@code integration.solaris} (not {@code client.map}) because, like every other
 * class in this package, it directly references Solaris types and must never be
 * instantiated except behind {@link DomainsSolarisIntegration#isAvailable()}.
 *
 * Controls: left-click claims, right-click unclaims — separate dedicated buttons (not one
 * toggle) specifically so you can hold a button down and drag across many chunks to mass-
 * claim/unclaim without a chunk you're just passing over flipping to the opposite of what you
 * want. Shift+left-click claims AND chunkloads in one action (or just turns chunkload on);
 * shift+right-click turns chunkload off without unclaiming. Panning is on middle-click-drag
 * instead of left, since left is now a direct claim action — this also matters for a reason
 * beyond input conflicts: this map lets you pan and see chunks far past where you're actually
 * standing, and letting that double as the claim button would make it too easy to claim land
 * you've never traveled to. ({@link net.phoenixvine.domains.api.DomainAPI#claim} enforces a
 * max distance server-side regardless, but the controls shouldn't invite trying to abuse it.)
 */
@OnlyIn(Dist.CLIENT)
public class SolarisClaimMapScreen extends Screen {

    private static final int MARGIN = 20;
    private static final int SIDEBAR_W = 150;
    private static final int LINE_H = 10;

    private static SolarisTexture texture;

    private final MapViewport viewport = new MapViewport(
            SolarisConfig.ZOOM_MIN.get().floatValue(), SolarisConfig.ZOOM_MAX.get().floatValue());
    private boolean panning = false;
    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private int lastActionChunkX = Integer.MIN_VALUE;
    private int lastActionChunkZ = Integer.MIN_VALUE;
    private int anchorChunkX;
    private int anchorChunkZ;
    private int radiusPixels;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private ChunkKey centerKey;

    public SolarisClaimMapScreen() {
        super(Component.translatable("domains.map.title"));
    }

    private static SolarisTexture texture() {
        if (texture == null) texture = new SolarisTexture("domains_claim", SolarisConfig.MAP_RADIUS_CHUNKS.get());
        return texture;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        SolarisTexture tex = texture();

        frameX = MARGIN;
        frameY = MARGIN;
        frameW = width - 2 * MARGIN - SIDEBAR_W - MARGIN;
        frameH = height - 2 * MARGIN;

        int viewportSpan = Math.min(frameW, frameH);
        if (viewportSpan > 0) {
            viewport.raiseZoomMin((float) viewportSpan / tex.getSizePixels());
        }

        radiusPixels = tex.getRadiusChunks() * 16;

        if (mc.player != null && mc.level != null) {
            BlockPos blockPos = mc.player.blockPosition();
            anchorChunkX = blockPos.getX() >> 4;
            anchorChunkZ = blockPos.getZ() >> 4;
            centerKey = ChunkKey.of(mc.level, new ChunkPos(anchorChunkX, anchorChunkZ));
            tex.maybeRebuild(centerKey);

            double fracX = mc.player.getX() - (anchorChunkX << 4);
            double fracZ = mc.player.getZ() - (anchorChunkZ << 4);
            double playerPixelX = radiusPixels + fracX;
            double playerPixelZ = radiusPixels + fracZ;

            viewport.setOffset(frameX + frameW / 2.0 - playerPixelX * viewport.getZoom(),
                    frameY + frameH / 2.0 - playerPixelZ * viewport.getZoom());
        }

        clampViewport();

        DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.requestSync(tex.getRadiusChunks()));
    }

    private void clampViewport() {
        viewport.clampOffsetToCover(texture().getSizePixels(), frameX, frameW, frameY, frameH);
    }

    /** World chunk coords under a screen position, or {@code null} if outside the map frame. */
    private int[] chunkAt(double mx, double my) {
        if (mx < frameX || mx > frameX + frameW || my < frameY || my > frameY + frameH) return null;
        double texLocalX = viewport.toWorldX(mx, 0);
        double texLocalZ = viewport.toWorldZ(my, 0);
        int blockX = (int) Math.floor(texLocalX - radiusPixels + (anchorChunkX << 4));
        int blockZ = (int) Math.floor(texLocalZ - radiusPixels + (anchorChunkZ << 4));
        return new int[] { blockX >> 4, blockZ >> 4 };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);

        SolarisTexture tex = texture();
        // Cheap no-op unless something actually invalidated the texture — without this, a
        // claim/unclaim/chunkload made on this very screen only showed up after closing and
        // reopening the map, since the only other maybeRebuild call is in init().
        if (centerKey != null) tex.maybeRebuild(centerKey);
        int size = tex.getSizePixels();

        VanillaPanel.draw(g, frameX - 8, frameY - 8, frameW + 16, frameH + 16, SolarisThemeUtils.C_BORDER);
        g.enableScissor(frameX, frameY, frameX + frameW, frameY + frameH);
        g.fill(frameX, frameY, frameX + frameW, frameY + frameH, SolarisThemeUtils.C_BG);

        int destX = (int) viewport.toScreenX(0, 0);
        int destY = (int) viewport.toScreenY(0, 0);
        int destSize = (int) (size * viewport.getZoom());
        g.blit(tex.textureId(), destX, destY, destSize, destSize, 0, 0, size, size, size, size);

        drawChunkGrid(g);

        int[] hovered = chunkAt(mx, my);
        S2CDomainSyncPacket.ClaimEntry hoveredEntry = hovered != null ?
                ClientDomainCache.entryAt(hovered[0], hovered[1]) : null;
        if (hovered != null) highlightChunk(g, hovered[0], hovered[1], 0x55FFFFFF);
        highlightChunk(g, anchorChunkX, anchorChunkZ, 0xFFFFFFFF);

        g.disableScissor();

        super.render(g, mx, my, partialTick);

        g.drawCenteredString(font, title, frameX + frameW / 2, 6, SolarisThemeUtils.C_ACCENT);

        renderSidebar(g, hovered, hoveredEntry);
    }

    /** Chunk boundary lines land exactly on 16px multiples in texture-local space. */
    private void drawChunkGrid(GuiGraphics g) {
        int size = texture().getSizePixels();
        int gridColor = 0x22FFFFFF;

        for (int px = 0; px <= size; px += 16) {
            int sx = (int) viewport.toScreenX(px, 0);
            if (sx < frameX || sx > frameX + frameW) continue;
            g.fill(sx, frameY, sx + 1, frameY + frameH, gridColor);
        }
        for (int pz = 0; pz <= size; pz += 16) {
            int sy = (int) viewport.toScreenY(pz, 0);
            if (sy < frameY || sy > frameY + frameH) continue;
            g.fill(frameX, sy, frameX + frameW, sy + 1, gridColor);
        }
    }

    private void highlightChunk(GuiGraphics g, int cx, int cz, int outlineColor) {
        double texLocalX = radiusPixels + ((cx - anchorChunkX) << 4);
        double texLocalZ = radiusPixels + ((cz - anchorChunkZ) << 4);
        int x0 = (int) viewport.toScreenX(texLocalX, 0);
        int y0 = (int) viewport.toScreenY(texLocalZ, 0);
        int s = (int) (16 * viewport.getZoom());
        if (x0 + s < frameX || x0 > frameX + frameW || y0 + s < frameY || y0 > frameY + frameH) return;
        g.renderOutline(x0, y0, s, s, outlineColor);
    }

    private void renderSidebar(GuiGraphics g, int[] hovered, S2CDomainSyncPacket.ClaimEntry hoveredEntry) {
        int x = width - SIDEBAR_W - MARGIN;
        int y = MARGIN;
        int w = SIDEBAR_W;
        int bottom = height - MARGIN;

        VanillaPanel.draw(g, x - 8, y - 8, w + 16, bottom - y + 16, SolarisThemeUtils.C_BORDER);
        g.fill(x, y, x + w, bottom, SolarisThemeUtils.C_PANEL);

        int tx = x + 8;
        int maxTextW = w - 16;
        int ty = y + 8;

        ty = drawWrapped(g, title, tx, ty, maxTextW, SolarisThemeUtils.C_ACCENT) + 2;

        ty = drawWrapped(g, Component.translatable("domains.map.claim_blocks", ClientDomainCache.usedClaimBlocks,
                ClientDomainCache.availableClaimBlocks), tx, ty, maxTextW, SolarisThemeUtils.C_TEXT);
        ty = drawWrapped(g, Component.translatable("domains.map.chunkload_blocks",
                ClientDomainCache.usedChunkloadBlocks, ClientDomainCache.availableChunkloadBlocks), tx, ty,
                maxTextW, SolarisThemeUtils.C_TEXT) + 4;

        ty = divider(g, tx, ty, x + w - 8);

        if (hovered != null) {
            ty = drawWrapped(g, Component.literal("Chunk " + hovered[0] + ", " + hovered[1]), tx, ty, maxTextW,
                    SolarisThemeUtils.C_TEXT);
            if (hoveredEntry == null) {
                ty = drawWrapped(g, Component.translatable("domains.hud.wilderness"), tx, ty, maxTextW,
                        SolarisThemeUtils.C_DIM);
            } else {
                ty = drawWrapped(g, Component.literal(hoveredEntry.ownerName()), tx, ty, maxTextW,
                        hoveredEntry.color());
                Component chunkloadText = Component.translatable(
                        hoveredEntry.chunkloaded() ? "domains.map.chunkloaded" : "domains.map.not_chunkloaded");
                ty = drawWrapped(g, chunkloadText, tx, ty, maxTextW,
                        hoveredEntry.chunkloaded() ? 0xFFD700 : SolarisThemeUtils.C_DIM);
                if (isMine(hoveredEntry)) {
                    ty = drawWrapped(g, Component.translatable("domains.map.yours"), tx, ty, maxTextW,
                            SolarisThemeUtils.C_ACCENT);
                }
            }
        } else {
            ty = drawWrapped(g, Component.translatable("domains.map.hover_hint"), tx, ty, maxTextW,
                    SolarisThemeUtils.C_DIM);
        }
        ty += 4;

        ty = divider(g, tx, ty, x + w - 8);

        ty = drawWrapped(g, Component.translatable("domains.map.hint_claim"), tx, ty, maxTextW,
                SolarisThemeUtils.C_DIM);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_unclaim"), tx, ty, maxTextW,
                SolarisThemeUtils.C_DIM);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_chunkload"), tx, ty, maxTextW,
                SolarisThemeUtils.C_DIM);
        drawWrapped(g, Component.translatable("domains.map.hint_pan_middle"), tx, ty, maxTextW,
                SolarisThemeUtils.C_DIM);
    }

    private int divider(GuiGraphics g, int x1, int y, int x2) {
        g.fill(x1, y, x2, y + 1, SolarisThemeUtils.C_BORDER2);
        return y + 8;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, y, color, false);
            y += LINE_H;
        }
        return y + 2;
    }

    private boolean isMine(S2CDomainSyncPacket.ClaimEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entry.ownerName().equals(mc.player.getName().getString());
    }

    /**
     * Claims (or shift: claims+chunkloads / turns chunkload on) whatever's hovered — no-ops if
     * we already acted on this exact chunk since the button went down, so holding the button
     * and dragging across many chunks claims each one once, not every frame.
     */
    private void performClaimAction(double mx, double my, boolean shift) {
        int[] hovered = chunkAt(mx, my);
        if (hovered == null || (hovered[0] == lastActionChunkX && hovered[1] == lastActionChunkZ)) return;
        lastActionChunkX = hovered[0];
        lastActionChunkZ = hovered[1];

        int cx = hovered[0];
        int cz = hovered[1];
        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(cx, cz);
        if (shift) {
            if (entry == null) {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(cx, cz));
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(cx, cz, true));
            } else {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(cx, cz, true));
            }
        } else if (entry == null) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(cx, cz));
        }
    }

    /**
     * Unclaims (or shift: just removes chunkload, keeping the claim) whatever's hovered — same
     * once-per-chunk-per-drag guard as {@link #performClaimAction}.
     */
    private void performUnclaimAction(double mx, double my, boolean shift) {
        int[] hovered = chunkAt(mx, my);
        if (hovered == null || (hovered[0] == lastActionChunkX && hovered[1] == lastActionChunkZ)) return;
        lastActionChunkX = hovered[0];
        lastActionChunkZ = hovered[1];

        int cx = hovered[0];
        int cz = hovered[1];
        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(cx, cz);
        if (entry == null) return;
        if (shift) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(cx, cz, false));
        } else {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.unclaim(cx, cz));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (button == 0) {
            leftHeld = true;
            lastActionChunkX = Integer.MIN_VALUE;
            lastActionChunkZ = Integer.MIN_VALUE;
            performClaimAction(mx, my, hasShiftDown());
            return true;
        }
        if (button == 1) {
            rightHeld = true;
            lastActionChunkX = Integer.MIN_VALUE;
            lastActionChunkZ = Integer.MIN_VALUE;
            performUnclaimAction(mx, my, hasShiftDown());
            return true;
        }
        if (button == 2) {
            panning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) leftHeld = false;
        if (button == 1) rightHeld = false;
        if (button == 2) panning = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            viewport.pan(dx, dy);
            clampViewport();
            return true;
        }
        if (leftHeld) {
            performClaimAction(mx, my, hasShiftDown());
            return true;
        }
        if (rightHeld) {
            performUnclaimAction(mx, my, hasShiftDown());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (viewport.adjustZoomToAnchor(delta, mx, my, 0, 0)) {
            clampViewport();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
