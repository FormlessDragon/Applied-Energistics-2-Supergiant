package ae2.client.gui.implementations;

import ae2.api.config.ActionItems;
import ae2.api.orientation.RelativeSide;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.OutputSideSelectionButton;
import ae2.container.implementations.ContainerOutputSides;
import ae2.core.AELog;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GuiOutputSides extends AEBaseGui<ContainerOutputSides> {
    private static final String STYLE_PATH = "/screens/output_sides.json";
    private static final RelativeSide[] RELATIVE_SIDES = RelativeSide.values();
    private static final Set<Block> PICK_BLOCK_FAILURES = new ReferenceOpenHashSet<>();

    private final Map<RelativeSide, OutputSideSelectionButton> sideButtons = new EnumMap<>(RelativeSide.class);
    private final Map<RelativeSide, Boolean> lastAllowedStates = new EnumMap<>(RelativeSide.class);
    private final Map<RelativeSide, Boolean> lastEnabledStates = new EnumMap<>(RelativeSide.class);
    private final Map<RelativeSide, ItemStack> neighbor = new EnumMap<>(RelativeSide.class);

    public GuiOutputSides(ContainerOutputSides container, InventoryPlayer playerInventory) {
        this(container, playerInventory, GuiStyleManager.loadStyleDoc(STYLE_PATH));
    }

    private GuiOutputSides(ContainerOutputSides container, InventoryPlayer playerInventory, GuiStyle style) {
        super(container, playerInventory, style);
        var background = style.getBackground();
        if (background != null) {
            Rectangle bounds = background.getDestRect();
            this.xSize = bounds.width;
            this.ySize = bounds.height;
        } else {
            this.xSize = 176;
            this.ySize = 107;
        }

        AESubGui.addBackButton(container, "returnToParent", this.widgets);
        ActionButton clear = new ActionButton(ActionItems.S_CLOSE, this.container::clearSides);
        clear.setHalfSize(true);
        clear.setDisableBackground(true);
        clear.setMessage(ButtonToolTips.OutputSideClear.text());
        this.widgets.add("clear", clear);

        for (RelativeSide side : RELATIVE_SIDES) {
            var button = new OutputSideSelectionButton(side, () -> getDisplayStack(side), () -> {
                if (!this.container.isSideAllowed(side)) {
                    return;
                }
                this.container.setSideEnabled(side, !this.container.isSideEnabled(side));
            });
            this.widgets.add(getButtonId(side), button);
            this.sideButtons.put(side, button);
            this.lastAllowedStates.put(side, null);
            this.lastEnabledStates.put(side, null);
        }
    }

    private static String getButtonId(RelativeSide side) {
        return "side_" + side.name().toLowerCase(Locale.ROOT);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        for (var entry : this.sideButtons.entrySet()) {
            RelativeSide side = entry.getKey();
            OutputSideSelectionButton button = entry.getValue();
            boolean allowed = this.container.isSideAllowed(side);
            boolean enabled = this.container.isSideEnabled(side);
            button.setAllowed(allowed);
            button.setOutputEnabled(enabled);
            button.enabled = allowed;
            if (!Boolean.valueOf(allowed).equals(this.lastAllowedStates.get(side))
                || !Boolean.valueOf(enabled).equals(this.lastEnabledStates.get(side))) {
                button.setTooltipMessage(Arrays.asList(
                    ButtonToolTips.OutputSideConfig.text(),
                    TextComponentItemStack.of(getDisplayStack(side)),
                    allowed ? GuiText.OutputSideToggleHint.text() : GuiText.OutputSideUnavailable.text(),
                    enabled ? GuiText.OutputSideEnabled.text() : GuiText.OutputSideDisabled.text()));
                this.lastAllowedStates.put(side, allowed);
                this.lastEnabledStates.put(side, enabled);
            }
        }
    }

    private ItemStack getDisplayStack(RelativeSide side) {
        var stack = neighbor.get(side);
        if (stack != null) return stack;

        var host = this.container.getHost();
        if (!(host instanceof TileEntity tile) || tile.getWorld() == null) {
            return ItemStack.EMPTY;
        }

        EnumFacing facing = host.getBlockOrientation().getSide(side);
        World world = tile.getWorld();
        BlockPos pos = tile.getPos().offset(facing);
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        try {
            RayTraceResult hit = getTargetHit(world, pos, facing.getOpposite());
            if (hit != null && pos.equals(hit.getBlockPos())) {
                ItemStack picked = block.getPickBlock(state, hit, world, pos, Minecraft.getMinecraft().player);
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
        stack = new ItemStack(item, 1, i);
        neighbor.put(side, stack);
        return stack;
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

    @Override
    public void initGui() {
        super.initGui();
        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.OutputSides.text());
    }
}
