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

package ae2.container.implementations;

import ae2.api.config.CpuSelectionMode;
import ae2.api.config.Settings;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingCPU;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.ITerminalHost;
import ae2.container.ISubGui;
import ae2.container.guisync.GuiSync;
import ae2.container.guisync.PacketWritable;
import ae2.core.network.NetworkPacketHelper;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.text.TextComponents;
import ae2.util.EnumCycler;
import com.google.common.collect.ImmutableSet;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

public class ContainerCraftingStatus extends ContainerCraftingCPU implements ISubGui {
    private static final int MAX_CPU_LIST_ENTRIES = 1024;
    private static final int MIN_CPU_LIST_ENTRY_BYTES = 17;

    private static final CraftingCpuList EMPTY_CPU_LIST = new CraftingCpuList(Collections.emptyList());

    private static final Comparator<CraftingCpuListEntry> CPU_COMPARATOR = Comparator
        .comparing((CraftingCpuListEntry e) -> e.name() == null)
        .thenComparing(e -> e.name() != null ? e.name().getFormattedText() : "")
        .thenComparingInt(CraftingCpuListEntry::serial);

    private static final String ACTION_SELECT_CPU = "selectCpu";
    private static final String ACTION_CYCLE_CPU_MODE = "cycleCpuMode";

    private final WeakHashMap<ICraftingCPU, Integer> cpuSerialMap = new WeakHashMap<>();
    private final ITerminalHost host;
    @GuiSync(8)
    public CraftingCpuList cpuList = EMPTY_CPU_LIST;
    private int nextCpuSerial = 1;
    private ImmutableSet<ICraftingCPU> lastCpuSet = ImmutableSet.of();
    private int lastUpdate = 0;
    @Nullable
    private ICraftingCPU selectedCpu;
    @GuiSync(9)
    private int selectedCpuSerial = -1;

    public ContainerCraftingStatus(InventoryPlayer ip, ITerminalHost host) {
        super(ip, host);
        this.host = host;
        registerClientAction(ACTION_SELECT_CPU, Integer.class, this::selectCpu);
        registerClientAction(ACTION_CYCLE_CPU_MODE, Integer.class, this::cycleCpuMode);
    }

    @Override
    public ISubGuiHost getHost() {
        return host;
    }

    @Override
    protected void setCPU(@Nullable ICraftingCPU cpu) {
        super.setCPU(cpu);
        this.selectedCpu = cpu;
        this.selectedCpuSerial = cpu == null ? -1 : getOrAssignCpuSerial(cpu);
    }

    @Override
    public void detectAndSendChanges() {
        IGrid network = this.getGrid();
        if (isServerSide() && network != null) {
            if (!lastCpuSet.equals(network.getCraftingService().getCpus())
                || ++lastUpdate >= 20) {
                lastCpuSet = network.getCraftingService().getCpus();
                cpuList = createCpuList();
                lastUpdate = 0;
            }
        } else {
            lastUpdate = 20;
            if (!lastCpuSet.isEmpty()) {
                cpuList = EMPTY_CPU_LIST;
                lastCpuSet = ImmutableSet.of();
            }
        }

        if (selectedCpuSerial != -1) {
            if (!containsCpuSerial(selectedCpuSerial)) {
                selectCpu(-1);
            }
        }

        if (selectedCpuSerial == -1) {
            for (var cpu : cpuList.cpus()) {
                if (cpu.currentJob() != null) {
                    selectCpu(cpu.serial());
                    break;
                }
            }

            if (selectedCpuSerial == -1 && !cpuList.cpus().isEmpty()) {
                selectCpu(cpuList.cpus().getFirst().serial());
            }
        }

        super.detectAndSendChanges();
    }

    private CraftingCpuList createCpuList() {
        var entries = new ObjectArrayList<CraftingCpuListEntry>(lastCpuSet.size());
        for (var cpu : lastCpuSet) {
            int serial = getOrAssignCpuSerial(cpu);
            var status = cpu.getJobStatus();
            float progress = 0;
            if (status != null && status.totalItems() > 0) {
                progress = (float) (status.progress() / (double) status.totalItems());
            }

            entries.add(new CraftingCpuListEntry(
                serial,
                cpu.getAvailableStorage(),
                cpu.getCoProcessors(),
                cpu.getName(),
                cpu.getSelectionMode(),
                status != null ? status.crafting() : null,
                progress,
                status != null ? status.elapsedTimeNanos() : 0));
        }
        entries.sort(CPU_COMPARATOR);
        return new CraftingCpuList(entries);
    }

    private int getOrAssignCpuSerial(ICraftingCPU cpu) {
        return cpuSerialMap.computeIfAbsent(cpu, ignored -> nextCpuSerial++);
    }

    @Override
    public boolean allowConfiguration() {
        return false;
    }

    public void selectCpu(int serial) {
        if (isClientSide()) {
            selectedCpuSerial = serial;
            sendClientAction(ACTION_SELECT_CPU, serial);
        } else {
            ICraftingCPU newSelectedCpu = null;
            if (serial != -1) {
                for (var cpu : lastCpuSet) {
                    if (cpuSerialMap.getOrDefault(cpu, -1) == serial) {
                        newSelectedCpu = cpu;
                        break;
                    }
                }
            }

            if (newSelectedCpu != selectedCpu) {
                setCPU(newSelectedCpu);
            }
        }
    }

