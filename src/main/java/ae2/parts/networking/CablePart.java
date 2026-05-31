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

package ae2.parts.networking;

import ae2.api.ids.AEPartIds;
import ae2.api.implementations.parts.ICablePart;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.pathing.ChannelMode;
import ae2.api.parts.BusSupport;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.items.parts.ColoredPartItem;
import ae2.items.tools.powered.ColorApplicatorItem;
import ae2.parts.AEBasePart;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public abstract class CablePart extends AEBasePart implements ICablePart {

    private static final IGridNodeListener<CablePart> NODE_LISTENER = new NodeListener<>() {
        @Override
        public void onInWorldConnectionChanged(CablePart nodeOwner, IGridNode node) {
            super.onInWorldConnectionChanged(nodeOwner, node);
            nodeOwner.markForUpdate();
        }
    };

    private static final Map<AECableType, Map<AEColor, ResourceLocation>> PART_IDS = new EnumMap<>(AECableType.class);

    static {
        PART_IDS.put(AECableType.GLASS, AEPartIds.CABLE_GLASS);
        PART_IDS.put(AECableType.COVERED, AEPartIds.CABLE_COVERED);
        PART_IDS.put(AECableType.SMART, AEPartIds.CABLE_SMART);
        PART_IDS.put(AECableType.DENSE_COVERED, AEPartIds.CABLE_DENSE_COVERED);
        PART_IDS.put(AECableType.DENSE_SMART, AEPartIds.CABLE_DENSE_SMART);
    }

    private final int[] channelsOnSide = {0, 0, 0, 0, 0, 0};
    private Set<EnumFacing> connections = Collections.emptySet();

    public CablePart(ColoredPartItem<?> partItem) {
        super(partItem);
        this.getMainNode()
            .setFlags(GridFlags.PREFERRED)
            .setIdlePowerUsage(0.0)
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(EnumFacing.class));
        this.getMainNode().setGridColor(partItem.getColor());
    }

    protected static void addConnectionBox(IPartCollisionHelper bch, EnumFacing direction, double min, double max,
                                           double distanceFromEnd) {
        switch (direction) {
            case DOWN -> bch.addBox(min, distanceFromEnd, min, max, min, max);
            case EAST -> bch.addBox(max, min, min, 16.0 - distanceFromEnd, max, max);
            case NORTH -> bch.addBox(min, min, distanceFromEnd, max, max, min);
            case SOUTH -> bch.addBox(min, min, max, max, max, 16.0 - distanceFromEnd);
            case UP -> bch.addBox(min, max, min, max, 16.0 - distanceFromEnd, max);
            case WEST -> bch.addBox(distanceFromEnd, min, min, min, max, max);
            default -> {
            }
        }
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    @Override
    public BusSupport supportsBuses() {
        return BusSupport.CABLE;
    }

    @Override
    public AEColor getCableColor() {
        if (getPartItem() instanceof ColoredPartItem<?> coloredPartItem) {
            return coloredPartItem.getColor();
        }
        return AEColor.TRANSPARENT;
    }

    @Override
    public final void getBoxes(IPartCollisionHelper bch) {
        getBoxes(bch, dir -> true);
    }

    public abstract void getBoxes(IPartCollisionHelper bch, Predicate<@Nullable EnumFacing> filterConnections);

    protected void addNonDenseBoxes(IPartCollisionHelper bch, Predicate<@Nullable EnumFacing> filterConnections,
                                    double min, double max) {
        if (filterConnections.test(null)) {
            bch.addBox(min, min, min, max, max, max);
        }

        var ph = this.getHost();
        if (ph != null) {
            for (var dir : EnumFacing.VALUES) {
                if (!filterConnections.test(dir)) {
                    continue;
                }

                var p = ph.getPart(dir);
                if (p != null) {
                    var dist = p.getCableConnectionLength(this.getCableConnectionType());

                    if (dist <= 0 || dist > 8) {
                        continue;
                    }

                    addConnectionBox(bch, dir, min, max, dist);
                }
            }
        }

        for (var of : this.getConnections()) {
            if (!filterConnections.test(of)) {
                continue;
            }

            addConnectionBox(bch, of, min, max, 0.0);
        }
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        if (cable == this.getCableConnectionType()) {
            return 4;
        } else if (cable.ordinal() >= this.getCableConnectionType().ordinal()) {
            return -1;
        } else {
            return 8;
        }
    }

    @Override
    public void onPlacement(EntityPlayer player) {
        super.onPlacement(player);

        ItemStack stack = player.getHeldItemOffhand();
        if (!stack.isEmpty() && stack.getItem() instanceof ColorApplicatorItem colorApp) {
            AEColor color = colorApp.getActiveColor(stack);
            if (color != null && color != getCableColor() && colorApp.consumeColor(stack, color, true)) {
                if (changeColor(color, player) && !player.capabilities.isCreativeMode) {
                    colorApp.consumeColor(stack, color, false);
                }
            }
        }
    }

    @Override
    public boolean changeColor(AEColor newColor, EntityPlayer who) {
        if (this.getCableColor() != newColor) {
            IPartItem<?> newPart = null;
            var partIds = PART_IDS.get(this.getCableConnectionType());
            if (partIds != null) {
                var id = partIds.get(newColor);
                if (id != null) {
                    newPart = IPartItem.byId(id);
                }
            }

            if (newPart != null) {
                if (isClientSide()) {
                    return true;
                }

                setPartItem(newPart);
                getMainNode().setGridColor(getCableColor());
                getHost().partChanged();
                getHost().markForUpdate();
                getHost().markForSave();
                return true;
            }
        }
        return false;
    }

    @Override
    public void setExposedOnSides(EnumSet<EnumFacing> sides) {
        this.getMainNode().setExposedOnSides(sides);
    }

    @Override
    public boolean isConnected(EnumFacing side) {
        return this.getConnections().contains(side);
    }

    public void markForUpdate() {
        this.getHost().markForUpdate();
    }

    protected void updateConnections() {
        if (!isClientSide()) {
            var n = this.getGridNode();
            if (n != null) {
                this.setConnections(n.getConnectedSides());
            } else {
                this.setConnections(Collections.emptySet());
            }
        }
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        super.writeToStream(data);

        boolean[] writeChannels = new boolean[EnumFacing.VALUES.length];
        byte[] channelsPerSide = new byte[EnumFacing.VALUES.length];

        for (var thisSide : EnumFacing.VALUES) {
            var part = this.getHost().getPart(thisSide);
            if (part != null) {
                int channels = 0;
                if (part.getGridNode() != null) {
                    for (var gc : part.getGridNode().getConnections()) {
                        channels = Math.max(channels, gc.getUsedChannels());
                    }
                }
                channelsPerSide[thisSide.ordinal()] = getVisualChannels(channels);
                writeChannels[thisSide.ordinal()] = true;
            }
        }

        int connectedSidesPacked = 0;
        var n = getGridNode();
        if (n != null) {
            for (var entry : n.getInWorldConnections().entrySet()) {
                var side = entry.getKey().ordinal();
                var connection = entry.getValue();
                channelsPerSide[side] = getVisualChannels(connection.getUsedChannels());
                writeChannels[side] = true;
                connectedSidesPacked |= 1 << side;
            }
        }
        data.writeByte((byte) connectedSidesPacked);

        for (int i = 0; i < writeChannels.length; i++) {
            if (writeChannels[i]) {
                data.writeByte(channelsPerSide[i]);
            }
        }
    }

    private byte getVisualChannels(int channels) {
        var node = getGridNode();
        if (node == null) {
            return 0;
        }

        byte visualMaxChannels = switch (getCableConnectionType()) {
            case GLASS, SMART, COVERED -> 8;
            case DENSE_COVERED, DENSE_SMART -> 32;
            default -> 0;
        };

        if (node.grid().getPathingService().getChannelMode() == ChannelMode.INFINITE) {
            return channels <= 0 ? 0 : visualMaxChannels;
        }

        int gridMaxChannels = node.getMaxChannels();
        if (visualMaxChannels == 0 || gridMaxChannels == 0) {
            return 0;
        }

        var result = (byte) (Math.min(visualMaxChannels, channels * visualMaxChannels / gridMaxChannels));
        if (result == 0 && channels > 0) {
            return 1;
        } else {
            return result;
        }
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        var changed = super.readFromStream(data);

        int connectedSidesPacked = data.readByte();
        var previousConnections = this.getConnections();

        boolean channelsChanged = false;

        var connections = EnumSet.noneOf(EnumFacing.class);
        for (var d : EnumFacing.VALUES) {
            boolean conOnSide = (connectedSidesPacked & (1 << d.ordinal())) != 0;
            if (conOnSide) {
                connections.add(d);
            }

            int ch = 0;

            if (conOnSide || this.getHost().getPart(d) != null) {
                ch = data.readByte() & 0xFF;
            }

            if (ch != this.channelsOnSide[d.ordinal()]) {
                channelsChanged = true;
                this.setChannelsOnSide(d.ordinal(), ch);
            }
        }
        this.setConnections(connections);

        return changed || !previousConnections.equals(this.getConnections()) || channelsChanged;
    }

    @Override
    public void writeVisualStateToNBT(NBTTagCompound data) {
        super.writeVisualStateToNBT(data);

        if (!isClientSide()) {
            updateConnections();
            var packet = new PacketBuffer(Unpooled.buffer());
            writeToStream(packet);
            readFromStream(packet);
        }

        for (var side : EnumFacing.VALUES) {
            if (connections.contains(side)) {
                var sideName = "channels" + StringUtils.capitalize(side.getName2());
                data.setInteger(sideName, channelsOnSide[side.ordinal()]);
            }
        }

        var connectionsTag = new NBTTagList();
        for (var connection : connections) {
            connectionsTag.appendTag(new NBTTagString(connection.getName2()));
        }
        data.setTag("connections", connectionsTag);
    }

    @Override
    public void readVisualStateFromNBT(NBTTagCompound data) {
        super.readVisualStateFromNBT(data);

        if (data.hasKey("channels")) {
            Arrays.fill(this.channelsOnSide, data.getInteger("channels"));
        } else {
            for (var side : EnumFacing.VALUES) {
                var sideName = "channels" + StringUtils.capitalize(side.getName2());
                channelsOnSide[side.ordinal()] = data.getInteger(sideName);
            }
        }

        var connections = EnumSet.noneOf(EnumFacing.class);
        var connectionsTag = data.getTagList("connections", 8);
        for (int i = 0; i < connectionsTag.tagCount(); i++) {
            var side = EnumFacing.byName(connectionsTag.getStringTagAt(i));
            if (side != null) {
                connections.add(side);
            }
        }
        setConnections(connections);
    }

    public int getChannelsOnSide(EnumFacing side) {
        if (!this.isPowered()) {
            return 0;
        }
        return this.channelsOnSide[side.ordinal()];
    }

    void setChannelsOnSide(int i, int channels) {
        this.channelsOnSide[i] = channels;
    }

    Set<EnumFacing> getConnections() {
        return this.connections;
    }

    void setConnections(Set<EnumFacing> connections) {
        this.connections = connections;
    }
}
