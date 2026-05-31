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

package ae2.crafting.execution;

import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;

public class ElapsedTimeTracker {
    private static final String NBT_ELAPSED_TIME = "elapsedTime";
    private static final String NBT_STARTED_WORK = "startedWork";
    private static final String NBT_COMPLETED_WORK = "completedWork";
    private final Reference2LongMap<AEKeyType> startedWorkByType = new Reference2LongOpenHashMap<>(
        AEKeyTypes.getAll().size());
    private final Reference2LongMap<AEKeyType> completedWorkByType = new Reference2LongOpenHashMap<>(
        AEKeyTypes.getAll().size());
    private long lastTime = System.nanoTime();
    private long elapsedTime = 0;

    public ElapsedTimeTracker() {
    }

    public ElapsedTimeTracker(NBTTagCompound data) {
        this.elapsedTime = data.getLong(NBT_ELAPSED_TIME);
        readLongByTypeMap(data.getCompoundTag(NBT_STARTED_WORK), startedWorkByType);
        readLongByTypeMap(data.getCompoundTag(NBT_COMPLETED_WORK), completedWorkByType);
    }

    private static void readLongByTypeMap(NBTTagCompound tag, Reference2LongMap<AEKeyType> output) {
        for (var keyType : AEKeyTypes.getAll()) {
            output.put(keyType, tag.getLong(keyType.getId().toString()));
        }
    }

    private static NBTTagCompound writeLongByTypeMap(Reference2LongMap<AEKeyType> input) {
        NBTTagCompound result = new NBTTagCompound();
        for (var entry : input.reference2LongEntrySet()) {
            result.setLong(entry.getKey().getId().toString(), entry.getLongValue());
        }
        return result;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound data = new NBTTagCompound();
        data.setLong(NBT_ELAPSED_TIME, elapsedTime);
        data.setTag(NBT_STARTED_WORK, writeLongByTypeMap(startedWorkByType));
        data.setTag(NBT_COMPLETED_WORK, writeLongByTypeMap(completedWorkByType));
        return data;
    }

    private void updateTime() {
        long currentTime = System.nanoTime();
        this.elapsedTime = this.elapsedTime + (currentTime - this.lastTime);
        this.lastTime = currentTime;
    }

    void decrementItems(long itemDiff, AEKeyType keyType) {
        updateTime();
        completedWorkByType.merge(keyType, itemDiff, this::saturatedSum);
    }

    private long saturatedSum(long a, long b) {
        var result = a + b;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    void addMaxItems(long itemDiff, AEKeyType keyType) {
        updateTime();
        startedWorkByType.merge(keyType, itemDiff, this::saturatedSum);
    }

    public long getElapsedTime() {
        boolean allDone = true;
        for (var keyType : AEKeyTypes.getAll()) {
            if (completedWorkByType.getLong(keyType) < startedWorkByType.getLong(keyType)) {
                allDone = false;
                break;
            }
        }

        if (!allDone) {
            return this.elapsedTime + (System.nanoTime() - this.lastTime);
        } else {
            return this.elapsedTime;
        }
    }

    public float getProgress() {
        double startedUnits = 0;
        double completedUnits = 0;
        for (var keyType : AEKeyTypes.getAll()) {
            var startedForType = startedWorkByType.getLong(keyType);
            var completedForType = completedWorkByType.getLong(keyType);
            startedUnits += startedForType / (double) keyType.getAmountPerUnit();
            completedUnits += completedForType / (double) keyType.getAmountPerUnit();
        }

        return MathHelper.clamp((float) (completedUnits / startedUnits), 0, 1);
    }

    public long getRemainingWorkUnits() {
        long startedUnits = 0;
        long completedUnits = 0;
        for (var keyType : AEKeyTypes.getAll()) {
            long amountPerUnit = keyType.getAmountPerUnit();
            startedUnits = saturatedSum(startedUnits, startedWorkByType.getLong(keyType) / amountPerUnit);
            completedUnits = saturatedSum(completedUnits, completedWorkByType.getLong(keyType) / amountPerUnit);
        }
        return Math.max(0, startedUnits - completedUnits);
    }

    public long getStartedWorkUnits() {
        long startedUnits = 0;
        for (var keyType : AEKeyTypes.getAll()) {
            startedUnits = saturatedSum(startedUnits, startedWorkByType.getLong(keyType) / keyType.getAmountPerUnit());
        }
        return startedUnits;
    }

    @Deprecated(forRemoval = true)
    public long getRemainingItemCount() {
        return getRemainingWorkUnits();
    }

    @Deprecated(forRemoval = true)
    public long getStartItemCount() {
        return getStartedWorkUnits();
    }
}
