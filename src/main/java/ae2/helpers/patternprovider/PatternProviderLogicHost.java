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

package ae2.helpers.patternprovider;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.ISubGuiHost;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.helpers.IPriorityHost;
import ae2.parts.AEBasePart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.EnumSet;

public interface PatternProviderLogicHost
    extends IConfigurableObject, IPriorityHost, PatternContainer, ISubGuiHost, IUpgradeableObject {
    PatternProviderLogic getLogic();

    TileEntity getTileEntity();

    EnumSet<EnumFacing> getTargets();

    boolean hasCustomName();

    @Nullable
    ITextComponent getCustomName();

    void saveChanges();

    @Override
    default IConfigManager getConfigManager() {
        return getLogic().getConfigManager();
    }

    @Override
    default int getPriority() {
        return getLogic().getPriority();
    }

    @Override
    default void setPriority(int newValue) {
        getLogic().setPriority(newValue);
    }

    default void openGui(EntityPlayer player, GuiHostLocator locator) {
        openGui(player, locator, false);
    }

    default void openGui(EntityPlayer player, GuiHostLocator ignoredLocator, boolean returnedFromSubScreen) {
        if (this instanceof AEBasePart) {
            GuiOpener.openPartGui(player, GuiIds.GuiKey.PATTERN_PROVIDER, (AEBasePart) this,
                returnedFromSubScreen);
        } else {
            GuiOpener.openGui(player, GuiIds.GuiKey.PATTERN_PROVIDER, getTileEntity(), returnedFromSubScreen);
        }
    }

    @Override
    default void openTerminalPatternContainerGui(EntityPlayer player) {
        openGui(player, null, false);
    }

    @Override
    default void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        openGui(player, subGui.getLocator(), true);
    }

    @Override
    default @Nullable IGrid getGrid() {
        return getLogic().getGrid();
    }

    AEItemKey getTerminalIcon();

    @Override
    default boolean isVisibleInTerminal() {
        return getLogic().getConfigManager().getSetting(Settings.PATTERN_ACCESS_TERMINAL) == YesNo.YES;
    }

    @Override
    default InternalInventory getTerminalPatternInventory() {
        return getLogic().getTerminalPatternInv();
    }

    @Override
    default boolean containsPattern(AEItemKey pattern) {
        return getLogic().containsPattern(pattern);
    }

    @Override
    default IUpgradeInventory getUpgrades() {
        return getLogic().getUpgrades();
    }

    @Override
    default long getTerminalSortOrder() {
        return getLogic().getSortValue();
    }

    @Override
    default PatternContainerGroup getTerminalGroup() {
        return getLogic().getTerminalGroup();
    }
}
