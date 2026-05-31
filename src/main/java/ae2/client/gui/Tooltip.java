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

package ae2.client.gui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.Arrays;
import java.util.List;

public record Tooltip(List<ITextComponent> content) {

    public Tooltip(List<ITextComponent> content) {
        this.content = new ObjectArrayList<>(content.size());

        for (ITextComponent unprocessedLine : content) {
            splitLine(unprocessedLine, this.content);
        }
    }

    public Tooltip(ITextComponent... content) {
        this(Arrays.asList(content));
    }

    private static void splitLine(ITextComponent unprocessedLine, List<ITextComponent> lines) {
        LineSplittingVisitor visitor = new LineSplittingVisitor(lines);
        visitComponent(unprocessedLine, visitor);
        visitor.flush();
    }

    private static void visitComponent(ITextComponent component, LineSplittingVisitor visitor) {
        // getFormattedText() already includes this component and all of its siblings.
        visitor.accept(component.getStyle(), component.getFormattedText());
    }

    private static class LineSplittingVisitor {
        private final List<ITextComponent> lines;

        private ITextComponent currentPart;

        private LineSplittingVisitor(List<ITextComponent> lines) {
            this.lines = lines;
        }

        private void accept(Style style, String text) {
            String[] parts = text.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    flush();
                }

                TextComponentString part = new TextComponentString(parts[i]);
                if (style != null) {
                    part.setStyle(style.createShallowCopy());
                }
                if (currentPart != null) {
                    currentPart.appendSibling(part);
                } else {
                    currentPart = part;
                }
            }
        }

        private void flush() {
            if (currentPart != null) {
                Style style = currentPart.getStyle();
                if (style == null) {
                    style = new Style();
                    currentPart.setStyle(style);
                }
                if (style.getColor() == null) {
                    style.setColor(lines.isEmpty() ? TextFormatting.WHITE : TextFormatting.GRAY);
                }

                lines.add(currentPart);
                currentPart = null;
            }
        }
    }
}
