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

package ae2.client.gui.widgets;

import ae2.api.config.ActionItems;
import ae2.client.gui.Icon;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.LocalizationEnum;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class ActionButton extends IconButton {
    private static final Pattern PATTERN_NEW_LINE = Pattern.compile("\\\\n", Pattern.LITERAL);
    private final Icon icon;
    private final List<ITextComponent> tooltip;

    public ActionButton(ActionItems action, Runnable onPress) {
        this(action, ignored -> onPress.run());
    }

    public ActionButton(ActionItems action, Consumer<ActionItems> onPress) {
        super(() -> onPress.accept(action));
        LocalizationEnum title;
        @Nullable LocalizationEnum detail;
        switch (action) {
            case COG -> {
                this.icon = Icon.COG;
                title = ButtonToolTips.PartitionStorage;
                detail = ButtonToolTips.PartitionStorageHint;
            }
            case CLOSE -> {
                this.icon = Icon.CLEAR;
                title = ButtonToolTips.Clear;
                detail = ButtonToolTips.ClearSettings;
            }
            case S_CLOSE -> {
                this.icon = Icon.S_CLEAR;
                title = ButtonToolTips.Clear;
                detail = ButtonToolTips.ClearSettings;
            }
            case STASH -> {
                this.icon = Icon.ARROW_UP;
                title = ButtonToolTips.Stash;
                detail = ButtonToolTips.StashDesc;
            }
            case S_STASH -> {
                this.icon = Icon.S_ARROW_UP;
                title = ButtonToolTips.Stash;
                detail = ButtonToolTips.StashDesc;
            }
            case STASH_TO_PLAYER_INV -> {
                this.icon = Icon.ARROW_DOWN;
                title = ButtonToolTips.StashToPlayer;
                detail = ButtonToolTips.StashToPlayerDesc;
            }
            case S_STASH_TO_PLAYER_INV -> {
                this.icon = Icon.S_ARROW_DOWN;
                title = ButtonToolTips.StashToPlayer;
                detail = ButtonToolTips.StashToPlayerDesc;
            }
            case ENCODE -> {
                this.icon = Icon.WHITE_ARROW_DOWN;
                title = ButtonToolTips.Encode;
                detail = ButtonToolTips.EncodeDescription;
            }
            case CYCLE_PROCESSING_OUTPUT -> {
                this.icon = Icon.SCHEDULING_DEFAULT;
                title = ButtonToolTips.CycleProcessingOutput;
                detail = ButtonToolTips.CycleProcessingOutputTooltip;
            }
            case S_CYCLE_PROCESSING_OUTPUT -> {
                this.icon = Icon.S_CYCLE;
                title = ButtonToolTips.CycleProcessingOutput;
                detail = ButtonToolTips.CycleProcessingOutputTooltip;
            }
            case S_CLEAR_PROCESSING_SECONDARY_OUTPUTS -> {
                this.icon = Icon.S_CLEAR;
                title = ButtonToolTips.ClearProcessingSecondaryOutputs;
                detail = ButtonToolTips.ClearProcessingSecondaryOutputsTooltip;
            }
            case TERMINAL_SETTINGS -> {
                this.icon = Icon.COG;
                title = ButtonToolTips.TerminalSettings;
                detail = null;
            }
            case WORK_INTERVAL -> {
                this.icon = Icon.COG;
                title = GuiText.WorkInterval;
                detail = GuiText.WorkIntervalHint;
            }
            case OUTPUT_SIDES -> {
                this.icon = Icon.OUTPUT_SIDE_CONFIG;
                title = ButtonToolTips.OutputSideConfig;
                detail = ButtonToolTips.OutputSideConfigHint;
            }
            case OUTPUT_SIDES_CLEAR -> {
                this.icon = Icon.CLEAR;
                title = ButtonToolTips.OutputSideClear;
                detail = ButtonToolTips.OutputSideClearHint;
            }
            case PATTERN_IMPORT_PRIORITIES -> {
                this.icon = Icon.PRIORITY;
                title = GuiText.PatternImportPrioritiesTitle;
                detail = null;
            }
            case PORTABLE_CELL_PICKUP_FILTER_CLEAR -> {
                this.icon = Icon.CLEAR;
                title = GuiText.PortableCellPickupFilterClear;
                detail = null;
            }
            default -> throw new IllegalArgumentException("Unknown ActionItem: " + action);
        }

        this.tooltip = buildTooltip(title, detail);
        setMessage(this.tooltip.getFirst());
    }

    private static List<ITextComponent> buildTooltip(LocalizationEnum title, @Nullable LocalizationEnum detail) {
        if (detail == null) {
            return Collections.singletonList(title.text());
        }

        String text = detail.getLocal();
        text = PATTERN_NEW_LINE.matcher(text).replaceAll("\n");
        List<String> wrappedLines = wrapTooltip(text);
        List<ITextComponent> lines = new ObjectArrayList<>(wrappedLines.size() + 1);
        lines.add(title.text());
        for (String line : wrappedLines) {
            lines.add(new TextComponentString(line));
        }

        return lines;
    }

    private static List<String> wrapTooltip(String text) {
        if (text.isEmpty()) {
            return Collections.singletonList("");
        }

        List<String> lines = new ObjectArrayList<>();
        for (String rawLine : text.split("\n", -1)) {
            if (rawLine.length() <= 30) {
                lines.add(rawLine);
                continue;
            }

            String remaining = rawLine;
            while (remaining.length() > 30) {
                int split = remaining.lastIndexOf(' ', 30);
                if (split <= 0) {
                    split = 30;
                }
                lines.add(remaining.substring(0, split));
                remaining = remaining.substring(split).trim();
            }
            if (!remaining.isEmpty()) {
                lines.add(remaining);
            }
        }
        return lines;
    }

    @Override
    public @NonNull List<ITextComponent> getTooltipMessage() {
        return this.tooltip;
    }

    @Override
    protected Icon getIcon() {
        return this.icon;
    }
}
