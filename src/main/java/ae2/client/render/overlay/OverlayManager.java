/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.render.overlay;

import ae2.api.util.DimensionalBlockPos;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.Map.Entry;
import java.util.Objects;

public class OverlayManager {

    private static final OverlayManager INSTANCE = new OverlayManager();

    private final Object2ObjectMap<DimensionalBlockPos, OverlayRenderer> overlayHandlers = new Object2ObjectOpenHashMap<>();

    public static OverlayManager getInstance() {
        return INSTANCE;
    }

    @SubscribeEvent
    public void renderWorldLastEvent(RenderWorldLastEvent event) {
        if (this.overlayHandlers.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            return;
        }

        double camX = minecraft.getRenderManager().viewerPosX;
        double camY = minecraft.getRenderManager().viewerPosY;
        double camZ = minecraft.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-camX, -camY, -camZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.glLineWidth(2.0F);
        GlStateManager.depthMask(false);

        for (OverlayRenderer renderer : this.overlayHandlers.entrySet().stream()
                                                            .filter(entry -> entry.getKey().isInWorld(minecraft.world))
                                                            .map(Entry::getValue)
                                                            .toList()) {
            GlStateManager.depthFunc(GL11.GL_GREATER);
            renderer.renderOccludedLines();

            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            renderer.renderFaces();
            renderer.renderVisibleLines();
        }

        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public void showArea(IOverlayDataSource source) {
        Objects.requireNonNull(source);

        OverlayRenderer handler = new OverlayRenderer(source);
        this.overlayHandlers.put(source.getOverlaySourceLocation(), handler);
    }

    @SuppressWarnings("unused")
    public boolean isShowing(IOverlayDataSource source) {
        return this.overlayHandlers.containsKey(source.getOverlaySourceLocation());
    }

    public void removeHandlers(IOverlayDataSource source) {
        this.overlayHandlers.remove(source.getOverlaySourceLocation());
    }
}
