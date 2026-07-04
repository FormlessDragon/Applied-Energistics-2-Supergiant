package ae2.items.tools;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.api.util.DimensionalBlockPos;
import ae2.client.render.overlay.PriorityTunerHighlightHandler;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.helpers.IPriorityHost;
import ae2.items.AEBaseItem;
import ae2.items.contents.PriorityTunerGuiHost;
import ae2.util.Platform;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PriorityTunerItem extends AEBaseItem implements IGuiItem {
    private static final String MODE_TAG = "priority_tuner_mode";
    private static final String PRIORITY_TAG = "priority_tuner_priority";
    private static final String PENDING_TAG = "priority_tuner_pending";
    private static final String PENDING_DIMENSION_TAG = "dimension";
    private static final String PENDING_X_TAG = "x";
    private static final String PENDING_Y_TAG = "y";
    private static final String PENDING_Z_TAG = "z";
    private static final String PENDING_SIDE_TAG = "side";
    private static final String PENDING_VALUE_TAG = "value";

    public PriorityTunerItem() {
        setMaxStackSize(1);
    }

    private static @Nullable PriorityTarget findPriorityTarget(World world, BlockPos pos, Vec3d hit, @Nullable EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof IPartHost partHost) {
            SelectedPart selectedPart = partHost.selectPartLocal(hit);
            if (selectedPart.part instanceof IPriorityHost priorityHost) {
                return new PriorityTarget(priorityHost, selectedPart.side);
            }
            if (side != null && partHost.getPart(side) instanceof IPriorityHost priorityHost) {
                return new PriorityTarget(priorityHost, side);
            }
        }
        if (tile instanceof IPriorityHost priorityHost) {
            return new PriorityTarget(priorityHost, null);
        }
        return null;
    }

    private static @Nullable IPriorityHost findPriorityHost(World world, BlockPos pos, int side) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof IPartHost partHost && side >= 0 && side < EnumFacing.VALUES.length
            && partHost.getPart(EnumFacing.VALUES[side]) instanceof IPriorityHost priorityHost) {
            return priorityHost;
        }
        if (tile instanceof IPriorityHost priorityHost) {
            return priorityHost;
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    private static void stageClientHighlight(ItemStack stack, World world, BlockPos pos, @Nullable EnumFacing side,
                                             Settings settings) {
        int dimensionId = world.provider.getDimension();
        if (hasPendingTarget(stack, dimensionId, pos, side)
            || PriorityTunerHighlightHandler.INSTANCE.has(dimensionId, pos, side)) {
            return;
        }

        int stagedCount = Math.max(getPendingCount(stack),
            PriorityTunerHighlightHandler.INSTANCE.getStagedCount(dimensionId));
        int priority = settings.mode().nextPriority(settings.priority(), stagedCount);
        PriorityTunerHighlightHandler.INSTANCE.add(dimensionId, pos, side, priority);
    }

    @SideOnly(Side.CLIENT)
    private static void clearClientHighlights() {
        PriorityTunerHighlightHandler.INSTANCE.clear();
    }

    public Settings getSettings(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return Settings.DEFAULT;
        }
        Mode mode = Mode.byName(tag.getString(MODE_TAG));
        int priority = tag.hasKey(PRIORITY_TAG, Constants.NBT.TAG_ANY_NUMERIC)
            ? tag.getInteger(PRIORITY_TAG)
            : Settings.DEFAULT.priority();
        return new Settings(mode, priority);
    }

    public void setSettings(ItemStack stack, Settings settings) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setString(MODE_TAG, settings.mode().getSerializedName());
        tag.setInteger(PRIORITY_TAG, settings.priority());
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (player.isSneaking()) {
            return EnumActionResult.PASS;
        }

        Vec3d hit = new Vec3d(hitX, hitY, hitZ);
        PriorityTarget target = findPriorityTarget(world, pos, hit, side);
        if (target == null) {
            return EnumActionResult.PASS;
        }

        ItemStack tool = player.getHeldItem(hand);
        Settings settings = getSettings(tool);
        if (world.isRemote) {
            if (settings.mode().isBatchMode()) {
                stageClientHighlight(tool, world, pos, target.side(), settings);
            }
            return EnumActionResult.SUCCESS;
        }

        if (!Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
            return EnumActionResult.FAIL;
        }

        switch (settings.mode()) {
            case INPUT -> {
                setSettings(tool, new Settings(settings.mode(), target.host().getPriority()));
                player.sendStatusMessage(PlayerMessages.PriorityTunerCopied.text(target.host().getPriority()), true);
            }
            case OUTPUT -> {
                target.host().setPriority(settings.priority());
                player.sendStatusMessage(PlayerMessages.PriorityTunerApplied.text(settings.priority()), true);
            }
            case INCREMENT, DECREMENT -> {
                int dimensionId = world.provider.getDimension();
                if (!hasPendingTarget(tool, dimensionId, pos, target.side())) {
                    int value = settings.mode().nextPriority(settings.priority(), getPendingCount(tool));
                    addPending(tool, dimensionId, pos, target.side(), value);
                    player.sendStatusMessage(PlayerMessages.PriorityTunerStaged.text(value), true);
                }
            }
        }

        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!player.isSneaking()) {
            return new ActionResult<>(EnumActionResult.PASS, held);
        }

        if (hasPending(held)) {
            if (world.isRemote) {
                clearClientHighlights();
            } else {
                int applied = applyPending(world, player, held);
                player.sendStatusMessage(PlayerMessages.PriorityTunerCommitted.text(applied), true);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }

        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.PRIORITY_TUNER, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    @Override
    public @Nullable ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                               @Nullable RayTraceResult hitResult) {
        return new PriorityTunerGuiHost(this, player, locator);
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        Settings settings = getSettings(stack);
        lines.add(modeText(settings.mode()).getFormattedText());
        lines.add(GuiText.PriorityTunerPriority.text(settings.priority()).getFormattedText());
    }

    private static net.minecraft.util.text.ITextComponent modeText(Mode mode) {
        return switch (mode) {
            case INPUT -> GuiText.PriorityTunerModeInput.text();
            case OUTPUT -> GuiText.PriorityTunerModeOutput.text();
            case INCREMENT -> GuiText.PriorityTunerModeIncrement.text();
            case DECREMENT -> GuiText.PriorityTunerModeDecrement.text();
        };
    }

    private static boolean hasPending(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(PENDING_TAG, Constants.NBT.TAG_LIST)
            && !tag.getTagList(PENDING_TAG, Constants.NBT.TAG_COMPOUND).isEmpty();
    }

    private static int getPendingCount(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(PENDING_TAG, Constants.NBT.TAG_LIST)) {
            return 0;
        }
        return tag.getTagList(PENDING_TAG, Constants.NBT.TAG_COMPOUND).tagCount();
    }

    private static boolean hasPendingTarget(ItemStack stack, int dimensionId, BlockPos pos, @Nullable EnumFacing side) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(PENDING_TAG, Constants.NBT.TAG_LIST)) {
            return false;
        }

        NBTTagList pending = tag.getTagList(PENDING_TAG, Constants.NBT.TAG_COMPOUND);
        int sideIndex = sideIndex(side);
        for (int i = 0; i < pending.tagCount(); i++) {
            if (matchesPendingTarget(pending.getCompoundTagAt(i), dimensionId, pos, sideIndex)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPendingTarget(NBTTagCompound entry, int dimensionId, BlockPos pos, int sideIndex) {
        return entry.getInteger(PENDING_DIMENSION_TAG) == dimensionId
            && entry.getInteger(PENDING_X_TAG) == pos.getX()
            && entry.getInteger(PENDING_Y_TAG) == pos.getY()
            && entry.getInteger(PENDING_Z_TAG) == pos.getZ()
            && entry.getInteger(PENDING_SIDE_TAG) == sideIndex;
    }

    private static int sideIndex(@Nullable EnumFacing side) {
        return side == null ? -1 : side.ordinal();
    }

    private static void addPending(ItemStack stack, int dimensionId, BlockPos pos, @Nullable EnumFacing side,
                                   int priority) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        NBTTagList pending = tag.getTagList(PENDING_TAG, Constants.NBT.TAG_COMPOUND);
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(PENDING_DIMENSION_TAG, dimensionId);
        entry.setInteger(PENDING_X_TAG, pos.getX());
        entry.setInteger(PENDING_Y_TAG, pos.getY());
        entry.setInteger(PENDING_Z_TAG, pos.getZ());
        entry.setInteger(PENDING_SIDE_TAG, sideIndex(side));
        entry.setInteger(PENDING_VALUE_TAG, priority);
        pending.appendTag(entry);
        tag.setTag(PENDING_TAG, pending);
    }

    private static int applyPending(World world, EntityPlayer player, ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(PENDING_TAG, Constants.NBT.TAG_LIST)) {
            return 0;
        }

        int applied = 0;
        int currentDimension = world.provider.getDimension();
        NBTTagList pending = tag.getTagList(PENDING_TAG, Constants.NBT.TAG_COMPOUND);
        Set<PendingIdentity> appliedTargets = new HashSet<>();
        for (int i = 0; i < pending.tagCount(); i++) {
            NBTTagCompound entry = pending.getCompoundTagAt(i);
            if (entry.getInteger(PENDING_DIMENSION_TAG) != currentDimension) {
                continue;
            }
            BlockPos pos = new BlockPos(
                entry.getInteger(PENDING_X_TAG),
                entry.getInteger(PENDING_Y_TAG),
                entry.getInteger(PENDING_Z_TAG));
            if (!Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
                continue;
            }
            int side = entry.getInteger(PENDING_SIDE_TAG);
            if (!appliedTargets.add(new PendingIdentity(currentDimension, pos, side))) {
                continue;
            }
            IPriorityHost host = findPriorityHost(world, pos, side);
            if (host == null) {
                continue;
            }
            host.setPriority(entry.getInteger(PENDING_VALUE_TAG));
            applied++;
        }
        tag.removeTag(PENDING_TAG);
        return applied;
    }

    public enum Mode {
        INPUT,
        OUTPUT,
        INCREMENT,
        DECREMENT;

        private static final Mode[] VALUES = values();

        static Mode byName(String name) {
            for (Mode mode : VALUES) {
                if (mode.getSerializedName().equals(name)) {
                    return mode;
                }
            }
            return Settings.DEFAULT.mode();
        }

        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Mode next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        boolean isBatchMode() {
            return this == INCREMENT || this == DECREMENT;
        }

        int nextPriority(int basePriority, int stagedCount) {
            return switch (this) {
                case INCREMENT -> basePriority + stagedCount + 1;
                case DECREMENT -> basePriority - stagedCount - 1;
                case INPUT, OUTPUT -> basePriority;
            };
        }
    }

    public record Settings(Mode mode, int priority) {
        public static final Settings DEFAULT = new Settings(Mode.OUTPUT, 0);
    }

    private record PriorityTarget(IPriorityHost host, @Nullable EnumFacing side) {
    }

    private record PendingIdentity(int dimensionId, BlockPos pos, int side) {
    }
}
