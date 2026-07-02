/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026, TeamAppliedEnergistics, All rights reserved.
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

package ae2.client.gui.implementations;

import ae2.api.cellterminal.CellTerminalBusPartitionMode;
import ae2.api.config.AccessRestriction;
import ae2.api.config.CellTerminalContentFilter;
import ae2.api.config.CellTerminalSearchMode;
import ae2.api.config.CellTerminalSlotLimit;
import ae2.api.config.CellTerminalSubnetVisibility;
import ae2.api.config.Settings;
import ae2.api.config.TerminalStyle;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.Upgrades;
import ae2.cellterminal.server.CellTerminalNetworkToolOperation;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.cellterminal.widget.BusTextPartitionLine;
import ae2.client.gui.cellterminal.widget.ButtonType;
import ae2.client.gui.cellterminal.widget.CardsDisplay;
import ae2.client.gui.cellterminal.widget.CellSlotsLine;
import ae2.client.gui.cellterminal.widget.CellTerminalLayout;
import ae2.client.gui.cellterminal.widget.CellTerminalRowList;
import ae2.client.gui.cellterminal.widget.CellTerminalSearchAssist;
import ae2.client.gui.cellterminal.widget.CellTerminalSearchOverlay;
import ae2.client.gui.cellterminal.widget.CellTerminalSearchQuery;
import ae2.client.gui.cellterminal.widget.ContinuationLine;
import ae2.client.gui.cellterminal.widget.DoubleClickTracker;
import ae2.client.gui.cellterminal.widget.IWidget;
import ae2.client.gui.cellterminal.widget.InlineRenameManager;
import ae2.client.gui.cellterminal.widget.NetworkToolRowWidget;
import ae2.client.gui.cellterminal.widget.Prioritizable;
import ae2.client.gui.cellterminal.widget.PriorityFieldManager;
import ae2.client.gui.cellterminal.widget.Renameable;
import ae2.client.gui.cellterminal.widget.SlotsLine;
import ae2.client.gui.cellterminal.widget.SmallButton;
import ae2.client.gui.cellterminal.widget.StorageBusHeader;
import ae2.client.gui.cellterminal.widget.StorageHeader;
import ae2.client.gui.cellterminal.widget.SubnetHeader;
import ae2.client.gui.cellterminal.widget.TempAreaHeader;
import ae2.client.gui.cellterminal.widget.TerminalLine;
import ae2.client.gui.me.common.KeyTypeSelectionWindow;
import ae2.client.gui.me.items.GuiSetProcessingPatternAmount;
import ae2.client.gui.me.items.WirelessUniversalTerminalSelectorWindow;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.style.PaletteColor;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.GridSelectionPopup;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.ItemStackButton;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.container.SlotSemantics;
import ae2.container.implementations.CellTerminalClientState;
import ae2.container.implementations.CellTerminalClientState.CellSlotEntry;
import ae2.container.implementations.CellTerminalClientState.CellTerminalTab;
import ae2.container.implementations.CellTerminalClientState.StorageEntry;
import ae2.container.implementations.ContainerCellTerminal;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.integration.modules.hei.GenericIngredientHelper;
import ae2.integration.modules.hei.target.CellTerminalPartitionTarget;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Optional;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import static ae2.core.localization.GuiText.AttachedTo;
import static ae2.core.localization.GuiText.CellTerminal;
import static ae2.core.localization.GuiText.CellTerminalBusTextPartitionMod;
import static ae2.core.localization.GuiText.CellTerminalBusTextPartitionOdBlack;
import static ae2.core.localization.GuiText.CellTerminalBusTextPartitionOdWhite;
import static ae2.core.localization.GuiText.CellTerminalCellEmpty;
import static ae2.core.localization.GuiText.CellTerminalControlsClickPartitionToggle;
import static ae2.core.localization.GuiText.CellTerminalControlsClickToRemove;
import static ae2.core.localization.GuiText.CellTerminalControlsDoubleClickStorage;
import static ae2.core.localization.GuiText.CellTerminalControlsDoubleClickStorageCell;
import static ae2.core.localization.GuiText.CellTerminalControlsFilterIndicator;
import static ae2.core.localization.GuiText.CellTerminalControlsJeiDrag;
import static ae2.core.localization.GuiText.CellTerminalControlsPartitionIndicator;
import static ae2.core.localization.GuiText.CellTerminalControlsRightClickRename;
import static ae2.core.localization.GuiText.CellTerminalControlsStorageBusCapacity;
import static ae2.core.localization.GuiText.CellTerminalControlsTempAreaDragCell;
import static ae2.core.localization.GuiText.CellTerminalControlsTempAreaSendCell;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsAttributeUniqueConfirm;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsAttributeUniqueHelp1;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsAttributeUniqueHelp2;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsAttributeUniqueHelp3;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsAttributeUniqueHelp4;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsAttributeUniqueName;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsAttributeUniquePreviewTitle;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsConfirmCancel;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsConfirmDoIt;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsConfirmTitle;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsHelpReadTooltip;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsMassPartitionBusConfirm;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsMassPartitionBusHelp1;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsMassPartitionBusHelp2;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsMassPartitionBusName;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsMassPartitionCellConfirm;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsMassPartitionCellHelp1;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsMassPartitionCellHelp2;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsMassPartitionCellName;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsNoTargets;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsTargetBreakdown;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsWarningCaution;
import static ae2.core.localization.GuiText.CellTerminalNetworkToolsWarningIrreversible;
import static ae2.core.localization.GuiText.CellTerminalSearchPlaceholder;
import static ae2.core.localization.GuiText.CellTerminalSubnetBack;
import static ae2.core.localization.GuiText.CellTerminalSubnetBackDesc;
import static ae2.core.localization.GuiText.CellTerminalSubnetClickLoadMain;
import static ae2.core.localization.GuiText.CellTerminalSubnetControlsClick;
import static ae2.core.localization.GuiText.CellTerminalSubnetControlsDblClick;
import static ae2.core.localization.GuiText.CellTerminalSubnetControlsEsc;
import static ae2.core.localization.GuiText.CellTerminalSubnetControlsRename;
import static ae2.core.localization.GuiText.CellTerminalSubnetControlsStar;
import static ae2.core.localization.GuiText.CellTerminalSubnetControlsTitle;
import static ae2.core.localization.GuiText.CellTerminalSubnetInbound;
import static ae2.core.localization.GuiText.CellTerminalSubnetLoadTooltip;
import static ae2.core.localization.GuiText.CellTerminalSubnetMainNetwork;
import static ae2.core.localization.GuiText.CellTerminalSubnetOutbound;
import static ae2.core.localization.GuiText.CellTerminalSubnetOverview;
import static ae2.core.localization.GuiText.CellTerminalSubnetOverviewDesc;
import static ae2.core.localization.GuiText.CellTerminalSubnetPos;
import static ae2.core.localization.GuiText.CellTerminalTabBusContent;
import static ae2.core.localization.GuiText.CellTerminalTabBusContentTooltip;
import static ae2.core.localization.GuiText.CellTerminalTabBusPartition;
import static ae2.core.localization.GuiText.CellTerminalTabBusPartitionTooltip;
import static ae2.core.localization.GuiText.CellTerminalTabCellContent;
import static ae2.core.localization.GuiText.CellTerminalTabCellContentTooltip;
import static ae2.core.localization.GuiText.CellTerminalTabCellPartition;
import static ae2.core.localization.GuiText.CellTerminalTabCellPartitionTooltip;
import static ae2.core.localization.GuiText.CellTerminalTabOverview;
import static ae2.core.localization.GuiText.CellTerminalTabOverviewTooltip;
import static ae2.core.localization.GuiText.CellTerminalTabSubnets;
import static ae2.core.localization.GuiText.CellTerminalTabTempCells;
import static ae2.core.localization.GuiText.CellTerminalTabTempCellsTooltip;
import static ae2.core.localization.GuiText.CellTerminalTabTools;
import static ae2.core.localization.GuiText.CellTerminalTabToolsTooltip;
import static ae2.core.localization.GuiText.CellTerminalUpgradeTooltipHintAvailableEntries;
import static ae2.core.localization.GuiText.CellTerminalUpgradeTooltipHintClick;
import static ae2.core.localization.GuiText.CellTerminalUpgradeTooltipHintEntryCellLines;
import static ae2.core.localization.GuiText.CellTerminalUpgradeTooltipHintEntryCells;
import static ae2.core.localization.GuiText.CellTerminalUpgradeTooltipHintEntryDrive;
import static ae2.core.localization.GuiText.CellTerminalUpgradeTooltipHintEntryStorageBus;
import static ae2.core.localization.GuiText.CellTerminalUpgradeTooltipHintShiftClick;
import static ae2.core.localization.GuiText.ConfigureVisibleTypes;
import static ae2.core.localization.GuiText.ModFilterTooltip;
import static ae2.core.localization.GuiText.ODFilterBlackTooltip;
import static ae2.core.localization.GuiText.ODFilterWhiteTooltip;
import static ae2.core.localization.GuiText.Unattached;
import static ae2.core.localization.GuiText.WirelessTerminalSelector;

public class GuiCellTerminal extends AEBaseGui<ContainerCellTerminal> implements ITextFieldGui {
    private static final int WIDTH = 208;
    private static final int MIN_ROWS = 6;
    private static final int MAX_ROWS = 13;
    private static final int HEADER_HEIGHT = 18;
    private static final int HEADER_BACKGROUND_HEIGHT = HEADER_HEIGHT + 1;
    private static final int CONTENT_BACKGROUND_FILL_DEST_Y = HEADER_BACKGROUND_HEIGHT;
    private static final int FOOTER_HEIGHT = 98;
    private static final int ROW_HEIGHT = 18;
    private static final int CONTENT_BACKGROUND_FIRST_ROW_SRC_Y = 51;
    private static final int CONTENT_BACKGROUND_FILL_SRC_Y = CONTENT_BACKGROUND_FIRST_ROW_SRC_Y + 1;
    private static final int CONTENT_BACKGROUND_FIRST_ROW_DEST_Y = HEADER_HEIGHT;
    private static final int CONTENT_BACKGROUND_FIRST_ROW_HEIGHT = ROW_HEIGHT + 1;
    private static final int TAB_WIDTH = 22;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_Y_OFFSET = -19;
    private static final int TOP_TAB_OUTSET = -TAB_Y_OFFSET;
    private static final int TEMP_AREA_SLOTS_PER_ROW = CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW;
    private static final int HELP_TEXTURE_SIZE = 16;
    private static final int HELP_TEXTURE_BORDER = 4;
    private static final int SEARCH_ERROR_MARGIN = 4;
    private static final int FEEDBACK_PADDING_X = 8;
    private static final int FEEDBACK_PADDING_Y = 5;
    private static final float SEARCH_ASSIST_Z_LEVEL = 500.0F;
    private static final CellTerminalTab[] VISIBLE_TABS = {
        CellTerminalTab.OVERVIEW,
        CellTerminalTab.CELL_CONTENT,
        CellTerminalTab.CELL_PARTITION,
        CellTerminalTab.TEMP_CELLS,
        CellTerminalTab.BUS_CONTENT,
        CellTerminalTab.BUS_PARTITION,
        CellTerminalTab.SUBNETS,
        CellTerminalTab.NETWORK_TOOLS
    };
    private static final Blitter BACKGROUND = Blitter.texture("guis/cell_terminal.png", 256, 256);
    private static final Blitter CONTROLS_HELP_BACKGROUND =
        Blitter.texture("guis/cell_terminal_controls_help.png", HELP_TEXTURE_SIZE, HELP_TEXTURE_SIZE);
    private static final int TOOL_CONFIRM_MIN_W = 200;
    private static final int TOOL_CONFIRM_MAX_W = 300;
    private static final int TOOL_CONFIRM_PADDING = 10;
    private static final int TOOL_CONFIRM_BTN_W = 60;
    private static final int TOOL_CONFIRM_BTN_H = 20;
    private static final int TOOL_CONFIRM_BTN_SPACING = 20;
    private static final int TOOL_CONFIRM_TITLE_H = 20;
    private static final int SEARCH_MAX_LENGTH = 100;
    private final AETextField searchField;
    private final CellTerminalSearchAssist searchAssist = new CellTerminalSearchAssist();
    private final Scrollbar scrollbar;
    private final List<Object> lineData = new ObjectArrayList<>();
    private final Int2ObjectMap<GhostTargetData> ghostTargetData = new Int2ObjectOpenHashMap<>();
    private final Set<String> expandedStorages = new ObjectOpenHashSet<>();
    private final Set<String> expandedBuses = new ObjectOpenHashSet<>();
    private final Set<String> selectedBuses = new ObjectOpenHashSet<>();
    private final Set<String> selectedTempCells = new ObjectOpenHashSet<>();
    private final Map<String, AETextField> busTextPartitionFields = new Object2ObjectOpenHashMap<>();
    private final Map<String, List<GenericStack>> contentPageCache = new Object2ObjectOpenHashMap<>();
    private final CellTerminalRowList rowList;
    private int visibleRows = MIN_ROWS;
    @Nullable
    private CellTerminalSearchOverlay searchOverlay;
    private long lastSearchClickTime;
    private String searchText = "";
    private String pendingContentPageKey;
    private int pendingContentPageFirst = -1;
    private CellTerminalNetworkToolOperation pendingToolConfirm;
    private String lastContextId = "";
    private String lastSearchText = "";
    private CellTerminalSearchQuery searchQuery = CellTerminalSearchQuery.parse("");
    private CellTerminalTab lastTab = CellTerminalTab.OVERVIEW;
    private long lastCacheRevision = Long.MIN_VALUE;

