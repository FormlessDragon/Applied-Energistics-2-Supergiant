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
import org.jspecify.annotations.Nullable;
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
        Vector3f color = getColorForSlot(drive, slot);
        if (color == null) {
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < LED_QUADS.length; i += 3) {
            buffer.pos(LED_QUADS[i], LED_QUADS[i + 1], LED_QUADS[i + 2]).color(color.x, color.y, color.z, 1.0f)
                  .endVertex();
        }
        tessellator.draw();
    }

    private static @Nullable Vector3f getColorForSlot(IChestOrDrive drive, int slot) {
        CellState state = drive.getCellStatus(slot);
        if (state == CellState.ABSENT) {
            return null;
        }
        if (!drive.isPowered()) {
            return UNPOWERED_COLOR;
        }

        Vector3f color = new Vector3f(STATE_COLORS.get(state));
        if (drive.isCellBlinking(slot)) {
            long t = System.currentTimeMillis() % 200;
            float factor = (t - 100) / 200.0f + 0.5f;
            factor = easeInOutCubic(factor);
            color.interpolate(BLINK_COLOR, factor);
        }
        return color;
    }

    private static float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }
}
