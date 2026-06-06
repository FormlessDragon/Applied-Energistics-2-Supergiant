package ae2.api.implementations.blockentities;

import ae2.api.parts.IPartHost;
import ae2.api.stacks.AEItemKey;
import ae2.core.localization.GuiText;
import ae2.text.TextComponentItemStack;
import ae2.text.TextComponents;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record PatternContainerGroup(
    @Nullable AEItemKey icon,
    ITextComponent name,
    List<ITextComponent> tooltip) {

    private static final PatternContainerGroup NOTHING = new PatternContainerGroup(null,
        GuiText.Nothing.text(), Collections.emptyList());

    public static PatternContainerGroup nothing() {
        return NOTHING;
    }

    public static PatternContainerGroup readFromPacket(PacketBuffer buffer) {
        AEItemKey icon = buffer.readBoolean() ? AEItemKey.fromPacket(buffer) : null;
        ITextComponent name = TextComponents.readFromPacket(buffer);
        int lineCount = buffer.readVarInt();
        ObjectList<ITextComponent> lines = new ObjectArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(TextComponents.readFromPacket(buffer));
        }
        return new PatternContainerGroup(icon, name, lines);
    }

    @Nullable
    public static PatternContainerGroup fromMachine(World level, BlockPos pos, EnumFacing side) {
        ICraftingMachine craftingMachine = ICraftingMachine.of(level, pos, side);
        if (craftingMachine != null) {
            return craftingMachine.getCraftingMachineInfo();
        }

        TileEntity target = level.getTileEntity(pos);
        if (target == null) {
            return null;
        }

        IItemHandler itemHandler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        if (itemHandler == null || itemHandler.getSlots() <= 0) {
            IFluidHandler fluidHandler = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
            if (fluidHandler == null || fluidHandler.getTankProperties().length == 0) {
                return null;
            }
        }

        AEItemKey icon;
        ITextComponent name;
        List<ITextComponent> tooltip = Collections.emptyList();

        if (target instanceof IPartHost partHost) {
            ae2.api.parts.IPart part = partHost.getPart(side);
            if (part == null) {
                return null;
            }

            icon = AEItemKey.of(part.getPartItem().asItem());
            if (icon == null) {
                return null;
            }

            if (part instanceof ae2.parts.AEBasePart aePart) {
                name = new net.minecraft.util.text.TextComponentString(aePart.getDisplayName());
            } else if (part instanceof IWorldNameable nameable) {
                name = nameable.getDisplayName();
            } else {
                name = icon.getDisplayName();
            }
        } else {
            net.minecraft.item.ItemStack targetItem = new net.minecraft.item.ItemStack(target.getBlockType());
            icon = AEItemKey.of(targetItem);

            if (target instanceof IWorldNameable nameable && nameable.hasCustomName()) {
                name = nameable.getDisplayName();
            } else {
                if (targetItem.isEmpty()) {
                    return null;
                }
                name = TextComponentItemStack.of(targetItem);
            }
        }

        return new PatternContainerGroup(icon, name, tooltip);
    }

    private static String componentKey(ITextComponent component) {
        return TextComponents.componentKey(component);
    }

    private static List<String> componentListKey(List<ITextComponent> components) {
        ObjectList<String> result = new ObjectArrayList<>(components.size());
        for (ITextComponent component : components) {
            result.add(componentKey(component));
        }
        return result;
    }

    public void writeToPacket(PacketBuffer buffer) {
        buffer.writeBoolean(this.icon != null);
        if (this.icon != null) {
            this.icon.writeToPacket(buffer);
        }
        TextComponents.writeToPacket(buffer, this.name);
        buffer.writeVarInt(this.tooltip.size());
        for (ITextComponent component : this.tooltip) {
            TextComponents.writeToPacket(buffer, component);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PatternContainerGroup(
            AEItemKey icon1, ITextComponent name1, List<ITextComponent> tooltip1
        ))) {
            return false;
        }
        return Objects.equals(this.icon, icon1)
            && Objects.equals(componentKey(this.name), componentKey(name1))
            && Objects.equals(componentListKey(this.tooltip), componentListKey(tooltip1));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.icon, componentKey(this.name), componentListKey(this.tooltip));
    }
}
