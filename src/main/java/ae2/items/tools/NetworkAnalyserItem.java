package ae2.items.tools;

import ae2.api.implementations.IPowerChannelState;
import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.networking.security.IActionHost;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.NetworkDataUpdatePacket;
import ae2.items.AEBaseItem;
import ae2.me.InWorldGridNode;
import ae2.me.NetworkData;
import ae2.me.helpers.IGridConnectedTile;
import ae2.me.netdata.LinkFlag;
import ae2.me.netdata.NodeFlag;
import ae2.me.netdata.State;
import ae2.me.tracker.PlayerTracker;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

public class NetworkAnalyserItem extends AEBaseItem implements IGuiItem {
    private static final long CACHE_VALID_TICKS = 200L;
    private static final String CONFIG_TAG = "networkAnalyserConfig";
    private static final String TARGET_DIM_TAG = "targetDim";
    private static final String TARGET_POS_TAG = "targetPos";
    private static final Map<IGrid, CachedNetworkData> NETWORK_CACHE = new WeakHashMap<>();

    public NetworkAnalyserItem() {
        setMaxStackSize(1);
    }

    public static NetworkAnalyserConfig getConfig(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(CONFIG_TAG, 10)
            ? NetworkAnalyserConfig.fromTag(tag.getCompoundTag(CONFIG_TAG))
            : NetworkAnalyserConfig.DEFAULT;
    }

    public static void setConfig(ItemStack stack, NetworkAnalyserConfig config) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setTag(CONFIG_TAG, config.toTag());
    }

    public static void clearCache() {
        NETWORK_CACHE.clear();
    }

    @Nullable
    public static TargetPos getTarget(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(TARGET_DIM_TAG) || !tag.hasKey(TARGET_POS_TAG)) {
            return null;
        }
        return new TargetPos(tag.getInteger(TARGET_DIM_TAG), BlockPos.fromLong(tag.getLong(TARGET_POS_TAG)));
    }

    private static void setTarget(ItemStack stack, World world, BlockPos pos) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setInteger(TARGET_DIM_TAG, world.provider.getDimension());
        tag.setLong(TARGET_POS_TAG, pos.toLong());
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!world.isRemote && !player.isSneaking()) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.NETWORK_ANALYSER, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (!world.isRemote && player.getHeldItem(hand).getItem() == this) {
            setTarget(player.getHeldItem(hand), world, pos);
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        if (world.isRemote || !(entity instanceof EntityPlayerMP player) || !isSelected || player.getHeldItemMainhand() != stack) {
            return;
        }

        TargetPos target = getTarget(stack);
        if (target == null || target.dimensionId != world.provider.getDimension()) {
            return;
        }
        if (!PlayerTracker.needUpdate(player, target.dimensionId, target.pos)) {
            return;
        }

        IGridNode node = findNode(world, target.pos);
        if (node == null || node.grid() == null) {
            return;
        }

        InitNetwork.sendToClient(player, new NetworkDataUpdatePacket(getOrScanNetwork(node.grid(), world)));
    }

    @Nullable
    private IGridNode findNode(World world, BlockPos pos) {
        IInWorldGridNodeHost host = GridHelper.getNodeHost(world, pos);
        if (host instanceof IGridConnectedTile gridConnectedTile) {
            return gridConnectedTile.getGridNode();
        }
        if (host != null) {
            for (EnumFacing facing : EnumFacing.VALUES) {
                IGridNode node = host.getGridNode(facing);
                if (node != null) {
                    return node;
                }
            }
            return host.getGridNode(null);
        }
        return null;
    }

    private NetworkData getOrScanNetwork(IGrid grid, World world) {
        long currentTick = world.getTotalWorldTime();
        int dimension = world.provider.getDimension();
        CachedNetworkData cached = NETWORK_CACHE.get(grid);
        if (cached != null && cached.dimension == dimension && currentTick - cached.generatedTick <= CACHE_VALID_TICKS) {
            return cached.data;
        }

        NetworkData data = scanNetwork(grid, world);
        NETWORK_CACHE.put(grid, new CachedNetworkData(dimension, currentTick, data));
        return data;
    }

    private NetworkData scanNetwork(IGrid grid, World world) {
        var connections = new ObjectOpenHashSet<IGridConnection>();
        Map<BlockPos, NetworkData.ANode> nodes = new java.util.HashMap<>();
        for (IGridNode node : grid.getNodes()) {
            if (node.getLevel().provider.getDimension() != world.provider.getDimension()) {
                continue;
            }
            NetworkData.ANode wrapped = wrapGridNode(node);
            if (wrapped != null) {
                nodes.put(wrapped.pos(), wrapped);
                connections.addAll(node.getConnections());
            }
        }

        Set<NetworkData.ALink> links = new ObjectOpenHashSet<>();
        for (IGridConnection connection : connections) {
            NetworkData.ANode a = getWrappedNode(nodes, connection.a());
            NetworkData.ANode b = getWrappedNode(nodes, connection.b());
            if (a != null && b != null && !Objects.equals(a, b)) {
                State<LinkFlag> state = new State<>(LinkFlag.NORMAL);
                if (connection.a().hasFlag(GridFlags.DENSE_CAPACITY) && connection.b().hasFlag(GridFlags.DENSE_CAPACITY)) {
                    state.set(LinkFlag.DENSE);
                }
                if (connection.a().hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)
                    && connection.b().hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) {
                    state.set(LinkFlag.COMPRESSED);
                }
                links.add(new NetworkData.ALink(a, b, (short) connection.getUsedChannels(), state));
            }
        }

        return new NetworkData(nodes.values().toArray(new NetworkData.ANode[0]), links.toArray(new NetworkData.ALink[0]));
    }

    @Nullable
    private NetworkData.ANode getWrappedNode(Map<BlockPos, NetworkData.ANode> nodes, IGridNode node) {
        if (node instanceof InWorldGridNode worldNode) {
            return nodes.get(worldNode.getLocation());
        }
        return null;
    }

    @Nullable
    private NetworkData.ANode wrapGridNode(IGridNode node) {
        if (node instanceof InWorldGridNode worldNode) {
            State<NodeFlag> state = new State<>(NodeFlag.NORMAL);
            if (!checkChannel(worldNode)) {
                state.set(NodeFlag.MISSING);
            }
            if (node.hasFlag(GridFlags.DENSE_CAPACITY)) {
                state.set(NodeFlag.DENSE);
            }
            return new NetworkData.ANode(worldNode.getLocation(), state);
        }
        return null;
    }

    private boolean checkChannel(InWorldGridNode node) {
        TileEntity tile = node.getLevel().getTileEntity(node.getLocation());
        if (tile instanceof IPartHost partHost) {
            for (EnumFacing face : EnumFacing.VALUES) {
                IPart part = partHost.getPart(face);
                if (part instanceof IActionHost actionHost && actionHost.getActionableNode() != null
                    && !actionHost.getActionableNode().isOnline()) {
                    return false;
                }
                if (part instanceof IPowerChannelState channelState && !channelState.isActive()) {
                    return false;
                }
            }
            return true;
        }
        return node.isOnline();
    }

    @Override
    public ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable RayTraceResult hitResult) {
        return new ItemGuiHost<>(this, player, locator);
    }

    public record TargetPos(int dimensionId, BlockPos pos) {
    }

    private record CachedNetworkData(int dimension, long generatedTick, NetworkData data) {
    }
}