    public GuiCellTerminal(ContainerCellTerminal container, InventoryPlayer playerInventory) {
        super(container, playerInventory, GuiStyleManager.loadStyleDoc("/screens/cell_terminal.json"));
        this.xSize = WIDTH;
        this.ySize = configuredHeight();

        this.searchField = this.widgets.addTextField("search");
        this.searchField.setMaxStringLength(SEARCH_MAX_LENGTH);
        this.searchField.setPlaceholder(tr("search.placeholder"));
        this.searchField.setTooltipMessage(searchTooltip());
        this.searchField.setResponder(text -> {
            this.searchText = text == null ? "" : text;
            requestRebuild();
        });
        this.searchOverlay = null;

        this.scrollbar = this.widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.scrollbar.setCaptureMouseWheel(true);

        if (container.getCellTerminalHost() instanceof IUpgradeableObject upgradeableObject) {
            this.widgets.add("upgrades", UpgradesPanel.create(
                this.widgets,
                container.getSlots(SlotSemantics.UPGRADE),
                container.getSlots(SlotSemantics.WIRELESS_SINGULARITY),
                upgradeableObject));
        }
        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.TERMINAL_STYLE, AEConfig.instance().getTerminalStyle(), this::toggleTerminalStyle));
        if (container.canConfigureTypeFilter()) {
            KeyTypeSelectionWindow<ContainerCellTerminal> keyTypeWindow
                = new KeyTypeSelectionWindow<>(this, ConfigureVisibleTypes.text());
            this.widgets.add("keyTypeSelectionWindow", keyTypeWindow);
            this.addToLeftToolbar(new TypeFilterButton(keyTypeWindow));
        }
        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.CELL_TERMINAL_SEARCH_MODE, AEConfig.instance().getCellTerminalSearchMode(),
            (btn, back) -> cycleSetting(btn, back,
                AEConfig.instance()::setCellTerminalSearchMode)));
        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.CELL_TERMINAL_CONTENT_FILTER, AEConfig.instance().getCellTerminalContentFilter(),
            (btn, back) -> cycleSetting(btn, back,
                AEConfig.instance()::setCellTerminalContentFilter)));
        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.CELL_TERMINAL_PARTITION_FILTER, AEConfig.instance().getCellTerminalPartitionFilter(),
            (btn, back) -> cycleSetting(btn, back,
                AEConfig.instance()::setCellTerminalPartitionFilter)));
        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.CELL_TERMINAL_SUBNET_VISIBILITY, AEConfig.instance().getCellTerminalSubnetVisibility(),
            this::cycleSubnetVisibility));
        this.addToLeftToolbar(new CellTerminalSlotLimitButton(
            AEConfig.instance().getCellTerminalSlotLimit(),
            (btn, back) -> cycleSetting(btn, back,
                AEConfig.instance()::setCellTerminalSlotLimit)));
        addWirelessUniversalTerminalButton();

        this.rowList = new CellTerminalRowList(configuredRows(), ROW_HEIGHT, GuiCellTerminal::rowHeightForData,
            this::createRowWidget, new CellTerminalRowList.ContentLinePredicate() {
            @Override
            public boolean isContentLine(List<?> allLines, int index) {
                return GuiCellTerminal.this.isContentLine(allLines, index);
            }

            @Override
            public boolean drawsHorizontalJunction(List<?> allLines, int index) {
                return GuiCellTerminal.this.drawsHorizontalJunction(allLines, index);
            }

            @Override
            public CellTerminalRowList.TreeLineInfo treeLineInfo(List<?> allLines, int index) {
                return GuiCellTerminal.this.treeLineInfo(allLines, index);
            }

            @Override
            public CellTerminalRowList.TreeLineInfo headerConnectorInfo(List<?> allLines, int index) {
                return GuiCellTerminal.this.headerConnectorInfo(allLines, index);
            }
        });
    }

    private static int rowHeightForData(Object data) {
        return data instanceof ToolRowData ? NetworkToolRowWidget.ROW_HEIGHT : ROW_HEIGHT;
    }

    private static String tr(String suffix, Object... args) {
        if (suffix.startsWith("networktools.attribute_unique.preview.type.")) {
            return translate("gui.ae2.CellTerminal." + suffix, args);
        }
        GuiText text = switch (suffix) {
            case "busTextPartition.mod" -> CellTerminalBusTextPartitionMod;
            case "busTextPartition.odBlack" -> CellTerminalBusTextPartitionOdBlack;
            case "busTextPartition.odWhite" -> CellTerminalBusTextPartitionOdWhite;
            case "cell.empty" -> CellTerminalCellEmpty;
            case "controls.click_partition_toggle" -> CellTerminalControlsClickPartitionToggle;
            case "controls.click_to_remove" -> CellTerminalControlsClickToRemove;
            case "controls.double_click_storage" -> CellTerminalControlsDoubleClickStorage;
            case "controls.double_click_storage_cell" -> CellTerminalControlsDoubleClickStorageCell;
            case "controls.filter_indicator" -> CellTerminalControlsFilterIndicator;
            case "controls.jei_drag" -> CellTerminalControlsJeiDrag;
            case "controls.partition_indicator" -> CellTerminalControlsPartitionIndicator;
            case "controls.right_click_rename" -> CellTerminalControlsRightClickRename;
            case "controls.storage_bus_capacity" -> CellTerminalControlsStorageBusCapacity;
            case "controls.temp_area.drag_cell" -> CellTerminalControlsTempAreaDragCell;
            case "controls.temp_area.send_cell" -> CellTerminalControlsTempAreaSendCell;
            case "networktools.attribute_unique.confirm" -> CellTerminalNetworkToolsAttributeUniqueConfirm;
            case "networktools.attribute_unique.help.1" -> CellTerminalNetworkToolsAttributeUniqueHelp1;
            case "networktools.attribute_unique.help.2" -> CellTerminalNetworkToolsAttributeUniqueHelp2;
            case "networktools.attribute_unique.help.3" -> CellTerminalNetworkToolsAttributeUniqueHelp3;
            case "networktools.attribute_unique.help.4" -> CellTerminalNetworkToolsAttributeUniqueHelp4;
            case "networktools.attribute_unique.name" -> CellTerminalNetworkToolsAttributeUniqueName;
            case "networktools.attribute_unique.preview.title" -> CellTerminalNetworkToolsAttributeUniquePreviewTitle;
            case "networktools.confirm.cancel" -> CellTerminalNetworkToolsConfirmCancel;
            case "networktools.confirm.do_it" -> CellTerminalNetworkToolsConfirmDoIt;
            case "networktools.confirm.title" -> CellTerminalNetworkToolsConfirmTitle;
            case "networktools.help.read_tooltip" -> CellTerminalNetworkToolsHelpReadTooltip;
            case "networktools.mass_partition_bus.confirm" -> CellTerminalNetworkToolsMassPartitionBusConfirm;
            case "networktools.mass_partition_bus.help.1" -> CellTerminalNetworkToolsMassPartitionBusHelp1;
            case "networktools.mass_partition_bus.help.2" -> CellTerminalNetworkToolsMassPartitionBusHelp2;
            case "networktools.mass_partition_bus.name" -> CellTerminalNetworkToolsMassPartitionBusName;
            case "networktools.mass_partition_cell.confirm" -> CellTerminalNetworkToolsMassPartitionCellConfirm;
            case "networktools.mass_partition_cell.help.1" -> CellTerminalNetworkToolsMassPartitionCellHelp1;
            case "networktools.mass_partition_cell.help.2" -> CellTerminalNetworkToolsMassPartitionCellHelp2;
            case "networktools.mass_partition_cell.name" -> CellTerminalNetworkToolsMassPartitionCellName;
            case "networktools.no_targets" -> CellTerminalNetworkToolsNoTargets;
            case "networktools.target_breakdown" -> CellTerminalNetworkToolsTargetBreakdown;
            case "networktools.warning.caution" -> CellTerminalNetworkToolsWarningCaution;
            case "networktools.warning.irreversible" -> CellTerminalNetworkToolsWarningIrreversible;
            case "search.placeholder" -> CellTerminalSearchPlaceholder;
            case "subnet.back" -> CellTerminalSubnetBack;
            case "subnet.back.desc" -> CellTerminalSubnetBackDesc;
            case "subnet.click_load_main" -> CellTerminalSubnetClickLoadMain;
            case "subnet.controls.click" -> CellTerminalSubnetControlsClick;
            case "subnet.controls.dblclick" -> CellTerminalSubnetControlsDblClick;
            case "subnet.controls.esc" -> CellTerminalSubnetControlsEsc;
            case "subnet.controls.rename" -> CellTerminalSubnetControlsRename;
            case "subnet.controls.star" -> CellTerminalSubnetControlsStar;
            case "subnet.controls.title" -> CellTerminalSubnetControlsTitle;
            case "subnet.inbound" -> CellTerminalSubnetInbound;
            case "subnet.loadTooltip" -> CellTerminalSubnetLoadTooltip;
            case "subnet.mainNetwork" -> CellTerminalSubnetMainNetwork;
            case "subnet.outbound" -> CellTerminalSubnetOutbound;
            case "subnet.overview" -> CellTerminalSubnetOverview;
            case "subnet.overview.desc" -> CellTerminalSubnetOverviewDesc;
            case "subnet.pos" -> CellTerminalSubnetPos;
            case "tab.busContent" -> CellTerminalTabBusContent;
            case "tab.busContent.tooltip" -> CellTerminalTabBusContentTooltip;
            case "tab.busPartition" -> CellTerminalTabBusPartition;
            case "tab.busPartition.tooltip" -> CellTerminalTabBusPartitionTooltip;
            case "tab.cellContent" -> CellTerminalTabCellContent;
            case "tab.cellContent.tooltip" -> CellTerminalTabCellContentTooltip;
            case "tab.cellPartition" -> CellTerminalTabCellPartition;
            case "tab.cellPartition.tooltip" -> CellTerminalTabCellPartitionTooltip;
            case "tab.overview" -> CellTerminalTabOverview;
            case "tab.overview.tooltip" -> CellTerminalTabOverviewTooltip;
            case "tab.subnets" -> CellTerminalTabSubnets;
            case "tab.tempCells" -> CellTerminalTabTempCells;
            case "tab.tempCells.tooltip" -> CellTerminalTabTempCellsTooltip;
            case "tab.tools" -> CellTerminalTabTools;
            case "tab.tools.tooltip" -> CellTerminalTabToolsTooltip;
            case "upgrade.tooltip_hint_available_entries" -> CellTerminalUpgradeTooltipHintAvailableEntries;
            case "upgrade.tooltip_hint_click" -> CellTerminalUpgradeTooltipHintClick;
            case "upgrade.tooltip_hint_entry_cell_lines" -> CellTerminalUpgradeTooltipHintEntryCellLines;
            case "upgrade.tooltip_hint_entry_cells" -> CellTerminalUpgradeTooltipHintEntryCells;
            case "upgrade.tooltip_hint_entry_drive" -> CellTerminalUpgradeTooltipHintEntryDrive;
            case "upgrade.tooltip_hint_entry_storage_bus" -> CellTerminalUpgradeTooltipHintEntryStorageBus;
            case "upgrade.tooltip_hint_shift_click" -> CellTerminalUpgradeTooltipHintShiftClick;
            default -> throw new IllegalArgumentException("Unmapped Cell Terminal translation key: " + suffix);
        };
        return text.getLocal(args);
    }

    private static String translate(String key, Object... args) {
        return new TextComponentTranslation(key, args).getFormattedText();
    }

    private static List<ITextComponent> searchTooltip() {
        return CellTerminalSearchAssist.shortTooltip();
    }

    private static List<GenericStack> toContentList(List<CellTerminalClientState.ContentEntry> content) {
        List<GenericStack> result = new ObjectArrayList<>(content.size());
        for (CellTerminalClientState.ContentEntry entry : content) {
            result.add(entry.stack());
        }
        return result;
    }

    private static List<GenericStack> toStackList(List<@Nullable GenericStack> partition) {
        List<GenericStack> result = new ObjectArrayList<>(partition.size());
        result.addAll(partition);
        return result;
    }

    private static List<GenericStack> applySlotLimit(List<GenericStack> stacks) {
        int limit = AEConfig.instance().getCellTerminalSlotLimit().limit();
        if (limit < 0 || stacks.size() <= limit) {
            return stacks;
        }
        return new ObjectArrayList<>(stacks.subList(0, limit));
    }

    private static String storageKey(StorageEntry storage) {
        return storage.stableTargetId() + "|" + storage.locator();
    }

    private static String busKey(CellTerminalClientState.BusEntry bus) {
        return bus.stableTargetId() + "|" + bus.locator();
    }

    private static String busTextPartitionFieldKey(CellTerminalClientState.BusEntry bus, String fieldId) {
        return busKey(bus) + "|" + fieldId;
    }

    private static boolean isTextBusPartition(CellTerminalClientState.BusEntry bus) {
        return bus.partitionMode() == CellTerminalBusPartitionMode.MOD_EXPRESSION
            || bus.partitionMode() == CellTerminalBusPartitionMode.ORE_DICTIONARY_EXPRESSIONS;
    }

    private static boolean isPreciseBusPartition(CellTerminalClientState.BusEntry bus) {
        return bus.partitionMode() == CellTerminalBusPartitionMode.PRECISE_SLOTS;
    }

    private static String tempCellKey(StorageEntry storage, CellSlotEntry slot) {
        return storage.stableTargetId() + "|" + storage.locator() + "|" + slot.slotIndex();
    }

    private static boolean isTempStorage(StorageEntry storage) {
        return storage.stableTargetId().startsWith("temp_cells@");
    }

    private static CellTerminalRowList.BranchKind branchKind(boolean partition) {
        return partition ? CellTerminalRowList.BranchKind.PARTITION : CellTerminalRowList.BranchKind.CONTENT;
    }

    private static String storageSectionKey(StorageEntry storage) {
        return "storage:" + storageKey(storage);
    }

    private static String tempCellSectionKey(StorageEntry storage, CellSlotEntry slot) {
        return "temp:" + tempCellKey(storage, slot);
    }

    private static String busSectionKey(CellTerminalClientState.BusEntry bus) {
        return "bus:" + busKey(bus);
    }

    private static String subnetConnectionSectionKey(CellTerminalClientState.SubnetEntry subnet,
                                                     CellTerminalClientState.ConnectionEntry connection,
                                                     int connectionIndex) {
        return "subnet:" + subnet.stableTargetId() + "|" + subnet.subnetId() + "|"
            + connection.stableTargetId() + "|" + connectionIndex;
    }

    private static String headerSectionKey(Object data) {
        if (data instanceof StorageHeaderData row) {
            return storageSectionKey(row.storage());
        }
        if (data instanceof TempCellRowData(StorageEntry storage, CellSlotEntry slot)) {
            return tempCellSectionKey(storage, slot);
        }
        if (data instanceof BusHeaderData row) {
            return busSectionKey(row.bus());
        }
        if (data instanceof SubnetConnectionRowData(
            CellTerminalClientState.SubnetEntry subnet, CellTerminalClientState.ConnectionEntry connection,
            int connectionIndex
        )) {
            return subnetConnectionSectionKey(subnet, connection, connectionIndex);
        }
        return null;
    }

    private static String contentSectionKey(Object data) {
        if (data instanceof CellLineData row) {
            return storageSectionKey(row.storage());
        }
        if (data instanceof CellSlotsRowData row) {
            return storageSectionKey(row.storage());
        }
        if (data instanceof TempSlotsRowData row) {
            return tempCellSectionKey(row.storage(), row.slot());
        }
        if (data instanceof BusRowData row) {
            return busSectionKey(row.bus());
        }
        if (data instanceof BusTextPartitionRowData row) {
            return busSectionKey(row.bus());
        }
        if (data instanceof SubnetConnectionContentRowData row) {
            return subnetConnectionSectionKey(row.subnet(), row.connection(), row.connectionIndex());
        }
        return null;
    }

    private static CellTerminalRowList.BranchKind contentBranchKind(Object data) {
        if (data instanceof TempSlotsRowData row) {
            return branchKind(row.partition());
        }
        if (data instanceof SubnetConnectionContentRowData row) {
            return branchKind(row.partition());
        }
        if (data instanceof CellLineData || data instanceof CellSlotsRowData
            || data instanceof BusRowData || data instanceof BusTextPartitionRowData) {
            return CellTerminalRowList.BranchKind.SINGLE;
        }
        return null;
    }

    private static boolean hasLaterSameSectionBranch(List<?> all, int index, String sectionKey,
                                                     CellTerminalRowList.BranchKind branchKind) {
        for (int i = index + 1; i < all.size(); i++) {
            Object data = all.get(i);
            if (headerSectionKey(data) != null) {
                return false;
            }
            String nextSectionKey = contentSectionKey(data);
            if (nextSectionKey == null) {
                continue;
            }
            if (!sectionKey.equals(nextSectionKey)) {
                return false;
            }
            if (contentBranchKind(data) == branchKind) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLaterCellSlotNode(List<?> all, int index, String sectionKey, CellSlotEntry slot) {
        for (int i = index + 1; i < all.size(); i++) {
            Object data = all.get(i);
            if (headerSectionKey(data) != null) {
                return false;
            }
            String nextSectionKey = contentSectionKey(data);
            if (nextSectionKey == null) {
                continue;
            }
            if (!sectionKey.equals(nextSectionKey)) {
                return false;
            }
            if (data instanceof CellSlotsRowData row && row.slot().slotIndex() != slot.slotIndex()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLaterBusRow(List<?> all, int index, String sectionKey) {
        for (int i = index + 1; i < all.size(); i++) {
            Object data = all.get(i);
            if (headerSectionKey(data) != null) {
                return false;
            }
            String nextSectionKey = contentSectionKey(data);
            if (nextSectionKey == null) {
                continue;
            }
            if (!sectionKey.equals(nextSectionKey)) {
                return false;
            }
            if (data instanceof BusRowData) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLaterBusTextPartitionRow(List<?> all, int index, String sectionKey) {
        for (int i = index + 1; i < all.size(); i++) {
            Object data = all.get(i);
            if (headerSectionKey(data) != null) {
                return false;
            }
            String nextSectionKey = contentSectionKey(data);
            if (nextSectionKey == null) {
                continue;
            }
            if (!sectionKey.equals(nextSectionKey)) {
                return false;
            }
            if (data instanceof BusTextPartitionRowData) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEarlierContentBranch(List<?> all, int index, String sectionKey) {
        for (int i = index - 1; i >= 0; i--) {
            Object data = all.get(i);
            if (headerSectionKey(data) != null) {
                return false;
            }
            String previousSectionKey = contentSectionKey(data);
            if (previousSectionKey == null) {
                continue;
            }
            if (!sectionKey.equals(previousSectionKey)) {
                return false;
            }
            if (contentBranchKind(data) == CellTerminalRowList.BranchKind.CONTENT) {
                return true;
            }
        }
        return false;
    }

    private static int visiblePartitionSlotCount(List<GenericStack> partition) {
        int lastOccupied = -1;
        for (int i = 0; i < partition.size(); i++) {
            if (partition.get(i) != null) {
                lastOccupied = i;
            }
        }
        if (lastOccupied < 0) {
            return GuiCellTerminal.TEMP_AREA_SLOTS_PER_ROW;
        }
        return ((lastOccupied / GuiCellTerminal.TEMP_AREA_SLOTS_PER_ROW) + 1) * GuiCellTerminal.TEMP_AREA_SLOTS_PER_ROW;
    }

    private static List<String> nameValues(List<String> values) {
        return values;
    }

    private static void appendContentSearchValues(List<String> values, List<CellTerminalClientState.ContentEntry> content) {
        for (CellTerminalClientState.ContentEntry entry : content) {
            appendGenericStackSearchValues(values, entry.stack());
        }
    }

    private static void appendPartitionSearchValues(List<String> values, List<@Nullable GenericStack> partition) {
        for (GenericStack stack : partition) {
            appendGenericStackSearchValues(values, stack);
        }
    }

    private static void appendGenericStackSearchValues(List<String> values, @Nullable GenericStack stack) {
        if (stack == null) {
            return;
        }
        values.add(stack.what().getDisplayName().getFormattedText());
        if (stack.what().getId() != null) {
            values.add(stack.what().getId().toString());
        }
    }

    private static List<String> optionalValue(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value);
    }

    private static List<String> containerValues(String... values) {
        List<String> result = new ObjectArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    @SafeVarargs
    private static List<String> aggregateSearchValues(List<String>... groups) {
        Set<String> values = new LinkedHashSet<>();
        for (List<String> group : groups) {
            values.addAll(group);
        }
        return List.copyOf(values);
    }

    private static int storagePartitionEntryCount(StorageEntry storage) {
        int count = 0;
        for (CellSlotEntry slot : storage.cellSlots()) {
            count += partitionEntryCount(slot.partition());
        }
        return count;
    }

    private static int busPartitionEntryCount(CellTerminalClientState.BusEntry bus) {
        if (isTextBusPartition(bus)) {
            int count = 0;
            if (!bus.textPartitionPrimary().isEmpty()) {
                count++;
            }
            if (!bus.textPartitionSecondary().isEmpty()) {
                count++;
            }
            return count;
        }
        return partitionEntryCount(bus.partition());
    }

    @Nullable
    private static AEKeyType cellKeyType(CellSlotEntry slot) {
        if (slot.cellStack().getItem() instanceof IBasicCellItem cell) {
            return cell.getKeyType();
        }
        for (CellTerminalClientState.ContentEntry entry : slot.content()) {
            return entry.stack().what().getType();
        }
        return null;
    }

    private static boolean supportsAutoPartition(CellSlotEntry slot) {
        ItemStack stack = slot.cellStack();
        return !stack.isEmpty()
            && stack.getItem() instanceof ICellWorkbenchItem workbenchItem
            && workbenchItem.supportsAutoPartition(stack);
    }

    private static void appendSubnetIdentity(List<String> lines, CellTerminalClientState.SubnetEntry subnet) {
        String visibleName = subnet.visibleName().getFormattedText();
        String defaultName = subnet.displayName().getFormattedText();
        String subnetId = subnet.subnetId();
        if (!visibleName.isEmpty()) {
            lines.add("");
            lines.add(visibleName);
        }
        if (!defaultName.isEmpty() && !Objects.equals(defaultName, visibleName)) {
            lines.add("§7" + defaultName);
        }
        if (!subnetId.isEmpty() && !Objects.equals(subnetId, defaultName) && !Objects.equals(subnetId, visibleName)) {
            lines.add("§7" + subnetId);
        }
    }

    private static boolean hasToolPreview(@Nullable CellTerminalClientState.ToolPreview preview,
                                          CellTerminalNetworkToolOperation op) {
        return preview != null && preview.operation() == op;
    }

    private static boolean canExecuteToolPreview(@Nullable CellTerminalClientState.ToolPreview preview,
                                                 CellTerminalNetworkToolOperation op) {
        if (!hasToolPreview(preview, op) || preview.plans().isEmpty()) {
            return false;
        }
        return switch (op) {
            case UNIQUE_TYPE_REALLOCATION ->
                preview.uniqueTypeSummary() != null && preview.uniqueTypeSummary().uniqueTypeCount() > 0;
            case PARTITION_CELLS_BY_CONTENT, PARTITION_STORAGE_BUSES_BY_CONTENT -> toolTargetCount(preview) > 0;
        };
    }

    private static int partitionEntryCount(List<@Nullable GenericStack> partition) {
        int count = 0;
        for (GenericStack stack : partition) {
            if (stack != null) {
                count++;
            }
        }
        return count;
    }

    private static int toolTargetCount(CellTerminalClientState.ToolPreview preview) {
        int total = 0;
        for (var breakdown : preview.targetBreakdown()) {
            total += breakdown.count();
        }
        return total;
    }

    private static boolean uniqueTypeHasCapacityError(CellTerminalClientState.ToolPreview preview) {
        var summary = preview.uniqueTypeSummary();
        if (summary == null) {
            return false;
        }
        for (var breakdown : summary.breakdown()) {
            if (breakdown.uniqueTypeCount() > breakdown.availableCellCount()) {
                return true;
            }
        }
        return false;
    }

    private static String localizedToolFailure(CellTerminalClientState.ToolFailureEntry failure) {
        if (failure.messageArgs().isEmpty()) {
            return translate(failure.message());
        }
        Object[] args = new Object[failure.messageArgs().size()];
        for (int index = 0; index < failure.messageArgs().size(); index++) {
            String arg = failure.messageArgs().get(index);
            args[index] = new TextComponentTranslation(arg);
        }
        return translate(failure.message(), args);
    }

    private static String stripFormatting(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean formatting = false;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (formatting) {
                formatting = false;
                continue;
            }
            if (c == '§') {
                formatting = true;
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private static boolean isSlotPartitionBus(CellTerminalClientState.BusEntry bus) {
        return bus.partitionMode() == CellTerminalBusPartitionMode.SLOTS
            || bus.partitionMode() == CellTerminalBusPartitionMode.PRECISE_SLOTS;
    }

    private static String toolCellSelectionKey(StorageEntry storage, CellSlotEntry slot) {
        return storage.stableTargetId() + "|" + storage.locator() + "|" + slot.slotIndex();
    }

    private static boolean isEmptyNonPartitionedCellCandidate(CellSlotEntry slot) {
        if (slot.contentEntryCount() != 0) {
            return false;
        }
        for (var stack : slot.partition()) {
            if (stack != null) {
                return false;
            }
        }
        return true;
    }

    private static int tabLocalX(int index) {
        return 4 + index * (TAB_WIDTH + 2);
    }

    private static int tabLocalY() {
        return TAB_Y_OFFSET;
    }

    private static int tabScreenX(int offsetX, int index) {
        return offsetX + tabLocalX(index);
    }

    private static int tabScreenY(int offsetY) {
        return offsetY + tabLocalY();
    }

    private static boolean isInTab(int x, int y, int tabX, int tabY) {
        return x >= tabX && x < tabX + TAB_WIDTH && y >= tabY && y < tabY + TAB_HEIGHT;
    }

    private static void drawControlsHelpBackground(Rectangle bounds) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int border = Math.min(HELP_TEXTURE_BORDER, Math.min(bounds.width, bounds.height) / 2);
        int right = bounds.x + bounds.width;
        int bottom = bounds.y + bounds.height;
        int centerSrcSize = HELP_TEXTURE_SIZE - 2 * HELP_TEXTURE_BORDER;
        int centerWidth = Math.max(0, bounds.width - 2 * border);
        int centerHeight = Math.max(0, bounds.height - 2 * border);

        CONTROLS_HELP_BACKGROUND.copy().src(0, 0, border, border)
                                .dest(bounds.x, bounds.y, border, border).blit();
        CONTROLS_HELP_BACKGROUND.copy().src(HELP_TEXTURE_SIZE - border, 0, border, border)
                                .dest(right - border, bounds.y, border, border).blit();
        CONTROLS_HELP_BACKGROUND.copy().src(0, HELP_TEXTURE_SIZE - border, border, border)
                                .dest(bounds.x, bottom - border, border, border).blit();
        CONTROLS_HELP_BACKGROUND.copy()
                                .src(HELP_TEXTURE_SIZE - border, HELP_TEXTURE_SIZE - border, border, border)
                                .dest(right - border, bottom - border, border, border).blit();

        if (centerWidth > 0) {
            CONTROLS_HELP_BACKGROUND.copy().src(border, 0, centerSrcSize, border)
                                    .dest(bounds.x + border, bounds.y, centerWidth, border).blit();
            CONTROLS_HELP_BACKGROUND.copy()
                                    .src(border, HELP_TEXTURE_SIZE - border, centerSrcSize, border)
                                    .dest(bounds.x + border, bottom - border, centerWidth, border).blit();
        }
        if (centerHeight > 0) {
            CONTROLS_HELP_BACKGROUND.copy().src(0, border, border, centerSrcSize)
                                    .dest(bounds.x, bounds.y + border, border, centerHeight).blit();
            CONTROLS_HELP_BACKGROUND.copy()
                                    .src(HELP_TEXTURE_SIZE - border, border, border, centerSrcSize)
                                    .dest(right - border, bounds.y + border, border, centerHeight).blit();
        }
        if (centerWidth > 0 && centerHeight > 0) {
            CONTROLS_HELP_BACKGROUND.copy().src(border, border, centerSrcSize, centerSrcSize)
                                    .dest(bounds.x + border, bounds.y + border, centerWidth, centerHeight).blit();
        }
    }

    private static CellTerminalClientState.UniqueTypeSummary requireUniqueTypeSummary(
        @Nullable CellTerminalClientState.ToolPreview preview) {
        if (preview == null || preview.uniqueTypeSummary() == null) {
            throw new IllegalStateException("Cell Terminal unique type confirmation requires preview summary");
        }
        return preview.uniqueTypeSummary();
    }

    private static int requireTargetBreakdownTotal(@Nullable CellTerminalClientState.ToolPreview preview) {
        if (preview == null || preview.targetBreakdown().isEmpty()) {
            throw new IllegalStateException("Cell Terminal mass partition confirmation requires target breakdown");
        }
        return toolTargetCount(preview);
    }

    private static void drawToolConfirmPanelBackground(int x, int y, int width, int height) {
        drawControlsHelpBackground(new Rectangle(x, y, width, height));
    }

    private static boolean isInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static boolean isPartitionGhostTargetRow(Object data) {
        if (data instanceof CellSlotsRowData row) {
            return row.partition();
        }
        if (data instanceof BusRowData row) {
            return row.partition();
        }
        if (data instanceof TempSlotsRowData row) {
            return row.partition();
        }
        if (data instanceof SubnetConnectionContentRowData row) {
            return row.partition();
        }
        return false;
    }

    static boolean slotGridClickTakesPriority(UpgradeGridPriorityRowKind rowKind) {
        return rowKind != UpgradeGridPriorityRowKind.OTHER;
    }

    private static UpgradeGridPriorityRowKind getUpgradeGridPriorityRowKind(Object data) {
        if (data instanceof CellSlotsRowData) {
            return UpgradeGridPriorityRowKind.CELL_SLOTS;
        }
        if (data instanceof TempSlotsRowData) {
            return UpgradeGridPriorityRowKind.TEMP_SLOTS;
        }
        if (data instanceof BusRowData) {
            return UpgradeGridPriorityRowKind.BUS_SLOTS;
        }
        return UpgradeGridPriorityRowKind.OTHER;
    }

    private static boolean isSupportedUpgradeInsertStack(ItemStack stack) {
        return !stack.isEmpty() && Upgrades.isUpgradeCardItem(stack);
    }

    private static Set<AEKeyType> nextKeyTypeSelection(
        IKeyTypeSelectionContainer.SyncedKeyTypes keyTypes) {
        int totalCount = keyTypes.keyTypes().size();
        int enabledCount = keyTypes.enabledSet().size();
        if (totalCount == enabledCount) {
            return Collections.singleton(keyTypes.keyTypes().keySet().iterator().next());
        }
        if (enabledCount > 1) {
            return new ObjectOpenHashSet<>(keyTypes.keyTypes().keySet());
        }
        AEKeyType current = keyTypes.enabledSet().getFirst();
        boolean found = false;
        for (AEKeyType keyType : keyTypes.keyTypes().keySet()) {
            if (found) {
                return Collections.singleton(keyType);
            }
            if (keyType == current) {
                found = true;
            }
        }
        return new ObjectOpenHashSet<>(keyTypes.keyTypes().keySet());
    }

    private static List<GenericStack> visibleRowStacks(List<GenericStack> stacks, int startIndex) {
        if (startIndex >= stacks.size()) {
            return List.of();
        }
        int endIndex = Math.min(stacks.size(), startIndex + CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW);
        return stacks.subList(startIndex, endIndex);
    }

    private static List<String> genericStackSearchValues(List<GenericStack> stacks) {
        List<String> values = new ObjectArrayList<>();
        for (GenericStack stack : stacks) {
            appendGenericStackSearchValues(values, stack);
        }
        return values;
    }

    private static int countNonNullStacks(List<GenericStack> stacks) {
        int count = 0;
        for (GenericStack stack : stacks) {
            if (stack != null) {
                count++;
            }
        }
        return count;
    }

    private void addWirelessUniversalTerminalButton() {
        if (!(this.container.getItemGuiHost() instanceof WirelessTerminalGuiHost<?> wirelessHost)) {
            return;
        }
        ItemStack stack = wirelessHost.getItemStack();
        if (!(stack.getItem() instanceof WirelessUniversalTerminalItem)) {
            return;
        }
        WirelessUniversalTerminalSelectorWindow selector = new WirelessUniversalTerminalSelectorWindow(this);
        this.widgets.add("wirelessUniversalTerminalSelector", selector);
        this.addToLeftToolbar(new ItemStackButton(
            () -> wirelessHost.getTerminalItem().getWirelessTerminalDefinition().icon(wirelessHost.getTerminalItem()),
            WirelessTerminalSelector.text(),
            selector::toggle));
    }

    private List<GenericStack> cellContentFor(StorageEntry storage, CellSlotEntry slot) {
        List<GenericStack> base = toContentList(slot.content());
        if (!slot.contentTruncated() || AEConfig.instance().getCellTerminalSlotLimit().limit() >= 0) {
            return base;
        }
        String key = storage.stableTargetId() + "|" + storage.locator() + "|" + slot.slotIndex()
            + "|" + slot.contentRevision();
        return mergedPageContent(key, base, slot.contentEntryCount(),
            first -> this.container.requestCellContentPageFromClient(storage, slot, first));
    }

    private List<GenericStack> busContentFor(CellTerminalClientState.BusEntry bus) {
        List<GenericStack> base = toContentList(bus.content());
        if (!bus.contentTruncated() || AEConfig.instance().getCellTerminalSlotLimit().limit() >= 0) {
            return base;
        }
        String key = bus.stableTargetId() + "|" + bus.locator() + "|-1|" + bus.contentRevision();
        return mergedPageContent(key, base, bus.contentEntryCount(),
            first -> this.container.requestBusContentPageFromClient(bus, first));
    }

    private List<GenericStack> mergedPageContent(String key, List<GenericStack> base, int totalEntries,
                                                 IntConsumer requestPage) {
        List<GenericStack> accumulated = this.contentPageCache.get(key);
        int loaded = accumulated == null ? 0 : accumulated.size();
        if (loaded < totalEntries && this.pendingContentPageKey == null) {
            this.pendingContentPageKey = key;
            this.pendingContentPageFirst = loaded;
            requestPage.accept(loaded);
        }
        if (accumulated == null || accumulated.size() <= base.size()) {
            return base;
        }
        return accumulated;
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        List<GuiTextField> fields = new ObjectArrayList<>();
        fields.add(this.searchField);
        if (this.searchOverlay != null && this.searchOverlay.isVisible()) {
            fields.add(this.searchOverlay.getField());
        }
        fields.addAll(this.busTextPartitionFields.values());
        return fields;
    }

    private int getConfiguredRows() {
        int margin = AEConfig.instance().getTerminalMargin();
        int floorHeight = HEADER_BACKGROUND_HEIGHT + FOOTER_HEIGHT + MIN_ROWS * ROW_HEIGHT;
        int availableHeight = Math.max(this.height, floorHeight + TOP_TAB_OUTSET) - 2 * margin - TOP_TAB_OUTSET;
        int possibleRows = Math.clamp((availableHeight - HEADER_BACKGROUND_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT,
            MIN_ROWS, MAX_ROWS);
        int styleRows = AEConfig.instance().getTerminalStyle().getRows(possibleRows);
        return Math.clamp(styleRows, MIN_ROWS, possibleRows);
    }

    private int configuredRows() {
        return this.visibleRows;
    }

    private int configuredHeight() {
        return HEADER_BACKGROUND_HEIGHT + FOOTER_HEIGHT + this.visibleRows * ROW_HEIGHT;
    }

    @Override
    protected int getGuiVisualTopOutset() {
        return TOP_TAB_OUTSET;
    }

    private void toggleTerminalStyle(SettingToggleButton<TerminalStyle> button, boolean backwards) {
        TerminalStyle next = button.getNextValue(backwards);
        button.set(next);
        AEConfig.instance().setTerminalStyle(next);
        this.setWorldAndResolution(this.mc, this.width, this.height);
    }

    private <T extends Enum<T>> void cycleSetting(SettingToggleButton<T> button, boolean backwards,
                                                  Consumer<T> persist) {
        T next = button.getNextValue(backwards);
        button.set(next);
        persist.accept(next);
        requestRebuild();
    }

    private void cycleSubnetVisibility(SettingToggleButton<CellTerminalSubnetVisibility> button, boolean backwards) {
        cycleSetting(button, backwards, AEConfig.instance()::setCellTerminalSubnetVisibility);
        this.scrollbar.setCurrentScroll(0);
    }

    private void requestRebuild() {
        this.lastCacheRevision = Long.MIN_VALUE;
    }

    private IWidget createRowWidget(Object data, int y, List<?> all, int index) {
        if (data instanceof StorageHeaderData header) {
            return buildStorageHeader(header, y);
        }
        if (data instanceof CellLineData cell) {
            return buildTerminalLine(cell, y);
        }
        if (data instanceof TempCellRowData temp) {
            return buildTempAreaHeader(temp, y);
        }
        if (data instanceof TempSlotsRowData row) {
            return buildTempContentLine(row, y);
        }
        if (data instanceof CellSlotsRowData row) {
            return buildCellSlotsLine(row, y);
        }
        if (data instanceof BusHeaderData header) {
            return buildBusHeader(header, y);
        }
        if (data instanceof BusRowData row) {
            return buildBusLine(row, y);
        }
        if (data instanceof BusTextPartitionRowData row) {
            return buildBusTextPartitionLine(row, y);
        }
        if (data instanceof SubnetRowData row) {
            return buildSubnetHeader(row, y);
        }
        if (data instanceof SubnetConnectionRowData row) {
            return buildSubnetConnectionHeader(row, y);
        }
        if (data instanceof SubnetConnectionContentRowData row) {
            return buildSubnetConnectionLine(row, y);
        }
        if (data instanceof ToolRowData row) {
            return buildToolRow(row, y);
        }
        return null;
    }

    private boolean isContentLine(List<?> all, int index) {
        return treeLineInfo(all, index) != null;
    }

    private boolean drawsHorizontalJunction(List<?> all, int index) {
        CellTerminalRowList.TreeLineInfo info = treeLineInfo(all, index);
        return info != null && info.drawsHorizontalJunction();
    }

    private CellTerminalRowList.TreeLineInfo treeLineInfo(List<?> all, int index) {
        if (index < 0 || index >= all.size()) {
            return null;
        }
        Object data = all.get(index);
        if (data instanceof CellLineData row) {
            String sectionKey = storageSectionKey(row.storage());
            return new CellTerminalRowList.TreeLineInfo(
                sectionKey,
                CellTerminalRowList.BranchKind.SINGLE,
                true,
                true,
                hasLaterSameSectionBranch(all, index, sectionKey, CellTerminalRowList.BranchKind.SINGLE),
                false);
        }
        if (data instanceof CellSlotsRowData row) {
            String sectionKey = storageSectionKey(row.storage());
            boolean hasLaterCell = hasLaterCellSlotNode(all, index, sectionKey, row.slot());
            return new CellTerminalRowList.TreeLineInfo(
                sectionKey,
                CellTerminalRowList.BranchKind.SINGLE,
                row.first(),
                row.first() || hasLaterCell,
                hasLaterCell,
                false);
        }
        if (data instanceof TempSlotsRowData row) {
            String sectionKey = tempCellSectionKey(row.storage(), row.slot());
            CellTerminalRowList.BranchKind branchKind = branchKind(row.partition());
            boolean hasLaterBranchRow = hasLaterSameSectionBranch(all, index, sectionKey, branchKind);
            return new CellTerminalRowList.TreeLineInfo(
                sectionKey,
                branchKind,
                row.first(),
                row.first() || hasLaterBranchRow,
                hasLaterBranchRow,
                row.partition() && row.first() && hasEarlierContentBranch(all, index, sectionKey));
        }
        if (data instanceof BusRowData row) {
            String sectionKey = busSectionKey(row.bus());
            return new CellTerminalRowList.TreeLineInfo(
                sectionKey,
                CellTerminalRowList.BranchKind.SINGLE,
                true,
                true,
                hasLaterBusRow(all, index, sectionKey),
                false);
        }
        if (data instanceof BusTextPartitionRowData row) {
            String sectionKey = busSectionKey(row.bus());
            return new CellTerminalRowList.TreeLineInfo(
                sectionKey,
                CellTerminalRowList.BranchKind.SINGLE,
                true,
                true,
                hasLaterBusTextPartitionRow(all, index, sectionKey),
                false);
        }
        if (data instanceof SubnetConnectionContentRowData row) {
            String sectionKey = subnetConnectionSectionKey(row.subnet(), row.connection(), row.connectionIndex());
            CellTerminalRowList.BranchKind branchKind = branchKind(row.partition());
            boolean hasLaterBranchRow = hasLaterSameSectionBranch(all, index, sectionKey, branchKind);
            boolean inheritsContentBranchAbove = row.partition() && row.first()
                && hasEarlierContentBranch(all, index, sectionKey);
            return new CellTerminalRowList.TreeLineInfo(
                sectionKey,
                branchKind,
                row.first(),
                row.first() || hasLaterBranchRow,
                hasLaterBranchRow,
                false,
                inheritsContentBranchAbove ? CellTerminalRowList.BranchKind.CONTENT : null);
        }
        return null;
    }

    private CellTerminalRowList.TreeLineInfo headerConnectorInfo(List<?> all, int index) {
        if (index < 0 || index + 1 >= all.size()) {
            return null;
        }
        String headerSectionKey = headerSectionKey(all.get(index));
        if (headerSectionKey == null) {
            return null;
        }
        CellTerminalRowList.TreeLineInfo nextInfo = treeLineInfo(all, index + 1);
        if (nextInfo == null || !headerSectionKey.equals(nextInfo.sectionKey())) {
            return null;
        }
        return nextInfo;
    }

    @Override
    public void initGui() {
        this.visibleRows = getConfiguredRows();
        this.xSize = WIDTH;
        this.ySize = configuredHeight();
        super.initGui();
        this.rowList.setRowsVisible(this.visibleRows);
        this.scrollbar.setHeight(this.visibleRows * ROW_HEIGHT);
        rebuildIfNeeded();
        if (AEConfig.instance().isAutoFocusSearch()) {
            setInitialFocus(this.searchField);
        }
    }

    @Override
    public void onGuiClosed() {
        if (this.searchOverlay != null) {
            this.searchOverlay.close();
        }
        super.onGuiClosed();
        PriorityFieldManager.getInstance().unfocusAll();
        InlineRenameManager.getInstance().cancelEditing();
        DoubleClickTracker.reset();
        this.selectedBuses.clear();
        this.selectedTempCells.clear();
        this.contentPageCache.clear();
        this.pendingContentPageKey = null;
        this.pendingContentPageFirst = -1;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        CellTerminalClientState state = this.container.getState();
        if (this.lastTab != state.tab()) {
            this.lastTab = state.tab();
            this.scrollbar.setCurrentScroll(0);
            this.selectedTempCells.clear();
            requestRebuild();
        }
        consumeContentPage(state);
        rebuildIfNeeded();
        ensureSearchOverlay();
        if (this.searchOverlay != null) {
            this.searchOverlay.tickKeyRepeat();
        }
        for (IWidget widget : this.rowList.getVisibleRows()) {
            if (widget instanceof BusTextPartitionLine line) {
                line.tickKeyRepeat();
            }
        }
        InlineRenameManager.getInstance().updateContext(getStyle(), this.fontRenderer, this.guiLeft, this.guiTop);
        InlineRenameManager.getInstance().tickKeyRepeat();
    }

    private void ensureSearchOverlay() {
        if (this.searchOverlay == null && this.fontRenderer != null && getStyle() != null) {
            this.searchOverlay = new CellTerminalSearchOverlay(this.fontRenderer, this.searchField);
        }
        if (this.searchOverlay != null) {
            this.searchOverlay.setGuiBounds(getBounds(true));
        }
    }

    private void consumeContentPage(CellTerminalClientState state) {
        CellTerminalClientState.ContentPage page = state.contentPage();
        if (page == null) {
            return;
        }
        String key = page.stableTargetId() + "|" + page.locator() + "|" + page.slotIndex()
            + "|" + page.contentRevision();
        List<GenericStack> accumulated = this.contentPageCache.computeIfAbsent(key, k -> new ObjectArrayList<>());
        if (page.firstIndex() == accumulated.size()) {
            for (CellTerminalClientState.ContentEntry entry : page.content()) {
                accumulated.add(entry.stack());
            }
            requestRebuild();
        }
        if (this.pendingContentPageKey != null && this.pendingContentPageKey.equals(key)
            && accumulated.size() > this.pendingContentPageFirst) {
            this.pendingContentPageKey = null;
            this.pendingContentPageFirst = -1;
        }
    }

    private void rebuildIfNeeded() {
        CellTerminalClientState state = this.container.getState();
        String searchKey = this.searchText.toLowerCase(Locale.ROOT) + "|"
            + AEConfig.instance().getCellTerminalSearchMode() + "|"
            + AEConfig.instance().getCellTerminalContentFilter() + "|"
            + AEConfig.instance().getCellTerminalPartitionFilter() + "|"
            + AEConfig.instance().getCellTerminalSubnetVisibility() + "|"
            + AEConfig.instance().getCellTerminalSlotLimit() + "|"
            + this.container.getClientKeyTypeSelection().enabledSet();
        if (this.lastCacheRevision == state.cacheRevision()
            && this.lastTab == state.tab()
            && this.lastContextId.equals(state.contextId())
            && this.lastSearchText.equals(searchKey)) {
            return;
        }
        this.lastCacheRevision = state.cacheRevision();
        this.lastTab = state.tab();
        this.lastContextId = state.contextId();
        this.lastSearchText = searchKey;
        rebuildLines(state);
    }

    private void rebuildLines(CellTerminalClientState state) {
        this.lineData.clear();
        this.searchQuery = CellTerminalSearchQuery.parse(this.searchText);
        updateSearchFieldTextColor();
        switch (state.tab()) {
            case OVERVIEW -> {
                for (StorageEntry storage : state.storages()) {
                    addOverviewStorageRows(storage);
                }
            }
            case TEMP_CELLS -> {
                for (StorageEntry storage : state.storages()) {
                    if (!isTempStorage(storage)) {
                        continue;
                    }
                    for (CellSlotEntry slot : storage.cellSlots()) {
                        this.lineData.add(new TempCellRowData(storage, slot));
                        if (slot.cellStack().isEmpty()) {
                            continue;
                        }
                        List<GenericStack> stacks = applySlotLimit(toContentList(slot.content()));
                        int contentRows = Math.max(1, (stacks.size() + TEMP_AREA_SLOTS_PER_ROW - 1)
                            / TEMP_AREA_SLOTS_PER_ROW);
                        for (int r = 0; r < contentRows; r++) {
                            int start = r * TEMP_AREA_SLOTS_PER_ROW;
                            this.lineData.add(new TempSlotsRowData(storage, slot, stacks, false, start, r == 0));
                        }
                        List<GenericStack> partitionStacks = toStackList(slot.partition());
                        int partitionCount = visiblePartitionSlotCount(partitionStacks);
                        int partitionRows = Math.max(1,
                            (partitionCount + TEMP_AREA_SLOTS_PER_ROW - 1) / TEMP_AREA_SLOTS_PER_ROW);
                        for (int r = 0; r < partitionRows; r++) {
                            int start = r * TEMP_AREA_SLOTS_PER_ROW;
                            this.lineData.add(new TempSlotsRowData(storage, slot, partitionStacks, true, start,
                                r == 0));
                        }
                    }
                }
            }
            case CELL_CONTENT, CELL_PARTITION -> {
                boolean partition = state.tab() == CellTerminalTab.CELL_PARTITION;
                for (StorageEntry storage : state.storages()) {
                    addCellStorageRows(storage, partition);
                }
            }
            case BUS_CONTENT, BUS_PARTITION -> {
                boolean partition = state.tab() == CellTerminalTab.BUS_PARTITION;
                for (var bus : state.buses()) {
                    BusVisibility visibility = collectBusVisibility(bus, partition);
                    if (!visibility.showHeader()) {
                        continue;
                    }
                    this.lineData.add(new BusHeaderData(bus, visibility.forceExpanded()));
                    if (!isBusExpanded(bus, visibility.forceExpanded())) {
                        continue;
                    }
                    if (partition && isTextBusPartition(bus)) {
                        List<BusTextPartitionRowData> textRows = visibility.visibleTextRows();
                        if (isSearching() && visibility.forceExpanded()) {
                            this.lineData.addAll(textRows);
                        } else {
                            this.lineData.addAll(buildBusTextPartitionRows(bus));
                        }
                        continue;
                    }
                    List<GenericStack> stacks = partition ? toStackList(bus.partition())
                        : applySlotLimit(busContentFor(bus));
                    int shown = partition ? bus.partitionSlotCapacity() : stacks.size();
                    int rows = Math.max(1, (shown + CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW - 1)
                        / CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW);
                    for (int r = 0; r < rows; r++) {
                        int start = r * CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW;
                        BusRowData row = new BusRowData(bus, stacks, partition, start, r == 0);
                        if (visibility.headerMatched() || !isSearching() || busRowMatches(row)) {
                            this.lineData.add(row);
                        }
                    }
                }
            }
            case SUBNETS -> {
                for (var subnet : state.subnets()) {
                    if (!subnet.mainNetwork() && !passesSubnetVisibility(subnet)) {
                        continue;
                    }
                    if (subnet.mainNetwork()) {
                        if (subnetMatches(subnet)) {
                            this.lineData.add(new SubnetRowData(subnet));
                        }
                        continue;
                    }
                    boolean subnetMatched = subnetMatches(subnet);
                    List<CellTerminalClientState.ConnectionEntry> connections = subnet.connections();
                    if (connections.isEmpty()) {
                        if (subnetMatched) {
                            this.lineData.add(new SubnetRowData(subnet));
                        }
                        continue;
                    }
                    for (int connIdx = 0; connIdx < connections.size(); connIdx++) {
                        CellTerminalClientState.ConnectionEntry conn = connections.get(connIdx);
                        if (!subnetMatched && !subnetConnectionMatches(subnet, conn)) {
                            continue;
                        }
                        this.lineData.add(new SubnetConnectionRowData(subnet, conn, connIdx));
                        addSubnetConnectionContentRows(subnet, conn, connIdx, false, subnetMatched);
                        addSubnetConnectionContentRows(subnet, conn, connIdx, true, subnetMatched);
                    }
                }
            }
            case NETWORK_TOOLS -> {
                for (CellTerminalNetworkToolOperation op : CellTerminalNetworkToolOperation.values()) {
                    this.lineData.add(new ToolRowData(op));
                }
            }
        }
        this.scrollbar.setRange(0, maxScrollOffset(), configuredRows());
    }

    private void addOverviewStorageRows(StorageEntry storage) {
        if (isTempStorage(storage)) {
            return;
        }
        StorageVisibility visibility = collectStorageVisibility(storage, true);
        if (!visibility.showHeader()) {
            return;
        }
        this.lineData.add(new StorageHeaderData(storage, visibility.forceExpanded()));
        if (!isStorageExpanded(storage, visibility.forceExpanded())) {
            return;
        }
        for (CellSlotEntry slot : visibility.visibleSlots()) {
            this.lineData.add(new CellLineData(storage, slot));
        }
    }

    private void addCellStorageRows(StorageEntry storage, boolean partition) {
        if (isTempStorage(storage)) {
            return;
        }
        StorageVisibility visibility = collectStorageVisibility(storage, false);
        if (!visibility.showHeader()) {
            return;
        }
        this.lineData.add(new StorageHeaderData(storage, visibility.forceExpanded()));
        if (!isStorageExpanded(storage, visibility.forceExpanded())) {
            return;
        }
        for (CellSlotEntry slot : visibility.visibleSlots()) {
            addCellSlotRows(storage, slot, partition);
        }
    }

    private StorageVisibility collectStorageVisibility(StorageEntry storage, boolean requireCellItem) {
        List<CellSlotEntry> filteredSlots = collectFilteredCellSlots(storage, requireCellItem);
        if (!isSearching()) {
            return new StorageVisibility(true, false, filteredSlots);
        }
        boolean headerMatched = storageHeaderMatches(storage);
        List<CellSlotEntry> matchedSlots = collectChildMatchedCellSlots(storage, filteredSlots);
        return new StorageVisibility(headerMatched, !matchedSlots.isEmpty(), headerMatched ? filteredSlots : matchedSlots);
    }

    private List<CellSlotEntry> collectFilteredCellSlots(StorageEntry storage, boolean requireCellItem) {
        List<CellSlotEntry> visibleSlots = new ObjectArrayList<>();
        for (CellSlotEntry slot : storage.cellSlots()) {
            if (requireCellItem && slot.cellStack().isEmpty()) {
                continue;
            }
            if (!passesCellFilters(slot)) {
                continue;
            }
            visibleSlots.add(slot);
        }
        return visibleSlots;
    }

    private List<CellSlotEntry> collectChildMatchedCellSlots(StorageEntry storage, List<CellSlotEntry> filteredSlots) {
        List<CellSlotEntry> matchedSlots = new ObjectArrayList<>();
        for (CellSlotEntry slot : filteredSlots) {
            if (cellSlotChildMatches(storage, slot)) {
                matchedSlots.add(slot);
            }
        }
        return matchedSlots;
    }

    private boolean isStorageExpanded(StorageEntry storage, boolean forceExpanded) {
        return forceExpanded || this.expandedStorages.contains(storageKey(storage));
    }

    private boolean isBusExpanded(CellTerminalClientState.BusEntry bus, boolean forceExpanded) {
        return forceExpanded || this.expandedBuses.contains(busKey(bus));
    }

    private boolean isSearching() {
        return !this.searchQuery.isEmpty();
    }

    private BusVisibility collectBusVisibility(CellTerminalClientState.BusEntry bus, boolean partitionTab) {
        if (!isSearching()) {
            return new BusVisibility(true, false, buildBusTextPartitionRows(bus));
        }
        boolean headerMatched = busHeaderMatches(bus);
        if (partitionTab && isTextBusPartition(bus)) {
            List<BusTextPartitionRowData> matchedRows = collectMatchingBusTextPartitionRows(bus);
            return new BusVisibility(headerMatched, !matchedRows.isEmpty(), matchedRows);
        }
        return new BusVisibility(headerMatched, busChildMatches(bus), List.of());
    }

    private List<BusTextPartitionRowData> buildBusTextPartitionRows(CellTerminalClientState.BusEntry bus) {
        List<BusTextPartitionRowData> rows = new ObjectArrayList<>();
        if (bus.partitionMode() == CellTerminalBusPartitionMode.ORE_DICTIONARY_EXPRESSIONS) {
            rows.add(new BusTextPartitionRowData(bus, "odWhite",
                tr("busTextPartition.odWhite"), bus.textPartitionPrimary(),
                ODFilterWhiteTooltip.getLocal()));
            rows.add(new BusTextPartitionRowData(bus, "odBlack",
                tr("busTextPartition.odBlack"), bus.textPartitionSecondary(),
                ODFilterBlackTooltip.getLocal()));
        } else if (bus.partitionMode() == CellTerminalBusPartitionMode.MOD_EXPRESSION) {
            rows.add(new BusTextPartitionRowData(bus, "mod",
                tr("busTextPartition.mod"), bus.textPartitionPrimary(),
                ModFilterTooltip.getLocal()));
        }
        return rows;
    }

    private List<BusTextPartitionRowData> collectMatchingBusTextPartitionRows(CellTerminalClientState.BusEntry bus) {
        List<BusTextPartitionRowData> matchedRows = new ObjectArrayList<>();
        for (BusTextPartitionRowData row : buildBusTextPartitionRows(bus)) {
            if (busTextPartitionRowMatches(row)) {
                matchedRows.add(row);
            }
        }
        return matchedRows;
    }

    private void addCellSlotRows(StorageEntry storage, CellSlotEntry slot, boolean partition) {
        List<GenericStack> stacks = partition ? toStackList(slot.partition())
            : applySlotLimit(cellContentFor(storage, slot));
        int count = partition
            ? (slot.totalTypes() > 0 ? slot.totalTypes() : CellTerminalLayout.CELL_SLOTS_PER_ROW)
            : stacks.size();
        int rows = Math.max(1, (count + CellTerminalLayout.CELL_SLOTS_PER_ROW - 1)
            / CellTerminalLayout.CELL_SLOTS_PER_ROW);
        if (slot.cellStack().isEmpty()) {
            rows = 1;
        }
        for (int r = 0; r < rows; r++) {
            int start = r * CellTerminalLayout.CELL_SLOTS_PER_ROW;
            this.lineData.add(new CellSlotsRowData(storage, slot, stacks, partition, start, r == 0));
        }
    }

    private int maxScrollOffset() {
        int visibleHeight = configuredRows() * ROW_HEIGHT;
        int totalHeight = 0;
        for (Object line : this.lineData) {
            totalHeight += rowHeightForData(line);
        }
        int hiddenHeight = Math.max(0, totalHeight - visibleHeight);
        return (hiddenHeight + ROW_HEIGHT - 1) / ROW_HEIGHT;
    }

    private void addSubnetConnectionContentRows(CellTerminalClientState.SubnetEntry subnet,
                                                CellTerminalClientState.ConnectionEntry conn, int connIdx,
                                                boolean partition,
                                                boolean parentMatched) {
        List<GenericStack> stacks = partition ? toStackList(conn.partition())
            : applySlotLimit(toContentList(conn.content()));
        if (!partition && stacks.isEmpty()) {
            return;
        }
        int shown = partition
            ? Math.clamp(conn.partitionSlotCapacity(), CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW,
            CellTerminalLayout.MAX_STORAGE_BUS_PARTITION_SLOTS)
            : stacks.size();
        int rows = Math.max(1, (shown + CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW - 1)
            / CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW);
        for (int r = 0; r < rows; r++) {
            int start = r * CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW;
            SubnetConnectionContentRowData row = new SubnetConnectionContentRowData(subnet, conn, connIdx, stacks,
                partition, start, r == 0);
            if (parentMatched || !isSearching() || subnetConnectionContentRowMatches(row)) {
                this.lineData.add(row);
            }
        }
    }

    private boolean storageHeaderMatches(StorageEntry storage) {
        return matchesSearch(field -> !"dir".equals(field), field -> storageHeaderSearchValues(storage, field));
    }

    private boolean cellSlotChildMatches(StorageEntry storage, CellSlotEntry slot) {
        return matchesSearch(field -> !"dir".equals(field), field -> cellSlotChildSearchValues(storage, slot, field));
    }

    private boolean busHeaderMatches(CellTerminalClientState.BusEntry bus) {
        return matchesSearch(field -> !"dir".equals(field), field -> busHeaderSearchValues(bus, field));
    }

    private boolean busChildMatches(CellTerminalClientState.BusEntry bus) {
        return matchesSearch(field -> !"dir".equals(field), field -> busChildSearchValues(bus, field));
    }

    private boolean busTextPartitionRowMatches(BusTextPartitionRowData row) {
        return matchesSearch(field -> !"dir".equals(field), field -> busTextPartitionSearchValues(row, field));
    }

    private boolean busRowMatches(BusRowData row) {
        return matchesSearch(field -> !"dir".equals(field), field -> busRowSearchValues(row, field));
    }

    private boolean subnetConnectionContentRowMatches(SubnetConnectionContentRowData row) {
        return matchesSearch(field -> !"priority".equals(field),
            field -> subnetConnectionContentRowSearchValues(row, field));
    }

    private boolean matchesSearch(Predicate<String> supportsField, Function<String, List<String>> getValues) {
        if (!isSearching()) {
            return true;
        }
        CellTerminalSearchMode mode = AEConfig.instance().getCellTerminalSearchMode();
        return this.searchQuery.matches(new CellTerminalSearchQuery.SearchSource() {
            @Override
            public CellTerminalSearchMode mode() {
                return mode;
            }

            @Override
            public boolean supportsField(String field) {
                return supportsField.test(field);
            }

            @Override
            public List<String> getValues(String field) {
                return getValues.apply(field);
            }
        });
    }

    private boolean subnetMatches(CellTerminalClientState.SubnetEntry subnet) {
        if (this.searchQuery.isEmpty()) {
            return true;
        }
        CellTerminalSearchMode mode = AEConfig.instance().getCellTerminalSearchMode();
        return this.searchQuery.matches(new CellTerminalSearchQuery.SearchSource() {
            @Override
            public CellTerminalSearchMode mode() {
                return mode;
            }

            @Override
            public boolean supportsField(String field) {
                return switch (field) {
                    case "dir", "content", "items", "partition" -> false;
                    default -> true;
                };
            }

            @Override
            public List<String> getValues(String field) {
                return subnetSearchValues(subnet, field);
            }
        });
    }

    private boolean subnetConnectionMatches(CellTerminalClientState.SubnetEntry subnet,
                                            CellTerminalClientState.ConnectionEntry conn) {
        if (this.searchQuery.isEmpty()) {
            return true;
        }
        CellTerminalSearchMode mode = AEConfig.instance().getCellTerminalSearchMode();
        return this.searchQuery.matches(new CellTerminalSearchQuery.SearchSource() {
            @Override
            public CellTerminalSearchMode mode() {
                return mode;
            }

            @Override
            public boolean supportsField(String field) {
                return !"priority".equals(field);
            }

            @Override
            public List<String> getValues(String field) {
                return subnetConnectionSearchValues(subnet, conn, field);
            }
        });
    }

    private String subnetDirectionSearchText(CellTerminalClientState.SubnetEntry subnet) {
        if (this.container.getState().tab() != CellTerminalTab.SUBNETS || subnet.connections().isEmpty()) {
            return "";
        }
        boolean hasOutbound = false;
        boolean hasInbound = false;
        for (var connection : subnet.connections()) {
            if (connection.outbound()) {
                hasOutbound = true;
            } else {
                hasInbound = true;
            }
        }
        if (hasOutbound && hasInbound) {
            return tr("subnet.outbound") + " " + tr("subnet.inbound");
        }
        if (hasOutbound) {
            return tr("subnet.outbound");
        }
        if (hasInbound) {
            return tr("subnet.inbound");
        }
        return "";
    }

    private List<String> storageHeaderSearchValues(StorageEntry storage, String field) {
        return switch (field) {
            case "" -> aggregateSearchValues(
                storageHeaderSearchValues(storage, "container"),
                storageHeaderSearchValues(storage, "renamed"),
                storageHeaderSearchValues(storage, "priority"),
                storageHeaderSearchValues(storage, "items"),
                storageHeaderSearchValues(storage, "partition"));
            case "container" ->
                containerValues(storage.visibleName().getFormattedText(), storage.displayName().getFormattedText(),
                    storage.stableTargetId());
            case "renamed" -> optionalValue(storage.renamedDisplayName());
            case "priority" -> List.of(Integer.toString(storage.priority()));
            case "items" -> List.of(Integer.toString(storage.contentEntryCount()));
            case "partition" -> List.of(Integer.toString(storagePartitionEntryCount(storage)));
            default -> List.of();
        };
    }

    private List<String> busHeaderSearchValues(CellTerminalClientState.BusEntry bus, String field) {
        return switch (field) {
            case "" -> aggregateSearchValues(
                busHeaderSearchValues(bus, "container"),
                busHeaderSearchValues(bus, "renamed"),
                busHeaderSearchValues(bus, "priority"),
                busHeaderSearchValues(bus, "items"),
                busHeaderSearchValues(bus, "partition"));
            case "container" ->
                containerValues(bus.visibleName().getFormattedText(), bus.displayName().getFormattedText(),
                    bus.connectedDisplayName() == null ? "" : bus.connectedDisplayName().getFormattedText(),
                    bus.stableTargetId());
            case "renamed" -> optionalValue(bus.renamedDisplayName());
            case "priority" -> List.of(Integer.toString(bus.priority()));
            case "items" -> List.of(Integer.toString(bus.contentEntryCount()));
            case "partition" -> List.of(Integer.toString(busPartitionEntryCount(bus)));
            default -> List.of();
        };
    }

    private List<String> subnetSearchValues(CellTerminalClientState.SubnetEntry subnet, String field) {
        return switch (field) {
            case "" -> aggregateSearchValues(
                subnetSearchValues(subnet, "container"),
                subnetSearchValues(subnet, "name"),
                subnetSearchValues(subnet, "dir"),
                subnetSearchValues(subnet, "renamed"));
            case "name", "container" -> containerValues(subnet.visibleName().getFormattedText(),
                subnet.displayName().getFormattedText(), subnet.subnetId());
            case "renamed" -> optionalValue(subnet.renamedDisplayName());
            case "part" -> List.of("subnet");
            case "dir" -> optionalValue(subnetDirectionSearchText(subnet));
            default -> List.of();
        };
    }

    private List<String> subnetConnectionSearchValues(CellTerminalClientState.SubnetEntry subnet,
                                                      CellTerminalClientState.ConnectionEntry conn,
                                                      String field) {
        return switch (field) {
            case "" -> aggregateSearchValues(
                subnetConnectionSearchValues(subnet, conn, "container"),
                subnetConnectionSearchValues(subnet, conn, "name"),
                subnetConnectionSearchValues(subnet, conn, "part"),
                subnetConnectionSearchValues(subnet, conn, "dir"),
                subnetConnectionSearchValues(subnet, conn, "renamed"));
            case "name" -> nameValues(modeFilteredStacks(subnetConnectionItemSearchValues(conn, false),
                subnetConnectionItemSearchValues(conn, true)));
            case "content" -> subnetConnectionItemSearchValues(conn, false);
            case "part" -> subnetConnectionItemSearchValues(conn, true);
            case "container" -> containerValues(
                subnet.visibleName().getFormattedText(),
                subnet.displayName().getFormattedText(),
                subnet.subnetId(),
                conn.displayName().getFormattedText(),
                connectionPositionText(conn));
            case "renamed" -> optionalValue(subnet.renamedDisplayName());
            case "items" -> List.of(Integer.toString(conn.content().size()));
            case "partition" -> List.of(Integer.toString(partitionEntryCount(conn.partition())));
            case "dir" -> List.of(conn.outbound() ? "outbound" : "inbound",
                conn.outbound() ? tr("subnet.outbound") : tr("subnet.inbound"));
            default -> List.of();
        };
    }

    private List<String> modeFilteredStacks(List<String> contentValues, List<String> partitionValues) {
        return switch (AEConfig.instance().getCellTerminalSearchMode()) {
            case INVENTORY -> contentValues;
            case PARTITION -> partitionValues;
            case MIXED -> aggregateSearchValues(contentValues, partitionValues);
        };
    }

    private List<String> cellSlotChildSearchValues(StorageEntry storage, CellSlotEntry slot, String field) {
        return switch (field) {
            case "" -> aggregateSearchValues(
                cellSlotChildSearchValues(storage, slot, "container"),
                cellSlotChildSearchValues(storage, slot, "name"),
                cellSlotChildSearchValues(storage, slot, "content"),
                cellSlotChildSearchValues(storage, slot, "part"),
                cellSlotChildSearchValues(storage, slot, "items"),
                cellSlotChildSearchValues(storage, slot, "partition"));
            case "name" -> nameValues(modeFilteredStacks(cellSlotItemSearchValues(slot, false),
                cellSlotItemSearchValues(slot, true)));
            case "content" -> cellSlotItemSearchValues(slot, false);
            case "part" -> cellSlotItemSearchValues(slot, true);
            case "container" -> containerValues(Integer.toString(slot.slotIndex()),
                slot.cellStack().isEmpty() ? "" : slot.cellStack().getDisplayName());
            case "items" -> List.of(Integer.toString(slot.contentEntryCount()));
            case "partition" -> List.of(Integer.toString(partitionEntryCount(slot.partition())));
            default -> List.of();
        };
    }

    private List<String> cellSlotItemSearchValues(CellSlotEntry slot, boolean partition) {
        List<String> values = new ObjectArrayList<>();
        if (partition) {
            appendPartitionSearchValues(values, slot.partition());
        } else {
            appendContentSearchValues(values, slot.content());
        }
        return values;
    }

    private List<String> busItemSearchValues(CellTerminalClientState.BusEntry bus, boolean partition) {
        List<String> values = new ObjectArrayList<>();
        if (partition) {
            if (isTextBusPartition(bus)) {
                values.add(bus.textPartitionPrimary());
                values.add(bus.textPartitionSecondary());
            } else {
                appendPartitionSearchValues(values, bus.partition());
            }
        } else {
            appendContentSearchValues(values, bus.content());
        }
        return values;
    }

    private List<String> busChildSearchValues(CellTerminalClientState.BusEntry bus, String field) {
        return switch (field) {
            case "" -> aggregateSearchValues(
                busChildSearchValues(bus, "name"),
                busChildSearchValues(bus, "content"),
                busChildSearchValues(bus, "part"),
                busChildSearchValues(bus, "items"),
                busChildSearchValues(bus, "partition"));
            case "name" ->
                nameValues(modeFilteredStacks(busItemSearchValues(bus, false), busItemSearchValues(bus, true)));
            case "content" -> busItemSearchValues(bus, false);
            case "part" -> busItemSearchValues(bus, true);
            case "items" -> List.of(Integer.toString(bus.contentEntryCount()));
            case "partition" -> List.of(Integer.toString(busPartitionEntryCount(bus)));
            default -> List.of();
        };
    }

    private List<String> busRowSearchValues(BusRowData row, String field) {
        List<GenericStack> slice = visibleRowStacks(row.stacks(), row.startIndex());
        return switch (field) {
            case "" -> aggregateSearchValues(
                busHeaderSearchValues(row.bus(), "container"),
                busRowSearchValues(row, "name"),
                busRowSearchValues(row, row.partition() ? "part" : "content"),
                busRowSearchValues(row, "items"),
                busRowSearchValues(row, "partition"));
            case "name" -> nameValues(genericStackSearchValues(slice));
            case "content" -> row.partition() ? List.of() : genericStackSearchValues(slice);
            case "part" -> row.partition() ? genericStackSearchValues(slice) : List.of();
            case "container", "renamed", "priority" -> busHeaderSearchValues(row.bus(), field);
            case "items" -> row.partition() ? List.of("0") : List.of(Integer.toString(countNonNullStacks(slice)));
            case "partition" -> row.partition() ? List.of(Integer.toString(countNonNullStacks(slice))) : List.of("0");
            default -> List.of();
        };
    }

    private List<String> busTextPartitionSearchValues(BusTextPartitionRowData row, String field) {
        String expression = row.expression();
        return switch (field) {
            case "" -> aggregateSearchValues(
                busTextPartitionSearchValues(row, "container"),
                busTextPartitionSearchValues(row, "name"),
                busTextPartitionSearchValues(row, "part"));
            case "container" -> containerValues(row.fieldId(), row.label());
            case "name" -> optionalValue(row.label());
            case "part", "content" -> optionalValue(expression);
            case "items", "partition" -> List.of(expression.isBlank() ? "0" : "1");
            default -> List.of();
        };
    }

    private List<String> subnetConnectionItemSearchValues(CellTerminalClientState.ConnectionEntry conn,
                                                          boolean partition) {
        List<String> values = new ObjectArrayList<>();
        if (partition) {
            appendPartitionSearchValues(values, conn.partition());
        } else {
            appendContentSearchValues(values, conn.content());
        }
        return values;
    }

    private List<String> subnetConnectionContentRowSearchValues(SubnetConnectionContentRowData row, String field) {
        List<GenericStack> slice = visibleRowStacks(row.stacks(), row.startIndex());
        return switch (field) {
            case "" -> aggregateSearchValues(
                subnetConnectionSearchValues(row.subnet(), row.connection(), "container"),
                subnetConnectionContentRowSearchValues(row, "name"),
                subnetConnectionContentRowSearchValues(row, row.partition() ? "part" : "content"),
                subnetConnectionSearchValues(row.subnet(), row.connection(), "dir"),
                subnetConnectionSearchValues(row.subnet(), row.connection(), "renamed"));
            case "name" -> nameValues(genericStackSearchValues(slice));
            case "content" -> row.partition() ? List.of() : genericStackSearchValues(slice);
            case "part" -> row.partition() ? genericStackSearchValues(slice) : List.of();
            case "container", "renamed", "dir" -> subnetConnectionSearchValues(row.subnet(), row.connection(), field);
            case "items" -> row.partition() ? List.of("0") : List.of(Integer.toString(countNonNullStacks(slice)));
            case "partition" -> row.partition() ? List.of(Integer.toString(countNonNullStacks(slice))) : List.of("0");
            default -> List.of();
        };
    }

    private boolean passesCellFilters(CellSlotEntry slot) {
        CellTerminalContentFilter cf = AEConfig.instance().getCellTerminalContentFilter();
        CellTerminalContentFilter pf = AEConfig.instance().getCellTerminalPartitionFilter();
        int partCount = 0;
        for (GenericStack s : slot.partition()) {
            if (s != null) {
                partCount++;
            }
        }
        return cf.matches(slot.contentEntryCount() > 0) && pf.matches(partCount > 0)
            && passesTypeFilter(slot);
    }

    private boolean passesTypeFilter(CellSlotEntry slot) {
        if (!this.container.canConfigureTypeFilter()) {
            return true;
        }
        List<AEKeyType> enabled = this.container.getClientKeyTypeSelection().enabledSet();
        if (enabled.isEmpty()) {
            return true;
        }
        AEKeyType type = cellKeyType(slot);
        if (type == null) {
            return true;
        }
        return enabled.contains(type);
    }

    private boolean passesSubnetVisibility(CellTerminalClientState.SubnetEntry subnet) {
        return switch (AEConfig.instance().getCellTerminalSubnetVisibility()) {
            case SHOW_ALL -> true;
            case SHOW_FAVORITES -> subnet.favorite();
            case DONT_SHOW -> false;
        };
    }

    private StorageHeader buildStorageHeader(StorageHeaderData data, int y) {
        StorageEntry storage = data.storage();
        String key = storageKey(storage);
        StorageHeader header = new StorageHeader(y, this.fontRenderer, this.itemRender);
        header.setIconSupplier(storage::icon);
        header.setNameSupplier(() -> storage.visibleName().getFormattedText());
        header.setHasCustomNameSupplier(() -> storage.renamedDisplayName() != null);
        header.setLocationSupplier(() -> storage.mountedCellCount() + "/" + storage.cellSlotCount());
        header.setExpandedSupplier(() -> data.forceExpanded() || this.expandedStorages.contains(key));
        header.setOnExpandToggle(() -> {
            if (!this.expandedStorages.add(key)) {
                this.expandedStorages.remove(key);
            }
            requestRebuild();
        });
        header.setPrioritizable(new StoragePriorityAdapter(storage));
        if (!storage.storageBus()) {
            header.setRenameInfo(new StorageRenameAdapter(storage), CellTerminalLayout.HEADER_NAME_X, 0,
                CellTerminalLayout.CONTENT_RIGHT_EDGE - 40);
        }
        if (!storage.stableTargetId().startsWith("temp_cells@")) {
            header.setOnNameDoubleClick(() -> this.container.highlightStorageFromClient(storage),
                storageKey(storage).hashCode());
        }
        header.setGuiOffsets(this.guiLeft, this.guiTop);
        header.setGuiStyle(getStyle());
        return header;
    }

    private TerminalLine buildTerminalLine(CellLineData data, int y) {
        StorageEntry storage = data.storage();
        CellSlotEntry slot = data.slot();
        TerminalLine line = new TerminalLine(y, this.fontRenderer, this.itemRender);
        line.setCellItemSupplier(slot::cellStack);
        line.setCellNameSupplier(() -> slot.cellStack().isEmpty()
            ? "#" + slot.slotIndex() + " " + tr("cell.empty")
            : "#" + slot.slotIndex() + " " + slot.cellStack().getDisplayName());
        line.setHasCustomNameSupplier(() -> false);
        line.setByteUsageSupplier(() -> {
            long total = slot.totalBytes();
            return total > 0 ? Math.min(1f, (float) slot.usedBytes() / total) : 0f;
        });
        CardsDisplay cards = buildCards(slot, y,
            (upgradeSlot, quickMove) ->
                this.container.interactTargetUpgradeFromClient(storage, slot, null, upgradeSlot, quickMove));
        if (cards != null) {
            line.setCardsDisplay(cards);
        }
        line.setCallback(hoverType -> {
            switch (hoverType) {
                case TerminalLine.HOVER_EJECT -> this.container.ejectCellFromClient(storage, slot);
                case TerminalLine.HOVER_INVENTORY -> this.container.setTabFromClient(CellTerminalTab.CELL_CONTENT);
                case TerminalLine.HOVER_PARTITION -> this.container.setTabFromClient(CellTerminalTab.CELL_PARTITION);
                default -> {
                }
            }
        });
        return line;
    }

    private TempAreaHeader buildTempAreaHeader(TempCellRowData data, int y) {
        StorageEntry storage = data.storage();
        CellSlotEntry slot = data.slot();
        TempAreaHeader header =
            new TempAreaHeader(y, this.fontRenderer, this.itemRender);
        header.setIconSupplier(slot::cellStack);
        header.setHasCellSupplier(() -> !slot.cellStack().isEmpty());
        header.setNameSupplier(() -> slot.cellStack().isEmpty()
            ? "#" + slot.slotIndex() : "#" + slot.slotIndex() + " " + slot.cellStack().getDisplayName());
        header.setHasCustomNameSupplier(() -> false);
        header.setCellSlotCallback(button -> {
            boolean holdingCell = !this.playerInventory.getItemStack().isEmpty();
            if (slot.cellStack().isEmpty()) {
                this.container.insertCellFromClient(storage, slot);
            } else if (holdingCell) {
                this.container.replaceCellFromClient(storage, slot);
            } else {
                this.container.ejectCellFromClient(storage, slot);
            }
        });
        header.setOnSendClick(() -> this.container.sendTempCellFromClient(storage, slot));
        header.setSelectedSupplier(() -> this.selectedTempCells.contains(tempCellKey(storage, slot)));
        header.setOnHeaderClick(() -> toggleTempCellSelection(storage, slot));
        header.setDrawConnector(!slot.cellStack().isEmpty());
        CardsDisplay cards = buildCards(slot, y,
            (upgradeSlot, quickMove) ->
                this.container.interactTargetUpgradeFromClient(storage, slot, null, upgradeSlot, quickMove));
        if (cards != null) {
            header.setCardsDisplay(cards);
        }
        return header;
    }

    private SlotsLine buildTempContentLine(TempSlotsRowData data, int y) {
        StorageEntry storage = data.storage();
        CellSlotEntry slot = data.slot();
        boolean supportsAutoPartition = supportsAutoPartition(slot);
        SlotsLine.SlotMode mode = data.partition() ? SlotsLine.SlotMode.PARTITION : SlotsLine.SlotMode.CONTENT;
        SlotsLine line;
        if (data.first()) {
            line = new SlotsLine(y, TEMP_AREA_SLOTS_PER_ROW, CellTerminalLayout.CELL_INDENT + 4,
                mode, data.startIndex(), this.fontRenderer);
            if (data.partition()) {
                line.setDrawTopSeparator(true);
            }
        } else {
            ContinuationLine cont =
                new ContinuationLine(y, TEMP_AREA_SLOTS_PER_ROW,
                    CellTerminalLayout.CELL_INDENT + 4, mode, data.startIndex(),
                    this.fontRenderer);
            cont.setDrawHorizontalBranch(false);
            cont.setDrawTopSeparator(true);
            line = cont;
        }
        line.setItemsSupplier(data::stacks);
        if (data.partition()) {
            line.setMaxSlots(visiblePartitionSlotCount(data.stacks()));
        } else {
            line.setPartitionSupplier(() -> toStackList(slot.partition()));
        }
        line.setGuiOffsets(this.guiLeft, this.guiTop);
        if (data.first()) {
            line.setSelectedSupplier(() -> this.selectedTempCells.contains(tempCellKey(storage, slot)));
            if (data.partition() || supportsAutoPartition) {
                ButtonType type = data.partition()
                    ? ButtonType.CLEAR_PARTITION
                    : ButtonType.DO_PARTITION;
                line.setTreeButton(new SmallButton(0, 0, type, () -> {
                    if (data.partition()) {
                        this.container.clearCellPartitionFromClient(storage, slot);
                    } else {
                        this.container.partitionCellFromContentFromClient(storage, slot);
                    }
                }));
            }
        }
        line.setSlotClickCallback((slotIndex, button) -> {
            if (data.partition()) {
                GenericStack carried = carriedStack();
                if (carried != null) {
                    this.container.addCellPartitionAtFromClient(storage, slot, slotIndex,
                        new GenericStack(carried.what(), Math.max(1, carried.amount())));
                } else if (slotIndex < data.stacks().size() && data.stacks().get(slotIndex) != null) {
                    this.container.removeCellPartitionAtFromClient(storage, slot, slotIndex);
                }
            } else if (slotIndex < data.stacks().size() && data.stacks().get(slotIndex) != null) {
                this.container.toggleCellPartitionFromClient(storage, slot, data.stacks().get(slotIndex));
            }
        });
        return line;
    }

    private SlotsLine buildCellSlotsLine(CellSlotsRowData data, int y) {
        StorageEntry storage = data.storage();
        CellSlotEntry slot = data.slot();
        boolean supportsAutoPartition = supportsAutoPartition(slot);
        SlotsLine.SlotMode mode = data.partition() ? SlotsLine.SlotMode.PARTITION : SlotsLine.SlotMode.CONTENT;
        int maxSlots = slot.totalTypes() > 0 ? slot.totalTypes() : Integer.MAX_VALUE;
        if (!data.first()) {
            ContinuationLine cont =
                new ContinuationLine(y, CellTerminalLayout.CELL_SLOTS_PER_ROW,
                    CellTerminalLayout.CELL_INDENT + 20, mode, data.startIndex(), this.fontRenderer);
            cont.setItemsSupplier(data::stacks);
            if (!data.partition()) {
                cont.setPartitionSupplier(() -> toStackList(slot.partition()));
            }
            cont.setMaxSlots(maxSlots);
            cont.setGuiOffsets(this.guiLeft, this.guiTop);
            cont.setDrawHorizontalBranch(false);
            cont.setSlotClickCallback((slotIndex, button) ->
                handleCellSlotPartitionClick(storage, slot, data, slotIndex));
            return cont;
        }
        CellSlotsLine line =
            new CellSlotsLine(y, CellTerminalLayout.CELL_SLOTS_PER_ROW,
                CellTerminalLayout.CELL_INDENT + 20, mode, data.startIndex(), this.fontRenderer, this.itemRender);
        line.setCellItemSupplier(slot::cellStack);
        line.setCellFilledSupplier(() -> !slot.cellStack().isEmpty());
        line.setItemsSupplier(data::stacks);
        if (!data.partition()) {
            line.setPartitionSupplier(() -> toStackList(slot.partition()));
        }
        line.setMaxSlots(maxSlots);
        line.setGuiOffsets(this.guiLeft, this.guiTop);
        line.setCellSlotCallback(button -> {
            if (slot.cellStack().isEmpty()) {
                this.container.insertCellFromClient(storage, slot);
            } else {
                this.container.ejectCellFromClient(storage, slot);
            }
        });
        line.setSlotClickCallback((slotIndex, button) -> handleCellSlotPartitionClick(storage, slot, data, slotIndex));
        CardsDisplay cards = buildCards(slot, y,
            (upgradeSlot, quickMove) ->
                this.container.interactTargetUpgradeFromClient(storage, slot, null, upgradeSlot, quickMove));
        if (cards != null) {
            line.setCardsDisplay(cards);
        }
        if (!slot.cellStack().isEmpty() && (data.partition() || supportsAutoPartition)) {
            ButtonType type = data.partition()
                ? ButtonType.CLEAR_PARTITION
                : ButtonType.DO_PARTITION;
            line.setTreeButton(new SmallButton(0, 0, type, () -> {
                if (data.partition()) {
                    this.container.clearCellPartitionFromClient(storage, slot);
                } else {
                    this.container.partitionCellFromContentFromClient(storage, slot);
                }
            }));
        }
        return line;
    }

    private void handleCellSlotPartitionClick(StorageEntry storage, CellSlotEntry slot,
                                              CellSlotsRowData data, int slotIndex) {
        if (data.partition()) {
            GenericStack carried = carriedStack();
            if (carried != null) {
                this.container.addCellPartitionAtFromClient(storage, slot, slotIndex,
                    new GenericStack(carried.what(), Math.max(1, carried.amount())));
            } else if (slotIndex < data.stacks().size() && data.stacks().get(slotIndex) != null) {
                this.container.removeCellPartitionAtFromClient(storage, slot, slotIndex);
            }
        } else {
            if (slotIndex < data.stacks().size() && data.stacks().get(slotIndex) != null) {
                this.container.toggleCellPartitionFromClient(storage, slot, data.stacks().get(slotIndex));
            }
        }
    }

    private SlotsLine buildBusLine(BusRowData data, int y) {
        var bus = data.bus();
        SlotsLine.SlotMode mode = data.partition() ? SlotsLine.SlotMode.PARTITION : SlotsLine.SlotMode.CONTENT;
        SlotsLine line;
        if (data.first()) {
            line = new SlotsLine(y, CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW,
                CellTerminalLayout.CELL_INDENT + 4, mode, data.startIndex(), this.fontRenderer);
        } else {
            line = new ContinuationLine(y,
                CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW, CellTerminalLayout.CELL_INDENT + 4, mode,
                data.startIndex(), this.fontRenderer);
        }
        line.setItemsSupplier(data::stacks);
        if (!data.partition()) {
            line.setPartitionSupplier(() -> toStackList(bus.partition()));
        }
        line.setMaxSlots(data.partition() ? bus.partitionSlotCapacity() : Integer.MAX_VALUE);
        line.setGuiOffsets(this.guiLeft, this.guiTop);
        line.setSlotClickCallback((slotIndex, button) -> {
            if (data.partition() && button == 2) {
                if (isPreciseBusPartition(bus)) {
                    GenericStack current = slotIndex < data.stacks().size() ? data.stacks().get(slotIndex) : null;
                    GenericStack carried = carriedStack();
                    if (current != null) {
                        openPreciseBusAmountEditor(bus, slotIndex, current);
                    } else if (carried != null) {
                        openPreciseBusAmountEditor(bus, slotIndex,
                            new GenericStack(carried.what(), Math.max(1, carried.amount())));
                    }
                    return;
                }
                return;
            }
            if (data.partition()) {
                GenericStack carried = carriedStack();
                if (carried != null) {
                    this.container.addBusPartitionAtFromClient(bus, slotIndex,
                        new GenericStack(carried.what(), Math.max(1, carried.amount())));
                } else if (slotIndex < data.stacks().size() && data.stacks().get(slotIndex) != null) {
                    this.container.removeBusPartitionAtFromClient(bus, slotIndex);
                }
            } else if (slotIndex < data.stacks().size() && data.stacks().get(slotIndex) != null) {
                this.container.toggleBusPartitionFromClient(bus, data.stacks().get(slotIndex));
            }
        });
        if (data.first()) {
            line.setSelectedSupplier(() -> this.selectedBuses.contains(busKey(bus)));
            if (isSlotPartitionBus(bus)) {
                line.setTreeButtonXOffset(-3);
                ButtonType type = data.partition()
                    ? ButtonType.CLEAR_PARTITION
                    : ButtonType.DO_PARTITION;
                line.setTreeButton(new SmallButton(0, 0, type, () -> {
                    if (data.partition()) {
                        this.container.clearBusPartitionFromClient(bus);
                    } else {
                        this.container.partitionBusFromContentFromClient(bus);
                    }
                }));
            }
        }
        return line;
    }

    private StorageBusHeader buildBusHeader(BusHeaderData data, int y) {
        var bus = data.bus();
        StorageBusHeader header =
            new StorageBusHeader(y, this.fontRenderer, this.itemRender);
        header.setIconSupplier(bus::icon);
        header.setNameSupplier(() -> bus.visibleName().getFormattedText());
        header.setHasCustomNameSupplier(() -> bus.renamedDisplayName() != null);
        header.setLocationSupplier(() -> connectedToLine(bus.connectedDisplayName()));
        String key = busKey(bus);
        header.setExpandedSupplier(() -> data.forceExpanded() || this.expandedBuses.contains(key));
        header.setOnExpandToggle(() -> {
            if (!this.expandedBuses.add(key)) {
                this.expandedBuses.remove(key);
            }
            requestRebuild();
        });
        header.setSupportsIOModeSupplier(() -> true);
        header.setAccessModeSupplier(() -> switch (bus.accessRestriction()) {
            case READ -> 1;
            case WRITE -> 2;
            default -> 3;
        });
        header.setOnIOModeClick(() -> {
            AccessRestriction next = switch (bus.accessRestriction()) {
                case READ_WRITE -> AccessRestriction.READ;
                case READ -> AccessRestriction.WRITE;
                default -> AccessRestriction.READ_WRITE;
            };
            this.container.writeBusModeFromClient(bus, next, bus.storageFilter(),
                bus.filterOnExtract(), bus.fuzzyMode());
        });
        header.setPrioritizable(new BusPriorityAdapter(bus));
        header.setRenameInfo(new BusRenameAdapter(bus), CellTerminalLayout.HEADER_NAME_X, 0,
            CellTerminalLayout.CONTENT_RIGHT_EDGE - 40);
        header.setOnNameDoubleClick(() -> this.container.highlightBusFromClient(bus),
            ("bus:" + bus.stableTargetId() + "|" + bus.locator()).hashCode());
        header.setDrawConnector(true);
        if (this.container.getState().tab() == CellTerminalTab.BUS_PARTITION) {
            header.setSelectedSupplier(() -> this.selectedBuses.contains(key));
            header.setOnHeaderClick(() -> {
                if (!this.selectedBuses.add(key)) {
                    this.selectedBuses.remove(key);
                }
            });
        }
        header.setGuiOffsets(this.guiLeft, this.guiTop);
        header.setGuiStyle(getStyle());
        if (bus.upgradesLoaded() && !bus.upgrades().isEmpty()) {
            List<CardsDisplay.CardEntry> entries = new ObjectArrayList<>();
            List<ItemStack> upgrades = bus.upgrades();
            for (int i = 0; i < upgrades.size(); i++) {
                entries.add(new CardsDisplay.CardEntry(upgrades.get(i), i));
            }
            var cards = new CardsDisplay(
                CellTerminalLayout.CARDS_X, y, () -> entries, this.itemRender);
            cards.setClickCallback((idx, quickMove) ->
                this.container.interactTargetUpgradeFromClient(null, null, bus, idx, quickMove));
            header.setCardsDisplay(cards);
        }
        return header;
    }

    private String connectionPositionText(CellTerminalClientState.ConnectionEntry conn) {
        var pos = conn.locator().pos();
        return tr("subnet.pos", pos.getX(), pos.getY(), pos.getZ(), conn.locator().dimensionId());
    }

    private List<String> subnetOverviewTooltip(CellTerminalClientState.SubnetEntry subnet) {
        List<String> lines = new ArrayList<>();
        lines.add(tr("subnet.overview"));
        lines.add("§7" + tr("subnet.overview.desc"));
        appendSubnetIdentity(lines, subnet);
        return lines;
    }

    private List<String> subnetConnectionTooltip(CellTerminalClientState.SubnetEntry subnet,
                                                 CellTerminalClientState.ConnectionEntry conn) {
        List<String> lines = subnetOverviewTooltip(subnet);
        lines.add("§7" + connectionPositionText(conn));
        return lines;
    }

    private List<String> subnetLoadTooltip(CellTerminalClientState.SubnetEntry subnet) {
        List<String> lines = new ArrayList<>(subnetOverviewTooltip(subnet));
        lines.add("");
        lines.add("§7" + tr("subnet.loadTooltip"));
        return lines;
    }

    private List<String> subnetMainTooltip(CellTerminalClientState.SubnetEntry subnet) {
        List<String> lines = new ArrayList<>();
        lines.add(tr("subnet.mainNetwork"));
        lines.add(tr("subnet.back"));
        lines.add("§7" + tr("subnet.back.desc"));
        lines.add("§e" + tr("subnet.click_load_main"));
        appendSubnetIdentity(lines, subnet);
        return lines;
    }

    private SubnetHeader buildSubnetConnectionHeader(SubnetConnectionRowData data, int y) {
        var subnet = data.subnet();
        var conn = data.connection();
        SubnetHeader header = new SubnetHeader(y, this.fontRenderer, this.itemRender);
        header.setNameSupplier(() -> subnet.visibleName().getFormattedText());
        header.setHasCustomNameSupplier(() -> subnet.renamedDisplayName() != null);
        header.setIsFavoriteSupplier(subnet::favorite);
        header.setCanLoadSupplier(() -> this.container.getState().connected());
        header.setDirectionSupplier(conn::outbound);
        header.setLocationSupplier(() -> connectionPositionText(conn));
        header.setOnStarClick(() -> this.container.favoriteSubnetFromClient(subnet, !subnet.favorite()));
        header.setOnLoadClick(() -> this.container.loadSubnetFromClient(subnet));
        header.setHeaderTooltipSupplier(() -> subnetConnectionTooltip(subnet, conn));
        header.setLoadTooltipSupplier(() -> subnetLoadTooltip(subnet));
        header.setRenameInfo(new SubnetRenameAdapter(subnet), CellTerminalLayout.HEADER_NAME_X, 0,
            CellTerminalLayout.CONTENT_RIGHT_EDGE - 40);
        header.setOnNameDoubleClick(() -> this.container.highlightSubnetConnectionFromClient(subnet, conn,
                data.connectionIndex()),
            (subnet.subnetId() + "@" + data.connectionIndex()).hashCode());
        return header;
    }

    private SlotsLine buildSubnetConnectionLine(SubnetConnectionContentRowData data, int y) {
        SlotsLine.SlotMode mode = data.partition() ? SlotsLine.SlotMode.PARTITION : SlotsLine.SlotMode.CONTENT;
        SlotsLine line;
        if (data.first()) {
            line = new SlotsLine(y, CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW,
                CellTerminalLayout.CELL_INDENT + 4, mode, data.startIndex(), this.fontRenderer);
        } else {
            ContinuationLine cont =
                new ContinuationLine(y, CellTerminalLayout.STORAGE_BUS_SLOTS_PER_ROW,
                    CellTerminalLayout.CELL_INDENT + 4, mode, data.startIndex(), this.fontRenderer);
            cont.setDrawHorizontalBranch(false);
            line = cont;
        }
        line.setItemsSupplier(data::stacks);
        if (!data.partition()) {
            line.setPartitionSupplier(() -> toStackList(data.connection().partition()));
        }
        line.setMaxSlots(data.partition() ? data.connection().partitionSlotCapacity() : Integer.MAX_VALUE);
        line.setGuiOffsets(this.guiLeft, this.guiTop);
        line.setSlotClickCallback((slotIndex, button) -> handleSubnetConnectionPartitionClick(data, slotIndex));
        if (data.first()) {
            ButtonType type = data.partition()
                ? ButtonType.CLEAR_PARTITION
                : ButtonType.DO_PARTITION;
            line.setTreeButton(new SmallButton(0, 0, type, () -> {
                if (data.partition()) {
                    this.container.clearSubnetConnectionPartitionFromClient(data.subnet(), data.connection(),
                        data.connectionIndex());
                } else {
                    this.container.partitionSubnetConnectionFromContentFromClient(data.subnet(), data.connection(),
                        data.connectionIndex());
                }
            }));
        }
        return line;
    }

    private void openPreciseBusAmountEditor(CellTerminalClientState.BusEntry bus, int slotIndex,
                                            GenericStack currentStack) {
        switchToScreen(new GuiSetProcessingPatternAmount(this, currentStack,
            newStack -> {
                if (newStack != null) {
                    this.container.writeBusPrecisePartitionAmountFromClient(bus, slotIndex, newStack);
                } else {
                    this.container.removeBusPartitionAtFromClient(bus, slotIndex);
                }
            },
            TextComponentItemStack.of(AEParts.PRECISE_STORAGE_BUS.stack())));
    }

    private BusTextPartitionLine buildBusTextPartitionLine(BusTextPartitionRowData data, int y) {
        String key = busTextPartitionFieldKey(data.bus(), data.fieldId());
        AETextField field = this.busTextPartitionFields.get(key);
        BusTextPartitionLine line = new BusTextPartitionLine(
            y,
            data.fieldId(),
            data.label(),
            data.expression(),
            data.placeholder(),
            this.fontRenderer,
            getStyle(),
            expression -> this.container.writeBusTextPartitionFromClient(data.bus(), data.fieldId(), expression),
            field);
        this.busTextPartitionFields.put(key, line.textField());
        line.setGuiOffsets(this.guiLeft, this.guiTop);
        return line;
    }

    private void handleSubnetConnectionPartitionClick(SubnetConnectionContentRowData data, int slotIndex) {
        if (data.partition()) {
            GenericStack carried = carriedStack();
            if (carried != null) {
                this.container.addSubnetConnectionPartitionAtFromClient(data.subnet(), data.connection(),
                    data.connectionIndex(), slotIndex, new GenericStack(carried.what(), Math.max(1, carried.amount())));
            } else if (slotIndex < data.stacks().size() && data.stacks().get(slotIndex) != null) {
                this.container.removeSubnetConnectionPartitionAtFromClient(data.subnet(), data.connection(),
                    data.connectionIndex(), slotIndex);
            }
        } else if (slotIndex < data.stacks().size() && data.stacks().get(slotIndex) != null) {
            this.container.toggleSubnetConnectionPartitionFromClient(data.subnet(), data.connection(),
                data.connectionIndex(), data.stacks().get(slotIndex));
        }
    }

    private SubnetHeader buildSubnetHeader(SubnetRowData data, int y) {
        var subnet = data.subnet();
        if (subnet.mainNetwork()) {
            SubnetHeader header = new SubnetHeader(y, this.fontRenderer, this.itemRender, true);
            header.setNameSupplier(() -> subnet.visibleName().getFormattedText());
            header.setIsFavoriteSupplier(subnet::favorite);
            header.setCanLoadSupplier(() -> this.container.getState().connected());
            header.setOnLoadClick(this.container::returnToParentFromClient);
            header.setMainTooltipSupplier(() -> subnetMainTooltip(subnet));
            return header;
        }
        SubnetHeader header = new SubnetHeader(y, this.fontRenderer, this.itemRender);
        header.setNameSupplier(() -> subnet.visibleName().getFormattedText());
        header.setLocationSupplier(() -> subnet.renamedDisplayName() == null
            ? subnet.subnetId()
            : subnet.displayName().getFormattedText());
        header.setIsFavoriteSupplier(subnet::favorite);
        header.setCanLoadSupplier(() -> this.container.getState().connected());
        header.setHasCustomNameSupplier(() -> subnet.renamedDisplayName() != null);
        header.setOnStarClick(() -> this.container.favoriteSubnetFromClient(subnet, !subnet.favorite()));
        header.setOnLoadClick(() -> this.container.loadSubnetFromClient(subnet));
        header.setHeaderTooltipSupplier(() -> subnetOverviewTooltip(subnet));
        header.setLoadTooltipSupplier(() -> subnetLoadTooltip(subnet));
        header.setRenameInfo(new SubnetRenameAdapter(subnet), CellTerminalLayout.HEADER_NAME_X, 0,
            CellTerminalLayout.CONTENT_RIGHT_EDGE - 40);
        header.setOnNameDoubleClick(() -> this.container.highlightSubnetFromClient(subnet),
            subnet.subnetId().hashCode());
        return header;
    }

    private NetworkToolRowWidget buildToolRow(ToolRowData data, int y) {
        CellTerminalNetworkToolOperation op = data.operation();
        NetworkToolRowWidget row = new NetworkToolRowWidget(y, this.fontRenderer,
            () -> toolIcon(op),
            () -> toolName(op),
            () -> {
                var preview = this.container.getState().preview();
                return hasToolPreview(preview, op) ? toolPreviewSummary(preview) : "";
            },
            () -> {
                var preview = this.container.getState().preview();
                return hasToolPreview(preview, op) ? toolPreviewSummaryColor(preview) : 0x404040;
            },
            () -> {
                var preview = this.container.getState().preview();
                return !hasToolPreview(preview, op) || canExecuteToolPreview(preview, op);
            },
            toolHelpLines(op),
            toolTooltipLines(op));
        row.setOnRunClicked(() -> runNetworkTool(op));
        return row;
    }

    private void runNetworkTool(CellTerminalNetworkToolOperation op) {
        var preview = this.container.getState().preview();
        if (!hasToolPreview(preview, op)) {
            previewTool(op);
            return;
        }
        if (canExecuteToolPreview(preview, op)) {
            this.pendingToolConfirm = op;
        }
    }

    private String toolName(CellTerminalNetworkToolOperation op) {
        return switch (op) {
            case UNIQUE_TYPE_REALLOCATION -> tr("networktools.attribute_unique.name");
            case PARTITION_CELLS_BY_CONTENT -> tr("networktools.mass_partition_cell.name");
            case PARTITION_STORAGE_BUSES_BY_CONTENT -> tr("networktools.mass_partition_bus.name");
        };
    }

    private ItemStack toolIcon(CellTerminalNetworkToolOperation op) {
        return switch (op) {
            case UNIQUE_TYPE_REALLOCATION -> AEBlocks.DRIVE.stack();
            case PARTITION_CELLS_BY_CONTENT -> AEItems.ITEM_CELL_1K.stack();
            case PARTITION_STORAGE_BUSES_BY_CONTENT -> AEParts.STORAGE_BUS.stack();
        };
    }

    private List<String> toolHelpLines(CellTerminalNetworkToolOperation op) {
        return switch (op) {
            case UNIQUE_TYPE_REALLOCATION -> List.of(
                tr("networktools.attribute_unique.help.1"),
                tr("networktools.attribute_unique.help.2"),
                tr("networktools.attribute_unique.help.3"),
                "",
                tr("networktools.attribute_unique.help.4"));
            case PARTITION_CELLS_BY_CONTENT -> List.of(
                tr("networktools.mass_partition_cell.help.1"),
                "",
                tr("networktools.mass_partition_cell.help.2"));
            case PARTITION_STORAGE_BUSES_BY_CONTENT -> List.of(
                tr("networktools.mass_partition_bus.help.1"),
                "",
                tr("networktools.mass_partition_bus.help.2"));
        };
    }

    private List<String> toolTooltipLines(CellTerminalNetworkToolOperation op) {
        var preview = this.container.getState().preview();
        if (!hasToolPreview(preview, op)) {
            return Collections.emptyList();
        }
        List<String> lines = new ObjectArrayList<>();
        if (op == CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION) {
            lines.add("§7" + tr("networktools.attribute_unique.preview.title"));
            var summary = preview.uniqueTypeSummary();
            if (summary == null) {
                lines.add("§80 / 0");
            } else {
                lines.add("§8" + summary.availableCellCount() + " / " + summary.uniqueTypeCount());
                for (var breakdown : summary.breakdown()) {
                    lines.add(typeBreakdownLine(breakdown));
                }
            }
        } else if (!preview.targetBreakdown().isEmpty()) {
            lines.add("§7" + tr("networktools.target_breakdown"));
            for (var breakdown : preview.targetBreakdown()) {
                lines.add("§8" + breakdown.count() + "x " + breakdown.label());
            }
        } else {
            lines.add("§7" + tr("networktools.no_targets"));
        }
        if (!preview.failures().isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            for (CellTerminalClientState.ToolFailureEntry failure : preview.failures()) {
                lines.add("§c" + localizedToolFailure(failure));
            }
        }
        return lines;
    }

    private String toolPreviewSummary(CellTerminalClientState.ToolPreview preview) {
        return switch (preview.operation()) {
            case UNIQUE_TYPE_REALLOCATION -> {
                var summary = preview.uniqueTypeSummary();
                if (summary == null) {
                    yield "0 / 0";
                }
                yield summary.availableCellCount() + " / " + summary.uniqueTypeCount();
            }
            case PARTITION_CELLS_BY_CONTENT, PARTITION_STORAGE_BUSES_BY_CONTENT ->
                Integer.toString(toolTargetCount(preview));
        };
    }

    private int toolPreviewSummaryColor(CellTerminalClientState.ToolPreview preview) {
        return switch (preview.operation()) {
            case UNIQUE_TYPE_REALLOCATION -> uniqueTypeHasCapacityError(preview) ? 0xFFAA3333 : 0x404040;
            case PARTITION_CELLS_BY_CONTENT, PARTITION_STORAGE_BUSES_BY_CONTENT ->
                toolTargetCount(preview) > 0 ? 0x404040 : 0x808080;
        };
    }

    private String typeBreakdownLine(CellTerminalClientState.TypeBreakdown breakdown) {
        String typeLabel = tr("networktools.attribute_unique.preview.type." + breakdown.typeId());
        String color = breakdown.uniqueTypeCount() > breakdown.availableCellCount() ? "§c" : "§a";
        return "§8" + typeLabel + ": " + color + breakdown.availableCellCount() + " / " + breakdown.uniqueTypeCount();
    }

    private List<String> currentSearchAssistLines() {
        AETextField field = activeSearchField();
        boolean focused = field.isFocused();
        return this.searchAssist.buildPanelLines(field, this.searchQuery, focused,
            isShiftKeyDown(), isCtrlKeyDown());
    }

    private SearchAssistLayout currentSearchAssistLayout() {
        List<String> lines = currentSearchAssistLines();
        if (lines.isEmpty()) {
            return null;
        }
        Rectangle anchor = searchAssistAnchorArea();
        int maxPanelWidth = Math.max(80, this.width - 2 * SEARCH_ERROR_MARGIN);
        int maxTextWidth = Math.max(40, maxPanelWidth - FEEDBACK_PADDING_X * 2);
        List<String> wrappedLines = new ObjectArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                wrappedLines.add(line);
            } else {
                wrappedLines.addAll(this.fontRenderer.listFormattedStringToWidth(line, maxTextWidth));
            }
        }

        int textWidth = 0;
        for (String line : wrappedLines) {
            textWidth = Math.max(textWidth, this.fontRenderer.getStringWidth(stripFormatting(line)));
        }
        int width = Math.min(maxPanelWidth, textWidth + FEEDBACK_PADDING_X * 2);
        int height = FEEDBACK_PADDING_Y * 2 + wrappedLines.size() * 10 - 2;
        int x = Math.clamp(anchor.x + anchor.width - width, SEARCH_ERROR_MARGIN,
            Math.max(SEARCH_ERROR_MARGIN, this.width - width - SEARCH_ERROR_MARGIN));
        int y = anchor.y + anchor.height + SEARCH_ERROR_MARGIN;
        return new SearchAssistLayout(new Rectangle(x, y, width, height), wrappedLines);
    }

    private Rectangle searchAssistAnchorArea() {
        if (this.searchOverlay != null && this.searchOverlay.isVisible()) {
            return this.searchOverlay.getAssistAnchorArea();
        }
        return this.searchField.getTooltipArea();
    }

    private SearchAssistLayout drawSearchAssistOverlay() {
        SearchAssistLayout layout = currentSearchAssistLayout();
        if (layout == null) {
            return null;
        }
        GlStateManager.pushMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.translate(0.0F, 0.0F, SEARCH_ASSIST_Z_LEVEL);
        this.zLevel = SEARCH_ASSIST_Z_LEVEL;
        this.itemRender.zLevel = SEARCH_ASSIST_Z_LEVEL;
        try {
            Rectangle bounds = layout.bounds();
            drawControlsHelpBackground(bounds);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            int textY = bounds.y + FEEDBACK_PADDING_Y;
            for (String line : layout.lines()) {
                this.fontRenderer.drawString(line, bounds.x + FEEDBACK_PADDING_X, textY,
                    line.startsWith("§4") ? 0xFF8A1F1F : CellTerminalLayout.COLOR_TEXT_NORMAL);
                textY += 10;
            }
        } finally {
            this.itemRender.zLevel = 0.0F;
            this.zLevel = 0.0F;
            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
        return layout;
    }

    private AETextField activeSearchField() {
        return this.searchOverlay != null && this.searchOverlay.isVisible()
            ? this.searchOverlay.getField()
            : this.searchField;
    }

    private void updateSearchFieldTextColor() {
        if (getStyle() == null) {
            return;
        }
        PaletteColor paletteColor = this.searchQuery.hasParseError()
            ? PaletteColor.TEXTFIELD_ERROR
            : PaletteColor.TEXTFIELD_TEXT;
        int color = getStyle().getColor(paletteColor).toARGB();
        this.searchField.setTextColor(color);
        if (this.searchOverlay != null) {
            this.searchOverlay.getField().setTextColor(color);
        }
    }

    private void previewTool(CellTerminalNetworkToolOperation op) {
        CellTerminalClientState state = this.container.getState();
        this.searchQuery = CellTerminalSearchQuery.parse(this.searchText);
        List<ContainerCellTerminal.ToolCellSlotSelection> cells = new ObjectArrayList<>();
        List<CellTerminalClientState.BusEntry> buses = new ObjectArrayList<>();
        if (op == CellTerminalNetworkToolOperation.PARTITION_STORAGE_BUSES_BY_CONTENT) {
            for (var bus : state.buses()) {
                if (isSlotPartitionBus(bus) && collectBusVisibility(bus, false).showHeader()) {
                    buses.add(bus);
                }
            }
        } else {
            Set<String> selectedCellKeys = new ObjectOpenHashSet<>();
            Set<AEKeyType> uniqueTypes = new ObjectOpenHashSet<>();
            for (StorageEntry storage : state.storages()) {
                if (isTempStorage(storage)) {
                    continue;
                }
                StorageVisibility visibility = collectStorageVisibility(storage, false);
                if (!visibility.showHeader()) {
                    continue;
                }
                for (CellSlotEntry slot : visibility.visibleSlots()) {
                    if (slot.cellStack().isEmpty()) {
                        continue;
                    }
                    cells.add(new ContainerCellTerminal.ToolCellSlotSelection(storage, slot));
                    selectedCellKeys.add(toolCellSelectionKey(storage, slot));
                    if (op == CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION && slot.contentEntryCount() > 0) {
                        AEKeyType keyType = cellKeyType(slot);
                        if (keyType != null) {
                            uniqueTypes.add(keyType);
                        }
                    }
                }
            }
            if (op == CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION && !uniqueTypes.isEmpty()) {
                for (StorageEntry storage : state.storages()) {
                    if (isTempStorage(storage)) {
                        continue;
                    }
                    for (CellSlotEntry slot : storage.cellSlots()) {
                        if (slot.cellStack().isEmpty() || !isEmptyNonPartitionedCellCandidate(slot)) {
                            continue;
                        }
                        AEKeyType keyType = cellKeyType(slot);
                        if (keyType == null || !uniqueTypes.contains(keyType)) {
                            continue;
                        }
                        String key = toolCellSelectionKey(storage, slot);
                        if (!selectedCellKeys.add(key)) {
                            continue;
                        }
                        cells.add(new ContainerCellTerminal.ToolCellSlotSelection(storage, slot));
                    }
                }
            }
        }
        this.container.previewToolFromClient(op, cells, buses);
    }

    private CardsDisplay buildCards(CellSlotEntry slot,
                                    int y,
                                    CardsDisplay.CardClickCallback onClick) {
        if (!slot.upgradesLoaded() || slot.upgrades().isEmpty()) {
            return null;
        }
        List<CardsDisplay.CardEntry> entries = new ObjectArrayList<>();
        List<ItemStack> upgrades = slot.upgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            entries.add(new CardsDisplay.CardEntry(upgrades.get(i), i));
        }
        var cards = new CardsDisplay(
            CellTerminalLayout.CARDS_X, y, () -> entries, this.itemRender);
        cards.setClickCallback(onClick);
        return cards;
    }

    @Nullable
    private GenericStack carriedStack() {
        ItemStack carried = this.playerInventory.getItemStack();
        if (carried.isEmpty()) {
            return null;
        }
        return GenericStack.fromItemStack(carried);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        int rows = this.visibleRows;
        BACKGROUND.copy().src(0, 0, WIDTH, HEADER_BACKGROUND_HEIGHT)
                  .dest(offsetX, offsetY, WIDTH, HEADER_BACKGROUND_HEIGHT).blit();
        if (rows > 0) {
            BACKGROUND.copy().src(0, CONTENT_BACKGROUND_FIRST_ROW_SRC_Y, WIDTH, CONTENT_BACKGROUND_FIRST_ROW_HEIGHT)
                      .dest(offsetX, offsetY + CONTENT_BACKGROUND_FIRST_ROW_DEST_Y,
                          WIDTH, CONTENT_BACKGROUND_FIRST_ROW_HEIGHT).blit();
        }
        for (int i = 1; i < rows; i++) {
            BACKGROUND.copy().src(0, CONTENT_BACKGROUND_FILL_SRC_Y, WIDTH, ROW_HEIGHT)
                      .dest(offsetX, offsetY + CONTENT_BACKGROUND_FILL_DEST_Y + i * ROW_HEIGHT,
                          WIDTH, ROW_HEIGHT).blit();
        }
        BACKGROUND.copy().src(0, 158, WIDTH, FOOTER_HEIGHT)
                  .dest(offsetX, offsetY + HEADER_BACKGROUND_HEIGHT + rows * ROW_HEIGHT, WIDTH, FOOTER_HEIGHT)
                  .blit();
        drawTabs(offsetX, offsetY, mouseX, mouseY);
    }

    private void drawTabs(int offsetX, int offsetY, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();

        for (int i = 0; i < VISIBLE_TABS.length; i++) {
            CellTerminalTab tab = VISIBLE_TABS[i];
            int tabX = tabScreenX(offsetX, i);
            int tabY = tabScreenY(offsetY);
            boolean current = tab == this.container.getState().tab();
            boolean hovered = mouseX >= tabX && mouseX < tabX + TAB_WIDTH
                && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            boolean disabled = !isTabEnabled(tab);
            drawTabFrame(tabX, tabY, current, hovered, disabled);
            drawTabIcon(tab, tabX + 3, tabY + 3, disabled);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
    }

    private ItemStack tabIcon(CellTerminalTab tab) {
        return switch (tab) {
            case OVERVIEW -> AEParts.CELL_TERMINAL.stack();
            case CELL_CONTENT -> AEBlocks.ME_CHEST.stack();
            case CELL_PARTITION -> AEBlocks.CELL_WORKBENCH.stack();
            case TEMP_CELLS -> AEItems.ITEM_CELL_64K.stack();
            case BUS_CONTENT, BUS_PARTITION -> AEParts.STORAGE_BUS.stack();
            case SUBNETS -> AEParts.INTERFACE.stack();
            case NETWORK_TOOLS -> AEItems.NETWORK_TOOL.stack();
        };
    }

    private boolean isTabEnabled(CellTerminalTab tab) {
        EnumSet<CellTerminalTab> enabledTabs = this.container.getState().enabledTabs();
        return enabledTabs.contains(tab);
    }

    private void toggleTempCellSelection(StorageEntry storage, CellSlotEntry slot) {
        if (slot.cellStack().isEmpty()) {
            return;
        }
        String key = tempCellKey(storage, slot);
        if (!this.selectedTempCells.add(key)) {
            this.selectedTempCells.remove(key);
            return;
        }

        if (!selectedTempCellsHaveSameType(slot)) {
            this.selectedTempCells.remove(key);
        }
    }

    private boolean selectedTempCellsHaveSameType(CellSlotEntry selectedSlot) {
        ItemStack selectedStack = selectedSlot.cellStack();
        for (StorageEntry storage : this.container.getState().storages()) {
            if (!isTempStorage(storage)) {
                continue;
            }
            for (CellSlotEntry slot : storage.cellSlots()) {
                if (!this.selectedTempCells.contains(tempCellKey(storage, slot))) {
                    continue;
                }
                if (slot.cellStack().isEmpty() || slot.cellStack().getItem() != selectedStack.getItem()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void drawTabFrame(int tabX, int tabY, boolean current, boolean hovered, boolean disabled) {
        Icon background = disabled ? Icon.CELL_TERMINAL_TAB_DISABLED
            : current ? Icon.CELL_TERMINAL_TAB_SELECTED
              : hovered ? Icon.CELL_TERMINAL_TAB_HOVER : Icon.CELL_TERMINAL_TAB;
        background.getBlitter().dest(tabX, tabY).blit();
    }

    private void drawTabIcon(CellTerminalTab tab, int iconX, int iconY, boolean disabled) {
        if (tab == CellTerminalTab.BUS_CONTENT || tab == CellTerminalTab.BUS_PARTITION) {
            ItemStack overlayIcon = tab == CellTerminalTab.BUS_CONTENT
                ? AEBlocks.ME_CHEST.stack()
                : AEBlocks.CELL_WORKBENCH.stack();
            drawCompositeTabIcon(overlayIcon, AEParts.STORAGE_BUS.stack(), iconX, iconY, disabled);
            return;
        }
        renderTabItem(tabIcon(tab), iconX, iconY, disabled);
    }

    private void renderTabItem(ItemStack icon, int x, int y, boolean disabled) {
        if (icon.isEmpty()) {
            return;
        }
        float colorMod = disabled ? 0.4F : 1.0F;
        GlStateManager.color(colorMod, colorMod, colorMod, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        this.itemRender.renderItemIntoGUI(icon, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawCompositeTabIcon(ItemStack topLeftIcon, ItemStack bottomRightIcon, int x, int y,
                                      boolean disabled) {
        float colorMod = disabled ? 0.4F : 1.0F;
        int scaleFactor = new ScaledResolution(this.mc).getScaleFactor();
        int offset = 4;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(colorMod, colorMod, colorMod, 1.0F);
        try {
            if (!topLeftIcon.isEmpty()) {
                for (int row = 0; row < 16; row++) {
                    int stripWidth = Math.max(0, 14 - row);
                    if (stripWidth == 0) {
                        continue;
                    }
                    GL11.glScissor(
                        x * scaleFactor,
                        this.mc.displayHeight - ((y + row + 1) * scaleFactor),
                        stripWidth * scaleFactor,
                        scaleFactor);
                    this.itemRender.renderItemIntoGUI(topLeftIcon, x - offset, y - offset);
                }
            }
            if (!bottomRightIcon.isEmpty()) {
                for (int row = 0; row < 16; row++) {
                    int clipStart = Math.min(16, 17 - row);
                    int stripWidth = 16 - clipStart;
                    if (stripWidth <= 0) {
                        continue;
                    }
                    GL11.glScissor(
                        (x + clipStart) * scaleFactor,
                        this.mc.displayHeight - ((y + row + 1) * scaleFactor),
                        stripWidth * scaleFactor,
                        scaleFactor);
                    this.itemRender.renderItemIntoGUI(bottomRightIcon, x + offset, y + offset);
                }
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.disableLighting();
        }

        float lineColor = disabled ? 0.15F : 0.3F;
        GlStateManager.disableTexture2D();
        GlStateManager.color(lineColor, lineColor, lineColor, 1.0F);
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + 15, y + 1);
        GL11.glVertex2f(x + 1, y + 15);
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        int localX = mouseX - offsetX;
        int localY = mouseY - offsetY;
        this.fontRenderer.drawString(CellTerminal.getLocal(), 8, 6, 0x404040);
        drawControlsHelp();
        PriorityFieldManager.getInstance().resetVisibility();
        hideBusTextPartitionFields();
        this.rowList.buildVisibleRows(this.lineData, this.scrollbar.getCurrentScroll());
        this.rowList.draw(localX, localY);
        PriorityFieldManager.getInstance().drawFieldsRelative(this.guiLeft, this.guiTop);
        InlineRenameManager.getInstance().drawRenameField();
    }

    @Override
    public List<String> getItemToolTip(ItemStack stack) {
        List<String> lines = new ObjectArrayList<>(super.getItemToolTip(stack));
        if (isSupportedUpgradeInsertStack(stack)) {
            lines.add("");
            lines.add("§b" + tr("upgrade.tooltip_hint_click"));
            lines.add("§b" + tr("upgrade.tooltip_hint_shift_click"));
            lines.add(tr("upgrade.tooltip_hint_available_entries"));
            lines.add(tr("upgrade.tooltip_hint_entry_drive"));
            lines.add(tr("upgrade.tooltip_hint_entry_cells"));
            lines.add(tr("upgrade.tooltip_hint_entry_cell_lines"));
            lines.add(tr("upgrade.tooltip_hint_entry_storage_bus"));
        }
        return lines;
    }

    private List<String> getCellTerminalHoveredItemTooltip(ItemStack stack, int localX, int localY) {
        List<String> lines = new ObjectArrayList<>(getItemToolTip(stack));
        GenericStack preciseStack = hoveredPreciseBusPartitionStack(localX, localY);
        if (preciseStack != null) {
            lines.add(Tooltips.getAmountTooltipLocal(ButtonToolTips.Amount, preciseStack));
            lines.add(Tooltips.getSetAmountTooltipLocal());
        }
        return lines;
    }

    @Nullable
    private GenericStack hoveredPreciseBusPartitionStack(int localX, int localY) {
        for (IWidget widget : this.rowList.getVisibleRows()) {
            if (!(widget instanceof SlotsLine slotsLine) || !widget.isHovered(localX, localY)) {
                continue;
            }
            Object data = this.rowList.getWidgetDataMap().get(widget);
            if (!(data instanceof BusRowData row) || !row.partition() || !isPreciseBusPartition(row.bus())) {
                continue;
            }
            int slotIndex = slotsLine.getHoveredSlotIndex();
            if (slotIndex >= 0 && slotIndex < row.stacks().size()) {
                return row.stacks().get(slotIndex);
            }
        }
        return null;
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        int localX = mouseX - this.guiLeft;
        int localY = mouseY - this.guiTop;

        if (this.pendingToolConfirm != null) {
            drawToolConfirmOverlay(localX, localY);
            return;
        }

        if (this.searchOverlay != null && this.searchOverlay.isVisible()) {
            this.searchOverlay.setGuiBounds(getBounds(true));
            this.searchOverlay.draw();
        }
        SearchAssistLayout searchAssistLayout = drawSearchAssistOverlay();
        if (isMouseOverSearchLayer(mouseX, mouseY, searchAssistLayout)) {
            return;
        }

        List<String> priorityTooltip = PriorityFieldManager.getInstance().getTooltip(mouseX, mouseY);
        if (!priorityTooltip.isEmpty()) {
            drawTooltipLines(mouseX, mouseY, priorityTooltip);
            return;
        }

        List<String> rowTooltip = this.rowList.getTooltip(localX, localY);
        if (!rowTooltip.isEmpty()) {
            drawTooltipLines(mouseX, mouseY, rowTooltip);
            return;
        }

        ItemStack hovered = this.rowList.getHoveredItemStack(localX, localY);
        if (!hovered.isEmpty()) {
            drawItemTooltipWithImages(mouseX, mouseY, hovered,
                getCellTerminalHoveredItemTooltip(hovered, localX, localY));
            return;
        }

        List<String> tabTooltip = tabTooltip(localX, localY);
        if (!tabTooltip.isEmpty()) {
            drawTooltipLines(mouseX, mouseY, tabTooltip);
            return;
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private boolean isMouseOverSearchLayer(int mouseX, int mouseY, @Nullable SearchAssistLayout searchAssistLayout) {
        if (this.searchOverlay != null && this.searchOverlay.contains(mouseX, mouseY)) {
            return true;
        }
        return searchAssistLayout != null && searchAssistLayout.bounds().contains(mouseX, mouseY);
    }

    private List<String> tabTooltip(int localX, int localY) {
        for (int i = 0; i < VISIBLE_TABS.length; i++) {
            int tabX = tabLocalX(i);
            int tabY = tabLocalY();
            if (isInTab(localX, localY, tabX, tabY)) {
                return tabTooltip(VISIBLE_TABS[i]);
            }
        }
        return List.of();
    }

    private List<String> tabTooltip(CellTerminalTab tab) {
        return switch (tab) {
            case OVERVIEW -> List.of(tr("tab.overview.tooltip"));
            case CELL_CONTENT -> List.of(tr("tab.cellContent.tooltip"));
            case CELL_PARTITION -> List.of(tr("tab.cellPartition.tooltip"));
            case TEMP_CELLS -> List.of(tr("tab.tempCells.tooltip"));
            case BUS_CONTENT -> List.of(tr("tab.busContent.tooltip"));
            case BUS_PARTITION -> List.of(tr("tab.busPartition.tooltip"));
            case SUBNETS -> List.of(tabName(tab), "§7" + tr("subnet.overview.desc"));
            case NETWORK_TOOLS -> List.of(tr("tab.tools.tooltip"));
        };
    }

    private String tabName(CellTerminalTab tab) {
        return switch (tab) {
            case OVERVIEW -> tr("tab.overview");
            case CELL_CONTENT -> tr("tab.cellContent");
            case CELL_PARTITION -> tr("tab.cellPartition");
            case TEMP_CELLS -> tr("tab.tempCells");
            case BUS_CONTENT -> tr("tab.busContent");
            case BUS_PARTITION -> tr("tab.busPartition");
            case SUBNETS -> tr("tab.subnets");
            case NETWORK_TOOLS -> tr("tab.tools");
        };
    }

    private void drawControlsHelp() {
        ControlsHelpLayout layout = controlsHelpLayoutLocal();
        if (layout == null) {
            return;
        }

        Rectangle bounds = layout.bounds();
        int panelLeft = bounds.x;
        int panelTop = bounds.y;
        drawControlsHelpBackground(bounds);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int textY = panelTop + 4;
        for (String line : layout.wrappedLines()) {
            if (!line.isEmpty()) {
                this.fontRenderer.drawString(line, panelLeft + 4, textY, CellTerminalLayout.COLOR_TEXT_NORMAL);
            }
            textY += ControlsHelpLayout.LINE_HEIGHT;
        }
    }

    @Override
    public List<Rectangle> getExclusionZones() {
        List<Rectangle> zones = new ArrayList<>(super.getExclusionZones());
        ControlsHelpLayout layout = controlsHelpLayoutLocal();
        if (layout != null) {
            Rectangle bounds = layout.bounds();
            zones.add(new Rectangle(this.guiLeft + bounds.x, this.guiTop + bounds.y, bounds.width, bounds.height));
        }
        return zones;
    }

    @Nullable
    private ControlsHelpLayout controlsHelpLayoutLocal() {
        List<String> lines = helpLines(this.container.getState().tab());
        if (lines.isEmpty()) {
            return null;
        }

        final int margin = 4;
        int panelLeft = -this.guiLeft + margin;
        int panelRight = -margin;

        Rectangle toolbarBounds = getLeftToolbarBounds();
        if (toolbarBounds.width > 0 && toolbarBounds.height > 0) {
            panelRight = Math.min(panelRight, toolbarBounds.x - margin);
        }

        int panelWidth = panelRight - panelLeft;
        if (panelWidth < ControlsHelpLayout.MIN_WIDTH) {
            return null;
        }

        List<String> wrapped = new ObjectArrayList<>();
        int textWidth = panelWidth - 2 * ControlsHelpLayout.PADDING;
        for (String line : lines) {
            if (line.isEmpty()) {
                wrapped.add("");
            } else {
                wrapped.addAll(this.fontRenderer.listFormattedStringToWidth(line, textWidth));
            }
        }

        int panelHeight = wrapped.size() * ControlsHelpLayout.LINE_HEIGHT + 2 * ControlsHelpLayout.PADDING;
        int panelBottom = this.height - this.guiTop - 28;
        int panelTop = panelBottom - panelHeight;
        return new ControlsHelpLayout(new Rectangle(panelLeft, panelTop, panelWidth, panelHeight), wrapped);
    }

    private List<String> helpLines(CellTerminalTab tab) {
        List<String> lines = new ObjectArrayList<>();
        switch (tab) {
            case OVERVIEW -> {
                lines.add(tr("controls.double_click_storage_cell"));
                lines.add(tr("controls.right_click_rename"));
            }
            case CELL_CONTENT -> {
                lines.add(tr("controls.partition_indicator"));
                lines.add(tr("controls.click_partition_toggle"));
                lines.add(tr("controls.double_click_storage"));
                lines.add(tr("controls.right_click_rename"));
            }
            case CELL_PARTITION -> {
                lines.add(tr("controls.jei_drag"));
                lines.add(tr("controls.click_to_remove"));
                lines.add(tr("controls.double_click_storage"));
                lines.add(tr("controls.right_click_rename"));
            }
            case BUS_CONTENT -> {
                lines.add(tr("controls.filter_indicator"));
                lines.add(tr("controls.click_to_remove"));
                lines.add(tr("controls.double_click_storage"));
                lines.add(tr("controls.right_click_rename"));
            }
            case BUS_PARTITION -> {
                lines.add(tr("controls.storage_bus_capacity"));
                lines.add(tr("controls.jei_drag"));
                lines.add(tr("controls.click_to_remove"));
                lines.add(tr("controls.double_click_storage"));
                lines.add(tr("controls.right_click_rename"));
            }
            case TEMP_CELLS -> {
                lines.add(tr("controls.temp_area.drag_cell"));
                lines.add(tr("controls.temp_area.send_cell"));
                lines.add("");
                lines.add(tr("controls.jei_drag"));
                lines.add(tr("controls.click_to_remove"));
            }
            case SUBNETS -> {
                lines.add(tr("subnet.controls.title"));
                lines.add("");
                lines.add(tr("subnet.controls.click"));
                lines.add(tr("subnet.controls.dblclick"));
                lines.add(tr("subnet.controls.star"));
                lines.add(tr("subnet.controls.rename"));
                lines.add(tr("subnet.controls.esc"));
            }
            case NETWORK_TOOLS -> {
                lines.add(tr("networktools.warning.caution"));
                lines.add(tr("networktools.warning.irreversible"));
                lines.add("");
                lines.add(tr("networktools.help.read_tooltip"));
            }
        }
        return lines;
    }

    private String connectedToLine(@Nullable ITextComponent connectedDisplayName) {
        return connectedDisplayName != null
            ? AttachedTo.getLocal(connectedDisplayName.getFormattedText())
            : Unattached.getLocal();
    }

    private ToolConfirmLayout toolConfirmLayout() {
        var preview = this.container.getState().preview();
        String title = tr("networktools.confirm.title", toolName(this.pendingToolConfirm));
        String message = toolConfirmMessage(this.pendingToolConfirm, preview);
        int titleWidth = this.fontRenderer.getStringWidth(title) + TOOL_CONFIRM_PADDING * 2;
        int modalWidth = Math.clamp(titleWidth, TOOL_CONFIRM_MIN_W, TOOL_CONFIRM_MAX_W);
        int maxTextWidth = modalWidth - TOOL_CONFIRM_PADDING * 2;
        List<String> wrappedMessage = this.fontRenderer.listFormattedStringToWidth(message, maxTextWidth);
        int messageHeight = wrappedMessage.size() * (this.fontRenderer.FONT_HEIGHT + 2);
        int modalHeight = TOOL_CONFIRM_PADDING + TOOL_CONFIRM_TITLE_H + messageHeight
            + TOOL_CONFIRM_PADDING + TOOL_CONFIRM_BTN_H + TOOL_CONFIRM_PADDING;
        int modalX = (WIDTH - modalWidth) / 2;
        int modalY = (this.ySize - modalHeight) / 2;
        int buttonsY = modalY + modalHeight - TOOL_CONFIRM_BTN_H - TOOL_CONFIRM_PADDING;
        int confirmX = modalX + modalWidth / 2 - TOOL_CONFIRM_BTN_W - TOOL_CONFIRM_BTN_SPACING / 2;
        int cancelX = modalX + modalWidth / 2 + TOOL_CONFIRM_BTN_SPACING / 2;
        return new ToolConfirmLayout(modalX, modalY, modalWidth, modalHeight, wrappedMessage,
            confirmX, cancelX, buttonsY);
    }

    private String toolConfirmMessage(CellTerminalNetworkToolOperation op,
                                      @Nullable CellTerminalClientState.ToolPreview preview) {
        return switch (op) {
            case UNIQUE_TYPE_REALLOCATION -> tr("networktools.attribute_unique.confirm",
                requireUniqueTypeSummary(preview).uniqueTypeCount(),
                requireUniqueTypeSummary(preview).availableCellCount());
            case PARTITION_CELLS_BY_CONTENT -> tr("networktools.mass_partition_cell.confirm",
                requireTargetBreakdownTotal(preview));
            case PARTITION_STORAGE_BUSES_BY_CONTENT -> tr("networktools.mass_partition_bus.confirm",
                requireTargetBreakdownTotal(preview));
        };
    }

    private void drawToolConfirmOverlay(int mouseX, int mouseY) {
        ToolConfirmLayout layout = toolConfirmLayout();
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.guiLeft, this.guiTop, SEARCH_ASSIST_Z_LEVEL + 50.0F);
        try {
            GlStateManager.disableLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawToolConfirmPanelBackground(layout.x, layout.y, layout.width, layout.height);

            String title = tr("networktools.confirm.title", toolName(this.pendingToolConfirm));
            this.fontRenderer.drawString(title, layout.x + TOOL_CONFIRM_PADDING,
                layout.y + TOOL_CONFIRM_PADDING, CellTerminalLayout.COLOR_TEXT_NORMAL);

            int textY = layout.y + TOOL_CONFIRM_PADDING + TOOL_CONFIRM_TITLE_H;
            for (String line : layout.messageLines) {
                this.fontRenderer.drawString(line, layout.x + TOOL_CONFIRM_PADDING, textY,
                    CellTerminalLayout.COLOR_TEXT_NORMAL);
                textY += this.fontRenderer.FONT_HEIGHT + 2;
            }

            drawToolConfirmButton(layout.confirmX, layout.buttonsY, tr("networktools.confirm.do_it"), mouseX, mouseY,
                canExecuteToolPreview(this.container.getState().preview(), this.pendingToolConfirm));
            drawToolConfirmButton(layout.cancelX, layout.buttonsY, tr("networktools.confirm.cancel"), mouseX, mouseY,
                true);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawToolConfirmButton(int x, int y, String text, int mouseX, int mouseY, boolean enabled) {
        AE2Button button = new AE2Button(x, y, TOOL_CONFIRM_BTN_W, TOOL_CONFIRM_BTN_H,
            new TextComponentString(text), null);
        button.enabled = enabled;
        button.drawButton(this.mc, mouseX, mouseY, 0.0F);
    }

    private void confirmPendingTool() {
        var preview = this.container.getState().preview();
        if (canExecuteToolPreview(preview, this.pendingToolConfirm)) {
            this.container.executeToolFromClient(preview);
        }
        this.pendingToolConfirm = null;
    }

    private void cancelPendingToolConfirm() {
        this.pendingToolConfirm = null;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int localX = mouseX - this.guiLeft;
        int localY = mouseY - this.guiTop;
        if (this.pendingToolConfirm != null) {
            if (mouseButton == 0) {
                ToolConfirmLayout layout = toolConfirmLayout();
                if (isInRect(localX, localY, layout.confirmX, layout.buttonsY,
                    TOOL_CONFIRM_BTN_W, TOOL_CONFIRM_BTN_H)) {
                    confirmPendingTool();
                    return;
                }
                if (isInRect(localX, localY, layout.cancelX, layout.buttonsY,
                    TOOL_CONFIRM_BTN_W, TOOL_CONFIRM_BTN_H)
                    || !isInRect(localX, localY, layout.x, layout.y, layout.width, layout.height)) {
                    cancelPendingToolConfirm();
                    return;
                }
            }
            return;
        }
        if (handleBusTextPartitionClick(localX, localY, mouseButton)) {
            return;
        }
        submitFocusedBusTextPartitionIfOutside(localX, localY);
        InlineRenameManager.getInstance().handleClickOutside(localX, localY);
        if (this.searchOverlay != null && this.searchOverlay.handleMouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleSearchFieldClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (InlineRenameManager.getInstance().handleMouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (PriorityFieldManager.getInstance().handleClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleUpgradeCardClick(localX, localY, mouseButton)) {
            return;
        }
        if (handleTabClick(localX, localY, mouseButton)) {
            return;
        }
        if (this.rowList.handleClick(localX, localY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleSearchFieldClick(int mouseX, int mouseY, int mouseButton) {
        if (this.searchOverlay != null && this.searchOverlay.isVisible()) {
            return false;
        }
        if (!this.searchField.getVisible() || !this.searchField.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton == 1) {
            this.searchField.setTextFromClient("");
            this.searchField.setFocused(true);
            return true;
        }
        if (mouseButton == 0) {
            long now = System.currentTimeMillis();
            if (now - this.lastSearchClickTime <= 250L) {
                this.lastSearchClickTime = 0L;
                ensureSearchOverlay();
                if (this.searchOverlay != null) {
                    this.searchOverlay.open();
                }
                return true;
            }
            this.lastSearchClickTime = now;
        }
        return false;
    }

    private boolean handleTabClick(int localX, int localY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        for (int i = 0; i < VISIBLE_TABS.length; i++) {
            int tabX = tabLocalX(i);
            int tabY = tabLocalY();
            if (isInTab(localX, localY, tabX, tabY)) {
                CellTerminalTab tab = VISIBLE_TABS[i];
                if (!isTabEnabled(tab)) {
                    return true;
                }
                this.container.setTabFromClient(tab);
                this.scrollbar.setCurrentScroll(0);
                requestRebuild();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.pendingToolConfirm != null) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                confirmPendingTool();
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                cancelPendingToolConfirm();
                return;
            }
        }
        if (this.searchOverlay != null && this.searchOverlay.handleKeyTyped(typedChar, keyCode, this.searchAssist)) {
            return;
        }
        if (InlineRenameManager.getInstance().handleKey(typedChar, keyCode)) {
            return;
        }
        if (handleBusTextPartitionKey(typedChar, keyCode)) {
            return;
        }
        if (PriorityFieldManager.getInstance().handleKeyTyped(typedChar, keyCode)) {
            return;
        }
        if (this.searchOverlay == null || !this.searchOverlay.isVisible()) {
            if (keyCode == Keyboard.KEY_ESCAPE && this.searchField.isFocused()) {
                this.searchField.setFocused(false);
                return;
            }
            if (keyCode == Keyboard.KEY_TAB && this.searchField.isFocused()) {
                if (this.searchAssist.handleTab(this.searchField, isShiftKeyDown())) {
                    return;
                }
            }
        }
        if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    public List<PartitionGhostTarget> getPartitionGhostTargets(GenericStack stack) {
        List<PartitionGhostTarget> targets = new ObjectArrayList<>();
        this.ghostTargetData.clear();
        for (IWidget widget : this.rowList.getVisibleRows()) {
            if (!(widget instanceof SlotsLine slotsLine)) {
                continue;
            }
            Object data = this.rowList.getWidgetDataMap().get(widget);
            if (!isPartitionGhostTargetRow(data)) {
                continue;
            }
            for (SlotsLine.PartitionSlotTarget pt : slotsLine.getPartitionTargets()) {
                int key = targets.size();
                this.ghostTargetData.put(key, new GhostTargetData(data, pt.absoluteIndex()));
                targets.add(new PartitionGhostTarget(key, pt.area(), stack));
            }
        }
        return targets;
    }

    @Override
    @Optional.Method(modid = "jei")
    public <I> List<IGhostIngredientHandler.Target<I>> getHEITargets(I ingredient, int ghostMouseButton) {
        List<IGhostIngredientHandler.Target<I>> targets = super.getHEITargets(ingredient, ghostMouseButton);

        GenericStack stack = GenericIngredientHelper.ingredientToStack(ingredient);
        if (stack != null) {
            for (PartitionGhostTarget target : getPartitionGhostTargets(stack)) {
                targets.add(new CellTerminalPartitionTarget<>(this, target.slotIndex(), target.area(),
                    target.stack()));
            }
        }
        return targets;
    }

    public void acceptPartitionGhost(int targetKey, GenericStack stack) {
        GhostTargetData targetData = this.ghostTargetData.get(targetKey);
        if (targetData == null) {
            AELog.warn("Ignoring unknown Cell Terminal HEI ghost partition target key %d", targetKey);
            return;
        }
        Object data = targetData.rowData();
        int slotIndex = targetData.slotIndex();
        GenericStack added = new GenericStack(stack.what(), Math.max(1, stack.amount()));
        switch (data) {
            case CellSlotsRowData row ->
                this.container.addCellPartitionAtFromClient(row.storage(), row.slot(), slotIndex, added);
            case TempSlotsRowData row ->
                this.container.addCellPartitionAtFromClient(row.storage(), row.slot(), slotIndex, added);
            case BusRowData row -> this.container.addBusPartitionAtFromClient(row.bus(), slotIndex, added);
            case SubnetConnectionContentRowData row -> this.container.addSubnetConnectionPartitionAtFromClient(
                row.subnet(), row.connection(), row.connectionIndex(), slotIndex, added);
            default ->
                AELog.warn("Ignoring unsupported Cell Terminal HEI ghost partition row data %s for target key %d",
                    data == null ? "null" : data.getClass().getName(), targetKey);
        }
    }

    private boolean handleBusTextPartitionClick(int localX, int localY, int mouseButton) {
        for (IWidget widget : this.rowList.getVisibleRows()) {
            if (!(widget instanceof BusTextPartitionLine line) || !line.isHovered(localX, localY)) {
                continue;
            }
            return line.handleClick(localX, localY, mouseButton);
        }
        return false;
    }

    private void submitFocusedBusTextPartitionIfOutside(int localX, int localY) {
        boolean hoveringTextLine = false;
        for (IWidget widget : this.rowList.getVisibleRows()) {
            if (widget instanceof BusTextPartitionLine line && line.isHovered(localX, localY)) {
                hoveringTextLine = true;
                break;
            }
        }
        if (hoveringTextLine) {
            return;
        }
        submitFocusedBusTextPartition();
    }

    private boolean handleBusTextPartitionKey(char typedChar, int keyCode) {
        for (IWidget widget : this.rowList.getVisibleRows()) {
            if (!(widget instanceof BusTextPartitionLine line) || !line.isFocused()) {
                continue;
            }
            Object data = this.rowList.getWidgetDataMap().get(widget);
            if (!(data instanceof BusTextPartitionRowData row)) {
                return false;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                line.textField().setText(row.expression());
                line.clearFocus();
                return true;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                line.submitIfChanged(row.expression());
                line.clearFocus();
                return true;
            }
            return line.handleKeyTyped(typedChar, keyCode);
        }
        return false;
    }

    private void submitFocusedBusTextPartition() {
        for (IWidget widget : this.rowList.getVisibleRows()) {
            if (!(widget instanceof BusTextPartitionLine line) || !line.isFocused()) {
                continue;
            }
            Object data = this.rowList.getWidgetDataMap().get(widget);
            if (data instanceof BusTextPartitionRowData row) {
                line.submitIfChanged(row.expression());
            }
            line.clearFocus();
            return;
        }
    }

    private void hideBusTextPartitionFields() {
        for (AETextField field : this.busTextPartitionFields.values()) {
            field.setVisible(false);
        }
    }

    private boolean handleUpgradeCardClick(int localX, int localY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        ItemStack carried = this.playerInventory.getItemStack();
        if (!isSupportedUpgradeInsertStack(carried)) {
            return false;
        }
        if (isShiftKeyDown()) {
            this.container.installVisibleUpgradeFromClient();
            return true;
        }
        for (int i = this.rowList.getVisibleRows().size() - 1; i >= 0; i--) {
            IWidget widget = this.rowList.getVisibleRows().get(i);
            if (!widget.isHovered(localX, localY)) {
                continue;
            }
            Object data = this.rowList.getWidgetDataMap().get(widget);
            if (data == null || isUpgradeBlockedBySlotGrid(data, widget, localX, localY)) {
                return false;
            }
            return installUpgradeForRow(data);
        }
        return false;
    }

    private boolean isUpgradeBlockedBySlotGrid(Object data, IWidget widget, int localX, int localY) {
        if (widget instanceof SlotsLine slotsLine
            && slotGridClickTakesPriority(getUpgradeGridPriorityRowKind(data))) {
            return slotsLine.isMouseOverSlotGrid(localX, localY);
        }
        return false;
    }

    private boolean installUpgradeForRow(Object data) {
        if (data instanceof StorageHeaderData data1) {
            StorageEntry storage = data1.storage();
            this.container.installTargetUpgradeFromClient(storage, null, null);
            return true;
        }
        if (data instanceof TempCellRowData(StorageEntry storage1, CellSlotEntry slot1)) {
            this.container.installTargetUpgradeFromClient(storage1, slot1, null);
            return true;
        }
        if (data instanceof TempSlotsRowData row) {
            this.container.installTargetUpgradeFromClient(row.storage(), row.slot(), null);
            return true;
        }
        if (data instanceof CellLineData(StorageEntry storage, CellSlotEntry slot)) {
            this.container.installTargetUpgradeFromClient(storage, slot, null);
            return true;
        }
        if (data instanceof CellSlotsRowData row) {
            this.container.installTargetUpgradeFromClient(row.storage(), row.slot(), null);
            return true;
        }
        if (data instanceof BusHeaderData data1) {
            CellTerminalClientState.BusEntry bus = data1.bus();
            this.container.installTargetUpgradeFromClient(null, null, bus);
            return true;
        }
        if (data instanceof BusRowData row) {
            this.container.installTargetUpgradeFromClient(null, null, row.bus());
            return true;
        }
        return false;
    }

    private void cycleVisibleKeyTypes() {
        var keyTypes = this.container.getClientKeyTypeSelection();
        Set<AEKeyType> next = nextKeyTypeSelection(keyTypes);
        for (AEKeyType keyType : next) {
            this.container.selectKeyType(this.container.windowId, keyType, true);
        }
        for (AEKeyType keyType : keyTypes.enabledSet()) {
            if (!next.contains(keyType)) {
                this.container.selectKeyType(this.container.windowId, keyType, false);
            }
        }
        requestRebuild();
    }

    enum UpgradeGridPriorityRowKind {
        CELL_SLOTS,
        TEMP_SLOTS,
        BUS_SLOTS,
        OTHER
    }

    private record ControlsHelpLayout(Rectangle bounds, List<String> wrappedLines) {
        private static final int MIN_WIDTH = 60;
        private static final int PADDING = 4;
        private static final int LINE_HEIGHT = 10;
    }

    private record SearchAssistLayout(Rectangle bounds, List<String> lines) {
    }

    public record PartitionGhostTarget(int slotIndex, Rectangle area, GenericStack stack) {
    }

    private record GhostTargetData(Object rowData, int slotIndex) {
    }

    private record StorageVisibility(boolean headerMatched, boolean childMatched, List<CellSlotEntry> visibleSlots) {
        private boolean showHeader() {
            return this.headerMatched || this.childMatched;
        }

        private boolean forceExpanded() {
            return this.childMatched;
        }
    }

    private record BusVisibility(boolean headerMatched, boolean childMatched,
                                 List<BusTextPartitionRowData> visibleTextRows) {
        private boolean showHeader() {
            return this.headerMatched || this.childMatched;
        }

        private boolean forceExpanded() {
            return this.childMatched;
        }
    }

    private record StorageHeaderData(StorageEntry storage, boolean forceExpanded) {
    }

    private record CellLineData(StorageEntry storage, CellSlotEntry slot) {
    }

    private record TempCellRowData(StorageEntry storage, CellSlotEntry slot) {
    }

    private record TempSlotsRowData(StorageEntry storage, CellSlotEntry slot, List<GenericStack> stacks,
                                    boolean partition, int startIndex, boolean first) {
    }

    private record CellSlotsRowData(StorageEntry storage, CellSlotEntry slot, List<GenericStack> stacks,
                                    boolean partition, int startIndex, boolean first) {
    }

    private record BusRowData(CellTerminalClientState.BusEntry bus, List<GenericStack> stacks, boolean partition,
                              int startIndex, boolean first) {
    }

    private record BusTextPartitionRowData(CellTerminalClientState.BusEntry bus,
                                           String fieldId,
                                           String label,
                                           String expression,
                                           String placeholder) {
    }

    private record BusHeaderData(CellTerminalClientState.BusEntry bus, boolean forceExpanded) {
    }

    private record SubnetRowData(CellTerminalClientState.SubnetEntry subnet) {
    }

    private record SubnetConnectionRowData(CellTerminalClientState.SubnetEntry subnet,
                                           CellTerminalClientState.ConnectionEntry connection, int connectionIndex) {
    }

    private record SubnetConnectionContentRowData(CellTerminalClientState.SubnetEntry subnet,
                                                  CellTerminalClientState.ConnectionEntry connection,
                                                  int connectionIndex, List<GenericStack> stacks, boolean partition,
                                                  int startIndex, boolean first) {
    }

    private record ToolRowData(CellTerminalNetworkToolOperation operation) {
    }

    private record ToolConfirmLayout(int x, int y, int width, int height, List<String> messageLines, int confirmX,
                                     int cancelX, int buttonsY) {
    }

    private static final class CellTerminalSlotLimitButton extends SettingToggleButton<CellTerminalSlotLimit> {
        private CellTerminalSlotLimitButton(CellTerminalSlotLimit value,
                                            IHandler<SettingToggleButton<CellTerminalSlotLimit>> onPress) {
            super(Settings.CELL_TERMINAL_SLOT_LIMIT, value, onPress);
        }

        private static String slotLimitLabel(CellTerminalSlotLimit value) {
            return value.label();
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            super.drawButton(minecraft, mouseX, mouseY, partialTicks);
            if (!this.visible) {
                return;
            }

            String label = slotLimitLabel(getCurrentValue());
            float scale = switch (getCurrentValue()) {
                case LIMIT_8 -> 1.0F;
                case LIMIT_32, LIMIT_64 -> 0.9F;
                case UNLIMITED -> 0.85F;
            };

            GlStateManager.pushMatrix();
            try {
                float textWidth = minecraft.fontRenderer.getStringWidth(label) * scale;
                float textHeight = minecraft.fontRenderer.FONT_HEIGHT * scale;
                float textX = this.x + (this.width - textWidth) * 0.5F;
                float textY = this.y + 1.0F + (this.height - textHeight) * 0.5F;
                GlStateManager.translate(textX, textY, 6.0F);
                GlStateManager.scale(scale, scale, 1.0F);
                minecraft.fontRenderer.drawStringWithShadow(label, 0.0F, 0.0F, 0xFFFFFF);
            } finally {
                GlStateManager.popMatrix();
            }
        }

        @Override
        protected Icon getIcon() {
            return null;
        }

        @Override
        protected void openSelectionPopup(AEBaseGui<?> gui) {
            List<GridSelectionPopup.Entry<CellTerminalSlotLimit>> entries =
                new ObjectArrayList<>(getValidValues().size());
            for (CellTerminalSlotLimit value : getValidValues()) {
                var appearance = getAppearance(value);
                List<ITextComponent> tooltipLines = appearance == null ? List.of() : appearance.tooltipLines();
                entries.add(GridSelectionPopup.Entry.text(value, slotLimitLabel(value), tooltipLines));
            }

            var bounds = gui.getBounds(false);
            gui.openSelectionPopup(GridSelectionPopup.forButton(this, gui.getGuiLeft(), gui.getGuiTop(), bounds.width,
                bounds.height, entries, this::setValueDirect));
        }
    }

    private final class BusPriorityAdapter implements Prioritizable {
        private final CellTerminalClientState.BusEntry bus;

        private BusPriorityAdapter(CellTerminalClientState.BusEntry bus) {
            this.bus = bus;
        }

        @Override
        public String getIdentityKey() {
            return "bus:" + bus.stableTargetId() + "|" + bus.locator();
        }

        @Override
        public int getPriority() {
            return bus.priority();
        }

        @Override
        public boolean supportsPriority() {
            return true;
        }

        @Override
        public void commitPriority(int priority) {
            container.writeBusPriorityFromClient(bus, priority);
        }
    }

    private final class SubnetRenameAdapter implements Renameable {
        private final CellTerminalClientState.SubnetEntry subnet;

        private SubnetRenameAdapter(CellTerminalClientState.SubnetEntry subnet) {
            this.subnet = subnet;
        }

        @Override
        public boolean isRenameable() {
            return true;
        }

        @Override
        public String getCurrentName() {
            return subnet.renamedDisplayName() == null ? "" : subnet.renamedDisplayName();
        }

        @Override
        public String getIdentityKey() {
            return "subnet:" + subnet.stableTargetId() + "|" + subnet.subnetId();
        }

        @Override
        public void commitName(String name) {
            container.renameSubnetFromClient(subnet, name);
        }
    }

    private final class StorageRenameAdapter implements Renameable {
        private final StorageEntry storage;

        private StorageRenameAdapter(StorageEntry storage) {
            this.storage = storage;
        }

        @Override
        public boolean isRenameable() {
            return true;
        }

        @Override
        public String getCurrentName() {
            return storage.renamedDisplayName() == null ? "" : storage.renamedDisplayName();
        }

        @Override
        public String getIdentityKey() {
            return "storageName:" + storage.stableTargetId() + "|" + storage.locator();
        }

        @Override
        public void commitName(String name) {
            container.renameStorageFromClient(storage, name);
        }
    }

    private final class BusRenameAdapter implements Renameable {
        private final CellTerminalClientState.BusEntry bus;

        private BusRenameAdapter(CellTerminalClientState.BusEntry bus) {
            this.bus = bus;
        }

        @Override
        public boolean isRenameable() {
            return true;
        }

        @Override
        public String getCurrentName() {
            return bus.renamedDisplayName() == null ? "" : bus.renamedDisplayName();
        }

        @Override
        public String getIdentityKey() {
            return "busName:" + bus.stableTargetId() + "|" + bus.locator();
        }

        @Override
        public void commitName(String name) {
            container.renameBusFromClient(bus, name);
        }
    }

    private final class StoragePriorityAdapter implements Prioritizable {
        private final StorageEntry storage;

        private StoragePriorityAdapter(StorageEntry storage) {
            this.storage = storage;
        }

        @Override
        public String getIdentityKey() {
            return "storage:" + storage.stableTargetId() + "|" + storage.locator();
        }

        @Override
        public int getPriority() {
            return storage.priority();
        }

        @Override
        public boolean supportsPriority() {
            return true;
        }

        @Override
        public void commitPriority(int priority) {
            container.writeStoragePriorityFromClient(storage, priority);
        }
    }

    private final class TypeFilterButton extends IconButton {
        private TypeFilterButton(KeyTypeSelectionWindow<ContainerCellTerminal> window) {
            super(() -> {
                if (isShiftKeyDown()) {
                    cycleVisibleKeyTypes();
                } else {
                    window.toggle();
                }
            });
            setMessage(ConfigureVisibleTypes.text());
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            StringJoiner joiner = new StringJoiner(", ");
            for (AEKeyType keyType : container.getClientKeyTypeSelection().enabledSet()) {
                joiner.add(keyType.getDescription().getFormattedText());
            }
            return List.of(ConfigureVisibleTypes.text(), new TextComponentString(joiner.toString()));
        }

        @Override
        protected Icon getIcon() {
            return Icon.TYPE_FILTER_ALL;
        }
    }
}
