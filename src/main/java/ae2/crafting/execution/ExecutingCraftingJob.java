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

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingLink;
import ae2.crafting.CraftingPlan;
import ae2.crafting.TemporaryPseudoCraftingProvider;
import ae2.crafting.inv.ListCraftingInventory;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import ae2.me.service.CraftingService;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class ExecutingCraftingJob {
    private static final String NBT_LINK = "link";
    private static final String NBT_PLAYER_ID = "playerId";
    private static final String NBT_FINAL_OUTPUT = "finalOutput";
    private static final String NBT_WAITING_FOR = "waitingFor";
    private static final String NBT_PSEUDO_INVENTORY = "pseudoInventory";
    private static final String NBT_TIME_TRACKER = "timeTracker";
    private static final String NBT_REMAINING_AMOUNT = "remainingAmount";
    private static final String NBT_REMAINING_INTERMEDIATE_FINAL_OUTPUT = "remainingIntermediateFinalOutput";
    private static final String NBT_PLAN_BYTES = "planBytes";
    private static final String NBT_TOTAL_FINAL_OUTPUT_AMOUNT = "totalFinalOutputAmount";
    private static final String NBT_TASKS = "tasks";
    private static final String NBT_TASK_PSEUDO = "pseudo";
    private static final String NBT_TEMPORARY_PATTERNS = "temporaryPatterns";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_CRAFTING_PROGRESS = "#craftingProgress";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final ListCraftingInventory pseudoInventory;
    final Map<IPatternDetails, TaskProgress> tasks = new Object2ObjectOpenHashMap<>();
    final Map<AEItemKey, TemporaryPseudoCraftingProvider> temporaryProviders = new Object2ObjectOpenHashMap<>();
    final ElapsedTimeTracker timeTracker;
    GenericStack finalOutput;
    @Nullable
    final
    Integer playerId;
    long totalFinalOutputAmount;
    long remainingAmount;
    long remainingIntermediateFinalOutput;
    long planBytes;
    boolean suspended;

    ExecutingCraftingJob(ICraftingPlan plan, CraftingDifferenceListener postCraftingDifference, CraftingLink link,
                         @Nullable Integer playerId, KeyCounter remainingMissingItems) {
        this.finalOutput = plan.finalOutput();
        this.totalFinalOutputAmount = this.finalOutput.amount();
        this.remainingAmount = this.finalOutput.amount();
        this.remainingIntermediateFinalOutput = plan.intermediateFinalOutputAmount();
        this.planBytes = plan.bytes();
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.pseudoInventory = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        if (plan instanceof CraftingPlan craftingPlan) {
            for (var provider : craftingPlan.temporaryProviders()) {
                for (var pattern : provider.getAvailablePatterns()) {
                    var temporaryProvider = provider instanceof TemporaryPseudoCraftingProvider temporary
                        ? temporary
                        : new TemporaryPseudoCraftingProvider(pattern);
                    this.temporaryProviders.put(PseudoPatternDetails.unwrap(pattern).getDefinition(), temporaryProvider);
                }
            }
        }

        // Fill waiting for and tasks
        this.timeTracker = new ElapsedTimeTracker();
        for (var entry : plan.emittedItems()) {
            waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        for (var entry : remainingMissingItems) {
            waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        for (Object2LongMap.Entry<IPatternDetails> entry : plan.patternTimes().object2LongEntrySet()) {
            tasks.computeIfAbsent(entry.getKey(), ignored -> new TaskProgress()).value += entry.getLongValue();
            for (var output : entry.getKey().getOutputs()) {
                var amount = output.amount() * entry.getLongValue() * output.what().getAmountPerUnit();
                timeTracker.addMaxItems(amount, output.what().getType());
            }
        }
        this.link = link;
        this.playerId = playerId;
        this.suspended = false;
    }

    ExecutingCraftingJob(NBTTagCompound data,
                         CraftingDifferenceListener postCraftingDifference, CraftingCpuLogic cpu) {
        this.link = new CraftingLink(data.getCompoundTag(NBT_LINK), cpu.cluster);
        IGrid grid = cpu.cluster.getGrid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(link);
        }

        this.finalOutput = GenericStack.readTag(data.getCompoundTag(NBT_FINAL_OUTPUT));
        this.totalFinalOutputAmount = data.hasKey(NBT_TOTAL_FINAL_OUTPUT_AMOUNT, Constants.NBT.TAG_LONG)
            ? data.getLong(NBT_TOTAL_FINAL_OUTPUT_AMOUNT)
            : GenericStack.getStackSizeOrZero(this.finalOutput);
        this.remainingAmount = data.getLong(NBT_REMAINING_AMOUNT);
        this.remainingIntermediateFinalOutput = data.getLong(NBT_REMAINING_INTERMEDIATE_FINAL_OUTPUT);
        this.planBytes = data.hasKey(NBT_PLAN_BYTES, Constants.NBT.TAG_LONG)
            ? data.getLong(NBT_PLAN_BYTES)
            : 0;
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.waitingFor.readFromNBT(data.getTagList(NBT_WAITING_FOR, Constants.NBT.TAG_COMPOUND));
        this.pseudoInventory = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.pseudoInventory.readFromNBT(data.getTagList(NBT_PSEUDO_INVENTORY, Constants.NBT.TAG_COMPOUND));
        this.timeTracker = new ElapsedTimeTracker(data.getCompoundTag(NBT_TIME_TRACKER));
        if (data.hasKey(NBT_PLAYER_ID, Constants.NBT.TAG_INT)) {
            this.playerId = data.getInteger(NBT_PLAYER_ID);
        } else {
            this.playerId = null;
        }

        NBTTagList tasksTag = data.getTagList(NBT_TASKS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tasksTag.tagCount(); ++i) {
            final NBTTagCompound item = tasksTag.getCompoundTagAt(i);
            var pattern = AEItemKey.fromTag(item);
            var details = pattern != null ? PatternDetailsHelper.decodePattern(pattern, cpu.cluster.getLevel()) : null;
            if (details != null) {
                if (item.getBoolean(NBT_TASK_PSEUDO)) {
                    details = PseudoPatternDetails.wrap(details);
                }
                final TaskProgress tp = new TaskProgress();
                tp.value = item.getLong(NBT_CRAFTING_PROGRESS);
                this.tasks.put(details, tp);
            }
        }
        NBTTagList temporaryPatternsTag = data.getTagList(NBT_TEMPORARY_PATTERNS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < temporaryPatternsTag.tagCount(); i++) {
            var item = temporaryPatternsTag.getCompoundTagAt(i);
            var pattern = AEItemKey.fromTag(item);
            var details = pattern != null ? PatternDetailsHelper.decodePattern(pattern, cpu.cluster.getLevel()) : null;
            if (details != null) {
                var pseudoDetails = PseudoPatternDetails.wrap(details);
                this.temporaryProviders.put(PseudoPatternDetails.unwrap(pseudoDetails).getDefinition(),
                    new TemporaryPseudoCraftingProvider(pseudoDetails));
            }
        }

        this.suspended = data.getBoolean(NBT_SUSPENDED);
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound data = new NBTTagCompound();

        NBTTagCompound linkData = new NBTTagCompound();
        link.writeToNBT(linkData);
        data.setTag(NBT_LINK, linkData);

        data.setTag(NBT_FINAL_OUTPUT, GenericStack.writeTag(finalOutput));

        data.setTag(NBT_WAITING_FOR, waitingFor.writeToNBT());
        data.setTag(NBT_PSEUDO_INVENTORY, pseudoInventory.writeToNBT());
        data.setTag(NBT_TIME_TRACKER, timeTracker.writeToNBT());

        final NBTTagList list = new NBTTagList();
        for (var e : this.tasks.entrySet()) {
            var item = e.getKey().getDefinition().toTag();
            item.setLong(NBT_CRAFTING_PROGRESS, e.getValue().value);
            item.setBoolean(NBT_TASK_PSEUDO, PseudoPatternDetails.isPseudo(e.getKey()));
            list.appendTag(item);
        }
        data.setTag(NBT_TASKS, list);

        final NBTTagList temporaryPatterns = new NBTTagList();
        for (var provider : this.temporaryProviders.values()) {
            temporaryPatterns.appendTag(PseudoPatternDetails.unwrap(provider.pattern()).getDefinition().toTag());
        }
        data.setTag(NBT_TEMPORARY_PATTERNS, temporaryPatterns);

        data.setLong(NBT_REMAINING_AMOUNT, remainingAmount);
        data.setLong(NBT_REMAINING_INTERMEDIATE_FINAL_OUTPUT, remainingIntermediateFinalOutput);
        data.setLong(NBT_PLAN_BYTES, planBytes);
        data.setLong(NBT_TOTAL_FINAL_OUTPUT_AMOUNT, totalFinalOutputAmount);
        if (this.playerId != null) {
            data.setInteger(NBT_PLAYER_ID, this.playerId);
        }

        data.setBoolean(NBT_SUSPENDED, suspended);
        return data;
    }

    Iterable<ICraftingProvider> getProvidersForPattern(IPatternDetails details) {
        var provider = this.temporaryProviders.get(PseudoPatternDetails.unwrap(details).getDefinition());
        if (provider != null) {
            return List.of(provider);
        }
        return List.of();
    }

    boolean isTemporaryPattern(IPatternDetails details) {
        return this.temporaryProviders.containsKey(PseudoPatternDetails.unwrap(details).getDefinition());
    }

    void merge(ICraftingPlan plan, KeyCounter remainingMissingItems) {
        if (!this.finalOutput.what().equals(plan.finalOutput().what())) {
            throw new IllegalArgumentException("Cannot merge crafting jobs with different final outputs");
        }

        this.remainingAmount = LongMath.saturatedAdd(this.remainingAmount, plan.finalOutput().amount());
        this.totalFinalOutputAmount = LongMath.saturatedAdd(this.totalFinalOutputAmount, plan.finalOutput().amount());
        this.finalOutput = new GenericStack(this.finalOutput.what(), this.totalFinalOutputAmount);
        this.remainingIntermediateFinalOutput = LongMath.saturatedAdd(this.remainingIntermediateFinalOutput,
            plan.intermediateFinalOutputAmount());
        this.planBytes = LongMath.saturatedAdd(this.planBytes, plan.bytes());

        if (plan instanceof CraftingPlan craftingPlan) {
            for (var provider : craftingPlan.temporaryProviders()) {
                for (var pattern : provider.getAvailablePatterns()) {
                    var temporaryProvider = provider instanceof TemporaryPseudoCraftingProvider temporary
                        ? temporary
                        : new TemporaryPseudoCraftingProvider(pattern);
                    this.temporaryProviders.put(PseudoPatternDetails.unwrap(pattern).getDefinition(), temporaryProvider);
                }
            }
        }

        for (var entry : plan.emittedItems()) {
            waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        for (var entry : remainingMissingItems) {
            waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        for (Object2LongMap.Entry<IPatternDetails> entry : plan.patternTimes().object2LongEntrySet()) {
            var progress = tasks.computeIfAbsent(entry.getKey(), ignored -> new TaskProgress());
            progress.value = LongMath.saturatedAdd(progress.value, entry.getLongValue());
            for (var output : entry.getKey().getOutputs()) {
                var amount = LongMath.saturatedMultiply(output.amount(),
                    LongMath.saturatedMultiply(entry.getLongValue(), output.what().getAmountPerUnit()));
                timeTracker.addMaxItems(amount, output.what().getType());
            }
        }
    }

    long estimateRemainingPlanBytes() {
        if (this.totalFinalOutputAmount <= 0) {
            return this.planBytes;
        }

        long remainingFinalOutputBytes = LongMath.saturatedMultiply(this.planBytes, this.remainingAmount)
            / this.totalFinalOutputAmount;
        return Math.max(remainingFinalOutputBytes, this.planBytes > 0 ? 1 : 0);
    }

    @FunctionalInterface
    interface CraftingDifferenceListener {
        void onCraftingDifference(AEKey what);
    }

    static class TaskProgress {
        long value = 0;
    }
}
