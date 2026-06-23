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
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class CraftingCPUCycler {

    private final Predicate<ICraftingCPU> cpuFilter;
    private final ChangeListener changeListener;
    private final List<CraftingCPURecord> cpus = new ObjectArrayList<>();
    private final List<CraftingCPURecord> readOnlyCpus = Collections.unmodifiableList(this.cpus);
    private final Reference2IntMap<ICraftingCPU> cpuSerialMap = new Reference2IntOpenHashMap<>();
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
        var previouslySelectedCpu = this.selectedCpu >= 0 && this.selectedCpu < this.cpus.size()
            ? this.cpus.get(this.selectedCpu).getCpu()
            : null;

        int matches = 0;
        boolean changed = !this.initialDataSent;
        this.initialDataSent = true;
        for (ICraftingCPU cpu : cpuSet) {
            boolean found = false;
            for (CraftingCPURecord cpuRecord : this.cpus) {
                if (cpuRecord.getCpu() == cpu) {
                    found = true;
                    if (!Objects.equals(cpuRecord.getRawName(), cpu.getName())
                        || cpuRecord.getSize() != cpu.getAvailableStorage()
                        || cpuRecord.getProcessors() != cpu.getCoProcessors()) {
                        changed = true;
                    }
                    break;
                }
            }

            final boolean matched = this.cpuFilter.test(cpu);
            if (matched) {
                matches++;
            }
            if (found != matched) {
                changed = true;
            }
        }

        if (changed || this.cpus.size() != matches) {
            this.cpus.clear();
            for (ICraftingCPU cpu : cpuSet) {
                if (this.cpuFilter.test(cpu)) {
                    this.cpus.add(new CraftingCPURecord(getOrAssignCpuSerial(cpu), cpu.getAvailableStorage(),
                        cpu.getCoProcessors(), cpu));
                }
            }

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

    private int getOrAssignCpuSerial(ICraftingCPU cpu) {
        int serial = this.cpuSerialMap.getInt(cpu);
        if (serial == 0) {
            serial = this.nextCpuSerial++;
            this.cpuSerialMap.put(cpu, serial);
        }
        return serial;
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
