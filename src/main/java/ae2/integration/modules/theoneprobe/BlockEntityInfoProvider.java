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
import ae2.api.integrations.igtooltip.providers.IconProvider;
import ae2.api.integrations.igtooltip.providers.ModNameProvider;
import ae2.api.integrations.igtooltip.providers.NameProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.core.AppEng;
import ae2.integration.modules.igtooltip.TooltipProviders;
import ae2.text.TextComponentItemStack;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mcjty.theoneprobe.api.IBlockDisplayOverride;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

public final class BlockEntityInfoProvider implements IProbeInfoProvider, IBlockDisplayOverride {
    private final ObjectList<ServerDataCollector> dataCollectors = new ObjectArrayList<>();
    private final ObjectList<BodyCustomizer<?>> bodyCustomizers = new ObjectArrayList<>();
    private final ObjectList<NameCustomizer<?>> nameCustomizers = new ObjectArrayList<>();
    private final ObjectList<ModNameCustomizer<?>> modNameCustomizers = new ObjectArrayList<>();
    private final ObjectList<IconCustomizer<?>> iconCustomizers = new ObjectArrayList<>();

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

            @Override
            public <T extends TileEntity> void addBlockEntityIcon(Class<T> blockEntityClass,
                                                                  Class<? extends Block> blockClass, ResourceLocation id, IconProvider<? super T> provider,
                                                                  int priority) {
                iconCustomizers.add(new IconCustomizer<>(blockEntityClass, provider, priority));
            }

            @Override
            public <T extends TileEntity> void addBlockEntityName(Class<T> blockEntityClass,
                                                                  Class<? extends Block> blockClass, ResourceLocation id, NameProvider<? super T> provider,
                                                                  int priority) {
                nameCustomizers.add(new NameCustomizer<>(blockEntityClass, provider, priority));
            }

            @Override
            public <T extends TileEntity> void addBlockEntityModName(Class<T> blockEntityClass,
                                                                     Class<? extends Block> blockClass, ResourceLocation id, ModNameProvider<? super T> provider,
                                                                     int priority) {
                modNameCustomizers.add(new ModNameCustomizer<>(blockEntityClass, provider, priority));
            }
        });

        nameCustomizers.sort(Comparator.comparingInt(NameCustomizer::priority));
        iconCustomizers.sort(Comparator.comparingInt(IconCustomizer::priority));
        modNameCustomizers.sort(Comparator.comparingInt(ModNameCustomizer::priority));
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

    @Override
    public boolean overrideStandardInfo(ProbeMode probeMode, IProbeInfo probeInfo, EntityPlayer player, World level,
                                        IBlockState blockState, IProbeHitData probeHitData) {
        var blockEntity = level.getTileEntity(probeHitData.getPos());
        if (blockEntity == null) {
            return false;
        }

        var serverData = getServerData(player, blockEntity);
        var context = getContext(player, probeHitData, serverData);

        ITextComponent name = null;
        String modName = null;
        ItemStack icon = ItemStack.EMPTY;

        for (var customizer : nameCustomizers) {
            name = customizer.getName(blockEntity, context);
            if (name != null) {
                break;
            }
        }

        for (var customizer : modNameCustomizers) {
            modName = customizer.getModName(blockEntity, context);
            if (modName != null) {
                break;
            }
        }

        for (var customizer : iconCustomizers) {
            icon = customizer.getIcon(blockEntity, context);
            if (icon != null && !icon.isEmpty()) {
                break;
            }
        }

        if (name != null || modName != null || (icon != null && !icon.isEmpty())) {
            ItemStack pickBlock = probeHitData.getPickBlock();
            if (name == null) {
                name = pickBlock != null ? TextComponentItemStack.of(pickBlock) : new TextComponentString("");
            }
            if (icon == null || icon.isEmpty()) {
                icon = pickBlock;
            }
            if (modName == null && pickBlock != null && !pickBlock.isEmpty()
                && pickBlock.getItem().getRegistryName() != null) {
                modName = Platform.getModName(pickBlock.getItem().getRegistryName().getNamespace());
            }

            var vertical = probeInfo.horizontal().item(icon).vertical();
            vertical.text(name.getFormattedText());
            if (modName != null) {
                vertical.text("§9§o" + modName);
            }
            return true;
        }

        return false;
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

    private record NameCustomizer<T>(Class<T> beClass, NameProvider<? super T> provider, int priority) {
        public @Nullable ITextComponent getName(TileEntity blockEntity, TooltipContext context) {
            if (this.beClass.isInstance(blockEntity)) {
                return this.provider.getName(this.beClass.cast(blockEntity), context);
            }
            return null;
        }
    }

    private record IconCustomizer<T>(Class<T> beClass, IconProvider<? super T> provider, int priority) {
        public ItemStack getIcon(TileEntity blockEntity, TooltipContext context) {
            if (this.beClass.isInstance(blockEntity)) {
                return this.provider.getIcon(this.beClass.cast(blockEntity), context);
            }
            return ItemStack.EMPTY;
        }
    }

    private record ModNameCustomizer<T>(Class<T> beClass, ModNameProvider<? super T> provider, int priority) {
        public @Nullable String getModName(TileEntity blockEntity, TooltipContext context) {
            if (this.beClass.isInstance(blockEntity)) {
                return this.provider.getModName(this.beClass.cast(blockEntity), context);
            }
            return null;
        }
    }

    private record BodyCustomizer<T>(Class<T> beClass, BodyProvider<? super T> provider, int priority) {
        public void buildTooltip(TileEntity blockEntity, TooltipContext context, TooltipBuilder tooltipBuilder) {
            if (this.beClass.isInstance(blockEntity)) {
                this.provider.buildTooltip(this.beClass.cast(blockEntity), context, tooltipBuilder);
            }
        }
    }
}
