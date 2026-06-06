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

package ae2.integration.modules.theoneprobe;

import ae2.api.integrations.igtooltip.ClientRegistration;
import ae2.api.integrations.igtooltip.CommonRegistration;
import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.core.AppEng;
import ae2.integration.modules.igtooltip.TooltipProviders;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;

public final class BlockEntityInfoProvider implements IProbeInfoProvider {
    private final ObjectList<ServerDataCollector> dataCollectors = new ObjectArrayList<>();
    private final ObjectList<BodyCustomizer<?>> bodyCustomizers = new ObjectArrayList<>();

    public BlockEntityInfoProvider() {
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
        TooltipProviders.loadClient(new ClientRegistration() {
            @Override
            public <T extends TileEntity> void addBlockEntityBody(Class<T> blockEntityClass,
                                                                  Class<? extends Block> blockClass, ResourceLocation id, BodyProvider<? super T> provider,
                                                                  int priority) {
                bodyCustomizers.add(new BodyCustomizer<>(blockEntityClass, provider, priority));
            }
        });
        bodyCustomizers.sort(Comparator.comparingInt(BodyCustomizer::priority));
    }

    private static TooltipContext getContext(EntityPlayer player, IProbeHitData data, NBTTagCompound serverData) {
        Vec3d hitLocation = data.getHitVec();
        return new TooltipContext(serverData, hitLocation, player);
    }

    @Override
    public String getID() {
        return AppEng.MOD_ID + ":block_entity";
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World level,
                             IBlockState blockState, IProbeHitData data) {
        var blockEntity = level.getTileEntity(data.getPos());
        if (blockEntity != null) {
            var serverData = getServerData(player, blockEntity);
            var context = getContext(player, data, serverData);
            TopTooltipBuilder tooltipBuilder = new TopTooltipBuilder(probeInfo);
            for (var customizer : bodyCustomizers) {
                customizer.buildTooltip(blockEntity, context, tooltipBuilder);
            }
            TopNetworkDebugProvider.addProbeInfo(player, blockEntity, data.getHitVec(), tooltipBuilder);
        }
    }

    private NBTTagCompound getServerData(EntityPlayer player, TileEntity blockEntity) {
        NBTTagCompound serverData = new NBTTagCompound();
        if (player instanceof EntityPlayerMP serverPlayer) {
            for (ServerDataCollector dataCollector : dataCollectors) {
                dataCollector.collect(blockEntity, serverPlayer, serverData);
            }
        }
        return serverData;
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
