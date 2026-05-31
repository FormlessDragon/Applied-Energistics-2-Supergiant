package ae2.items.tools;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.api.stacks.GenericStack;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.PlayerMessages;
import ae2.helpers.IConfigInvHost;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.items.AEBaseItem;
import ae2.items.contents.ConfigModifierGuiHost;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class ConfigModifierItem extends AEBaseItem implements IGuiItem {
    private static final String MODE_TAG = "mode";
    private static final String DATA_TAG = "data";

    public ConfigModifierItem() {
        setMaxStackSize(1);
    }

    @Nullable
    private static IConfigInvHost findConfigHost(World world, BlockPos pos, Vec3d hit) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof IConfigInvHost configHost) {
            return configHost;
        }
        if (tile instanceof IPartHost partHost) {
            SelectedPart selectedPart = partHost.selectPartLocal(hit);
            if (selectedPart.part instanceof IConfigInvHost configHost) {
                return configHost;
            }
        }
        return null;
    }

    public Settings getSettings(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return Settings.DEFAULT;
        }
        Mode mode = Mode.byName(tag.getString(MODE_TAG));
        long data = tag.hasKey(DATA_TAG) ? tag.getLong(DATA_TAG) : Settings.DEFAULT.data();
        return new Settings(mode, data);
    }

    public void setSettings(ItemStack stack, Settings settings) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(MODE_TAG, settings.mode().getSerializedName());
        tag.setLong(DATA_TAG, settings.data());
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.CONFIG_MODIFIER, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        IConfigInvHost host = findConfigHost(world, pos, new Vec3d(hitX, hitY, hitZ));
        if (host == null) {
            return EnumActionResult.PASS;
        }
        if (!world.isRemote) {
            modify(host, player.getHeldItem(hand));
            player.sendStatusMessage(PlayerMessages.ConfigModifierSuccess.text(), true);
        }
        return EnumActionResult.SUCCESS;
    }

    private void modify(IConfigInvHost host, ItemStack tool) {
        Settings settings = getSettings(tool);
        var config = host.getConfig();
        long min = config.getMode() == GenericStackInv.Mode.CONFIG_TYPES ? 0 : 1;
        config.beginBatch();
        try {
            for (int slot = 0; slot < config.size(); slot++) {
                GenericStack stack = config.getStack(slot);
                if (stack == null) {
                    continue;
                }
                long amount = settings.mode().modify(stack.amount(), settings.data(), min,
                    config.getMaxAmount(stack.what()));
                if (amount < 0) {
                    config.setStack(slot, null);
                } else {
                    config.setStack(slot, new GenericStack(stack.what(), amount));
                }
            }
        } finally {
            config.endBatch();
        }
    }

    @Override
    public ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable RayTraceResult hitResult) {
        return new ConfigModifierGuiHost(this, player, locator);
    }

    public enum Mode {
        ADD,
        SUB,
        MUL,
        DIV,
        MAX,
        MIN,
        SET,
        RMV;

        static Mode byName(String name) {
            for (Mode mode : values()) {
                if (mode.getSerializedName().equals(name)) {
                    return mode;
                }
            }
            return Settings.DEFAULT.mode();
        }

        public long modify(long value, long data, long min, long max) {
            return switch (this) {
                case ADD -> Math.min(value + data, max);
                case SUB -> Math.max(value - data, min);
                case MUL -> Math.min(value * data, max);
                case DIV -> Math.max(value / Math.max(data, 1), min);
                case MAX -> max;
                case MIN -> min;
                case SET -> Math.clamp(data, min, max);
                case RMV -> -1;
            };
        }

        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public record Settings(Mode mode, long data) {
        public static final Settings DEFAULT = new Settings(Mode.MUL, 1);
    }
}
