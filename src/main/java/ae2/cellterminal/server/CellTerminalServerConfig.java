package ae2.cellterminal.server;

import ae2.core.AELog;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Server-side feature policy for Cell Terminal containers.
 * <p>
 * This object keeps Cell Terminal backend switches in one place so server actions can reject disabled entries before
 * resolving targets or mutating live network state. The default instance preserves the current behavior: every tab,
 * action, network tool, and write operation is enabled, and periodic scans run every 100 ticks.
 */
public record CellTerminalServerConfig(Set<ServerTab> enabledTabs,
                                       int periodicScanIntervalTicks,
                                       Set<Action> enabledActions,
                                       boolean networkToolsEnabled,
                                       Set<CellTerminalNetworkToolOperation> enabledNetworkTools,
                                       boolean writeOperationsEnabled,
                                       Set<WriteOperation> enabledWriteOperations) {
    /**
     * The scan cadence used before Cell Terminal server configuration existed.
     */
    public static final int DEFAULT_PERIODIC_SCAN_INTERVAL_TICKS = 100;

    /**
     * Creates an immutable server policy.
     *
     * @param enabledTabs               Tabs that may be selected by a client.
     * @param periodicScanIntervalTicks Maximum delay between automatic backend scans.
     * @param enabledActions            Non-tool, non-write client actions accepted by the server.
     * @param networkToolsEnabled       Global switch for every network-tool preview and execute entry.
     * @param enabledNetworkTools       Individual network-tool operations accepted by the server.
     * @param writeOperationsEnabled    Global switch for every mutating write operation.
     * @param enabledWriteOperations    Individual write operations accepted by the server.
     */
    public CellTerminalServerConfig {
        enabledTabs = Set.copyOf(Objects.requireNonNull(enabledTabs, "enabledTabs"));
        enabledActions = Set.copyOf(Objects.requireNonNull(enabledActions, "enabledActions"));
        enabledNetworkTools = Set.copyOf(Objects.requireNonNull(enabledNetworkTools, "enabledNetworkTools"));
        enabledWriteOperations = Set.copyOf(Objects.requireNonNull(enabledWriteOperations, "enabledWriteOperations"));
        if (periodicScanIntervalTicks <= 0) {
            throw new IllegalArgumentException("periodicScanIntervalTicks must be positive");
        }
    }

    /**
     * Returns the behavior-compatible Cell Terminal server policy.
     */
    public static CellTerminalServerConfig defaults() {
        return builder().build();
    }

    /**
     * Loads the immutable Cell Terminal server policy from the central configuration boundary.
     * <p>
     * Forge-exposed switches are intentionally introduced through this method so container code does not keep feature
     * defaults locally. The current policy maps to the behavior-compatible defaults until the Forge config surface is
     * extended for Cell Terminal-specific switches.
     */
    public static CellTerminalServerConfig loadFromConfig() {
        AELog.info("Loading Cell Terminal server config from built-in defaults.");
        return defaults();
    }

    /**
     * Starts a mutable builder with every Cell Terminal feature enabled.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks whether the server may switch to the requested Cell Terminal tab.
     */
    public boolean isTabEnabled(ServerTab tab) {
        return this.enabledTabs.contains(Objects.requireNonNull(tab, "tab"));
    }

    /**
     * Checks whether the server may run the requested non-tool client action.
     */
    public boolean isActionEnabled(Action action) {
        return this.enabledActions.contains(Objects.requireNonNull(action, "action"));
    }

    /**
     * Checks whether the server may preview or execute the requested network tool.
     */
    public boolean isNetworkToolEnabled(CellTerminalNetworkToolOperation operation) {
        return this.networkToolsEnabled
            && this.enabledNetworkTools.contains(Objects.requireNonNull(operation, "operation"));
    }

    /**
     * Checks whether the server may perform the requested mutating write.
     */
    public boolean isWriteOperationEnabled(WriteOperation operation) {
        return this.writeOperationsEnabled
            && this.enabledWriteOperations.contains(Objects.requireNonNull(operation, "operation"));
    }

    /**
     * Server-visible Cell Terminal tab identifiers. Names intentionally match the serialized client tab names so the
     * container can validate incoming tab actions without depending on GUI classes here.
     */
    public enum ServerTab {
        OVERVIEW,
        CELL_CONTENT,
        CELL_PARTITION,
        TEMP_CELLS,
        BUS_CONTENT,
        BUS_PARTITION,
        SUBNETS,
        NETWORK_TOOLS;

        /**
         * Parses a serialized tab name from a client action.
         */
        public static ServerTab fromSerializedName(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Cell Terminal tab name is empty");
            }
            return valueOf(name);
        }
    }

    /**
     * Client actions that do not directly mutate storage-cell or bus configuration.
     */
    public enum Action {
        REFRESH,
        LOAD_SUBNET,
        RETURN_TO_PARENT,
        RENAME_SUBNET,
        FAVORITE_SUBNET,
        RESTORE_LAST_SUBNET,
        HIGHLIGHT_SUBNET,
        SELECT_TARGET_UPGRADES,
        REQUEST_CONTENT_PAGE
    }

    /**
     * Mutating server writes guarded by the Cell Terminal write switch.
     */
    public enum WriteOperation {
        SUBNET_METADATA,
        NETWORK_TOOL_EXECUTE,
        PARTITION,
        PRIORITY,
        BUS_MODE,
        CELL_SLOT,
        TARGET_UPGRADE
    }

    /**
     * Mutable builder for Cell Terminal server policy. It is intended for config-loading code to make explicit feature
     * decisions and then publish an immutable {@link CellTerminalServerConfig}.
     */
    public static final class Builder {
        private final EnumSet<ServerTab> enabledTabs = EnumSet.allOf(ServerTab.class);
        private final EnumSet<Action> enabledActions = EnumSet.allOf(Action.class);
        private final EnumSet<CellTerminalNetworkToolOperation> enabledNetworkTools =
            EnumSet.allOf(CellTerminalNetworkToolOperation.class);
        private final EnumSet<WriteOperation> enabledWriteOperations = EnumSet.allOf(WriteOperation.class);
        private int periodicScanIntervalTicks = DEFAULT_PERIODIC_SCAN_INTERVAL_TICKS;
        private boolean networkToolsEnabled = true;
        private boolean writeOperationsEnabled = true;

        private Builder() {
        }

        private static <E extends Enum<E>> void set(EnumSet<E> set, E value, boolean enabled) {
            Objects.requireNonNull(value, "value");
            if (enabled) {
                set.add(value);
            } else {
                set.remove(value);
            }
        }

        /**
         * Sets the automatic scan interval in server ticks.
         */
        public Builder periodicScanIntervalTicks(int periodicScanIntervalTicks) {
            if (periodicScanIntervalTicks <= 0) {
                throw new IllegalArgumentException("periodicScanIntervalTicks must be positive");
            }
            this.periodicScanIntervalTicks = periodicScanIntervalTicks;
            return this;
        }

        /**
         * Enables or disables a tab selection entry.
         */
        public Builder tab(ServerTab tab, boolean enabled) {
            set(this.enabledTabs, tab, enabled);
            return this;
        }

        /**
         * Enables or disables a non-tool client action entry.
         */
        public Builder action(Action action, boolean enabled) {
            set(this.enabledActions, action, enabled);
            return this;
        }

        /**
         * Enables or disables all network-tool entries.
         */
        public Builder networkToolsEnabled(boolean enabled) {
            this.networkToolsEnabled = enabled;
            return this;
        }

        /**
         * Enables or disables a single network-tool operation.
         */
        public Builder networkTool(CellTerminalNetworkToolOperation operation, boolean enabled) {
            set(this.enabledNetworkTools, operation, enabled);
            return this;
        }

        /**
         * Enables or disables all mutating write entries.
         */
        public Builder writeOperationsEnabled(boolean enabled) {
            this.writeOperationsEnabled = enabled;
            return this;
        }

        /**
         * Enables or disables a single mutating write operation.
         */
        public Builder writeOperation(WriteOperation operation, boolean enabled) {
            set(this.enabledWriteOperations, operation, enabled);
            return this;
        }

        /**
         * Builds an immutable server policy from the configured switches.
         */
        public CellTerminalServerConfig build() {
            return new CellTerminalServerConfig(
                this.enabledTabs,
                this.periodicScanIntervalTicks,
                this.enabledActions,
                this.networkToolsEnabled,
                this.enabledNetworkTools,
                this.writeOperationsEnabled,
                this.enabledWriteOperations);
        }
    }
}
