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
package ae2.client.render.tesr;

import ae2.api.implementations.blockentities.IChestOrDrive;
import ae2.api.storage.cells.CellState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector3f;
import java.util.EnumMap;

public final class CellLedRenderer {
    private static final EnumMap<CellState, Vector3f> STATE_COLORS = new EnumMap<>(CellState.class);
    private static final Vector3f UNPOWERED_COLOR = new Vector3f(0, 0, 0);
    private static final Vector3f BLINK_COLOR = new Vector3f(1, 0.5f, 0.5f);

    private static final float L = 5 / 16.f;
    private static final float R = 4 / 16.f;
    private static final float T = 1 / 16.f;
    private static final float B = -0.001f / 16.f;
    private static final float FR = -0.001f / 16.f;
    private static final float BA = 0.999f / 16.f;

    private static final float[] LED_QUADS = {
        R, T, FR, L, T, FR, L, B, FR, R, B, FR,
        L, T, FR, L, T, BA, L, B, BA, L, B, FR,
        R, T, BA, R, T, FR, R, B, FR, R, B, BA,
        R, T, BA, L, T, BA, L, T, FR, R, T, FR,
        R, B, FR, L, B, FR, L, B, BA, R, B, BA};

    static {
        for (var cellState : CellState.values()) {
            int color = cellState.getStateColor();
            STATE_COLORS.put(cellState, new Vector3f(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f));
        }
    }

    private CellLedRenderer() {
    }

    public static void renderLed(IChestOrDrive drive, int slot) {
        Vector3f color = getColorForSlot(drive, slot, Float.NaN);
        if (color == null) {
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderLed(buffer, color, 0, 0, 0);
        tessellator.draw();
    }

    public static void renderLeds(IChestOrDrive drive, int slotCount, SlotOriginProvider slotOriginProvider) {
        float blinkFactor = getBlinkFactor();
        int firstSlot = -1;
        Vector3f firstColor = null;
        for (int slot = 0; slot < slotCount; slot++) {
            firstColor = getColorForSlot(drive, slot, blinkFactor);
            if (firstColor != null) {
                firstSlot = slot;
                break;
            }
        }
        if (firstSlot == -1) {
            return;
        }

        Vector3f slotOrigin = new Vector3f();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        slotOriginProvider.getSlotOrigin(firstSlot, slotOrigin);
        renderLed(buffer, firstColor, slotOrigin.x, slotOrigin.y, slotOrigin.z);
        for (int slot = firstSlot + 1; slot < slotCount; slot++) {
            Vector3f color = getColorForSlot(drive, slot, blinkFactor);
            if (color == null) {
                continue;
            }

            slotOriginProvider.getSlotOrigin(slot, slotOrigin);
            renderLed(buffer, color, slotOrigin.x, slotOrigin.y, slotOrigin.z);
        }
        tessellator.draw();
    }

    private static void renderLed(BufferBuilder buffer, Vector3f color, float x, float y, float z) {
        for (int i = 0; i < LED_QUADS.length; i += 3) {
            buffer.pos(x + LED_QUADS[i], y + LED_QUADS[i + 1], z + LED_QUADS[i + 2])
                  .color(color.x, color.y, color.z, 1.0f)
                  .endVertex();
        }
    }

    private static @Nullable Vector3f getColorForSlot(IChestOrDrive drive, int slot, float blinkFactor) {
        CellState state = drive.getCellStatus(slot);
        if (state == CellState.ABSENT) {
            return null;
        }
        if (!drive.isPowered()) {
            return UNPOWERED_COLOR;
        }

        Vector3f color = STATE_COLORS.get(state);
        if (!drive.isCellBlinking(slot)) {
            return color;
        }

        color = new Vector3f(color);
        if (Float.isNaN(blinkFactor)) {
            blinkFactor = getBlinkFactor();
        }
        color.interpolate(BLINK_COLOR, blinkFactor);
        return color;
    }

    private static float getBlinkFactor() {
        long t = System.currentTimeMillis() % 200;
        float factor = (t - 100) / 200.0f + 0.5f;
        return easeInOutCubic(factor);
    }

    @FunctionalInterface
    public interface SlotOriginProvider {
        void getSlotOrigin(int slot, Vector3f slotOrigin);
    }

    private static float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }
}
