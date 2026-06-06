package ae2.integration.modules.igtooltip;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.IOrientationStrategy;
import ae2.core.definitions.AEItems;
import ae2.core.localization.Side;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.me.InWorldGridNode;
import ae2.me.helpers.IGridConnectedTile;
import ae2.me.service.TickManagerService;
import ae2.parts.AEBasePart;
import ae2.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

public final class DebugProvider {
    private static final String TAG_NODES = "debugNodes";
    private static final String TAG_NODE_NAME = "nodeName";
    private static final String TAG_TICK_TIME = "tickTime";
    private static final String TAG_TICK_SLEEPING = "tickSleeping";
    private static final String TAG_TICK_ALERTABLE = "tickAlertable";
    private static final String TAG_TICK_AWAKE = "tickAwake";
    private static final String TAG_TICK_QUEUED = "tickQueued";
    private static final String TAG_TICK_CURRENT_RATE = "tickCurrentRate";
    private static final String TAG_TICK_LAST_TICK = "tickLastTick";
    private static final String TAG_TICK_TIME_AVG = "avg";
    private static final String TAG_TICK_TIME_MAX = "max";
    private static final String TAG_TICK_TIME_SUM = "sum";
    private static final String TAG_NODE_EXPOSED = "exposedSides";

    private DebugProvider() {
    }

    public static void provideBlockEntityBody(TileEntity object, TooltipContext context, TooltipBuilder tooltip) {
        var player = context.player();

        if (!DebugProvider.isVisible(player)) {
            return;
        }

        DebugProvider.addBlockEntityRotation(object, tooltip);
        DebugProvider.addToTooltip(context.serverData(), tooltip);
    }

    public static void provideBlockEntityData(EntityPlayer player, TileEntity object, NBTTagCompound serverData) {
        if (object instanceof IGridConnectedTile gridConnectedTile && DebugProvider.isVisible(player)) {
            DebugProvider.addServerDataMainNode(serverData, gridConnectedTile.getMainNode());
        }
    }

    public static void providePartBody(AEBasePart object, TooltipContext context, TooltipBuilder tooltip) {
        DebugProvider.addToTooltip(context.serverData(), tooltip);
    }

    public static void providePartData(EntityPlayer player, AEBasePart part, NBTTagCompound serverData) {
        if (DebugProvider.isVisible(player)) {
            DebugProvider.addServerDataMainNode(serverData, part.getMainNode());
            DebugProvider.addServerDataNode(serverData, TopText.debug_external_node,
                part.getExternalFacingNode());
        }
    }

    private static void addBlockEntityRotation(TileEntity blockEntity, TooltipBuilder tooltip) {
        var blockState = blockEntity.getWorld().getBlockState(blockEntity.getPos());
        var strategy = IOrientationStrategy.get(blockState);
        if (!strategy.getProperties().isEmpty()) {
            var orientation = BlockOrientation.get(strategy, blockState);
            tooltip.addLine(new TextComponentString("")
                .appendSibling(label(TopText.debug_forward.text(), TextFormatting.WHITE))
                .appendSibling(localizeSide(orientation.getSide(ae2.api.orientation.RelativeSide.FRONT)))
                .appendSibling(spaceLabel(TopText.debug_spin.text(), TextFormatting.WHITE))
                .appendSibling(new TextComponentString(String.valueOf(orientation.getSpin()))));
        }
    }

    private static void addToTooltip(NBTTagCompound serverData, TooltipBuilder tooltip) {
        var nodes = serverData.getTagList(TAG_NODES, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < nodes.tagCount(); i++) {
            var nodeCompound = nodes.getCompoundTagAt(i);
            if (nodes.tagCount() > 1) {
                var nodeNameKey = nodeCompound.getString(TAG_NODE_NAME);
                tooltip.addLine(styled(new TextComponentTranslation(nodeNameKey), TextFormatting.ITALIC));
            }
            addNodeToTooltip(nodeCompound, tooltip);
        }
    }

