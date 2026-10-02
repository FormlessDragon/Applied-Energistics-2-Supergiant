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

package ae2.client.gui.me.patternaccess;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.container.me.patternaccess.ContainerPatternAccessTerm;
import ae2.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenCustomHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.BitSet;
import java.util.Locale;
import java.util.Objects;

/**
 * This class is used on the client-side to represent a pattern provider and its inventory as it is shown in the
 * {@link GuiPatternAccessTerm}'s table for {@link ContainerPatternAccessTerm}.
 */
public class PatternContainerEntry implements Comparable<PatternContainerEntry> {
    public static final int MAX_INVENTORY_SIZE = 4096;

    private final PatternContainerGroup group;
    private final String searchName;
    private final long serverId;
    private final AppEngInternalInventory inventory;
    private final long order;
    private final boolean editableTerminalName;
    private final boolean terminalVisibilityModifiable;
    private final boolean readOnly;
    /**
     * Decoded pattern data cached per stack reference. Slots are rendered every frame and decoding constructs a full
     * pattern (NBT parse + recipe lookup), so the per-frame render paths must hit this cache instead.
     */
    private final Reference2ObjectMap<ItemStack, PatternDisplayCache> patternDisplayCache =
        new Reference2ObjectOpenCustomHashMap<>(new Hash.Strategy<>() {
            @Override
            public int hashCode(ItemStack o) {
                if (o == null) {
                    return Integer.MIN_VALUE;
                }
                return Objects.hash(o.getItem(), o.getItemDamage(), o.getTagCompound());
            }

            @Override
            public boolean equals(ItemStack a, ItemStack b) {
                if (a == b) {
                    return true;
                }
                if (a == null || b == null) {
                    return false;
                }
                if (a.isItemEqual(b)) {
                    return ItemStack.areItemStackTagsEqual(a, b);
                }
                return false;
            }
        });
    /**
     * Slots matching the active input/output search filters; rebuilt on every list refresh.
     */
    private final BitSet matchedSlots = new BitSet();

    private record PatternDisplayCache(boolean valid, ItemStack output) {
    }

    public PatternContainerEntry(long serverId, int slots, long order, boolean editableTerminalName,
                                 boolean terminalVisibilityModifiable, boolean readOnly,
                                 PatternContainerGroup group) {
        this.inventory = new AppEngInternalInventory(Math.clamp(slots, 0, MAX_INVENTORY_SIZE));
        this.group = group;
        this.searchName = group.name().getFormattedText().toLowerCase(Locale.ROOT);
        this.serverId = serverId;
        this.order = order;
        this.editableTerminalName = editableTerminalName;
        this.terminalVisibilityModifiable = terminalVisibilityModifiable;
        this.readOnly = readOnly;
    }

    public PatternContainerGroup getGroup() {
        return group;
    }

    public String getSearchName() {
        return searchName;
    }

    @Override
    public int compareTo(PatternContainerEntry o) {
        return Long.compare(this.order, o.order);
    }

    public long getServerId() {
        return this.serverId;
    }

    public AppEngInternalInventory getInventory() {
        return inventory;
    }

    public boolean canEditTerminalName() {
        return this.editableTerminalName;
    }

    public boolean canModifyTerminalVisibility() {
        return this.terminalVisibilityModifiable;
    }

    /**
     * Whether this provider's pattern slots reject player edits. Read-only providers still display their patterns but
     * do not accept insertions, extractions, swaps, or quick-moves.
     */
    public boolean isReadOnly() {
        return this.readOnly;
    }

    /**
     * Returns the stack that should be rendered for a pattern slot: the pattern's primary output, or the raw stack when
     * the pattern cannot be decoded or has no item output. Results are cached per stack reference so the render loop
     * does not decode patterns every frame.
     */
    public ItemStack getPatternDisplayStack(ItemStack stack, World level) {
        var cached = getOrComputeDisplay(stack, level);
        return cached.valid() && !cached.output().isEmpty() ? cached.output() : stack;
    }

    /**
     * Whether the stack cannot be decoded into a pattern (rendered with the red invalid overlay). Cached together with
     * {@link #getPatternDisplayStack} so both share a single decode per stack.
     */
    public boolean isPatternInvalid(ItemStack stack, World level) {
        return !getOrComputeDisplay(stack, level).valid();
    }

    /**
     * Drops cached decode results for a stack that was replaced by a slot update.
     */
    void invalidatePatternDisplay(ItemStack stack) {
        this.patternDisplayCache.remove(stack);
    }

    public boolean isSlotMatched(int slot) {
        return this.matchedSlots.get(slot);
    }

    void setSlotMatched(int slot) {
        this.matchedSlots.set(slot);
    }

    void clearMatchedSlots() {
        this.matchedSlots.clear();
    }

    private PatternDisplayCache getOrComputeDisplay(ItemStack stack, World level) {
        var cached = this.patternDisplayCache.get(stack);
        if (cached != null) {
            return cached;
        }

        IPatternDetails pattern = PatternDetailsHelper.decodePattern(stack, level);
        ItemStack output = ItemStack.EMPTY;
        if (pattern != null) {
            var primary = pattern.getPrimaryOutput();
            if (primary.what() instanceof AEItemKey itemKey) {
                output = itemKey.toStack();
            } else {
                output = GenericStack.wrapInItemStack(primary);
            }
        }
        cached = new PatternDisplayCache(pattern != null, output);
        this.patternDisplayCache.put(stack, cached);
        return cached;
    }
}
