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

package ae2.container.me.crafting;

import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingCPU;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class CraftingCPUCycler {

    private final Predicate<ICraftingCPU> cpuFilter;
    private final ChangeListener changeListener;
    private final ObjectList<CraftingCPURecord> cpus = new ObjectArrayList<>();
    private final List<CraftingCPURecord> readOnlyCpus = ObjectLists.unmodifiable(this.cpus);
    /**
     * Only the current filtered CPUs are retained; removed CPUs must not stay alive through serial bookkeeping.
     */
    private final Reference2ObjectMap<ICraftingCPU, CraftingCPURecord> activeCpuRecords =
        new Reference2ObjectOpenHashMap<>();
    private IGrid activeNetwork;
    private int selectedCpu = -1;
    private int nextCpuSerial = 1;
    private boolean initialDataSent;
    private boolean allowNoSelection;

    public CraftingCPUCycler(Predicate<ICraftingCPU> cpuFilter, ChangeListener changeListener) {
        this.cpuFilter = cpuFilter;
        this.changeListener = changeListener;
    }

    public void detectAndSendChanges(IGrid network) {
        final ImmutableSet<ICraftingCPU> cpuSet = network.getCraftingService().getCpus();
        boolean networkChanged = this.activeNetwork != network;
        if (networkChanged) {
            this.activeNetwork = network;
            this.activeCpuRecords.clear();
            this.cpus.clear();
            this.selectedCpu = -1;
        }

        var previouslySelectedCpu = !networkChanged && this.selectedCpu >= 0 && this.selectedCpu < this.cpus.size()
            ? this.cpus.get(this.selectedCpu).getCpu()
            : null;

        int matches = 0;
        boolean changed = networkChanged || !this.initialDataSent;
        this.initialDataSent = true;
        ObjectArrayList<ICraftingCPU> matchingCpus = new ObjectArrayList<>();
        for (ICraftingCPU cpu : cpuSet) {
            final boolean matched = this.cpuFilter.test(cpu);
            if (matched) {
                matches++;
                matchingCpus.add(cpu);
                CraftingCPURecord cpuRecord = this.activeCpuRecords.get(cpu);
                if (cpuRecord == null
                    || !Objects.equals(cpuRecord.getRawName(), cpu.getName())
                    || cpuRecord.getSize() != cpu.getAvailableStorage()
                    || cpuRecord.getProcessors() != cpu.getCoProcessors()) {
                    changed = true;
                }
            } else if (this.activeCpuRecords.containsKey(cpu)) {
                changed = true;
            }
        }

        if (changed || this.cpus.size() != matches || this.activeCpuRecords.size() != matchingCpus.size()) {
            this.cpus.clear();
            Reference2ObjectMap<ICraftingCPU, CraftingCPURecord> refreshedRecords =
                new Reference2ObjectOpenHashMap<>(matchingCpus.size());
            for (var i = 0; i < matchingCpus.size(); i++) {
                var cpu = matchingCpus.get(i);
                CraftingCPURecord previousRecord = this.activeCpuRecords.get(cpu);
                int serial = previousRecord != null ? previousRecord.getSerial() : this.nextCpuSerial++;
                CraftingCPURecord record = new CraftingCPURecord(serial, cpu.getAvailableStorage(),
                    cpu.getCoProcessors(), cpu);
                this.cpus.add(record);
                refreshedRecords.put(cpu, record);
            }
            this.activeCpuRecords.clear();
            this.activeCpuRecords.putAll(refreshedRecords);

            Collections.sort(this.cpus);
            for (int i = 0; i < this.cpus.size(); i++) {
                CraftingCPURecord cpu = this.cpus.get(i);
                if (cpu.getName() == null) {
                    cpu.setName(new TextComponentString("#" + (i + 1)));
                }
            }
            if (previouslySelectedCpu != null) {
                this.selectedCpu = -1;
                for (int i = 0; i < this.cpus.size(); i++) {
                    if (this.cpus.get(i).getCpu() == previouslySelectedCpu) {
                        this.selectedCpu = i;
                        break;
                    }
                }
            }

            this.notifyListener();
        }
    }

    public void cycleCpu(boolean next) {
        if (next) {
            this.selectedCpu++;
        } else {
            this.selectedCpu--;
        }

        int lowerLimit = this.allowNoSelection ? -1 : 0;
        if (this.selectedCpu < lowerLimit) {
            this.selectedCpu = this.cpus.size() - 1;
        } else if (this.selectedCpu >= this.cpus.size()) {
            this.selectedCpu = lowerLimit;
        }

        this.notifyListener();
    }

    public List<CraftingCPURecord> cpus() {
        return this.readOnlyCpus;
    }

    public int getSelectedCpuSerial() {
        if (this.selectedCpu < 0 || this.selectedCpu >= this.cpus.size()) {
            return -1;
        }
        return this.cpus.get(this.selectedCpu).getSerial();
    }

    public void selectCpu(int serial) {
        if (serial == -1 && this.allowNoSelection) {
            this.selectedCpu = -1;
            this.notifyListener();
            return;
        }

        for (int i = 0; i < this.cpus.size(); i++) {
            if (this.cpus.get(i).getSerial() == serial) {
                this.selectedCpu = i;
                this.notifyListener();
                return;
            }
        }
    }

    public void setAllowNoSelection(boolean allowNoSelection) {
        this.allowNoSelection = allowNoSelection;
    }

    private void notifyListener() {
        if (this.selectedCpu >= this.cpus.size()) {
            this.selectedCpu = -1;
        }

        if (!this.allowNoSelection && this.selectedCpu == -1 && !this.cpus.isEmpty()) {
            this.selectedCpu = 0;
        }

        if (this.selectedCpu != -1) {
            this.changeListener.onChange(this.cpus.get(this.selectedCpu), true);
        } else {
            this.changeListener.onChange(null, !this.cpus.isEmpty());
        }
    }

    @FunctionalInterface
    public interface ChangeListener {
        void onChange(CraftingCPURecord selectedCpu, boolean cpusAvailable);
    }
}
