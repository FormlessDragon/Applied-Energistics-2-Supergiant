package ae2.api.implementations.blockentities;

import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.stacks.AEItemKey;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import ae2.helpers.patternprovider.PatternProviderTargets;
import ae2.parts.AEBasePart;
import ae2.text.TextComponentItemStack;
import ae2.text.TextComponents;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PatternContainerGroup(
    @Nullable AEItemKey icon,
    ITextComponent name,
    List<ITextComponent> tooltip) {

    private static final PatternContainerGroup NOTHING = new PatternContainerGroup(null,
        GuiText.Nothing.text(), Collections.emptyList());
    private static final int MAX_TOOLTIP_LINES = 256;
    private static final Set<Block> PICK_BLOCK_FAILURES = Collections.newSetFromMap(new IdentityHashMap<>());

    public static PatternContainerGroup nothing() {
        return NOTHING;
    }

    public static PatternContainerGroup readFromPacket(PacketBuffer buffer) {
        AEItemKey icon = buffer.readBoolean() ? AEItemKey.fromPacket(buffer) : null;
        ITextComponent name = TextComponents.readFromPacket(buffer);
        int lineCount = buffer.readVarInt();
        if (lineCount < 0 || lineCount > MAX_TOOLTIP_LINES) {
            throw new IllegalArgumentException("Invalid pattern container tooltip line count: " + lineCount);
        }
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

        if (!PatternProviderTargets.hasTarget(level, pos, side)) {
            return null;
        }

        AEItemKey icon;
        ITextComponent name;
        List<ITextComponent> tooltip = Collections.emptyList();

        if (target instanceof IPartHost partHost) {
            IPart part = partHost.getPart(side);
            if (part == null) {
                return null;
            }

            icon = AEItemKey.of(part.getPartItem().asItem());
            if (icon == null) {
                return null;
            }

            if (part instanceof AEBasePart aePart) {
                name = TextComponentItemStack.of(aePart.getPartItem().asItemStack());
            } else if (part instanceof IWorldNameable nameable) {
                name = nameable.getDisplayName();
            } else {
                name = icon.getDisplayName();
            }
        } else {
            ItemStack targetItem = getTargetDisplayStack(level, pos, side);
            icon = AEItemKey.of(targetItem);

            if (target instanceof IWorldNameable nameable && nameable.hasCustomName()) {
                name = nameable.getDisplayName();
            } else {
                if (!targetItem.isEmpty()) {
                    name = TextComponentItemStack.of(targetItem);
                } else {
                    name = new TextComponentTranslation(target.getBlockType().getTranslationKey() + ".name");
                }
            }
        }

        return new PatternContainerGroup(icon, name, tooltip);
    }

    private static ItemStack getTargetDisplayStack(World level, BlockPos pos, EnumFacing side) {
        IBlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        try {
            RayTraceResult hit = getTargetHit(level, pos, side);
            if (hit != null && pos.equals(hit.getBlockPos())) {
                ItemStack picked = block.getPickBlock(state, hit, level, pos, null);
                if (!picked.isEmpty()) {
                    return picked;
                }
            }
        } catch (Throwable e) {
            if (e instanceof VirtualMachineError fatal) {
                throw fatal;
            }
            if (PICK_BLOCK_FAILURES.add(block)) {
                AELog.warn(e, "Failed to get pick block stack for pattern provider target %s at %s", block, pos);
            }
        }
        Item item = Item.getItemFromBlock(block);
        int i = 0;
        if (item.getHasSubtypes()) {
            i = block.getMetaFromState(state);
        }
        return new ItemStack(item, 1, i);
    }

    @Nullable
    private static RayTraceResult getTargetHit(World level, BlockPos pos, EnumFacing targetSide) {
        EnumFacing providerToTarget = targetSide.getOpposite();
        Vec3d from = new Vec3d(
            pos.getX() + 0.5D - providerToTarget.getXOffset() * 0.499D,
            pos.getY() + 0.5D - providerToTarget.getYOffset() * 0.499D,
            pos.getZ() + 0.5D - providerToTarget.getZOffset() * 0.499D);
        Vec3d to = from.add(
            providerToTarget.getXOffset(),
            providerToTarget.getYOffset(),
            providerToTarget.getZOffset());
        return level.rayTraceBlocks(from, to, true);
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