    private static void addNodeToTooltip(NBTTagCompound tag, TooltipBuilder tooltip) {
        if (tag.hasKey(TAG_TICK_TIME, Constants.NBT.TAG_COMPOUND)) {
            long[] tickTimes = readLongArray(tag);
            if (tickTimes.length == 3) {
                var avg = tickTimes[0];
                var max = tickTimes[1];
                var sum = tickTimes[2];

                tooltip.addLine(new TextComponentString("")
                    .appendSibling(label(TopText.debug_tick_time.text(), TextFormatting.WHITE))
                    .appendSibling(label(TopText.debug_avg.text(), TextFormatting.ITALIC))
                    .appendSibling(styled(Platform.formatTimeMeasurement(avg), TextFormatting.WHITE))
                    .appendSibling(spaceLabel(TopText.debug_max.text(), TextFormatting.ITALIC))
                    .appendSibling(styled(Platform.formatTimeMeasurement(max), TextFormatting.WHITE))
                    .appendSibling(spaceLabel(TopText.debug_sum.text(), TextFormatting.ITALIC))
                    .appendSibling(styled(Platform.formatTimeMeasurement(sum), TextFormatting.WHITE)));
            }
        }

        if (tag.hasKey(TAG_TICK_QUEUED)) {
            ITextComponent status = new TextComponentString("");
            if (tag.getBoolean(TAG_TICK_SLEEPING)) {
                appendStatus(status, TopText.debug_sleeping.text());
            }
            if (tag.getBoolean(TAG_TICK_ALERTABLE)) {
                appendStatus(status, TopText.debug_alertable.text());
            }
            if (tag.getBoolean(TAG_TICK_AWAKE)) {
                appendStatus(status, TopText.debug_awake.text());
            }
            if (tag.getBoolean(TAG_TICK_QUEUED)) {
                appendStatus(status, TopText.debug_queued.text());
            }

            tooltip.addLine(new TextComponentString("")
                .appendSibling(label(TopText.debug_tick_status.text(), TextFormatting.WHITE))
                .appendSibling(status));
            tooltip.addLine(new TextComponentString("")
                .appendSibling(label(TopText.debug_tick_rate.text(), TextFormatting.WHITE))
                .appendText(String.valueOf(tag.getInteger(TAG_TICK_CURRENT_RATE)))
                .appendSibling(spaceLabel(TopText.debug_last.text(), TextFormatting.WHITE))
                .appendText(String.valueOf(tag.getLong(TAG_TICK_LAST_TICK)))
                .appendText(" ")
                .appendSibling(TopText.debug_ticks_ago.text()));
        }

        if (tag.hasKey(TAG_NODE_EXPOSED, Constants.NBT.TAG_INT)) {
            var exposedSides = tag.getInteger(TAG_NODE_EXPOSED);
            var line = label(TopText.debug_node_exposed.text(), TextFormatting.WHITE);
            for (EnumFacing value : EnumFacing.values()) {
                var sideText = new TextComponentString(value.name().substring(0, 1));
                sideText.setStyle(new Style()
                    .setColor((exposedSides & (1 << value.ordinal())) == 0 ? TextFormatting.GRAY
                        : TextFormatting.GREEN));
                line.appendSibling(sideText);
            }
            tooltip.addLine(line);
        }
    }

    private static void addServerDataMainNode(NBTTagCompound tag, IManagedGridNode managedGridNode) {
        addServerDataNode(tag, TopText.debug_main_node, managedGridNode.getNode());
    }

    private static void addServerDataNode(NBTTagCompound tag, TopText name, @Nullable IGridNode node) {
        var nodeTag = toServerData(node, name);
        if (nodeTag != null) {
            NBTTagList nodes;
            if (tag.hasKey(TAG_NODES, Constants.NBT.TAG_LIST)) {
                nodes = (NBTTagList) tag.getTag(TAG_NODES);
            } else {
                nodes = new NBTTagList();
                tag.setTag(TAG_NODES, nodes);
            }
            nodes.appendTag(nodeTag);
        }
    }

