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
import ae2.core.AppEngBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Icon {
    private static final Map<ResourceLocation, Icon> icons = new LinkedHashMap<>();
    private static long registryVersion;

    public static final Icon REDSTONE_LOW = registerBuiltin("redstone_low");
    public static final Icon REDSTONE_HIGH = registerBuiltin("redstone_high");
    public static final Icon REDSTONE_PULSE = registerBuiltin("redstone_pulse");
    public static final Icon REDSTONE_IGNORE = registerBuiltin("redstone_ignore");
    public static final Icon REDSTONE_OFF = registerBuiltin("redstone_off");
    public static final Icon REDSTONE_ON = registerBuiltin("redstone_on");
    public static final Icon REDSTONE_ABOVE_EQUAL = registerBuiltin("redstone_above_equal");
    public static final Icon REDSTONE_BELOW = registerBuiltin("redstone_below");
    public static final Icon CLEAR = registerBuiltin("clear");
    public static final Icon ENTER = registerBuiltin("enter");
    public static final Icon WHITE_ARROW_DOWN = registerBuiltin("white_arrow_down");
    public static final Icon LOCKED = registerBuiltin("locked");
    public static final Icon UNLOCKED = registerBuiltin("unlocked");
    public static final Icon HELP = registerBuiltin("help");
    public static final Icon BACKGROUND_PRIMARY_OUTPUT = registerBuiltin("background_primary_output");
    public static final Icon BACKGROUND_STORAGE_CELL = registerBuiltin("background_storage_cell");
    public static final Icon CELL_RESTRICTION = registerBuiltin("cell_restriction");
    public static final Icon GENERIC_RESOURCE_PACKAGE_FRAME = registerBuiltin("generic_resource_package_frame");

    public static final Icon VIEW_MODE_STORED = registerBuiltin("view_mode_stored");
    public static final Icon VIEW_MODE_ALL = registerBuiltin("view_mode_all");
    public static final Icon VIEW_MODE_CRAFTING = registerBuiltin("view_mode_crafting");
    public static final Icon BLOCKING_MODE_NO = registerBuiltin("blocking_mode_no");
    public static final Icon BLOCKING_MODE_YES = registerBuiltin("blocking_mode_yes");
    public static final Icon BLOCKING_MODE_STRONG = registerBuiltin("blocking_mode_strong");
    public static final Icon BLOCKING_MODE_TYPE_NORMAL = registerBuiltin("blocking_mode_type_normal");
    public static final Icon BLOCKING_MODE_TYPE_SMART = registerBuiltin("blocking_mode_type_smart");
    public static final Icon WHITELIST = registerBuiltin("whitelist");
    public static final Icon BLACKLIST = registerBuiltin("blacklist");


    public static final Icon BACK = registerBuiltin("back");
    public static final Icon TRANSPARENT_FACADES_OFF = registerBuiltin("transparent_facades_off");
    public static final Icon TYPE_FILTER_ALL = registerBuiltin("type_filter_all");
    public static final Icon BACKGROUND_ORE = registerBuiltin("background_ore");

    public static final Icon SEARCH_AUTO_FOCUS = registerBuiltin("search_auto_focus");
    public static final Icon BACKGROUND_PLATE = registerBuiltin("background_plate");
    public static final Icon TAB_CRAFTING = registerBuiltin("tab_crafting");
    public static final Icon TAB_PROCESSING = registerBuiltin("tab_processing");

    public static final Icon ARROW_UP = registerBuiltin("arrow_up");
    public static final Icon PATTERN_UPLOAD = registerBuiltin("pattern_upload");
    public static final Icon ARROW_DOWN = registerBuiltin("arrow_down");
    public static final Icon ARROW_RIGHT = registerBuiltin("arrow_right");
    public static final Icon ARROW_LEFT = registerBuiltin("arrow_left");
    public static final Icon STORAGE_FILTER_EXTRACTABLE_ONLY = registerBuiltin("storage_filter_extractable_only");
    public static final Icon STORAGE_FILTER_EXTRACTABLE_NONE = registerBuiltin("storage_filter_extractable_none");
    public static final Icon FILTER_ON_EXTRACT_ENABLED = registerBuiltin("filter_on_extract_enabled");
    public static final Icon FILTER_ON_EXTRACT_DISABLED = registerBuiltin("filter_on_extract_disabled");
    public static final Icon BACKGROUND_INGOT = registerBuiltin("background_ingot");
    public static final Icon BACKGROUND_STORAGE_COMPONENT = registerBuiltin("background_storage_component");

    public static final Icon SORT_BY_NAME = registerBuiltin("sort_by_name");
    public static final Icon SORT_BY_AMOUNT = registerBuiltin("sort_by_amount");
    public static final Icon CRAFTING_PLAN_SORT_AVAILABILITY = registerBuiltin("crafting_plan_sort_availability");
    public static final Icon CRAFTING_PLAN_SORT_USED_COUNT = registerBuiltin("crafting_plan_sort_used_count");
    public static final Icon CRAFTING_PLAN_SORT_USED_PERCENT = registerBuiltin("crafting_plan_sort_used_percent");
    public static final Icon CRAFTING_PLAN_SORT_ASCENDING = registerBuiltin("crafting_plan_sort_ascending");
    public static final Icon CRAFTING_PLAN_SORT_DESCENDING = registerBuiltin("crafting_plan_sort_descending");
    public static final Icon COG = registerBuiltin("cog");
    public static final Icon WIRELESS_SETTINGS_PAGE = registerBuiltin("wireless_settings_page");
    public static final Icon COG_DISABLED = registerBuiltin("cog_disabled");
    public static final Icon SORT_BY_INVENTORY_TWEAKS = registerBuiltin("sort_by_inventory_tweaks");
    public static final Icon SORT_BY_MOD = registerBuiltin("sort_by_mod");
    public static final Icon SORT_BY_HEI = registerBuiltin("sort_by_hei");
    public static final Icon PRIORITY = registerBuiltin("priority");
    public static final Icon PLAYER_PIN = registerBuiltin("player_pin");
    public static final Icon BACKGROUND_VIEW_CELL = registerBuiltin("background_view_cell");
    public static final Icon BACKGROUND_WIRELESS_TERM = registerBuiltin("background_wireless_term");

    public static final Icon FULLNESS_EMPTY = registerBuiltin("fullness_empty");
    public static final Icon FULLNESS_HALF = registerBuiltin("fullness_half");
    public static final Icon FULLNESS_FULL = registerBuiltin("fullness_full");
    public static final Icon PATTERN_ACCESS_SHOW = registerBuiltin("pattern_access_show");
    public static final Icon PATTERN_ACCESS_HIDE = registerBuiltin("pattern_access_hide");
    public static final Icon PATTERN_AUTO_FILL_OFF = registerBuiltin("pattern_auto_fill_off");
    public static final Icon PATTERN_AUTO_FILL_ON = registerBuiltin("pattern_auto_fill_on");
    public static final Icon PATTERN_TERMINAL_VISIBLE = registerBuiltin("pattern_terminal_visible");
    public static final Icon PATTERN_TERMINAL_HIDDEN = registerBuiltin("pattern_terminal_hidden");
    public static final Icon PATTERN_TERMINAL_ALL = registerBuiltin("pattern_terminal_all");
    public static final Icon PATTERN_TERMINAL_NOT_FULL = registerBuiltin("pattern_terminal_not_full");
    public static final Icon PATTERN_PROVIDER_INSERTION_DEFAULT = registerBuiltin("pattern_provider_insertion_default");
    public static final Icon PATTERN_PROVIDER_INSERTION_PREFER_EMPTY = registerBuiltin(
        "pattern_provider_insertion_prefer_empty");
    public static final Icon PATTERN_PROVIDER_INSERTION_EMPTY_ONLY = registerBuiltin(
        "pattern_provider_insertion_empty_only");
    public static final Icon PATTERN_PROVIDER_OUTPUT_SIDE_SINGLE = registerBuiltin(
        "pattern_provider_output_side_single");
    public static final Icon PATTERN_PROVIDER_OUTPUT_SIDE_SPLIT_BY_INGREDIENTS_TYPE = registerBuiltin(
        "pattern_provider_output_side_split_by_ingredients_type");
    public static final Icon BACKGROUND_TRASH = registerBuiltin("background_trash");

    public static final Icon FUZZY_PERCENT_25 = registerBuiltin("fuzzy_percent_25");
    public static final Icon FUZZY_PERCENT_50 = registerBuiltin("fuzzy_percent_50");
    public static final Icon FUZZY_PERCENT_75 = registerBuiltin("fuzzy_percent_75");
    public static final Icon FUZZY_PERCENT_99 = registerBuiltin("fuzzy_percent_99");
    public static final Icon FUZZY_IGNORE = registerBuiltin("fuzzy_ignore");
    public static final Icon INSCRIBER_SEPARATE_SIDES = registerBuiltin("inscriber_separate_sides");
    public static final Icon INSCRIBER_COMBINED_SIDES = registerBuiltin("inscriber_combined_sides");
    public static final Icon AUTO_EXPORT_OFF = registerBuiltin("auto_export_off");
    public static final Icon AUTO_EXPORT_ON = registerBuiltin("auto_export_on");
    public static final Icon OUTPUT_SIDE_CONFIG = registerBuiltin("output_side_config");
    public static final Icon INSCRIBER_BUFFER_4 = registerBuiltin("inscriber_buffer_4");
    public static final Icon INSCRIBER_BUFFER_64 = registerBuiltin("inscriber_buffer_64");
    public static final Icon INSCRIBER_BUFFER_1 = registerBuiltin("inscriber_buffer_1");
    public static final Icon BACKGROUND_WIRELESS_BOOSTER = registerBuiltin("background_wireless_booster");

    public static final Icon CONDENSER_OUTPUT_TRASH = registerBuiltin("condenser_output_trash");
    public static final Icon CONDENSER_OUTPUT_MATTER_BALL = registerBuiltin("condenser_output_matter_ball");
    public static final Icon CONDENSER_OUTPUT_SINGULARITY = registerBuiltin("condenser_output_singularity");
    public static final Icon BACKGROUND_ENCODED_PATTERN = registerBuiltin("background_encoded_pattern");

    public static final Icon INVALID = registerBuiltin("invalid");
    public static final Icon HORIZONTAL_TAB = registerBuiltin("horizontal_tab");
    public static final Icon HORIZONTAL_TAB_SELECTED = registerBuiltin("horizontal_tab_selected");
    public static final Icon HORIZONTAL_TAB_FOCUS = registerBuiltin("horizontal_tab_focus");
    public static final Icon BACKGROUND_BLANK_PATTERN = registerBuiltin("background_blank_pattern");
    public static final Icon TOOLBAR_BUTTON_BACKGROUND = registerBuiltin("toolbar_button_background");
    public static final Icon TOOLBAR_BUTTON_BACKGROUND_FOCUS = registerBuiltin("toolbar_button_background_focus");
    public static final Icon TOOLBAR_BUTTON_BACKGROUND_HOVER = registerBuiltin("toolbar_button_background_hover");
    public static final Icon SMALL_SQUARE_BUTTON_BACKGROUND = registerBuiltin("small_square_button_background");
    public static final Icon SMALL_SQUARE_BUTTON_BACKGROUND_HOVER = registerBuiltin("small_square_button_background_hover");

    public static final Icon ACCESS_WRITE = registerBuiltin("access_write");
    public static final Icon ACCESS_READ = registerBuiltin("access_read");
    public static final Icon ACCESS_READ_WRITE = registerBuiltin("access_read_write");
    public static final Icon REGULATE_STOCK_ON = registerBuiltin("regulate_stock_on");
    public static final Icon REGULATE_STOCK_OFF = registerBuiltin("regulate_stock_off");
    public static final Icon CRAFT_HAMMER = registerBuiltin("craft_hammer");
    public static final Icon BACKGROUND_CHARGABLE = registerBuiltin("background_chargable");

    public static final Icon POWER_UNIT_AE = registerBuiltin("power_unit_ae");
    public static final Icon POWER_UNIT_EU = registerBuiltin("power_unit_eu");
    public static final Icon POWER_UNIT_J = registerBuiltin("power_unit_j");
    public static final Icon POWER_UNIT_W = registerBuiltin("power_unit_w");
    public static final Icon POWER_UNIT_RF = registerBuiltin("power_unit_rf");
    public static final Icon POWER_UNIT_TR = registerBuiltin("power_unit_tr");
    public static final Icon BACKGROUND_SINGULARITY = registerBuiltin("background_singularity");

    public static final Icon COPY_MODE_ON = registerBuiltin("copy_mode_on");
    public static final Icon BACKGROUND_SPATIAL_CELL_NO_SHADOW = registerBuiltin("background_spatial_cell_no_shadow");
    public static final Icon BACKGROUND_SPATIAL_CELL = registerBuiltin("background_spatial_cell");

    public static final Icon COPY_MODE_OFF = registerBuiltin("copy_mode_off");
    public static final Icon TAB_BUTTON_BACKGROUND_BORDERLESS = registerBuiltin("tab_button_background_borderless");
    public static final Icon TAB_BUTTON_BACKGROUND = registerBuiltin("tab_button_background");
    public static final Icon SLOT_BACKGROUND = registerBuiltin("slot_background");
    public static final Icon BACKGROUND_FUEL = registerBuiltin("background_fuel");

    public static final Icon TERMINAL_STYLE_SMALL = registerBuiltin("terminal_style_small");
    public static final Icon TERMINAL_STYLE_MEDIUM = registerBuiltin("terminal_style_medium");
    public static final Icon TERMINAL_STYLE_TALL = registerBuiltin("terminal_style_tall");
    public static final Icon TERMINAL_STYLE_FULL = registerBuiltin("terminal_style_full");
    public static final Icon BACKGROUND_UPGRADE = registerBuiltin("background_upgrade");

    public static final Icon PLACEMENT_BLOCK = registerBuiltin("placement_block");
    public static final Icon PLACEMENT_ITEM = registerBuiltin("placement_item");
    public static final Icon TAB_BUTTON_BACKGROUND_BORDERLESS_FOCUS = registerBuiltin("tab_button_background_borderless_focus");
    public static final Icon TAB_BUTTON_BACKGROUND_FOCUS = registerBuiltin("tab_button_background_focus");

    public static final Icon SCHEDULING_DEFAULT = registerBuiltin("scheduling_default");
    public static final Icon SCHEDULING_ROUND_ROBIN = registerBuiltin("scheduling_round_robin");
    public static final Icon SCHEDULING_RANDOM = registerBuiltin("scheduling_random");
    public static final Icon OVERLAY_OFF = registerBuiltin("overlay_off");
    public static final Icon OVERLAY_ON = registerBuiltin("overlay_on");

    public static final Icon CYCLE = registerBuiltin("cycle");
    public static final Icon S_ARROW_UP = registerBuiltin("s_arrow_up");
    public static final Icon S_ARROW_DOWN = registerBuiltin("s_arrow_down");
    public static final Icon S_CLEAR = registerBuiltin("s_clear");
    public static final Icon S_CYCLE = registerBuiltin("s_cycle");
    public static final Icon S_SUBSTITUTION_ENABLED = registerBuiltin("s_substitution_enabled");
    public static final Icon S_SUBSTITUTION_DISABLED = registerBuiltin("s_substitution_disabled");
    public static final Icon S_FLUID_SUBSTITUTION_ENABLED = registerBuiltin("s_fluid_substitution_enabled");
    public static final Icon S_FLUID_SUBSTITUTION_DISABLED = registerBuiltin("s_fluid_substitution_disabled");
    public static final Icon S_STORAGE = registerBuiltin("s_storage");
    public static final Icon CRAFT_CONFIRM_CPU_LIST_STORAGE = registerBuiltin("craft_confirm_cpu_list_storage");
    public static final Icon S_PROCESSOR = registerBuiltin("s_processor");
    public static final Icon CRAFT_CONFIRM_CPU_LIST_PROCESSOR = registerBuiltin("craft_confirm_cpu_list_processor");
    public static final Icon S_CRAFT = registerBuiltin("s_craft");
    public static final Icon S_TERMINAL = registerBuiltin("s_terminal");
    public static final Icon S_MACHINE = registerBuiltin("s_machine");

    public static final Icon CTL_CRAFT_TREE = registerBuiltin("craft_tree");
    public static final Icon CRAFTING_TREE_BRANCHES_ALL = registerBuiltin("crafting_tree_branches_all");
    public static final Icon CRAFTING_TREE_BRANCHES_FAILED = registerBuiltin("crafting_tree_branches_failed");
    public static final Icon CRAFTING_TREE_SCREENSHOT = registerBuiltin("crafting_tree_screenshot");
    public static final Icon CRAFTING_TREE_NODE_SELECTED = registerBuiltin("crafting_tree_node_selected");
    public static final Icon CRAFTING_TREE_NODE_NORMAL = registerBuiltin("crafting_tree_node_normal");
    public static final Icon CRAFTING_TREE_NODE_MISSING = registerBuiltin("crafting_tree_node_missing");
    public static final Icon CRAFTING_TREE_MISSING_OVERLAY = registerBuiltin("crafting_tree_missing_overlay");
    public static final Icon CRAFTING_TREE_MACHINE = registerBuiltin("crafting_tree_machine");

    public static final Icon ENABLED = registerBuiltin("enabled");
    public static final Icon DISABLED = registerBuiltin("disabled");
    public static final Icon ADVANCED_MEMORY_CARD_BIND_OUTPUT = registerBuiltin("advanced_memory_card_bind_output");
    public static final Icon ADVANCED_MEMORY_CARD_BIND_INPUT = registerBuiltin("advanced_memory_card_bind_input");
    public static final Icon ADVANCED_MEMORY_CARD_COPY_OUTPUT = registerBuiltin("advanced_memory_card_copy_output");
    public static final Icon ADVANCED_MEMORY_CARD_DELETE_BINDING = registerBuiltin("advanced_memory_card_delete_binding");
    public static final Icon ADVANCED_MEMORY_CARD_REFRESH = registerBuiltin("advanced_memory_card_refresh");
    public static final Icon ADVANCED_MEMORY_CARD_INPUT = registerBuiltin("advanced_memory_card_input");
    public static final Icon ADVANCED_MEMORY_CARD_OUTPUT = registerBuiltin("advanced_memory_card_output");
    public static final Icon ADVANCED_MEMORY_CARD_BOUND = registerBuiltin("advanced_memory_card_bound");
    public static final Icon ADVANCED_MEMORY_CARD_UNBOUND = registerBuiltin("advanced_memory_card_unbound");
    public static final Icon ADVANCED_MEMORY_CARD_STATUS_INPUT = registerBuiltin("advanced_memory_card_status_input");
    public static final Icon ADVANCED_MEMORY_CARD_STATUS_OUTPUT = registerBuiltin("advanced_memory_card_status_output");
    public static final Icon ADVANCED_MEMORY_CARD_STATUS_UNBOUND = registerBuiltin("advanced_memory_card_status_unbound");
    public static final Icon CRAFTING_CPU_MODE_ALL = registerBuiltin("crafting_cpu_mode_all");
    public static final Icon CRAFTING_CPU_ACTIVITY_ALL = registerBuiltin("crafting_cpu_activity_all");
    public static final Icon CRAFTING_CPU_ACTIVITY_ACTIVE = registerBuiltin("crafting_cpu_activity_active");
    public static final Icon CRAFTING_CPU_ACTIVITY_INACTIVE = registerBuiltin("crafting_cpu_activity_inactive");
    public static final Icon CRAFTING_CPU_LIST_ROW_BACKGROUND = registerBuiltin("crafting_cpu_list_row_background");
    public static final Icon CRAFTING_CPU_LIST_ROW_BACKGROUND_FOCUSED = registerBuiltin(
        "crafting_cpu_list_row_background_focused");
    public static final Icon RENAME = registerBuiltin("rename");
    public static final Icon HIGHLIGHT = registerBuiltin("highlight");
    public static final Icon PORTABLE_CELL_PICKUP_MATCH_NBT_ENABLED = registerBuiltin(
        "portable_cell_pickup_match_nbt_enabled");
    public static final Icon PORTABLE_CELL_PICKUP_MATCH_NBT_DISABLED = registerBuiltin(
        "portable_cell_pickup_match_nbt_disabled");
    public static final Icon PORTABLE_CELL_PICKUP_MATCH_DAMAGE_ENABLED = registerBuiltin(
        "portable_cell_pickup_match_damage_enabled");
    public static final Icon PORTABLE_CELL_PICKUP_MATCH_DAMAGE_DISABLED = registerBuiltin(
        "portable_cell_pickup_match_damage_disabled");
    public static final Icon PORTABLE_CELL_PICKUP_MATCH_ORE_DICTIONARY_ENABLED = registerBuiltin(
        "portable_cell_pickup_match_ore_dictionary_enabled");
    public static final Icon PORTABLE_CELL_PICKUP_MATCH_ORE_DICTIONARY_DISABLED = registerBuiltin(
        "portable_cell_pickup_match_ore_dictionary_disabled");

    public static final Icon CELL_TERMINAL_BTN_DO_PARTITION = registerBuiltin("cell_terminal_btn_do_partition");
    public static final Icon CELL_TERMINAL_BTN_CLEAR_PARTITION = registerBuiltin("cell_terminal_btn_clear_partition");
    public static final Icon CELL_TERMINAL_BTN_READ_ONLY = registerBuiltin("cell_terminal_btn_read_only");
    public static final Icon CELL_TERMINAL_BTN_WRITE_ONLY = registerBuiltin("cell_terminal_btn_write_only");
    public static final Icon CELL_TERMINAL_BTN_READ_WRITE = registerBuiltin("cell_terminal_btn_read_write");
    public static final Icon CELL_TERMINAL_ACT_EJECT = registerBuiltin("cell_terminal_act_eject");
    public static final Icon CELL_TERMINAL_ACT_EJECT_HOVER = registerBuiltin("cell_terminal_act_eject_hover");
    public static final Icon CELL_TERMINAL_ACT_INVENTORY = registerBuiltin("cell_terminal_act_inventory");
    public static final Icon CELL_TERMINAL_ACT_PARTITION = registerBuiltin("cell_terminal_act_partition");
    public static final Icon CELL_TERMINAL_SEARCH_MODE_INVENTORY = registerBuiltin(
        "cell_terminal_search_mode_inventory");
    public static final Icon CELL_TERMINAL_SEARCH_MODE_PARTITION = registerBuiltin(
        "cell_terminal_search_mode_partition");
    public static final Icon CELL_TERMINAL_SEARCH_MODE_MIXED = registerBuiltin("cell_terminal_search_mode_mixed");
    public static final Icon CELL_TERMINAL_MAIN_NET = registerBuiltin("cell_terminal_main_net");
    public static final Icon CELL_TERMINAL_ARROW_OUT = registerBuiltin("cell_terminal_arrow_out");
    public static final Icon CELL_TERMINAL_ARROW_IN = registerBuiltin("cell_terminal_arrow_in");
    public static final Icon CELL_TERMINAL_HELP = registerBuiltin("cell_terminal_help");
    public static final Icon CELL_TERMINAL_RUN = registerBuiltin("cell_terminal_run");
    public static final Icon CELL_TERMINAL_RUN_DISABLED = registerBuiltin("cell_terminal_run_disabled");
    public static final Icon CELL_TERMINAL_STAR_ON = registerBuiltin("cell_terminal_star_on");
    public static final Icon CELL_TERMINAL_STAR_OFF = registerBuiltin("cell_terminal_star_off");
    public static final Icon CELL_TERMINAL_MINI_SLOT = registerBuiltin("cell_terminal_mini_slot");
    public static final Icon CELL_TERMINAL_MINI_SLOT_PARTITION = registerBuiltin("cell_terminal_mini_slot_partition");
    public static final Icon CELL_TERMINAL_CONTENT_FILTER_SHOW_ALL = registerBuiltin(
        "cell_terminal_content_filter_show_all");
    public static final Icon CELL_TERMINAL_CONTENT_FILTER_SHOW_ONLY = registerBuiltin(
        "cell_terminal_content_filter_show_only");
    public static final Icon CELL_TERMINAL_CONTENT_FILTER_HIDE = registerBuiltin("cell_terminal_content_filter_hide");
    public static final Icon CELL_TERMINAL_PARTITION_FILTER_SHOW_ALL = registerBuiltin(
        "cell_terminal_partition_filter_show_all");
    public static final Icon CELL_TERMINAL_PARTITION_FILTER_SHOW_ONLY = registerBuiltin(
        "cell_terminal_partition_filter_show_only");
    public static final Icon CELL_TERMINAL_PARTITION_FILTER_HIDE = registerBuiltin(
        "cell_terminal_partition_filter_hide");
    public static final Icon CELL_TERMINAL_TAB = registerBuiltin("cell_terminal_tab");
    public static final Icon CELL_TERMINAL_TAB_HOVER = registerBuiltin("cell_terminal_tab_hover");
    public static final Icon CELL_TERMINAL_TAB_SELECTED = registerBuiltin("cell_terminal_tab_selected");
    public static final Icon CELL_TERMINAL_TAB_DISABLED = registerBuiltin("cell_terminal_tab_disabled");
    public static final Icon CELL_TERMINAL_SUBNET_VISIBILITY_SHOW_ALL = registerBuiltin(
        "cell_terminal_subnet_visibility_show_all");
    public static final Icon CELL_TERMINAL_SUBNET_VISIBILITY_SHOW_FAVORITES = registerBuiltin(
        "cell_terminal_subnet_visibility_show_favorites");
    public static final Icon CELL_TERMINAL_SUBNET_VISIBILITY_DONT_SHOW = registerBuiltin(
        "cell_terminal_subnet_visibility_dont_show");

    private final Size manualSize;
    public int width;
    private final ResourceLocation id;
    private final ResourceLocation texture;
    private final int hash;
    public int height;

    private Icon(ResourceLocation id, ResourceLocation texture, int width, int height, Size manualSize) {
        this.id = Objects.requireNonNull(id, "id");
        this.texture = Objects.requireNonNull(texture, "texture");
        this.width = width;
        this.height = height;
        this.manualSize = manualSize;
        this.hash = Objects.hash(id, texture);
    }

    private static Icon registerBuiltin(String name) {
        return register(AppEng.makeId(name), AppEng.makeId("textures/guis/icons/" + name + ".png"));
    }

    @SuppressWarnings("unused")
    private static Icon registerBuiltin(String name, int width, int height) {
        return register(AppEng.makeId(name), AppEng.makeId("textures/guis/icons/" + name + ".png"), width, height);
    }

    public static Icon register(ResourceLocation id) {
        return register(id, defaultTexture(id));
    }

    public static Icon register(ResourceLocation id, ResourceLocation texture) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(texture, "texture");
        return register(id, texture, 0, 0, null);
    }

    public static Icon register(ResourceLocation id, int width, int height) {
        return register(id, defaultTexture(id), width, height);
    }

    public static Icon register(ResourceLocation id, ResourceLocation texture, int width, int height) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(texture, "texture");
        validateManualSize(id, width, height);
        return register(id, texture, width, height, new Size(width, height));
    }

    private static synchronized Icon register(ResourceLocation id, ResourceLocation texture, int width, int height,
                                              Size manualSize) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(texture, "texture");

        Icon existing = icons.get(id);
        if (existing != null) {
            if (existing.matchesDefinition(texture, manualSize)) {
                return existing;
            }
            AppEngBase.LOGGER.error("Duplicate GUI icon id {} has conflicting definitions. Existing: {}, new: {}",
                id, existing.definitionDescription(), definitionDescription(texture, width, height, manualSize));
            throw new IllegalArgumentException("Duplicate GUI icon id " + id
                + " has conflicting definitions. Existing: " + existing.definitionDescription()
                + ", new: " + definitionDescription(texture, width, height, manualSize));
        }

        Icon icon = new Icon(id, texture, width, height, manualSize);
        icons.put(id, icon);
        registryVersion++;
        return icon;
    }

    public static synchronized List<Icon> getRegisteredIcons() {
        return List.copyOf(icons.values());
    }

    public static synchronized Icon byId(ResourceLocation id) {
        return icons.get(Objects.requireNonNull(id, "id"));
    }

    public static synchronized long getRegistryVersion() {
        return registryVersion;
    }

    public static synchronized void invalidate() {
        if (!FMLCommonHandler.instance().getSide().isClient()) {
            String message = "GUI icon sizes can only be invalidated on the physical client";
            AppEngBase.LOGGER.error(message);
            throw new IllegalStateException(message);
        }

        for (Icon icon : icons.values()) {
            icon.resolveTextureSize();
        }
        registryVersion++;
        IconAtlas.invalidate();
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

    private static String definitionDescription(ResourceLocation texture, int width, int height, Size manualSize) {
        if (manualSize != null) {
            return texture + " manualSize=" + width + "x" + height;
        }
        return texture + " textureSize";
    }

    private static ResourceLocation defaultTexture(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return new ResourceLocation(id.getNamespace(), "textures/guis/icons/" + id.getPath() + ".png");
    }

    private static void validateManualSize(ResourceLocation id, int width, int height) {
        if (width <= 0 || height <= 0) {
            AppEngBase.LOGGER.error("GUI icon {} size must be positive, got {}x{}", id, width, height);
            throw new IllegalArgumentException("GUI icon " + id + " size must be positive, got "
                + width + "x" + height);
        }
    }

    public boolean hasResolvedSize() {
        return this.width > 0 && this.height > 0;
    }

    private boolean matchesDefinition(ResourceLocation texture, Size newManualSize) {
        if (!Objects.equals(this.texture, texture)) {
            return false;
        }

        return Objects.equals(this.manualSize, newManualSize);
    }

    private String definitionDescription() {
        return definitionDescription(this.texture, this.width, this.height, this.manualSize);
    }

    private void resolveTextureSize() {
        if (this.manualSize != null) {
            return;
        }

        Size textureSize = ClientIconSizeReader.read(this.texture);
        this.width = textureSize.width();
        this.height = textureSize.height();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Icon icon = (Icon) o;
        return Objects.equals(id, icon.id) && Objects.equals(texture, icon.texture);
    }

    @Override
    public String toString() {
        return "icon #" + id + " @" + definitionDescription();
    }

    @Override
    public int hashCode() {
        return hash;
    }

    record Size(int width, int height) {
    }
}
