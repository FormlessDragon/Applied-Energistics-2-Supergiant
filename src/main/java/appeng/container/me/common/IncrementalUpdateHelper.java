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

package appeng.container.me.common;

import appeng.api.stacks.AEKey;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * This utility class helps menus send grouped {@link AEKey} data to the client and keep it updated without resending
 * the full key each time. This matters when an item stack is serialized using its tag, since echoing that stack back
 * can break server-side identity or merge distinct server-side entries on the client when they share the same tag.
 */
public class IncrementalUpdateHelper implements Iterable<AEKey> {

    /**
     * Maps stacks to serial numbers. This relies on the fact that these stacks are equal iff their type is equal, and
     * two stacks with different counts are still equal.
     */
    private final BiMap<AEKey, Long> mapping;

    private final Set<AEKey> changes = new ObjectLinkedOpenHashSet<>();
    private long serial;
    private boolean fullUpdate = true;

    public IncrementalUpdateHelper() {
        this.mapping = HashBiMap.create();
    }

    @Nullable
    public Long getSerial(AEKey stack) {
        return this.mapping.get(stack);
    }

    public long getOrAssignSerial(AEKey key) {
        return this.mapping.computeIfAbsent(key, ignored -> ++this.serial);
    }

    @Nullable
    public AEKey getBySerial(long serial) {
        return this.mapping.inverse().get(serial);
    }

    public void clear() {
        this.changes.clear();
        this.fullUpdate = true;
    }

    public void reset() {
        clear();
        this.serial = 0;
        this.mapping.clear();
    }

    public void addChange(AEKey entry) {
        if (!this.changes.add(entry)) {
            this.changes.remove(entry);
            this.changes.add(entry);
        }
    }

    public void removeSerial(AEKey what) {
        this.mapping.remove(what);
    }

    public void commitChanges() {
        this.changes.clear();
        this.fullUpdate = false;
    }

    public boolean hasChanges() {
        return this.fullUpdate || !this.changes.isEmpty();
    }

    public boolean isFullUpdate() {
        return this.fullUpdate;
    }

    @Override
    @NotNull
    public Iterator<AEKey> iterator() {
        return this.changes.iterator();
    }

    @Override
    public void forEach(Consumer<? super AEKey> action) {
        this.changes.forEach(action);
    }

    @Override
    public Spliterator<AEKey> spliterator() {
        return this.changes.spliterator();
    }
}

