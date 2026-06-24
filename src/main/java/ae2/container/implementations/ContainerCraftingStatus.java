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
import ae2.client.gui.Icon;
import ae2.container.ISubGui;
import ae2.container.guisync.GuiSync;
import ae2.container.guisync.PacketWritable;
import ae2.core.AELog;
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
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;

public class ContainerCraftingStatus extends ContainerCraftingCPU implements ISubGui {
    private static final int MAX_CPU_LIST_ENTRIES = 1024;
    private static final int MIN_CPU_LIST_ENTRY_BYTES = 47;
    private static final int MAX_ICON_ID_LENGTH = 128;

    private static final CraftingCpuList EMPTY_CPU_LIST = new CraftingCpuList(Collections.emptyList());

    private static final Comparator<CraftingCpuListEntry> CPU_COMPARATOR = Comparator
        .comparing((CraftingCpuListEntry e) -> e.name() == null)
        .thenComparing(e -> e.name() != null ? e.name().getFormattedText() : "")
        .thenComparingInt(CraftingCpuListEntry::serial);

    private static final String ACTION_SELECT_CPU = "selectCpu";
    private static final String ACTION_CYCLE_CPU_MODE = "cycleCpuMode";
    private static final String ACTION_SET_CPU_MODE = "setCpuMode";
    private static final String ACTION_RENAME_CPU = "renameCpu";
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;

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
        registerClientAction(ACTION_SET_CPU_MODE, SetCpuModePayload.class, this::setCpuMode);
        registerClientAction(ACTION_RENAME_CPU, RenameCpuPayload.class, this::renameCpu);
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

    private static Icon getCpuListBackgroundIcon(CraftingCPUCluster cluster, int serial, boolean focused) {
        String state = focused ? "focused" : "unfocused";
        Icon icon;
        try {
            icon = focused ? cluster.getFocusedCpuListBackgroundIcon() : cluster.getUnfocusedCpuListBackgroundIcon();
        } catch (RuntimeException e) {
            AELog.error(e, String.format(
                "Failed to get %s crafting CPU list row background icon from %s for serial %d",
                state,
                cluster.getClass().getName(),
                serial));
            throw e;
        }

        if (icon == null) {
            String message = String.format(
                "Crafting CPU %s returned null %s list row background icon for serial %d",
                cluster.getClass().getName(),
                state,
                serial);
            AELog.error(message);
            throw new IllegalStateException(message);
        }

        Icon registeredIcon = Icon.byId(icon.id());
        if (registeredIcon != icon) {
            String message = String.format(
                "Crafting CPU %s returned unregistered %s list row background icon %s for serial %d",
                cluster.getClass().getName(),
                state,
                icon.id(),
                serial);
            AELog.error(message);
            throw new IllegalStateException(message);
        }

        validateIconIdLength(icon.id(), state + " crafting CPU list row background icon");
        return icon;
    }

    private static void validateIconIdLength(ResourceLocation iconId, String context) {
        if (iconId.toString().length() > MAX_ICON_ID_LENGTH) {
            String message = String.format(
                "%s id %s exceeds max packet length %d",
                context,
                iconId,
                MAX_ICON_ID_LENGTH);
            AELog.error(message);
            throw new IllegalStateException(message);
        }
    }

    private static CraftingCPUCluster requireCraftingCpuCluster(ICraftingCPU cpu, int serial, String operation) {
        if (cpu instanceof CraftingCPUCluster cluster) {
            return cluster;
        }

        String message = String.format(
            "Cannot %s for serial %d because %s is not a CraftingCPUCluster",
            operation,
            serial,
            cpu.getClass().getName());
        AELog.error(message);
        throw new IllegalStateException(message);
    }

    private static World requireCpuWorld(CraftingCPUCluster cluster, int serial) {
        World world = cluster.getLevel();
        if (world != null) {
            return world;
        }

        String message = String.format(
            "Cannot create crafting CPU status list entry for serial %d because %s has no level",
            serial,
            cluster.getClass().getName());
        AELog.error(message);
        throw new IllegalStateException(message);
    }

