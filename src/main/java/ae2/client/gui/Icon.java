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

import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.IconAtlas;
import ae2.core.AppEng;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class Icon {
    private static final Set<Icon> registeredIcons = new ObjectOpenHashSet<>();

    public static final Icon REDSTONE_LOW = registerBuiltin("redstone_low", 16, 16);
    public static final Icon REDSTONE_HIGH = registerBuiltin("redstone_high", 16, 16);
    public static final Icon REDSTONE_PULSE = registerBuiltin("redstone_pulse", 16, 16);
    public static final Icon REDSTONE_IGNORE = registerBuiltin("redstone_ignore", 16, 16);
    public static final Icon REDSTONE_OFF = registerBuiltin("redstone_off", 16, 16);
    public static final Icon REDSTONE_ON = registerBuiltin("redstone_on", 16, 16);
    public static final Icon REDSTONE_ABOVE_EQUAL = registerBuiltin("redstone_above_equal", 16, 16);
    public static final Icon REDSTONE_BELOW = registerBuiltin("redstone_below", 16, 16);
    public static final Icon CLEAR = registerBuiltin("clear", 16, 16);
    public static final Icon ENTER = registerBuiltin("enter", 16, 16);
    public static final Icon WHITE_ARROW_DOWN = registerBuiltin("white_arrow_down", 16, 16);
    public static final Icon LOCKED = registerBuiltin("locked", 16, 16);
    public static final Icon UNLOCKED = registerBuiltin("unlocked", 16, 16);
    public static final Icon HELP = registerBuiltin("help", 16, 16);
    public static final Icon BACKGROUND_PRIMARY_OUTPUT = registerBuiltin("background_primary_output", 16, 16);
    public static final Icon BACKGROUND_STORAGE_CELL = registerBuiltin("background_storage_cell", 16, 16);
    public static final Icon CELL_RESTRICTION = registerBuiltin("cell_restriction", 16, 16);
    public static final Icon GENERIC_RESOURCE_PACKAGE_FRAME = registerBuiltin("generic_resource_package_frame", 16, 16);

    public static final Icon VIEW_MODE_STORED = registerBuiltin("view_mode_stored", 16, 16);
    public static final Icon VIEW_MODE_ALL = registerBuiltin("view_mode_all", 16, 16);
    public static final Icon VIEW_MODE_CRAFTING = registerBuiltin("view_mode_crafting", 16, 16);
    public static final Icon BLOCKING_MODE_NO = registerBuiltin("blocking_mode_no", 16, 16);
    public static final Icon BLOCKING_MODE_YES = registerBuiltin("blocking_mode_yes", 16, 16);
    public static final Icon BLOCKING_MODE_STRONG = registerBuiltin("blocking_mode_strong", 16, 16);
    public static final Icon BLOCKING_MODE_TYPE_NORMAL = registerBuiltin("blocking_mode_type_normal", 16, 16);
    public static final Icon BLOCKING_MODE_TYPE_SMART = registerBuiltin("blocking_mode_type_smart", 16, 16);


    public static final Icon BACK = registerBuiltin("back", 16, 16);
    public static final Icon TRANSPARENT_FACADES_OFF = registerBuiltin("transparent_facades_off", 16, 16);
    public static final Icon TYPE_FILTER_ALL = registerBuiltin("type_filter_all", 16, 16);
    public static final Icon BACKGROUND_ORE = registerBuiltin("background_ore", 16, 16);

    public static final Icon SEARCH_AUTO_FOCUS = registerBuiltin("search_auto_focus", 16, 16);
    public static final Icon BACKGROUND_PLATE = registerBuiltin("background_plate", 16, 16);
    public static final Icon TAB_CRAFTING = registerBuiltin("tab_crafting", 16, 16);
    public static final Icon TAB_PROCESSING = registerBuiltin("tab_processing", 16, 16);

    public static final Icon ARROW_UP = registerBuiltin("arrow_up", 16, 16);
    public static final Icon PATTERN_UPLOAD = registerBuiltin("pattern_upload", 16, 16);
    public static final Icon ARROW_DOWN = registerBuiltin("arrow_down", 16, 16);
    public static final Icon ARROW_RIGHT = registerBuiltin("arrow_right", 16, 16);
    public static final Icon ARROW_LEFT = registerBuiltin("arrow_left", 16, 16);
    public static final Icon STORAGE_FILTER_EXTRACTABLE_ONLY = registerBuiltin("storage_filter_extractable_only", 16, 16);
    public static final Icon STORAGE_FILTER_EXTRACTABLE_NONE = registerBuiltin("storage_filter_extractable_none", 16, 16);
    public static final Icon FILTER_ON_EXTRACT_ENABLED = registerBuiltin("filter_on_extract_enabled", 16, 16);
    public static final Icon FILTER_ON_EXTRACT_DISABLED = registerBuiltin("filter_on_extract_disabled", 16, 16);
    public static final Icon BACKGROUND_INGOT = registerBuiltin("background_ingot", 16, 16);
    public static final Icon BACKGROUND_STORAGE_COMPONENT = registerBuiltin("background_storage_component", 16, 16);

    public static final Icon SORT_BY_NAME = registerBuiltin("sort_by_name", 16, 16);
    public static final Icon SORT_BY_AMOUNT = registerBuiltin("sort_by_amount", 16, 16);
    public static final Icon CRAFTING_PLAN_SORT_AVAILABILITY = registerBuiltin("crafting_plan_sort_availability", 16, 16);
    public static final Icon CRAFTING_PLAN_SORT_USED_COUNT = registerBuiltin("crafting_plan_sort_used_count", 16, 16);
    public static final Icon CRAFTING_PLAN_SORT_USED_PERCENT = registerBuiltin("crafting_plan_sort_used_percent", 16, 16);
    public static final Icon CRAFTING_PLAN_SORT_ASCENDING = registerBuiltin("crafting_plan_sort_ascending", 16, 16);
    public static final Icon CRAFTING_PLAN_SORT_DESCENDING = registerBuiltin("crafting_plan_sort_descending", 16, 16);
    public static final Icon COG = registerBuiltin("cog", 16, 16);
    public static final Icon WIRELESS_SETTINGS_PAGE = registerBuiltin("wireless_settings_page", 16, 16);
    public static final Icon COG_DISABLED = registerBuiltin("cog_disabled", 16, 16);
    public static final Icon SORT_BY_INVENTORY_TWEAKS = registerBuiltin("sort_by_inventory_tweaks", 16, 16);
    public static final Icon SORT_BY_MOD = registerBuiltin("sort_by_mod", 16, 16);
    public static final Icon SORT_BY_HEI = registerBuiltin("sort_by_hei", 16, 16);
    public static final Icon PRIORITY = registerBuiltin("priority", 16, 16);
    public static final Icon PLAYER_PIN = registerBuiltin("player_pin", 16, 16);
    public static final Icon BACKGROUND_VIEW_CELL = registerBuiltin("background_view_cell", 16, 16);
    public static final Icon BACKGROUND_WIRELESS_TERM = registerBuiltin("background_wireless_term", 16, 16);

    public static final Icon FULLNESS_EMPTY = registerBuiltin("fullness_empty", 16, 16);
    public static final Icon FULLNESS_HALF = registerBuiltin("fullness_half", 16, 16);
    public static final Icon FULLNESS_FULL = registerBuiltin("fullness_full", 16, 16);
    public static final Icon PATTERN_ACCESS_SHOW = registerBuiltin("pattern_access_show", 16, 16);
    public static final Icon PATTERN_ACCESS_HIDE = registerBuiltin("pattern_access_hide", 16, 16);
    public static final Icon PATTERN_AUTO_FILL_OFF = registerBuiltin("pattern_auto_fill_off", 16, 16);
    public static final Icon PATTERN_AUTO_FILL_ON = registerBuiltin("pattern_auto_fill_on", 16, 16);
    public static final Icon PATTERN_TERMINAL_VISIBLE = registerBuiltin("pattern_terminal_visible", 16, 16);
    public static final Icon PATTERN_TERMINAL_HIDDEN = registerBuiltin("pattern_terminal_hidden", 16, 16);
    public static final Icon PATTERN_TERMINAL_ALL = registerBuiltin("pattern_terminal_all", 16, 16);
    public static final Icon PATTERN_TERMINAL_NOT_FULL = registerBuiltin("pattern_terminal_not_full", 16, 16);
    public static final Icon PATTERN_PROVIDER_INSERTION_DEFAULT = registerBuiltin(
        "pattern_provider_insertion_default", 16, 16);
    public static final Icon PATTERN_PROVIDER_INSERTION_PREFER_EMPTY = registerBuiltin(
        "pattern_provider_insertion_prefer_empty", 16, 16);
    public static final Icon PATTERN_PROVIDER_INSERTION_EMPTY_ONLY = registerBuiltin(
        "pattern_provider_insertion_empty_only", 16, 16);
    public static final Icon PATTERN_PROVIDER_OUTPUT_SIDE_SINGLE = registerBuiltin(
        "pattern_provider_output_side_single", 16, 16);
    public static final Icon PATTERN_PROVIDER_OUTPUT_SIDE_SPLIT_BY_INGREDIENTS_TYPE = registerBuiltin(
        "pattern_provider_output_side_split_by_ingredients_type", 16, 16);
    public static final Icon BACKGROUND_TRASH = registerBuiltin("background_trash", 16, 16);

    public static final Icon FUZZY_PERCENT_25 = registerBuiltin("fuzzy_percent_25", 16, 16);
    public static final Icon FUZZY_PERCENT_50 = registerBuiltin("fuzzy_percent_50", 16, 16);
    public static final Icon FUZZY_PERCENT_75 = registerBuiltin("fuzzy_percent_75", 16, 16);
    public static final Icon FUZZY_PERCENT_99 = registerBuiltin("fuzzy_percent_99", 16, 16);
    public static final Icon FUZZY_IGNORE = registerBuiltin("fuzzy_ignore", 16, 16);
    public static final Icon INSCRIBER_SEPARATE_SIDES = registerBuiltin("inscriber_separate_sides", 16, 16);
    public static final Icon INSCRIBER_COMBINED_SIDES = registerBuiltin("inscriber_combined_sides", 16, 16);
    public static final Icon AUTO_EXPORT_OFF = registerBuiltin("auto_export_off", 16, 16);
    public static final Icon AUTO_EXPORT_ON = registerBuiltin("auto_export_on", 16, 16);
    public static final Icon OUTPUT_SIDE_CONFIG = registerBuiltin("output_side_config", 16, 16);
    public static final Icon INSCRIBER_BUFFER_4 = registerBuiltin("inscriber_buffer_4", 16, 16);
    public static final Icon INSCRIBER_BUFFER_64 = registerBuiltin("inscriber_buffer_64", 16, 16);
    public static final Icon INSCRIBER_BUFFER_1 = registerBuiltin("inscriber_buffer_1", 16, 16);
    public static final Icon BACKGROUND_WIRELESS_BOOSTER = registerBuiltin("background_wireless_booster", 16, 16);

    public static final Icon CONDENSER_OUTPUT_TRASH = registerBuiltin("condenser_output_trash", 16, 16);
    public static final Icon CONDENSER_OUTPUT_MATTER_BALL = registerBuiltin("condenser_output_matter_ball", 16, 16);
    public static final Icon CONDENSER_OUTPUT_SINGULARITY = registerBuiltin("condenser_output_singularity", 16, 16);
    public static final Icon BACKGROUND_ENCODED_PATTERN = registerBuiltin("background_encoded_pattern", 16, 16);

    public static final Icon INVALID = registerBuiltin("invalid", 16, 16);
    public static final Icon HORIZONTAL_TAB = registerBuiltin("horizontal_tab", 22, 22);
    public static final Icon HORIZONTAL_TAB_SELECTED = registerBuiltin("horizontal_tab_selected", 22, 22);
    public static final Icon HORIZONTAL_TAB_FOCUS = registerBuiltin("horizontal_tab_focus", 22, 22);
    public static final Icon BACKGROUND_BLANK_PATTERN = registerBuiltin("background_blank_pattern", 16, 16);
    public static final Icon TOOLBAR_BUTTON_BACKGROUND = registerBuiltin("toolbar_button_background", 18, 20);
    public static final Icon TOOLBAR_BUTTON_BACKGROUND_FOCUS = registerBuiltin("toolbar_button_background_focus", 18, 20);
    public static final Icon TOOLBAR_BUTTON_BACKGROUND_HOVER = registerBuiltin("toolbar_button_background_hover", 18, 20);

    public static final Icon ACCESS_WRITE = registerBuiltin("access_write", 16, 16);
    public static final Icon ACCESS_READ = registerBuiltin("access_read", 16, 16);
    public static final Icon ACCESS_READ_WRITE = registerBuiltin("access_read_write", 16, 16);
    public static final Icon REGULATE_STOCK_ON = registerBuiltin("regulate_stock_on", 16, 16);
    public static final Icon REGULATE_STOCK_OFF = registerBuiltin("regulate_stock_off", 16, 16);
    public static final Icon CRAFT_HAMMER = registerBuiltin("craft_hammer", 16, 16);
    public static final Icon BACKGROUND_CHARGABLE = registerBuiltin("background_chargable", 16, 16);

    public static final Icon POWER_UNIT_AE = registerBuiltin("power_unit_ae", 16, 16);
    public static final Icon POWER_UNIT_EU = registerBuiltin("power_unit_eu", 16, 16);
    public static final Icon POWER_UNIT_J = registerBuiltin("power_unit_j", 16, 16);
    public static final Icon POWER_UNIT_W = registerBuiltin("power_unit_w", 16, 16);
    public static final Icon POWER_UNIT_RF = registerBuiltin("power_unit_rf", 16, 16);
    public static final Icon POWER_UNIT_TR = registerBuiltin("power_unit_tr", 16, 16);
    public static final Icon BACKGROUND_SINGULARITY = registerBuiltin("background_singularity", 16, 16);

    public static final Icon COPY_MODE_ON = registerBuiltin("copy_mode_on", 16, 16);
    public static final Icon BACKGROUND_SPATIAL_CELL_NO_SHADOW = registerBuiltin("background_spatial_cell_no_shadow", 16, 16);
    public static final Icon BACKGROUND_SPATIAL_CELL = registerBuiltin("background_spatial_cell", 16, 16);

    public static final Icon COPY_MODE_OFF = registerBuiltin("copy_mode_off", 16, 16);
    public static final Icon TAB_BUTTON_BACKGROUND_BORDERLESS = registerBuiltin("tab_button_background_borderless", 25, 22);
    public static final Icon TAB_BUTTON_BACKGROUND = registerBuiltin("tab_button_background", 20, 20);
    public static final Icon SLOT_BACKGROUND = registerBuiltin("slot_background", 18, 18);
    public static final Icon BACKGROUND_FUEL = registerBuiltin("background_fuel", 16, 16);

    public static final Icon TERMINAL_STYLE_SMALL = registerBuiltin("terminal_style_small", 16, 16);
    public static final Icon TERMINAL_STYLE_MEDIUM = registerBuiltin("terminal_style_medium", 16, 16);
    public static final Icon TERMINAL_STYLE_TALL = registerBuiltin("terminal_style_tall", 16, 16);
    public static final Icon TERMINAL_STYLE_FULL = registerBuiltin("terminal_style_full", 16, 16);
    public static final Icon BACKGROUND_UPGRADE = registerBuiltin("background_upgrade", 16, 16);

    public static final Icon PLACEMENT_BLOCK = registerBuiltin("placement_block", 16, 16);
    public static final Icon PLACEMENT_ITEM = registerBuiltin("placement_item", 16, 16);
    public static final Icon TAB_BUTTON_BACKGROUND_BORDERLESS_FOCUS = registerBuiltin("tab_button_background_borderless_focus", 25, 22);
    public static final Icon TAB_BUTTON_BACKGROUND_FOCUS = registerBuiltin("tab_button_background_focus", 22, 22);

    public static final Icon SCHEDULING_DEFAULT = registerBuiltin("scheduling_default", 16, 16);
    public static final Icon SCHEDULING_ROUND_ROBIN = registerBuiltin("scheduling_round_robin", 16, 16);
    public static final Icon SCHEDULING_RANDOM = registerBuiltin("scheduling_random", 16, 16);
    public static final Icon OVERLAY_OFF = registerBuiltin("overlay_off", 16, 16);
    public static final Icon OVERLAY_ON = registerBuiltin("overlay_on", 16, 16);

    public static final Icon S_ARROW_UP = registerBuiltin("s_arrow_up", 8, 8);
    public static final Icon S_ARROW_DOWN = registerBuiltin("s_arrow_down", 8, 8);
    public static final Icon S_CLEAR = registerBuiltin("s_clear", 8, 8);
    public static final Icon S_CYCLE = registerBuiltin("s_cycle", 8, 8);
    public static final Icon S_SUBSTITUTION_ENABLED = registerBuiltin("s_substitution_enabled", 8, 8);
    public static final Icon S_SUBSTITUTION_DISABLED = registerBuiltin("s_substitution_disabled", 8, 8);
    public static final Icon S_FLUID_SUBSTITUTION_ENABLED = registerBuiltin("s_fluid_substitution_enabled", 8, 8);
    public static final Icon S_FLUID_SUBSTITUTION_DISABLED = registerBuiltin("s_fluid_substitution_disabled", 8, 8);
    public static final Icon S_STORAGE = registerBuiltin("s_storage", 10, 10);
    public static final Icon CRAFT_CONFIRM_CPU_LIST_STORAGE = registerBuiltin("craft_confirm_cpu_list_storage", 16, 16);
    public static final Icon S_PROCESSOR = registerBuiltin("s_processor", 10, 10);
    public static final Icon CRAFT_CONFIRM_CPU_LIST_PROCESSOR = registerBuiltin("craft_confirm_cpu_list_processor", 16, 16);
    public static final Icon S_CRAFT = registerBuiltin("s_craft", 10, 10);
    public static final Icon S_TERMINAL = registerBuiltin("s_terminal", 10, 10);
    public static final Icon S_MACHINE = registerBuiltin("s_machine", 10, 10);

    public static final Icon CTL_CRAFT_TREE = registerBuiltin("craft_tree", 16, 16);
    public static final Icon CRAFTING_TREE_BRANCHES_ALL = registerBuiltin("crafting_tree_branches_all", 16, 16);
    public static final Icon CRAFTING_TREE_BRANCHES_FAILED = registerBuiltin("crafting_tree_branches_failed", 16, 16);
    public static final Icon CRAFTING_TREE_SCREENSHOT = registerBuiltin("crafting_tree_screenshot", 16, 16);
    public static final Icon CRAFTING_TREE_NODE_SELECTED = registerBuiltin("crafting_tree_node_selected", 20, 20);
    public static final Icon CRAFTING_TREE_NODE_NORMAL = registerBuiltin("crafting_tree_node_normal", 20, 20);
    public static final Icon CRAFTING_TREE_NODE_MISSING = registerBuiltin("crafting_tree_node_missing", 20, 20);
    public static final Icon CRAFTING_TREE_MISSING_OVERLAY = registerBuiltin("crafting_tree_missing_overlay", 20, 20);
    public static final Icon CRAFTING_TREE_MACHINE = registerBuiltin("crafting_tree_machine", 20, 20);

    public static final Icon ENABLED = registerBuiltin("enabled", 16, 16);
    public static final Icon DISABLED = registerBuiltin("disabled", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_BIND_OUTPUT = registerBuiltin("advanced_memory_card_bind_output", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_BIND_INPUT = registerBuiltin("advanced_memory_card_bind_input", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_COPY_OUTPUT = registerBuiltin("advanced_memory_card_copy_output", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_DELETE_BINDING = registerBuiltin("advanced_memory_card_delete_binding", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_REFRESH = registerBuiltin("advanced_memory_card_refresh", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_INPUT = registerBuiltin("advanced_memory_card_input", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_OUTPUT = registerBuiltin("advanced_memory_card_output", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_BOUND = registerBuiltin("advanced_memory_card_bound", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_UNBOUND = registerBuiltin("advanced_memory_card_unbound", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_STATUS_INPUT = registerBuiltin("advanced_memory_card_status_input", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_STATUS_OUTPUT = registerBuiltin("advanced_memory_card_status_output", 16, 16);
    public static final Icon ADVANCED_MEMORY_CARD_STATUS_UNBOUND = registerBuiltin("advanced_memory_card_status_unbound", 16, 16);
    public static final Icon PATTERN_PROVIDER_RENAME = registerBuiltin("pattern_provider_rename", 6, 11);

    public final int width;
    public final int height;
    private final ResourceLocation id;
    private final ResourceLocation texture;
    private final int hash;

    private Icon(ResourceLocation id, ResourceLocation texture, int width, int height) {
        this.id = Objects.requireNonNull(id, "id");
        this.texture = Objects.requireNonNull(texture, "texture");
        this.width = width;
        this.height = height;
        this.hash = width * 31 * 31 * 31 + height * 31 * 31 + id.hashCode() * 31 + texture.hashCode();
    }

    private static Icon registerBuiltin(String name, int width, int height) {
        return register(AppEng.makeId(name), AppEng.makeId("textures/guis/icons/" + name + ".png"), width, height);
    }

    public static Icon register(ResourceLocation id, int width, int height) {
        return register(id, new ResourceLocation(id.getNamespace(), "textures/guis/icons/" + id.getPath() + ".png"), width, height);
    }

    public static synchronized Icon register(ResourceLocation id, ResourceLocation texture, int width, int height) {
        Icon icon = new Icon(id, texture, width, height);

        if (registeredIcons.contains(icon)) {
            throw new IllegalArgumentException("Duplicate GUI icon id " + id);
        }

        registeredIcons.add(icon);
        IconAtlas.invalidate();
        return icon;
    }

    public static synchronized List<Icon> getRegisteredIcons() {
        return ImmutableList.copyOf(registeredIcons);
    }

    public ResourceLocation id() {
        return this.id;
    }

    public ResourceLocation texture() {
        return this.texture;
    }

    public Blitter getBlitter() {
        return IconAtlas.getBlitter(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Icon icon = (Icon) o;
        return width == icon.width && height == icon.height && Objects.equals(id, icon.id) && Objects.equals(texture, icon.texture);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
