package ae2.items.tools;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.networking.security.IActionHost;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.api.util.DimensionalBlockPos;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.GuiText;
import ae2.items.AEBaseItem;
import ae2.items.contents.AdvancedMemoryCardGuiHost;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardAction;
import ae2.parts.p2p.P2PTunnelPart;
import ae2.util.Platform;
import net.minecraft.client.util.ITooltipFlag;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class AdvancedMemoryCardItem extends AEBaseItem implements IGuiItem {
    private static final String MODE_TAG = "advanced_memory_card_mode";

    public AdvancedMemoryCardItem() {
        setMaxStackSize(1);
    }

    public static AdvancedMemoryCardAction.Mode getMode(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(MODE_TAG, 8)) {
            return AdvancedMemoryCardAction.Mode.BIND_OUTPUT;
        }
        try {
            return AdvancedMemoryCardAction.Mode.valueOf(tag.getString(MODE_TAG));
        } catch (IllegalArgumentException ignored) {
            return AdvancedMemoryCardAction.Mode.BIND_OUTPUT;
        }
    }

    public static void setMode(ItemStack stack, AdvancedMemoryCardAction.Mode mode) {
        if (stack.isEmpty()) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(MODE_TAG, mode.name());
    }

    private static String modeText(AdvancedMemoryCardAction.Mode mode) {
        return switch (mode) {
            case BIND_OUTPUT -> GuiText.AdvancedMemoryCardModeOutput.getLocal();
            case BIND_INPUT -> GuiText.AdvancedMemoryCardModeInput.getLocal();
            case COPY_OUTPUT -> GuiText.AdvancedMemoryCardModeCopy.getLocal();
            case DELETE_BINDING -> GuiText.AdvancedMemoryCardModeDelete.getLocal();
        };
    }

    static @Nullable EnumFacing resolveClickedSide(@Nullable SelectedPart selectedPart, Predicate<IPart> focusablePart) {
        if (selectedPart != null && selectedPart.side != null && selectedPart.part != null
            && focusablePart.test(selectedPart.part)) {
            return selectedPart.side;
        }
        return null;
    }

    static @Nullable IGridNode resolveClickedGridNode(@Nullable SelectedPart selectedPart,
                                                      Predicate<IPart> focusablePart) {
        if (selectedPart != null && selectedPart.part != null && focusablePart.test(selectedPart.part)) {
            return selectedPart.part.getGridNode();
        }
        return null;
    }

    static @Nullable IGridNode resolveSelectedPartGridNode(@Nullable SelectedPart selectedPart) {
        return selectedPart != null && selectedPart.part != null ? selectedPart.part.getGridNode() : null;
    }

    private static SelectedPart resolveClickedPart(IPartHost partHost, RayTraceResult hitResult) {
        return resolveClickedPart(partHost, hitResult.sideHit,
            hitResult.hitVec.x - hitResult.getBlockPos().getX(),
            hitResult.hitVec.y - hitResult.getBlockPos().getY(),
            hitResult.hitVec.z - hitResult.getBlockPos().getZ());
    }

    private static SelectedPart resolveClickedPart(IPartHost partHost, EnumFacing side, double hitX, double hitY,
                                                   double hitZ) {
        Vec3d localHit = new Vec3d(hitX, hitY, hitZ);
        SelectedPart selectedPart = partHost.selectPartLocal(localHit);
        if (selectedPart.part instanceof P2PTunnelPart<?>) {
            return selectedPart;
        }

        if (side != null) {
            IPart sidePart = partHost.getPart(side);
            if (sidePart instanceof P2PTunnelPart<?>) {
                return new SelectedPart(sidePart, side);
            }
            if (selectedPart.part == null && sidePart != null) {
                return new SelectedPart(sidePart, side);
            }
        }

        if (selectedPart.part != null) {
            return selectedPart;
        }

        IPart centerPart = partHost.getPart(null);
        return centerPart == null ? selectedPart : new SelectedPart(centerPart, null);
    }

    @SideOnly(Side.CLIENT)
    private static void clearClientHighlight() {
        ae2.client.render.overlay.AdvancedMemoryCardHighlightHandler.INSTANCE.clear();
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        lines.add(modeText(getMode(stack)));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (player.isSneaking()) {
            return EnumActionResult.PASS;
        }

        if (!world.isRemote) {
            if (!Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
                return EnumActionResult.FAIL;
            }

            TileEntity tile = world.getTileEntity(pos);
            if (GridHelper.getNodeHost(world, pos) != null || tile instanceof IPartHost
                || tile instanceof IActionHost) {
                EnumFacing guiSide = side;
                if (tile instanceof IPartHost partHost) {
                    SelectedPart selectedPart = resolveClickedPart(partHost, side, hitX, hitY, hitZ);
                    if (selectedPart.part instanceof P2PTunnelPart<?> && selectedPart.side != null) {
                        guiSide = selectedPart.side;
                    }
                }
                return GuiOpener.openItemGui(player, GuiIds.GuiKey.ADVANCED_MEMORY_CARD,
                    GuiHostLocators.forItemUseContext(player, hand, pos, guiSide, hitX, hitY, hitZ))
                    ? EnumActionResult.SUCCESS
                    : EnumActionResult.FAIL;
            }
        }

        return EnumActionResult.PASS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (world.isRemote) {
                clearClientHighlight();
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }
        return new ActionResult<>(EnumActionResult.PASS, held);
    }

    @Override
    public @Nullable ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                               @Nullable RayTraceResult hitResult) {
        IInWorldGridNodeHost gridHost = null;
        IGridNode clickedGridNode = null;
        BlockPos clickedPos = null;
        EnumFacing focusedSide = null;
        boolean preferClickedNodeForGrid = false;
        if (hitResult != null && hitResult.getBlockPos() != null) {
            clickedPos = hitResult.getBlockPos();
            TileEntity tile = player.world.getTileEntity(hitResult.getBlockPos());
            if (tile instanceof IPartHost partHost) {
                SelectedPart selectedPart = resolveClickedPart(partHost, hitResult);
                focusedSide = resolveClickedSide(selectedPart, part -> part instanceof P2PTunnelPart<?>);
                clickedGridNode = resolveSelectedPartGridNode(selectedPart);
                preferClickedNodeForGrid = selectedPart.part instanceof P2PTunnelPart<?>;
            } else if (tile instanceof IActionHost actionHost) {
                clickedGridNode = actionHost.getActionableNode();
            }
            if (tile instanceof IInWorldGridNodeHost directHost) {
                gridHost = directHost;
            } else {
                gridHost = GridHelper.getNodeHost(player.world, hitResult.getBlockPos());
            }
            if (gridHost == null && clickedGridNode != null) {
                preferClickedNodeForGrid = true;
            }
        }
        return new AdvancedMemoryCardGuiHost(this, player, locator, gridHost, clickedGridNode, clickedPos,
            focusedSide, preferClickedNodeForGrid);
    }
}
