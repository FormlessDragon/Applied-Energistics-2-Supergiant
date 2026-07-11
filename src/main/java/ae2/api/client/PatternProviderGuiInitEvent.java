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

package ae2.api.client;

import ae2.helpers.patternprovider.PatternProviderLogicHost;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Posted on the Forge event bus after a pattern provider GUI has added its built-in toolbar buttons.
 * <p>
 * Add-ons can use this event to append controls to the native pattern provider toolbar without depending on the
 * GUI's internal widget implementation.
 */
@SideOnly(Side.CLIENT)
public final class PatternProviderGuiInitEvent extends Event {

    private final PatternProviderLogicHost host;
    private final Consumer<GuiButton> leftToolbar;

    public PatternProviderGuiInitEvent(PatternProviderLogicHost host, Consumer<GuiButton> leftToolbar) {
        this.host = Objects.requireNonNull(host, "host");
        this.leftToolbar = Objects.requireNonNull(leftToolbar, "leftToolbar");
    }

    /**
     * @return the pattern provider host represented by the GUI
     */
    public PatternProviderLogicHost getHost() {
        return this.host;
    }

    /**
     * Appends a button to the native left toolbar.
     *
     * @return the supplied button, for convenient assignment
     */
    public <B extends GuiButton> B addToLeftToolbar(B button) {
        this.leftToolbar.accept(Objects.requireNonNull(button, "button"));
        return button;
    }
}
