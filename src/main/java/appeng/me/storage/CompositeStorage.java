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

package appeng.me.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.Map;
import java.util.Objects;

/**
 * Combines several ME storages that each handle only a given key-space.
 */
public class CompositeStorage implements MEStorage, ITickingMonitor {
    private final InventoryCache cache;

    private Map<AEKeyType, MEStorage> storages;

    private boolean forceCacheRebuild = true;

    public CompositeStorage(Map<AEKeyType, MEStorage> storages) {
        this.storages = storages;
        this.cache = new InventoryCache();
    }

    public CompositeStorage() {
        this(new Object2ObjectOpenHashMap<>());
    }

    public void setStorages(Map<AEKeyType, MEStorage> storages) {
        this.storages = Objects.requireNonNull(storages);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        var storage = storages.get(what.getType());
        return storage != null && storage.isPreferredStorageFor(what, source);
    }

    @Override
    public boolean isStickyStorageFor(AEKey what, IActionSource source) {
        var storage = storages.get(what.getType());
        return storage != null && storage.isStickyStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = storages.get(what.getType());
        var inserted = storage != null ? storage.insert(what, amount, mode, source) : 0;

        if (inserted > 0 && mode == Actionable.MODULATE) {
            forceCacheRebuild = true;
        }

        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = storages.get(what.getType());
        var extracted = storage != null ? storage.extract(what, amount, mode, source) : 0;

        if (extracted > 0 && mode == Actionable.MODULATE) {
            forceCacheRebuild = true;
        }

        return extracted;
    }

    @Override
    public ITextComponent getDescription() {
        ITextComponent types = new TextComponentString("");
        boolean first = true;
        for (var keyType : storages.keySet()) {
            if (!first) {
                types.appendText(", ");
            } else {
                first = false;
            }
            types.appendSibling(keyType.getDescription());
        }
        return GuiText.ExternalStorage.text(types);
    }

    @Override
    public TickRateModulation onTick() {
        forceCacheRebuild = false;
        boolean changed = this.cache.update();
        if (changed) {
            return TickRateModulation.URGENT;
        } else {
            return TickRateModulation.SLOWER;
        }
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (forceCacheRebuild) {
            forceCacheRebuild = false;
            cache.update();
        }
        this.cache.getAvailableKeys(out);
    }

    private class InventoryCache {
        private KeyCounter frontBuffer = new KeyCounter();
        private KeyCounter backBuffer = new KeyCounter();

        public boolean update() {
// Flip back & front buffer and start building a new list
            var tmp = backBuffer;
            backBuffer = frontBuffer;
            frontBuffer = tmp;
            frontBuffer.reset();

// Rebuild the front buffer
            for (var storage : storages.values()) {
                storage.getAvailableStacks(frontBuffer);
            }

            boolean changed = false;
            for (var entry : frontBuffer) {
                var old = backBuffer.get(entry.getKey());
                if (old == 0 || old != entry.getLongValue()) {
                    changed = true;
                }
            }
            for (var oldEntry : backBuffer) {
                if (frontBuffer.get(oldEntry.getKey()) == 0) {
                    changed = true;
                }
            }

            frontBuffer.removeZeros();

            return changed;
        }

        public void getAvailableKeys(KeyCounter out) {
            out.addAll(frontBuffer);
        }
    }
}