    private boolean containsCpuSerial(int serial) {
        for (var cpu : cpuList.cpus()) {
            if (cpu.serial() == serial) {
                return true;
            }
        }
        return false;
    }

    public void cycleCpuMode(int serial, boolean backwards) {
        if (serial <= 0) {
            return;
        }

        if (isClientSide()) {
            updateCpuModeClientSide(serial, backwards);
            sendClientAction(ACTION_CYCLE_CPU_MODE, backwards ? -serial : serial);
            return;
        }

        CpuSelectionMode updatedMode = null;
        for (var cpu : lastCpuSet) {
            if (cpuSerialMap.getOrDefault(cpu, -1) != serial || !(cpu instanceof CraftingCPUCluster cluster)) {
                continue;
            }

            updatedMode = EnumCycler.rotateEnum(
                cluster.getSelectionMode(),
                backwards,
                Settings.CPU_SELECTION_MODE.getValues());
            cluster.getConfigManager().putSetting(Settings.CPU_SELECTION_MODE, updatedMode);
            break;
        }

        if (updatedMode != null) {
            if (selectedCpuSerial == serial) {
                this.schedulingMode = updatedMode;
            }
            this.cpuList = createCpuList();
            this.lastUpdate = 0;
        }
    }

    private void cycleCpuMode(int encodedSerial) {
        cycleCpuMode(Math.abs(encodedSerial), encodedSerial < 0);
    }

    private void updateCpuModeClientSide(int serial, boolean backwards) {
        ObjectList<CraftingCpuListEntry> updatedCpus = new ObjectArrayList<>(cpuList.cpus().size());
        boolean changed = false;
        CpuSelectionMode selectedUpdatedMode = null;

        for (var cpu : cpuList.cpus()) {
            if (cpu.serial() == serial) {
                CpuSelectionMode updatedMode = EnumCycler.rotateEnum(
                    cpu.mode(),
                    backwards,
                    Settings.CPU_SELECTION_MODE.getValues());
                updatedCpus.add(new CraftingCpuListEntry(
                    cpu.serial(),
                    cpu.storage(),
                    cpu.coProcessors(),
                    cpu.name(),
                    updatedMode,
                    cpu.currentJob(),
                    cpu.progress(),
                    cpu.elapsedTimeNanos()));
                changed = true;
                if (selectedCpuSerial == serial) {
                    selectedUpdatedMode = updatedMode;
                }
            } else {
                updatedCpus.add(cpu);
            }
        }

        if (changed) {
            this.cpuList = new CraftingCpuList(updatedCpus);
            if (selectedUpdatedMode != null) {
                this.schedulingMode = selectedUpdatedMode;
            }
        }
    }

    public int getSelectedCpuSerial() {
        return selectedCpuSerial;
    }

    public record CraftingCpuList(List<CraftingCpuListEntry> cpus) implements PacketWritable {
        public CraftingCpuList {
            cpus = List.copyOf(cpus);
        }

        @SuppressWarnings("unused")
        public CraftingCpuList(ByteBuf data) {
            PacketBuffer buffer = new PacketBuffer(data);
            int count = buffer.readInt();
            if (count < 0 || count > MAX_CPU_LIST_ENTRIES
                || count > buffer.readableBytes() / MIN_CPU_LIST_ENTRY_BYTES) {
                throw new IllegalArgumentException("Invalid crafting CPU list entry count: " + count);
            }
            ObjectList<CraftingCpuListEntry> readCpus = new ObjectArrayList<>(count);
            for (int i = 0; i < count; i++) {
                readCpus.add(CraftingCpuListEntry.readFromPacket(buffer));
            }
            this(List.copyOf(readCpus));
        }

        @Override
        public void writeToPacket(ByteBuf data) {
            PacketBuffer buffer = new PacketBuffer(data);
            buffer.writeInt(this.cpus.size());
            for (CraftingCpuListEntry cpu : this.cpus) {
                cpu.writeToPacket(buffer);
            }
        }
    }

    public record CraftingCpuListEntry(
        int serial,
        long storage,
        int coProcessors,
        @Nullable ITextComponent name,
        CpuSelectionMode mode,
        @Nullable GenericStack currentJob,
        float progress,
        long elapsedTimeNanos) {

        public static CraftingCpuListEntry readFromPacket(PacketBuffer buffer) {
            int serial = buffer.readInt();
            long storage = buffer.readLong();
            int coProcessors = buffer.readInt();
            ITextComponent name = TextComponents.readFromPacket(buffer);
            CpuSelectionMode mode = NetworkPacketHelper.readEnumOrNull(buffer, CpuSelectionMode.class);
            if (mode == null) {
                throw new IllegalArgumentException("Invalid crafting CPU selection mode");
            }
            return new CraftingCpuListEntry(
                serial,
                storage,
                coProcessors,
                name,
                mode,
                GenericStack.readBuffer(buffer),
                buffer.readFloat(),
                buffer.readVarLong());
        }

        public void writeToPacket(PacketBuffer buffer) {
            buffer.writeInt(this.serial);
            buffer.writeLong(this.storage);
            buffer.writeInt(this.coProcessors);
            TextComponents.writeToPacket(buffer, this.name);
            buffer.writeEnumValue(this.mode);
            GenericStack.writeBuffer(this.currentJob, buffer);
            buffer.writeFloat(this.progress);
            buffer.writeVarLong(this.elapsedTimeNanos);
        }
    }
}
