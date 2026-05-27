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

package appeng.helpers.patternprovider;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ISubGuiHost;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.GuiIds;
import appeng.container.ISubGui;
import appeng.core.gui.GuiOpener;
import appeng.core.gui.locator.GuiHostLocator;
import appeng.helpers.IPriorityHost;
import appeng.parts.AEBasePart;
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
        return getLogic().getPatternInv();
    }

    @Override
    default appeng.api.upgrades.IUpgradeInventory getUpgrades() {
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
