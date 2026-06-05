package ae2.container.implementations;

import ae2.api.orientation.RelativeSide;
import ae2.container.AEBaseContainer;
import ae2.container.ISubGui;
import ae2.container.guisync.GuiSync;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.helpers.IOutputSideConfigHost;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;

public class ContainerOutputSides extends AEBaseContainer implements ISubGui {
    private static final String ACTION_SET_SIDE = "setSide";
    private static final String ACTION_CLEAR_SIDES = "clearSides";

    private final IOutputSideConfigHost host;

    @GuiSync(1)
    public int outputSideMask;

    @GuiSync(2)
    public int allowedSideMask;

    public ContainerOutputSides(InventoryPlayer ip, IOutputSideConfigHost host) {
        super(ip, host);
        this.host = host;
        registerClientAction(ACTION_SET_SIDE, RelativeSideState.class, this::setSideEnabled);
        registerClientAction(ACTION_CLEAR_SIDES, this::clearSides);
    }

    private static boolean isSet(int mask, RelativeSide side) {
        return (mask & (1 << side.ordinal())) != 0;
    }

    public boolean isSideAllowed(RelativeSide side) {
        return isSet(this.allowedSideMask, side);
    }

    public boolean isSideEnabled(RelativeSide side) {
        return isSet(this.outputSideMask, side);
    }

    public void setSideEnabled(RelativeSide side, boolean enabled) {
        setSideEnabled(new RelativeSideState(side, enabled));
    }

    private void setSideEnabled(RelativeSideState state) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_SIDE, state);
            return;
        }

        EnumFacing side = this.host.getBlockOrientation().getSide(state.side());
        if (!this.host.isOutputSideAllowed(side)) {
            return;
        }

        this.host.setOutputSideEnabled(side, state.enabled());
    }

    public void clearSides() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_SIDES);
            return;
        }
        this.host.clearOutputSides();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.outputSideMask = encodeMask(this.host.getOutputSides());
            this.allowedSideMask = encodeMask(this.host.getAllowedOutputSides());
        }
        super.broadcastChanges();
    }

    @Override
    public GuiHostLocator getLocator() {
        return super.getLocator();
    }

    @Override
    public IOutputSideConfigHost getHost() {
        return this.host;
    }

    private int encodeMask(Iterable<EnumFacing> sides) {
        int mask = 0;
        for (RelativeSide side : RelativeSide.values()) {
            EnumFacing absoluteSide = this.host.getBlockOrientation().getSide(side);
            for (EnumFacing configuredSide : sides) {
                if (configuredSide == absoluteSide) {
                    mask |= 1 << side.ordinal();
                    break;
                }
            }
        }
        return mask;
    }

    private record RelativeSideState(RelativeSide side, boolean enabled) {
    }
}
