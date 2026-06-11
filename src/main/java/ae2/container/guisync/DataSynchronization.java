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

package ae2.container.guisync;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class DataSynchronization {
    private final Short2ObjectOpenHashMap<SynchronizedField<?>> fields = new Short2ObjectOpenHashMap<>();

    public DataSynchronization(Object host) {
        collectFields(host, host.getClass());
    }

    private static @Nullable Short getSyncId(Field field) {
        var guiSync = field.getAnnotation(GuiSync.class);
        if (guiSync != null) {
            return guiSync.value();
        }

        return null;
    }

    private void collectFields(Object host, Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            Short key = getSyncId(field);
            if (key != null) {
                short shortKey = key;
                if (this.fields.containsKey(shortKey)) {
                    throw new IllegalStateException(
                        "Class " + host.getClass() + " declares the same sync id twice: " + key);
                }
                this.fields.put(shortKey, SynchronizedField.create(host, field));
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            collectFields(host, superclass);
        }
    }

    public boolean hasChanges() {
        for (SynchronizedField<?> value : this.fields.values()) {
            if (value.hasChanges()) {
                return true;
            }
        }
        return false;
    }

    public void writeFull(ByteBuf data) {
        writeFields(data, true);
    }

    public void writeUpdate(ByteBuf data) {
        writeFields(data, false);
    }

    private void writeFields(ByteBuf data, boolean includeUnchanged) {
        for (Short2ObjectMap.Entry<SynchronizedField<?>> entry : this.fields.short2ObjectEntrySet()) {
            if (includeUnchanged || entry.getValue().hasChanges()) {
                data.writeShort(entry.getShortKey());
                entry.getValue().write(data);
            }
        }
        data.writeShort(-1);
    }

    public ShortSet readUpdate(ByteBuf data) {
        ShortSet updatedFields = new ShortOpenHashSet();
        readUpdate(data, updatedFields);
        return updatedFields;
    }

    public void readUpdate(ByteBuf data, ShortSet updatedFields) {
        for (short key = data.readShort(); key != -1; key = data.readShort()) {
            SynchronizedField<?> field = this.fields.get(key);
            if (field == null) {
                throw new IllegalArgumentException("Server sent unknown GUI field " + key);
            }

            field.read(data);
            updatedFields.add(key);
        }
    }

    public boolean hasFields() {
        return !this.fields.isEmpty();
    }
}