    private static BlockPos requireCpuCorePos(CraftingCPUCluster cluster, int serial) {
        BlockPos corePos = cluster.getCorePos();
        if (corePos != null) {
            return corePos;
        }

        String message = String.format(
            "Cannot create crafting CPU status list entry for serial %d because %s has no core position",
            serial,
            cluster.getClass().getName());
        AELog.error(message);
        throw new IllegalStateException(message);
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

    private CraftingCpuList createCpuList() {
        var entries = new ObjectArrayList<CraftingCpuListEntry>(lastCpuSet.size());
        for (var cpu : lastCpuSet) {
            int serial = getOrAssignCpuSerial(cpu);
            if (!(cpu instanceof CraftingCPUCluster cluster)) {
                String message = String.format(
                    "Cannot create crafting CPU status list entry for serial %d because %s is not a CraftingCPUCluster",
                    serial,
                    cpu.getClass().getName());
                AELog.error(message);
                throw new IllegalStateException(message);
            }

            Icon unfocusedBackgroundIcon = getCpuListBackgroundIcon(cluster, serial, false);
            Icon focusedBackgroundIcon = getCpuListBackgroundIcon(cluster, serial, true);
            World world = requireCpuWorld(cluster, serial);
            BlockPos corePos = requireCpuCorePos(cluster, serial);
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
                status != null ? status.elapsedTimeNanos() : 0,
                unfocusedBackgroundIcon.id(),
                focusedBackgroundIcon.id(),
                world.provider.getDimension(),
                corePos,
                cluster.getBoundsMin(),
                cluster.getBoundsMax()));
        }
        entries.sort(CPU_COMPARATOR);
        return new CraftingCpuList(entries);
    }

    public void cycleCpuMode(int serial, boolean backwards) {
        if (serial <= 0) {
            AELog.warn("Rejected crafting CPU mode cycle for invalid serial %d", serial);
            return;
        }

        if (isClientSide()) {
            updateCpuModeClientSide(serial, backwards);
            sendClientAction(ACTION_CYCLE_CPU_MODE, backwards ? -serial : serial);
            return;
        }

        CpuSelectionMode updatedMode = null;
        for (var cpu : lastCpuSet) {
            if (cpuSerialMap.getOrDefault(cpu, -1) != serial) {
                continue;
            }

            CraftingCPUCluster cluster = requireCraftingCpuCluster(cpu, serial, "cycle CPU mode");
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

    public void setCpuMode(int serial, CpuSelectionMode mode) {
        setCpuMode(new SetCpuModePayload(serial, mode));
    }

    private void setCpuMode(SetCpuModePayload payload) {
        if (payload.serial() <= 0) {
            AELog.warn("Rejected crafting CPU mode set for invalid serial %d", payload.serial());
            return;
        }
        if (payload.mode() == null) {
            AELog.warn("Rejected crafting CPU mode set for serial %d because mode is null", payload.serial());
            return;
        }

        if (isClientSide()) {
            updateCpuModeClientSide(payload.serial(), payload.mode());
            sendClientAction(ACTION_SET_CPU_MODE, payload);
            return;
        }

        for (var cpu : lastCpuSet) {
            if (cpuSerialMap.getOrDefault(cpu, -1) != payload.serial()) {
                continue;
            }

            CraftingCPUCluster cluster = requireCraftingCpuCluster(cpu, payload.serial(), "set CPU mode");
            if (cluster.getSelectionMode() == payload.mode()) {
                return;
            }

            cluster.getConfigManager().putSetting(Settings.CPU_SELECTION_MODE, payload.mode());
            if (selectedCpuSerial == payload.serial()) {
                this.schedulingMode = payload.mode();
            }
            this.cpuList = createCpuList();
            this.lastUpdate = 0;
            return;
        }

        AELog.warn("Failed to set mode for unknown crafting CPU serial %d", payload.serial());
    }

    public void renameCpu(int serial, String name) {
        renameCpu(new RenameCpuPayload(serial, name));
    }

    private void renameCpu(RenameCpuPayload payload) {
        if (payload.serial() <= 0) {
            AELog.warn("Rejected crafting CPU rename for invalid serial %d", payload.serial());
            return;
        }
        if (payload.name() != null && payload.name().length() > MAX_CUSTOM_NAME_LENGTH) {
            AELog.warn(
                "Rejected crafting CPU rename for serial %d because name length %d exceeds max %d",
                payload.serial(),
                payload.name().length(),
                MAX_CUSTOM_NAME_LENGTH);
            return;
        }

        if (isClientSide()) {
            sendClientAction(ACTION_RENAME_CPU, payload);
            return;
        }

        for (var cpu : lastCpuSet) {
            if (cpuSerialMap.getOrDefault(cpu, -1) != payload.serial()) {
                continue;
            }

            CraftingCPUCluster cluster = requireCraftingCpuCluster(cpu, payload.serial(), "rename CPU");
            if (!cluster.rename(payload.name())) {
                AELog.warn("Failed to rename crafting CPU %d because its core is unavailable", payload.serial());
                return;
            }

            this.cpuList = createCpuList();
            this.lastUpdate = 0;
            if (selectedCpuSerial == payload.serial()) {
                setCPU(cluster);
            }
            return;
        }

        AELog.warn("Failed to rename unknown crafting CPU serial %d", payload.serial());
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
                CpuSelectionMode updatedMode = EnumCycler.rotateEnum(cpu.mode(), backwards, Settings.CPU_SELECTION_MODE.getValues());
                updatedCpus.add(withUpdatedCpuMode(cpu, updatedMode));
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

    private void updateCpuModeClientSide(int serial, CpuSelectionMode mode) {
        Objects.requireNonNull(mode, "mode");

        ObjectList<CraftingCpuListEntry> updatedCpus = new ObjectArrayList<>(cpuList.cpus().size());
        boolean changed = false;

        for (var cpu : cpuList.cpus()) {
            if (cpu.serial() == serial) {
                updatedCpus.add(withUpdatedCpuMode(cpu, mode));
                changed = true;
            } else {
                updatedCpus.add(cpu);
            }
        }

        if (changed) {
            this.cpuList = new CraftingCpuList(updatedCpus);
            if (selectedCpuSerial == serial) {
                this.schedulingMode = mode;
            }
        }
    }

    private static CraftingCpuListEntry withUpdatedCpuMode(CraftingCpuListEntry cpu, CpuSelectionMode mode) {
        return new CraftingCpuListEntry(
            cpu.serial(),
            cpu.storage(),
            cpu.coProcessors(),
            cpu.name(),
            mode,
            cpu.currentJob(),
            cpu.progress(),
            cpu.elapsedTimeNanos(),
            cpu.unfocusedBackgroundIcon(),
            cpu.focusedBackgroundIcon(),
            cpu.dimensionId(),
            cpu.corePos(),
            cpu.boundsMin(),
            cpu.boundsMax());
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
        long elapsedTimeNanos,
        ResourceLocation unfocusedBackgroundIcon,
        ResourceLocation focusedBackgroundIcon,
        int dimensionId,
        BlockPos corePos,
        BlockPos boundsMin,
        BlockPos boundsMax) {

        public CraftingCpuListEntry {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(unfocusedBackgroundIcon, "unfocusedBackgroundIcon");
            Objects.requireNonNull(focusedBackgroundIcon, "focusedBackgroundIcon");
            Objects.requireNonNull(corePos, "corePos");
            Objects.requireNonNull(boundsMin, "boundsMin");
            Objects.requireNonNull(boundsMax, "boundsMax");
        }

        public static CraftingCpuListEntry readFromPacket(PacketBuffer buffer) {
            int serial = buffer.readInt();
            long storage = buffer.readLong();
            int coProcessors = buffer.readInt();
            ITextComponent name = TextComponents.readFromPacket(buffer);
            CpuSelectionMode mode = NetworkPacketHelper.readEnumOrNull(buffer, CpuSelectionMode.class);
            if (mode == null) {
                throw new IllegalArgumentException("Invalid crafting CPU selection mode");
            }
            ResourceLocation unfocusedBackgroundIcon = readIconId(buffer, "unfocused");
            ResourceLocation focusedBackgroundIcon = readIconId(buffer, "focused");
            int dimensionId = buffer.readInt();
            BlockPos corePos = buffer.readBlockPos();
            BlockPos boundsMin = buffer.readBlockPos();
            BlockPos boundsMax = buffer.readBlockPos();
            return new CraftingCpuListEntry(
                serial,
                storage,
                coProcessors,
                name,
                mode,
                GenericStack.readBuffer(buffer),
                buffer.readFloat(),
                buffer.readVarLong(),
                unfocusedBackgroundIcon,
                focusedBackgroundIcon,
                dimensionId,
                corePos,
                boundsMin,
                boundsMax);
        }

        private static ResourceLocation readIconId(PacketBuffer buffer, String state) {
            try {
                return new ResourceLocation(buffer.readString(MAX_ICON_ID_LENGTH));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid " + state
                    + " crafting CPU list row background icon id", e);
            }
        }

        private static void writeIconId(PacketBuffer buffer, ResourceLocation iconId) {
            validateIconIdLength(iconId, "crafting CPU list row background icon");
            buffer.writeString(iconId.toString());
        }

        public void writeToPacket(PacketBuffer buffer) {
            buffer.writeInt(this.serial);
            buffer.writeLong(this.storage);
            buffer.writeInt(this.coProcessors);
            TextComponents.writeToPacket(buffer, this.name);
            buffer.writeEnumValue(this.mode);
            writeIconId(buffer, this.unfocusedBackgroundIcon);
            writeIconId(buffer, this.focusedBackgroundIcon);
            buffer.writeInt(this.dimensionId);
            buffer.writeBlockPos(this.corePos);
            buffer.writeBlockPos(this.boundsMin);
            buffer.writeBlockPos(this.boundsMax);
            GenericStack.writeBuffer(this.currentJob, buffer);
            buffer.writeFloat(this.progress);
            buffer.writeVarLong(this.elapsedTimeNanos);
        }
    }

    public record RenameCpuPayload(int serial, @Nullable String name) {
    }

    public record SetCpuModePayload(int serial, CpuSelectionMode mode) {
    }
}
