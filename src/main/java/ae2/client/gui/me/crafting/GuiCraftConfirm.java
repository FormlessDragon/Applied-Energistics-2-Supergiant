/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package ae2.client.gui.me.crafting;

import ae2.api.stacks.GenericStack;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.StackWithBounds;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.TabButton;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.me.crafting.CraftingPlanSummary;
import ae2.container.me.crafting.CraftingPlanSummaryEntry;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchCraftingTreePacket;
import ae2.integration.Integrations;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;

/**
 * This screen shows the computed crafting plan and allows the player to select a CPU on which it should be scheduled
 * for crafting.
 */
public class GuiCraftConfirm extends AEBaseGui<ContainerCraftConfirm> {

    private final CraftConfirmTableRenderer table;
    private final AE2Button start;
    private final AE2Button bookmarkMissing;
    private final AE2Button selectCPU;
    private final AE2Button cancel;
    private final TabButton craftTree;
    private final Scrollbar scrollbar;

    public GuiCraftConfirm(ContainerCraftConfirm container, InventoryPlayer playerInventory, ITextComponent title,
                           GuiStyle style) {
        super(container, playerInventory, style);
        this.table = new CraftConfirmTableRenderer(this, 9, 19);

        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);

        this.start = widgets.addButton("start", GuiText.Start.text(), this::start);
        this.start.enabled = false;

        this.bookmarkMissing = widgets.addButton("bookmarkMissing", GuiText.BookmarkMissing.text(), this::bookmarkMissing);
        this.bookmarkMissing.enabled = false;
        this.bookmarkMissing.visible = false;

        this.selectCPU = widgets.addButton("selectCpu", getNextCpuButtonLabel(), this::selectNextCpu);
        this.selectCPU.enabled = false;

        this.cancel = widgets.addButton("cancel", GuiText.Cancel.text(), container::goBack);

        this.craftTree = new TabButton(Icon.CTL_CRAFT_TREE, GuiText.CraftTree.text(), this::showCraftingTree);
        widgets.add("craftTree", this.craftTree);

        if (title != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title);
        }
    }

    private ITextComponent getNextCpuButtonLabel() {
        if (this.container.hasNoCPU()) {
            return GuiText.NoCraftingCPUs.text();
        }

        ITextComponent cpuName;
        if (this.container.cpuName == null) {
            cpuName = GuiText.Automatic.text();
        } else {
            cpuName = this.container.cpuName;
        }

        return GuiText.SelectedCraftingCPU.text(cpuName);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        CraftingPlanSummary plan = container.getPlan();
        if (plan != null) {
            this.table.render(mouseX - offsetX, mouseY - offsetY, plan.entries(), scrollbar.getCurrentScroll());
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var errorResult = container.submitError.result();
        if (errorResult != null && errorResult.errorCode() != null) {
            switchToScreen(new GuiCraftError(this, errorResult.errorCode(), errorResult.errorDetail()));
            return;
        }

        this.selectCPU.setMessage(getNextCpuButtonLabel());

        CraftingPlanSummary plan = container.getPlan();
        boolean planIsStartable = plan != null && !plan.simulation();
        boolean hasMissingEntries = plan != null && plan.hasMissingEntries();
        boolean shiftDown = isShiftKeyDown();
        var startButtonState = CraftConfirmStartButtonState.compute(this.container.hasNoCPU(), planIsStartable,
            hasMissingEntries, shiftDown);
        this.start.setMessage(startButtonState.label().text());
        this.start.enabled = startButtonState.clickable();
        this.start.setForceHighlighted(startButtonState.highlighted());
        this.selectCPU.enabled = planIsStartable;
        boolean canBookmarkMissing = Integrations.hei().isEnabled() && hasMissingEntries;
        this.bookmarkMissing.visible = canBookmarkMissing;
        this.bookmarkMissing.enabled = canBookmarkMissing;

        ITextComponent planDetails = GuiText.CalculatingWait.text();
        ITextComponent cpuDetails = new TextComponentString("");
        if (plan != null) {
            String byteUsed = NumberFormat.getInstance().format(plan.usedBytes());
            planDetails = GuiText.BytesUsed.text(byteUsed);

            if (plan.simulation()) {
                cpuDetails = GuiText.PartialPlan.text();
            } else if (this.container.getCpuAvailableBytes() > 0) {
                cpuDetails = GuiText.ConfirmCraftCpuStatus.text(
                    this.container.getCpuAvailableBytes(),
                    this.container.getCpuCoProcessors());
            } else {
                cpuDetails = GuiText.ConfirmCraftNoCpu.text();
            }
        }

        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.CraftingPlan.text(planDetails));
        setTextContent("cpu_status", cpuDetails);

        final int size = plan != null ? plan.entries().size() : 0;
        scrollbar.setRange(0, this.table.getScrollableRows(size), 1);
    }

    @Nullable
    @Override
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = table.getHoveredStack();
        if (hovered != null) {
            return hovered;
        }
        return super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            this.start();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void selectNextCpu() {
        getContainer().cycleSelectedCPU(!isHandlingRightClick());
    }

    private void start() {
        getContainer().startJob(isShiftKeyDown());
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);

        List<ITextComponent> hoveredTooltip = this.table.getHoveredTooltip();
        if (hoveredTooltip != null) {
            drawTooltipWithHeader(mouseX, mouseY, hoveredTooltip);
        }

        CraftingPlanSummary plan = container.getPlan();
        if (plan != null && plan.hasMissingEntries() && this.start.visible
            && mouseX >= this.start.x && mouseY >= this.start.y
            && mouseX < this.start.x + this.start.width && mouseY < this.start.y + this.start.height) {
            drawTooltipWithHeader(mouseX, mouseY, List.of(GuiText.ForceStartHoldShift.text()));
        }

    }

    private void bookmarkMissing() {
        CraftingPlanSummary plan = container.getPlan();
        if (plan == null) {
            return;
        }

        List<GenericStack> missingStacks = new ObjectArrayList<>();
        for (CraftingPlanSummaryEntry entry : plan.entries()) {
            if (entry.missingAmount() > 0) {
                missingStacks.add(new GenericStack(entry.what(), entry.missingAmount()));
            }
        }

        Integrations.hei().addBookmarkGroup(missingStacks);
    }

    private void showCraftingTree() {
        InitNetwork.sendToServer(new SwitchCraftingTreePacket());
    }
}
