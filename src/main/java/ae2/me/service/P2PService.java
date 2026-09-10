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

package ae2.me.service;

import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.events.GridBootingStatusChange;
import ae2.api.networking.events.GridPowerStatusChange;
import ae2.api.networking.ticking.ITickManager;
import ae2.core.AELog;
import ae2.parts.p2p.MEP2PTunnelPart;
import ae2.parts.p2p.P2PTunnelPart;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Random;
import java.util.stream.Stream;

public class P2PService implements IGridService, IGridServiceProvider {
    static {
        GridHelper.addGridServiceEventHandler(GridBootingStatusChange.class, P2PService.class,
            (service, evt) -> {
                if (!evt.isBooting()) {
                    service.wakeInputTunnels();
                }
            });
        GridHelper.addGridServiceEventHandler(GridPowerStatusChange.class, P2PService.class,
            (service, evt) -> service.wakeInputTunnels());
    }

    private final IGrid myGrid;
    private final Multimap<Short, P2PTunnelPart<?>> inputs = LinkedHashMultimap.create();
    private final Multimap<Short, P2PTunnelPart<?>> outputs = LinkedHashMultimap.create();
    private final Short2ObjectMap<Reference2BooleanMap<Class<?>>> inputMatchCache = new Short2ObjectOpenHashMap<>();
    // Membership is immediately visible; peer callbacks run once per frequency after lifecycle changes settle.
    private final Short2ByteLinkedOpenHashMap pendingUpdates = new Short2ByteLinkedOpenHashMap();
    private final ShortArrayList frequencyDispatch = new ShortArrayList();
    private final ObjectArrayList<P2PTunnelPart<?>> tunnelDispatch = new ObjectArrayList<>();
    private boolean dispatching;
    private final Random frequencyGenerator;

    public P2PService(IGrid g) {
        this.myGrid = g;
        this.frequencyGenerator = new Random(g.hashCode());
    }

    public static P2PService get(IGrid grid) {
        return grid.getService(P2PService.class);
    }

    public void wakeInputTunnels() {
        final ITickManager tm = this.myGrid.getTickManager();
        for (P2PTunnelPart<?> tunnel : this.inputs.values()) {
            if (tunnel instanceof MEP2PTunnelPart) {
                tm.wakeDevice(tunnel.getGridNode());
            }
        }
    }

    @Override
    public void removeNode(IGridNode node) {
        if (node.getOwner() instanceof P2PTunnelPart<?> tunnel) {
            if (tunnel instanceof MEP2PTunnelPart && !node.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                return;
            }

            short frequency = tunnel.getFrequency();
            boolean removedInput = this.inputs.remove(frequency, tunnel);
            boolean removedOutput = this.outputs.remove(frequency, tunnel);
            if (removedInput) {
                this.inputMatchCache.remove(frequency);
                updateTunnel(frequency, true);
            }
            if (removedOutput) {
                updateTunnel(frequency, false);
            }
        }
    }

    @Override
    public void addNode(IGridNode node, @Nullable NBTTagCompound savedData) {
        if (node.getOwner() instanceof P2PTunnelPart<?> tunnel) {
            if (tunnel instanceof MEP2PTunnelPart && !node.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                return;
            }

            if (tunnel.isOutput()) {
                if (!this.outputs.put(tunnel.getFrequency(), tunnel)) {
                    return;
                }
            } else {
                this.addInput(tunnel);
            }
            this.updateTunnel(tunnel.getFrequency(), !tunnel.isOutput());
        }
    }

    private void addInput(P2PTunnelPart<?> tunnel) {
        if (!tunnel.supportsMultipleInputs()) {
            this.inputs.removeAll(tunnel.getFrequency());
        }
        this.inputs.put(tunnel.getFrequency(), tunnel);
        this.inputMatchCache.remove(tunnel.getFrequency());
    }

    private void updateTunnel(short freq, boolean updateOutputs) {
        byte flag = (byte) (updateOutputs ? 2 : 1);
        this.pendingUpdates.put(freq, (byte) (this.pendingUpdates.get(freq) | flag));
    }

    private void updateAllTunnels(short freq) {
        this.pendingUpdates.put(freq, (byte) 3);
    }

