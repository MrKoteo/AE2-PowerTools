package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.ItemRegistry;
import com.ae2powertools.client.BlockHighlightRenderer;
import com.ae2powertools.features.scanner.ScannerClientState.ChunkLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.LoopLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.Tab;


/**
 * Client-side renderer for scanner overlays and direction arrows.
 */
@SideOnly(Side.CLIENT)
public class ScannerRenderer {

    // ========== Overlay Constants ==========
    private static final int PADDING_EXTERNAL = 5;
    private static final int PADDING_INTERNAL = 4;
    private static final int LINE_SPACING = 2;

    // ========== Arrow Rendering Constants ==========
    private static final float ARROW_BASE_DISTANCE = 0.6f;
    private static final float ARROW_SPREAD_RADIUS = 0.12f;
    private static final float ARROW_LENGTH = 0.05f;
    private static final float ARROW_WIDTH = 0.02f;
    private static final float ARROW_THICKNESS = 0.01f;
    private static final float MIN_PITCH_ANGLE = 10.0f;
    private static final float TEXT_SCALE = 0.0012f;
    private static final float TEXT_HEIGHT_OFFSET = 0.04f;
    private static final float ARROW_ALPHA = 1.0f;

    // ========== Arrow Gradient Constants ==========
    private static final float GRADIENT_START_FACTOR = 0.8f;
    private static final float GRADIENT_END_FACTOR = 0.4f;
    private static final float GRADIENT_CURVE = 0.5f;
    private static final int GRADIENT_RINGS = 16;
    private static final boolean GRADIENT_FRONT_TO_BACK = true;
    private static final boolean ACCENTUATE_BACK = true;

