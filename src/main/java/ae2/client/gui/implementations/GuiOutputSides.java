package ae2.client.gui.implementations;

import ae2.api.orientation.RelativeSide;
import ae2.api.parts.IPart;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.OutputSideSelectionButton;
import ae2.container.implementations.ContainerOutputSides;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.tile.networking.TileCableBus;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GuiOutputSides extends AEBaseGui<ContainerOutputSides> {
    private static final String STYLE_PATH = "/screens/output_sides.json";
    private static final RelativeSide[] RELATIVE_SIDES = RelativeSide.values();

    private final Map<RelativeSide, OutputSideSelectionButton> sideButtons = new EnumMap<>(RelativeSide.class);
    private final Map<RelativeSide, Boolean> lastAllowedStates = new EnumMap<>(RelativeSide.class);
    private final Map<RelativeSide, Boolean> lastEnabledStates = new EnumMap<>(RelativeSide.class);

    public GuiOutputSides(ContainerOutputSides container, InventoryPlayer playerInventory, ITextComponent title) {
        this(container, playerInventory, title, GuiStyleManager.loadStyleDoc(STYLE_PATH));
    }

    private GuiOutputSides(ContainerOutputSides container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
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
        this.widgets.add("clear", new IconButton(this.container::clearSides) {
            private final List<ITextComponent> tooltip = List.of(
                ButtonToolTips.OutputSideClear.text(),
                ButtonToolTips.OutputSideClearHint.text());

            {
                setMessage(this.tooltip.getFirst());
            }

            @Override
            protected Icon getIcon() {
                return Icon.CLEAR;
            }

            @Override
            public List<ITextComponent> getTooltipMessage() {
                return this.tooltip;
            }
        });

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
            button.enabled = allowed;
            if (!Boolean.valueOf(allowed).equals(this.lastAllowedStates.get(side))
                || !Boolean.valueOf(enabled).equals(this.lastEnabledStates.get(side))) {
                button.setTooltipMessage(List.of(
                    ButtonToolTips.OutputSideConfig.text(),
                    allowed ? GuiText.OutputSideToggleHint.text() : GuiText.OutputSideUnavailable.text(),
                    enabled ? GuiText.OutputSideEnabled.text() : GuiText.OutputSideDisabled.text()));
                this.lastAllowedStates.put(side, allowed);
                this.lastEnabledStates.put(side, enabled);
            }
        }
    }

    private ItemStack getDisplayStack(RelativeSide side) {
        var host = this.container.getHost();
        if (!(host instanceof TileEntity tile) || tile.getWorld() == null) {
            return ItemStack.EMPTY;
        }

        EnumFacing facing = host.getBlockOrientation().getSide(side);
        var pos = tile.getPos().offset(facing);
        var adjacentTile = tile.getWorld().getTileEntity(pos);
        if (adjacentTile instanceof TileCableBus cableBus) {
            IPart part = cableBus.getPart(facing.getOpposite());
            if (part != null) {
                return part.getPartItem().asItemStack();
            }
        }

        return new ItemStack(tile.getWorld().getBlockState(pos).getBlock());
    }

    @Override
    public void initGui() {
        super.initGui();
        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.OutputSides.text());
    }
}
