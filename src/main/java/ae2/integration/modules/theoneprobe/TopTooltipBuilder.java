/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2020, AlgorithmX2, All rights reserved.
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

package ae2.integration.modules.theoneprobe;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.core.localization.LocalizationEnum;
import mcjty.theoneprobe.api.IProbeInfo;
import net.minecraft.util.text.TextFormatting;

public class TopTooltipBuilder implements TooltipBuilder {
    private final IProbeInfo probeInfo;

    public TopTooltipBuilder(IProbeInfo probeInfo) {
        this.probeInfo = probeInfo;
    }

    @Override
    public String localize(LocalizationEnum text) {
        return TopTooltipFormatter.localize(text);
    }

    @Override
    public String localize(String translationKey) {
        return TopTooltipFormatter.localize(translationKey);
    }

    @Override
    public void addLine(String line) {
        probeInfo.text(line);
    }

    @Override
    public void addLine(LocalizationEnum line) {
        addLine(TopTooltipFormatter.localize(line));
    }

    @Override
    public void addLine(LocalizationEnum line, TextFormatting formatting) {
        addLine(TopTooltipFormatter.style(TopTooltipFormatter.localize(line), formatting));
    }

    @Override
    public void addLabel(LocalizationEnum label, String value) {
        addLabel(label, value, TextFormatting.WHITE);
    }

    @Override
    public void addLabel(LocalizationEnum label, String value, TextFormatting valueFormatting) {
        addLine(TopTooltipFormatter.labeledValue(label, value, valueFormatting));
    }
}