    @Nullable
    private static NBTTagCompound toServerData(@Nullable IGridNode node, TopText name) {
        if (node == null) {
            return null;
        }

        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG_NODE_NAME, name.getTranslationKey());

        if (node.getService(IGridTickable.class) != null) {
            var tickManager = (TickManagerService) node.grid().getTickManager();
            var avg = tickManager.getAverageTime(node);
            var max = tickManager.getMaximumTime(node);
            var sum = tickManager.getOverallTime(node);

            NBTTagCompound tickTime = new NBTTagCompound();
            tickTime.setLong(TAG_TICK_TIME_AVG, avg);
            tickTime.setLong(TAG_TICK_TIME_MAX, max);
            tickTime.setLong(TAG_TICK_TIME_SUM, sum);
            tag.setTag(TAG_TICK_TIME, tickTime);

            var status = tickManager.getStatus(node);
            tag.setBoolean(TAG_TICK_SLEEPING, status.sleeping());
            tag.setBoolean(TAG_TICK_ALERTABLE, status.alertable());
            tag.setBoolean(TAG_TICK_AWAKE, status.awake());
            tag.setBoolean(TAG_TICK_QUEUED, status.queued());
            tag.setInteger(TAG_TICK_CURRENT_RATE, status.currentRate());
            tag.setLong(TAG_TICK_LAST_TICK, status.lastTick());
        }

        if (node instanceof InWorldGridNode inWorldNode) {
            int exposedSides = 0;
            for (var value : EnumFacing.values()) {
                if (inWorldNode.isExposedOnSide(value)) {
                    exposedSides |= 1 << value.ordinal();
                }
            }
            tag.setInteger(TAG_NODE_EXPOSED, exposedSides);
        }

        return tag;
    }

    private static boolean isVisible(EntityPlayer player) {
        return AEItems.DEBUG_CARD.is(player.getHeldItem(EnumHand.OFF_HAND))
            || AEItems.DEBUG_CARD.is(player.getHeldItem(EnumHand.MAIN_HAND));
    }

    private static TextComponentString styled(String text, TextFormatting formatting) {
        TextComponentString component = new TextComponentString(text);
        component.setStyle(new Style().setColor(formatting));
        return component;
    }

    private static ITextComponent styled(ITextComponent component, TextFormatting formatting) {
        component.setStyle(new Style().setColor(formatting));
        return component;
    }

    private static void appendStatus(ITextComponent line, ITextComponent status) {
        if (!line.getSiblings().isEmpty()) {
            line.appendSibling(new TextComponentString(", "));
        }
        line.appendSibling(status);
    }

    private static ITextComponent label(ITextComponent text, TextFormatting formatting) {
        text.appendText(": ");
        return styled(text, formatting);
    }

    private static ITextComponent spaceLabel(ITextComponent text, TextFormatting formatting) {
        return new TextComponentString(" ").appendSibling(label(text, formatting));
    }

    private static ITextComponent localizeSide(EnumFacing side) {
        return switch (side) {
            case NORTH -> Side.North.text();
            case SOUTH -> Side.South.text();
            case EAST -> Side.East.text();
            case WEST -> Side.West.text();
            case UP -> Side.Up.text();
            case DOWN -> Side.Down.text();
        };
    }

    private static long[] readLongArray(NBTTagCompound tag) {
        NBTTagCompound tickTime = tag.getCompoundTag(DebugProvider.TAG_TICK_TIME);
        return new long[]{
            tickTime.getLong(TAG_TICK_TIME_AVG),
            tickTime.getLong(TAG_TICK_TIME_MAX),
            tickTime.getLong(TAG_TICK_TIME_SUM)
        };
    }
}
