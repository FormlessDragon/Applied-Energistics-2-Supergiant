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

package ae2.items.materials;

import ae2.items.AEBaseItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextComponent.Serializer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class NamePressItem extends AEBaseItem {
    private static final String NAME_PRESS_NAME_TAG = "name_press_name";

    public NamePressItem() {
        super();
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        ITextComponent inscribedName = null;
        var tag = stack.getTagCompound();
        if (tag != null) {
            String rawName = tag.getString(NAME_PRESS_NAME_TAG);
            if (!rawName.isEmpty()) {
                try {
                    inscribedName = Serializer.jsonToComponent(rawName);
                } catch (Exception ignored) {
                    inscribedName = new TextComponentString(rawName);
                }
            }
        }

        if (inscribedName != null) {
            lines.add(TextFormatting.GRAY + inscribedName.getFormattedText());
        }
    }
}