    @Override
    public void onServerEndTick() {
        if (this.dispatching || this.pendingUpdates.isEmpty()) {
            return;
        }
        this.dispatching = true;
        this.frequencyDispatch.addAll(this.pendingUpdates.keySet());
        try {
            for (int i = 0; i < this.frequencyDispatch.size(); i++) {
                short frequency = this.frequencyDispatch.getShort(i);
                byte flags = this.pendingUpdates.remove(frequency);
                if ((flags & 2) != 0) {
                    dispatchTunnels(frequency, this.outputs.get(frequency));
                }
                if ((flags & 1) != 0) {
                    dispatchTunnels(frequency, this.inputs.get(frequency));
                }
            }
        } finally {
            this.frequencyDispatch.clear();
            this.dispatching = false;
        }
    }

    private void dispatchTunnels(short frequency, Collection<P2PTunnelPart<?>> members) {
        this.tunnelDispatch.addAll(members);
        try {
            for (int i = 0; i < this.tunnelDispatch.size(); i++) {
                var tunnel = this.tunnelDispatch.get(i);
                if (tunnel.getFrequency() == frequency && members.contains(tunnel)) {
                    try {
                        tunnel.onTunnelNetworkChange();
                    } catch (RuntimeException exception) {
                        AELog.error(exception, "P2P network-change callback failed for " + tunnel);
                    }
                }
            }
        } finally {
            this.tunnelDispatch.clear();
        }
    }

    public void updateFreq(P2PTunnelPart<?> t, short newFrequency) {
        final short oldFrequency = t.getFrequency();
        if (oldFrequency == newFrequency && (t.isOutput() ? this.outputs : this.inputs).containsEntry(oldFrequency, t)) {
            return;
        }
        boolean removedOutput = this.outputs.remove(oldFrequency, t);
        boolean removedInput = this.inputs.remove(oldFrequency, t);
        if (removedInput) {
            this.inputMatchCache.remove(oldFrequency);
        }
        t.setFrequency(newFrequency);

        if (t.isOutput()) {
            this.outputs.put(t.getFrequency(), t);
        } else {
            this.addInput(t);
        }
        if (removedInput) {
            this.updateTunnel(oldFrequency, true);
        }
        if (removedOutput) {
            this.updateTunnel(oldFrequency, false);
        }
        this.updateAllTunnels(newFrequency);
        t.onTunnelConfigChange();
    }

    public short newFrequency() {
        short newFrequency;
        int cycles = 0;

        do {
            newFrequency = (short) this.frequencyGenerator.nextInt(1 << 16);
            cycles++;
        } while (newFrequency == 0 || this.inputs.containsKey(newFrequency));

        if (cycles > 25) {
            AELog.debug("Generating a new P2P frequency '%1$d' took %2$d cycles", newFrequency, cycles);
        }

        return newFrequency;
    }

    public <T extends P2PTunnelPart<T>> Stream<T> getOutputs(short freq, Class<T> c) {
        if (!this.inputs.containsKey(freq)) {
            return Stream.empty();
        }
        // Check that a matching input exists for the requested type
        Reference2BooleanMap<Class<?>> matches = this.inputMatchCache.computeIfAbsent(freq, ignored -> new Reference2BooleanOpenHashMap<>());
        boolean hasMatchingInput = matches.computeIfAbsent(c, type -> {
            for (P2PTunnelPart<?> input : this.inputs.get(freq)) {
                if (((Class<?>) type).isInstance(input)) {
                    return true;
                }
            }
            return false;
        });
        if (!hasMatchingInput) {
            return Stream.empty();
        }

        return this.outputs.get(freq)
                           .stream()
                           .filter(c::isInstance)
                           .map(c::cast);
    }

    public P2PTunnelPart<?> getInput(short freq) {
        Collection<P2PTunnelPart<?>> matchingInputs = this.inputs.get(freq);
        return matchingInputs.isEmpty() ? null : matchingInputs.iterator().next();
    }

    public <T extends P2PTunnelPart<T>> Stream<T> getInputs(short freq, Class<T> c) {
        return this.inputs.get(freq)
                          .stream()
                          .filter(c::isInstance)
                          .map(c::cast);
    }
}
