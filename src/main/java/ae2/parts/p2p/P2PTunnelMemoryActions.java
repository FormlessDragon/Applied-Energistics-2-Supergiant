package ae2.parts.p2p;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartItem;
import ae2.me.service.P2PService;
import ae2.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

public final class P2PTunnelMemoryActions {

    private P2PTunnelMemoryActions() {
    }

    public static void bindOutput(P2PTunnelPart<?> input, P2PTunnelPart<?> output) {
        if (input == output) {
            return;
        }

        short frequency = ensureInput(input);
        updateTunnel(output, true, frequency);
        syncNameFromTunnel(input, output);
    }

    public static void bindInput(P2PTunnelPart<?> output, P2PTunnelPart<?> input) {
        if (output == input || output.getFrequency() == 0) {
            return;
        }

        updateTunnel(input, false, output.getFrequency());
        syncNameFromTunnel(output, input);
        renameFrequency(input, output.getCustomName());
    }

    public static void bindInputFromCard(P2PTunnelPart<?> input, short frequency) {
        if (frequency == 0) {
            return;
        }

        BindingState state = cardInputBindingState(frequency);
        updateTunnel(input, state.output(), state.frequency());
    }

    public static void copyOutput(P2PTunnelPart<?> input, P2PTunnelPart<?> output) {
        if (input == output || input.isOutput() || input.getFrequency() == 0) {
            return;
        }

        updateTunnel(output, true, input.getFrequency());
        syncNameFromTunnel(input, output);
    }

    public static void clearBinding(P2PTunnelPart<?> tunnel) {
        BindingState state = clearedBindingState();
        updateTunnel(tunnel, state.output(), state.frequency());
    }

    public static void renameFrequency(P2PTunnelPart<?> tunnel, @Nullable String name) {
        String normalizedName = normalizeCustomName(name);
        short frequency = tunnel.getFrequency();
        IGrid grid = tunnel.getMainNode().getGrid();
        if (frequency == 0 || grid == null) {
            setCustomName(tunnel, normalizedName);
            return;
        }

        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof P2PTunnelPart<?> other && other.getFrequency() == frequency) {
                setCustomName(other, normalizedName);
            }
        }
    }

    public static P2PTunnelPart<?> changeType(P2PTunnelPart<?> tunnel, IPartItem<?> partItem, EntityPlayer player) {
        if (tunnel.getPartItem() == partItem) {
            return tunnel;
        }

        boolean output = tunnel.isOutput();
        short frequency = tunnel.getFrequency();
        IPart newPart = tunnel.getHost().replacePart(partItem, tunnel.getSide(), player, null);
        if (!(newPart instanceof P2PTunnelPart<?> newTunnel)) {
            return null;
        }

        updateTunnel(newTunnel, output, frequency);
        Platform.notifyBlocksOfNeighbors(newTunnel.getLevel(), newTunnel.getTileEntity().getPos());
        return newTunnel;
    }

    public static P2PTunnelPart<?> changeTypeAndClearBinding(P2PTunnelPart<?> tunnel, IPartItem<?> partItem,
                                                             EntityPlayer player) {
        P2PTunnelPart<?> changedTunnel = changeType(tunnel, partItem, player);
        if (changedTunnel != null) {
            BindingState state = clearedBindingState();
            updateTunnel(changedTunnel, state.output(), state.frequency());
        }
        return changedTunnel;
    }

    private static short ensureInput(P2PTunnelPart<?> tunnel) {
        boolean wasOutput = tunnel.isOutput();
        short frequency = tunnel.getFrequency();
        tunnel.setOutput(false);
        frequency = chooseInputFrequency(wasOutput, frequency, () -> {
            if (tunnel.getMainNode().getGrid() != null) {
                return P2PService.get(tunnel.getMainNode().getGrid()).newFrequency();
            }
            return ThreadLocalRandom.current().nextInt(1, 1 << 16);
        });
        updateTunnel(tunnel, false, frequency);
        return frequency;
    }

    static short chooseInputFrequency(boolean wasOutput, short frequency, IntSupplier newFrequency) {
        if (wasOutput || frequency == 0) {
            return (short) newFrequency.getAsInt();
        }
        return frequency;
    }

    static BindingState clearedBindingState() {
        return new BindingState(false, (short) 0);
    }

    static BindingState cardInputBindingState(short frequency) {
        return new BindingState(false, frequency);
    }

    static @Nullable String normalizeCustomName(@Nullable String name) {
        return name == null || name.isEmpty() ? null : name;
    }

    private static void updateTunnel(P2PTunnelPart<?> tunnel, boolean output, short frequency) {
        tunnel.setOutput(output);
        if (tunnel.getMainNode().getGrid() != null) {
            P2PService.get(tunnel.getMainNode().getGrid()).updateFreq(tunnel, frequency);
        } else {
            tunnel.setFrequency(frequency);
            tunnel.onTunnelNetworkChange();
        }
        tunnel.onTunnelConfigChange();
    }

    private static void syncNameFromTunnel(P2PTunnelPart<?> source, P2PTunnelPart<?> target) {
        setCustomName(target, normalizeCustomName(source.getCustomName()));
    }

    private static void setCustomName(P2PTunnelPart<?> tunnel, @Nullable String name) {
        tunnel.setCustomName(name);
        tunnel.onCustomNameChanged();
    }

    record BindingState(boolean output, short frequency) {
    }
}
