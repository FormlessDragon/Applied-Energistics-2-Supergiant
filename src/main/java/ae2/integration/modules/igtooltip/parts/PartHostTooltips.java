package ae2.integration.modules.igtooltip.parts;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.text.TextComponentItemStack;
import ae2.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public final class PartHostTooltips {

    private PartHostTooltips() {
    }

    public static @Nullable ITextComponent getName(TileEntity object, TooltipContext context) {
        return getName((IPartHost) object, context);
    }

    public static @Nullable ITextComponent getName(IPartHost object, TooltipContext context) {
        var selected = getPart(object, context.hitLocation());

        if (selected.facade != null) {
            return TextComponentItemStack.of(selected.facade.getItemStack());
        } else if (selected.part != null) {
            for (var provider : PartTooltipProviders.getProviders(selected.part).nameProviders()) {
                var name = provider.getName(selected.part, context);
                if (name != null) {
                    return name;
                }
            }

            return TextComponentItemStack.of(selected.part.getPartItem().asItemStack());
        } else {
            return null;
        }
    }

    public static @Nullable String getModName(TileEntity blockEntity, TooltipContext context) {
        return getModName((IPartHost) blockEntity, context);
    }

    public static @Nullable String getModName(IPartHost object, TooltipContext context) {
        var selected = getPart(object, context.hitLocation());

        Item item;
        if (selected.facade != null) {
            item = selected.facade.getItemStack().getItem();
        } else if (selected.part != null) {
            item = selected.part.getPartItem().asItem();
        } else {
            return null;
        }

        ResourceLocation id = item.getRegistryName();
        if (id == null) {
            return null;
        }
        return Platform.getModName(id.getNamespace());
    }

    public static @Nullable ItemStack getIcon(TileEntity object, TooltipContext context) {
        return getIcon((IPartHost) object, context);
    }

    public static @Nullable ItemStack getIcon(IPartHost object, TooltipContext context) {
        var selected = getPart(object, context.hitLocation());
        if (selected.facade != null) {
            return selected.facade.getItemStack();
        } else if (selected.part != null) {
            for (var provider : PartTooltipProviders.getProviders(selected.part).iconProviders()) {
                var icon = provider.getIcon(selected.part, context);
                if (icon != null) {
                    return icon;
                }
            }

            return selected.part.getPartItem().asItemStack();
        } else {
            return null;
        }
    }

    public static void buildTooltip(TileEntity object, TooltipContext context, TooltipBuilder tooltip) {
        buildTooltip((IPartHost) object, context, tooltip);
    }

    public static void buildTooltip(IPartHost object, TooltipContext context,
                                    TooltipBuilder tooltip) {
        // Pick the part the cursor is on
        var selected = getPart(object, context.hitLocation());
        if (selected.part != null) {
            // Then pick the data for that particular part
            var partTag = context.serverData().getCompoundTag(getPartDataName(selected.side));

            buildPartTooltip(selected.part, partTag, context, tooltip);
        }
    }

    private static <T extends IPart> void buildPartTooltip(T part,
                                                           NBTTagCompound partTag,
                                                           TooltipContext blockContext,
                                                           TooltipBuilder tooltip) {
        var partContext = new TooltipContext(partTag, blockContext.hitLocation(), blockContext.player());

        for (var provider : PartTooltipProviders.getProviders(part).bodyProviders()) {
            provider.buildTooltip(part, partContext, tooltip);
        }
    }

    public static void provideServerData(EntityPlayer player, TileEntity object, NBTTagCompound serverData) {
        provideServerData(player, (IPartHost) object, serverData);
    }

    public static void provideServerData(EntityPlayer player, IPartHost object, NBTTagCompound serverData) {
        var partTag = new NBTTagCompound();
        for (var location : Platform.DIRECTIONS_WITH_NULL) {
            var part = object.getPart(location);
            if (part == null) {
                continue;
            }

            for (var provider : PartTooltipProviders.getProviders(part).serverDataProviders()) {
                provider.provideServerData(player, part, partTag);
            }

            // Send it to the client if there's some data for it
            if (!Platform.isNbtEmpty(partTag)) {
                serverData.setTag(getPartDataName(location), partTag);
                partTag = new NBTTagCompound();
            }
        }

    }

    private static String getPartDataName(@Nullable EnumFacing location) {
        return "cableBusPart" + (location == null ? "center" : location.name());
    }

    /**
     * Hits a {@link IPartHost} with {@link net.minecraft.util.math.BlockPos}.
     * <p/>
     * You can derive the looked at {@link IPart} by doing that. If a facade is being looked at, it is defined as being
     * absent.
     *
     * @return maybe the looked at {@link IPart}
     */
    private static SelectedPart getPart(IPartHost partHost, Vec3d hitLocation) {
        return partHost.selectPartWorld(hitLocation);
    }
}
