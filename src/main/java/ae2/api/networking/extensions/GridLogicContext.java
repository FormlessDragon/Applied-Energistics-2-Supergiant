package ae2.api.networking.extensions;

import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.security.IActionSource;
import ae2.api.upgrades.IUpgradeInventory;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Stable access to the state shared by AE2 grid logic implementations.
 */
public final class GridLogicContext {
    private final Item machineType;
    private final Object owner;
    private final IManagedGridNode managedNode;
    private final IActionSource actionSource;
    private final IUpgradeInventory upgrades;
    private final TileEntity host;
    private final Supplier<? extends Set<EnumFacing>> targetSides;

    public GridLogicContext(Item machineType, Object owner, IManagedGridNode managedNode, IActionSource actionSource,
                            IUpgradeInventory upgrades, TileEntity host,
                            Supplier<? extends Set<EnumFacing>> targetSides) {
        this.machineType = Objects.requireNonNull(machineType, "machineType");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.managedNode = Objects.requireNonNull(managedNode, "managedNode");
        this.actionSource = Objects.requireNonNull(actionSource, "actionSource");
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
        this.host = Objects.requireNonNull(host, "host");
        this.targetSides = Objects.requireNonNull(targetSides, "targetSides");
    }

    public Item getMachineType() {
        return machineType;
    }

    /**
     * Returns the logic owner, usually the owning block entity or part.
     */
    public Object getOwner() {
        return owner;
    }

    public IManagedGridNode getManagedNode() {
        return managedNode;
    }

    public IActionSource getActionSource() {
        return actionSource;
    }

    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public TileEntity getHostTile() {
        return host;
    }

    /**
     * Returns a snapshot of the sides this machine currently targets.
     */
    public Set<EnumFacing> getTargetSides() {
        var sides = Objects.requireNonNull(targetSides.get(), "targetSides returned null");
        if (sides.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(sides));
    }
}
