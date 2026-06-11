package ae2.integration.modules.waila;

import ae2.api.integrations.igtooltip.ClientRegistration;
import ae2.api.integrations.igtooltip.CommonRegistration;
import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.integration.modules.igtooltip.NetworkDebugProvider;
import ae2.integration.modules.igtooltip.TooltipProviders;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

public final class WailaBlockEntityInfoProvider implements IWailaDataProvider {
    private final ObjectList<ServerDataCollector> dataCollectors = new ObjectArrayList<>();
    private final ObjectList<BodyCustomizer<?>> bodyCustomizers = new ObjectArrayList<>();

    public WailaBlockEntityInfoProvider(boolean loadServerData, boolean loadClientBody) {
        if (loadServerData) {
            TooltipProviders.loadCommon(new CommonRegistration() {
                @Override
                public <T extends TileEntity> void addBlockEntityData(ResourceLocation id, Class<T> blockEntityClass,
                                                                      ServerDataProvider<? super T> provider) {
                    dataCollectors.add((blockEntity, player, serverData) -> {
                        if (blockEntityClass.isInstance(blockEntity)) {
                            provider.provideServerData(player, blockEntityClass.cast(blockEntity), serverData);
                        }
                    });
                }
            });
        }
        if (loadClientBody) {
            TooltipProviders.loadClient(new ClientRegistration() {
                @Override
                public <T extends TileEntity> void addBlockEntityBody(Class<T> blockEntityClass,
                                                                      Class<? extends Block> blockClass,
                                                                      ResourceLocation id,
                                                                      BodyProvider<? super T> provider,
                                                                      int priority) {
                    bodyCustomizers.add(new BodyCustomizer<>(blockEntityClass, provider, priority));
                }
            });
        }
        bodyCustomizers.sort(Comparator.comparingInt(BodyCustomizer::priority));
    }

    private static Vec3d getHitLocation(IWailaDataAccessor accessor) {
        RayTraceResult hit = accessor.getMOP();
        if (hit != null && hit.hitVec != null) {
            return hit.hitVec;
        }

        BlockPos pos = accessor.getPosition();
        if (pos != null) {
            return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }

        return Vec3d.ZERO;
    }

    @Override
    public @NotNull List<String> getWailaBody(ItemStack itemStack, List<String> currentToolTip,
                                              IWailaDataAccessor accessor, IWailaConfigHandler config) {
        var blockEntity = accessor.getTileEntity();
        if (blockEntity == null) {
            return currentToolTip;
        }

        var hitLocation = getHitLocation(accessor);
        var serverData = accessor.getNBTData();
        if (serverData == null) {
            serverData = new NBTTagCompound();
        }

        var context = new TooltipContext(serverData, hitLocation, accessor.getPlayer());
        var tooltipBuilder = new WailaTooltipBuilder(currentToolTip);
        for (var customizer : bodyCustomizers) {
            customizer.buildTooltip(blockEntity, context, tooltipBuilder);
        }
        NetworkDebugProvider.addProbeInfoFromServerData(blockEntity, hitLocation, serverData, tooltipBuilder);
        return currentToolTip;
    }

    @Override
    public @NotNull NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag,
                                              World world, BlockPos pos) {
        if (tag == null) {
            tag = new NBTTagCompound();
        }

        if (te != null) {
            for (ServerDataCollector dataCollector : dataCollectors) {
                dataCollector.collect(te, player, tag);
            }
            NetworkDebugProvider.provideServerData(player, te, tag);
        }
        return tag;
    }

    @FunctionalInterface
    private interface ServerDataCollector {
        void collect(TileEntity blockEntity, EntityPlayerMP player, NBTTagCompound serverData);
    }

    private record BodyCustomizer<T>(Class<T> beClass, BodyProvider<? super T> provider, int priority) {
        public void buildTooltip(TileEntity blockEntity, TooltipContext context, TooltipBuilder tooltipBuilder) {
            if (this.beClass.isInstance(blockEntity)) {
                this.provider.buildTooltip(this.beClass.cast(blockEntity), context, tooltipBuilder);
            }
        }
    }
}