    // Loop color (red)
    private static final int LOOP_COLOR = 0xFF4444;
    // Chunk color (orange)
    private static final int CHUNK_COLOR = 0xFFAA00;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!ScannerClientState.isOverlayEnabled()) return;
        if (!ScannerClientState.hasActiveSession()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;
        if (!isHoldingScanner(mc)) return;

        Tab currentTab = ScannerClientState.getCurrentTab();
        BlockPos playerPos = mc.player.getPosition();

        // Build display lines based on current tab
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (currentTab == Tab.LOOPS) {
            List<LoopLocationClient> selectedInDim = ScannerClientState.getSelectedLoopLocationsInDimension(
                mc.player.dimension);
            if (selectedInDim.isEmpty()) return;

            for (LoopLocationClient loc : selectedInDim) {
                double distance = loc.getDistanceFrom(playerPos);
                String distanceStr = formatDistance(distance);
                String posStr = String.format("[%d, %d, %d]", loc.pos.getX(), loc.pos.getY(), loc.pos.getZ());
                lines.add(loc.description + " " + posStr + ": " + distanceStr);
                colors.add(LOOP_COLOR);
            }
        } else {
            List<ChunkLocationClient> selectedInDim = ScannerClientState.getSelectedChunkLocationsInDimension(
                mc.player.dimension);
            if (selectedInDim.isEmpty()) return;

            for (ChunkLocationClient loc : selectedInDim) {
                double distance = loc.getDistanceFrom(playerPos);
                String distanceStr = formatDistance(distance);
                String text = I18n.format("gui.ae2powertools.scanner.chunk_entry", loc.chunkX, loc.chunkZ)
                    + ": " + distanceStr;
                lines.add(text);
                colors.add(CHUNK_COLOR);
            }
        }

        if (lines.isEmpty()) return;

        // Calculate dimensions
        int lineHeight = mc.fontRenderer.FONT_HEIGHT;
        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));

        int boxW = maxWidth + PADDING_INTERNAL * 2 + 8;  // Extra 8 for color indicator
        int boxH = lines.size() * lineHeight + (lines.size() - 1) * LINE_SPACING + PADDING_INTERNAL * 2;

        // Position (top left)
        ScaledResolution res = new ScaledResolution(mc);
        int boxX = PADDING_EXTERNAL;
        int boxY = PADDING_EXTERNAL;

        // Draw box with border
        int bgColor = 0xC0101010;
        int borderColor = 0xFF404040;

        Gui.drawRect(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, borderColor);
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, bgColor);

        // Draw text with color indicators
        int textX = boxX + PADDING_INTERNAL;
        int textY = boxY + PADDING_INTERNAL;

        for (int i = 0; i < lines.size(); i++) {
            int color = colors.get(i) | 0xFF000000;
            Gui.drawRect(textX, textY + 1, textX + 4, textY + lineHeight - 1, color);
            mc.fontRenderer.drawStringWithShadow(lines.get(i), textX + 8, textY, 0xFFFFFF);
            textY += lineHeight + LINE_SPACING;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ScannerClientState.hasActiveSession()) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) return;
        if (!isHoldingScanner(mc)) return;

        int playerDim = player.dimension;
        float partialTicks = event.getPartialTicks();
        Tab currentTab = ScannerClientState.getCurrentTab();

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        if (currentTab == Tab.LOOPS) {
            // Render block outlines and arrows for selected loop locations
            List<LoopLocationClient> selectedInDim = ScannerClientState.getSelectedLoopLocationsInDimension(playerDim);
            if (selectedInDim.isEmpty()) return;

            // Render block outlines
            GlStateManager.pushMatrix();
            GlStateManager.translate(-playerX, -playerY, -playerZ);

            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.glLineWidth(3.0F);

            for (LoopLocationClient loc : selectedInDim) {
                BlockHighlightRenderer.renderBlockOutline(loc.pos, 1.0f, 0.27f, 0.27f, 0.8f);
            }

            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();

            // Render direction arrows
            if (ScannerClientState.isOverlayEnabled()) {
                for (LoopLocationClient loc : selectedInDim) {
                    double distance = loc.getDistanceFrom(player.getPosition());
                    drawDirectionArrow(player, loc.pos, LOOP_COLOR, distance, partialTicks);
                }
            }
        } else {
            // Render arrows for selected chunk locations (no block outlines for chunks)
            List<ChunkLocationClient> selectedInDim = ScannerClientState.getSelectedChunkLocationsInDimension(playerDim);
            if (selectedInDim.isEmpty()) return;

            // Render direction arrows pointing to chunk centers
            if (ScannerClientState.isOverlayEnabled()) {
                for (ChunkLocationClient loc : selectedInDim) {
                    BlockPos centerPos = loc.getCenterPos();
                    double distance = loc.getDistanceFrom(player.getPosition());
                    drawDirectionArrowYAgnostic(player, centerPos, CHUNK_COLOR, distance, partialTicks);
                }
            }
        }
    }

    /**
     * Format distance for display.
     */
    private String formatDistance(double distance) {
        if (distance < 1000) {
            return String.format("%.0fm", distance);
        } else {
            return String.format("%.1fkm", distance / 1000);
        }
    }

    /**
     * Draws a 3D directional arrow pointing towards the target position (y-agnostic).
     * Always points horizontally at -MIN_PITCH_ANGLE regardless of target Y.
     * Used for chunks where the Y coordinate is unknown.
     */
    private void drawDirectionArrowYAgnostic(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks) {
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, true);
    }

    /**
     * Draws a 3D directional arrow pointing towards the target position.
     * Adapted from ClientRenderEvents in Simple-Structure-Scanner.
     */
    private void drawDirectionArrow(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks) {
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, false);
    }

    /**
     * Internal method for drawing direction arrows.
     * @param yAgnostic If true, always use -MIN_PITCH_ANGLE for pitch (horizontal arrow)
     */
    private void drawDirectionArrowInternal(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks, boolean yAgnostic) {
        Minecraft mc = Minecraft.getMinecraft();

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        double eyeY = playerY + player.getEyeHeight();

        float cameraYaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        float cameraPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;

        double dx = target.getX() + 0.5 - playerX;
        double dy = target.getY() + 0.5 - eyeY;
        double dz = target.getZ() + 0.5 - playerZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (horizontalDist < 1) return;

        float targetYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float targetPitch;

        if (yAgnostic) {
            // For chunks, always point horizontally (slightly downward)
            targetPitch = -MIN_PITCH_ANGLE;
        } else {
            targetPitch = (float) Math.toDegrees(Math.atan2(dy, horizontalDist));

            if (Math.abs(targetPitch) < MIN_PITCH_ANGLE) {
                targetPitch = targetPitch >= 0 ? MIN_PITCH_ANGLE : -MIN_PITCH_ANGLE;
            }
        }

        double camYawRad = Math.toRadians(cameraYaw);
        double camPitchRad = Math.toRadians(cameraPitch);
        double camForwardX = -Math.sin(camYawRad) * Math.cos(camPitchRad);
        double camForwardY = -Math.sin(camPitchRad);
        double camForwardZ = Math.cos(camYawRad) * Math.cos(camPitchRad);

        double baseX = playerX + camForwardX * ARROW_BASE_DISTANCE;
        double baseY = eyeY + camForwardY * ARROW_BASE_DISTANCE;
        double baseZ = playerZ + camForwardZ * ARROW_BASE_DISTANCE;

        double relativeYaw = Math.toRadians(targetYaw - cameraYaw);
        double targetPitchRad = Math.toRadians(targetPitch);

        double targetDirX = dx / horizontalDist;
        double targetDirZ = dz / horizontalDist;

        double offsetX = targetDirX * ARROW_SPREAD_RADIUS;
        double offsetZ = targetDirZ * ARROW_SPREAD_RADIUS;
        double offsetY = Math.sin(targetPitchRad) * ARROW_SPREAD_RADIUS;

        double forwardOffset = Math.cos(relativeYaw);
        if (forwardOffset < 0) {
            double behindFactor = 1.0 + Math.abs(forwardOffset) * 0.5;
            offsetX *= behindFactor;
            offsetZ *= behindFactor;
        }

        double arrowX = baseX + offsetX;
        double arrowY = baseY + offsetY;
        double arrowZ = baseZ + offsetZ;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float alpha = ARROW_ALPHA;

        double renderX = arrowX - playerX;
        double renderY = arrowY - playerY;
        double renderZ = arrowZ - playerZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY, renderZ);

        GlStateManager.rotate(180 + targetYaw, 0, 1, 0);
        GlStateManager.rotate(targetPitch, 1, 0, 0);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthRange(0.0, 0.001);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        float halfThick = ARROW_THICKNESS / 2;
        float len = ARROW_LENGTH;
        float w = ARROW_WIDTH;

        for (int i = 0; i < GRADIENT_RINGS; i++) {
            float t0 = (float) i / GRADIENT_RINGS;
            float t1 = (float) (i + 1) / GRADIENT_RINGS;

            float z0 = -t0 * len;
            float z1 = -t1 * len;

            float w0 = w * (1.0f - t0);
            float w1 = w * (1.0f - t1);

            float curve0 = (float) (1.0 - Math.pow(1.0 - t0, GRADIENT_CURVE));
            float curve1 = (float) (1.0 - Math.pow(1.0 - t1, GRADIENT_CURVE));

            float factor0, factor1;
            if (GRADIENT_FRONT_TO_BACK) {
                factor0 = GRADIENT_START_FACTOR + curve0 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
                factor1 = GRADIENT_START_FACTOR + curve1 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
            } else {
                factor0 = GRADIENT_START_FACTOR + curve0 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
                factor1 = GRADIENT_START_FACTOR + curve1 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
            }

            float r0 = r * factor0, g0 = g * factor0, b0 = b * factor0;
            float r1 = r * factor1, g1 = g * factor1, b1 = b * factor1;

            boolean isTip = (i == GRADIENT_RINGS - 1);

            if (isTip) {
                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(0, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(0, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();

                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(0, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
            } else {
                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();

                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();

                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
            }

            if (i == 0) {
                if (ACCENTUATE_BACK) {
                    if (!GRADIENT_FRONT_TO_BACK) {
                        r0 = Math.min(r0 / 1.2f, 1.0f);
                        g0 = Math.min(g0 / 1.2f, 1.0f);
                        b0 = Math.min(b0 / 1.2f, 1.0f);
                    } else {
                        r0 = Math.min(r0 * 1.2f, 1.0f);
                        g0 = Math.min(g0 * 1.2f, 1.0f);
                        b0 = Math.min(b0 * 1.2f, 1.0f);
                    }
                }

                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
            }
        }

        tessellator.draw();

        GL11.glDepthRange(0.0, 1.0);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();

        GlStateManager.popMatrix();

        // Draw distance text above arrow
        String distanceStr = formatDistance(distance);

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY + TEXT_HEIGHT_OFFSET, renderZ);

        GlStateManager.rotate(-cameraYaw, 0, 1, 0);
        GlStateManager.rotate(cameraPitch, 1, 0, 0);

        GlStateManager.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        int textWidth = mc.fontRenderer.getStringWidth(distanceStr);
        mc.fontRenderer.drawStringWithShadow(distanceStr, -textWidth / 2.0f, 0, color | 0xFF000000);

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }

    /**
     * Check if the player is holding a Network Health Scanner in either hand.
     */
    private boolean isHoldingScanner(Minecraft mc) {
        if (mc.player == null) return false;

        return mc.player.getHeldItemMainhand().getItem() == ItemRegistry.NETWORK_HEALTH_SCANNER
            || mc.player.getHeldItemOffhand().getItem() == ItemRegistry.NETWORK_HEALTH_SCANNER;
    }
}
