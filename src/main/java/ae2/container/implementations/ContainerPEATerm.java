package ae2.container.implementations;

import ae2.api.config.Settings;
import ae2.api.config.ShowPatternProviders;
import ae2.api.config.YesNo;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.IPEATermContainerHost;
import ae2.api.util.IConfigManager;
import ae2.container.GuiIds;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import ae2.helpers.InventoryAction;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ContainerPEATerm extends ContainerPatternEncodingTerm implements IPatternAccess {

    private static final String ACTION_OPEN_PROVIDER = "openProvider";
    private static final String ACTION_TOGGLE_PROVIDER_VISIBILITY = "toggleProviderVisibility";
    private static final String ACTION_RENAME_GROUP = "renameGroup";
    private static final String ACTION_RENAME_PROVIDER = "renameProvider";

    private final IPEATermContainerHost host;
    private final IConfigManager clientConfigManager = IConfigManager.builder(() -> {})
                                                                    .registerSetting(Settings.PATTERN_AUTO_FILL, YesNo.NO)
                                                                    .registerSetting(
                                                                        Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                                                                        ShowPatternProviders.VISIBLE)
                                                                    .build();
    private final PatternAccessSupport<ContainerPEATerm> patternAccessSupport;
    @GuiSync(91)
    public ShowPatternProviders showPatternProviders = ShowPatternProviders.VISIBLE;

    public ContainerPEATerm(InventoryPlayer ip, IPEATermContainerHost host) {
        this(GuiIds.GuiKey.PEA_TERMINAL, ip, host, true);
    }

    public ContainerPEATerm(GuiIds.GuiKey guiKey, InventoryPlayer ip, IPEATermContainerHost host,
                            boolean bindInventory) {
        super(guiKey, ip, host, bindInventory);
        this.host = host;
        this.patternAccessSupport = new PatternAccessSupport<>(
            this::getPatternProviderGrid,
            this::getShownProviders,
            () -> getPlayer().world,
            slot -> isPlayerSideSlot(slot) || isEncodedPatternSlot(slot),
            this::sendPacketToClient,
            new PatternAccessSupport.PlayerHandAccess() {
                @Override
                public ItemStack getCarried() {
                    return ContainerPEATerm.this.getCarried();
                }

                @Override
                public void setCarried(ItemStack stack) {
                    ContainerPEATerm.this.setCarried(stack);
                }
            },
            this);
        registerClientAction(ACTION_OPEN_PROVIDER, Long.class, this::openPatternProvider);
        registerClientAction(ACTION_TOGGLE_PROVIDER_VISIBILITY, Long.class, this::togglePatternProviderVisibility);
        registerClientAction(ACTION_RENAME_GROUP, PatternAccessSupport.RenamePatternGroupPayload.class,
            this::renamePatternGroup);
        registerClientAction(ACTION_RENAME_PROVIDER, PatternAccessSupport.RenamePatternProviderPayload.class,
            this::renamePatternProvider);
    }

    @Override
    public void openPatternProvider(long inventoryId) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_PROVIDER, inventoryId);
            return;
        }

        this.patternAccessSupport.openProvider(getPlayer(), inventoryId);
    }

    @Override
    public void renamePatternProvider(long inventoryId, String name) {
        renamePatternProvider(new PatternAccessSupport.RenamePatternProviderPayload(inventoryId, name));
    }

    @Override
    public void renamePatternGroup(long[] inventoryIds, String name) {
        renamePatternGroup(new PatternAccessSupport.RenamePatternGroupPayload(inventoryIds, name));
    }

    @Override
    public void togglePatternProviderVisibility(long inventoryId) {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_PROVIDER_VISIBILITY, inventoryId);
            return;
        }

        this.patternAccessSupport.toggleProviderVisibility(inventoryId);
    }

    private void renamePatternGroup(PatternAccessSupport.RenamePatternGroupPayload payload) {
        if (isClientSide()) {
            sendClientAction(ACTION_RENAME_GROUP, payload);
            return;
        }

        this.patternAccessSupport.renameGroup(payload);
    }

    private void renamePatternProvider(PatternAccessSupport.RenamePatternProviderPayload payload) {
        if (isClientSide()) {
            sendClientAction(ACTION_RENAME_PROVIDER, payload);
            return;
        }

        this.patternAccessSupport.renameProvider(payload);
    }

    @Override
    public ShowPatternProviders getShownProviders() {
        return this.showPatternProviders;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return super.getLinkStatus();
    }

    @Override
    public IConfigManager getConfigManager() {
        if (isServerSide()) {
            return this.host.getConfigManager();
        }
        return this.clientConfigManager;
    }

    @Nullable
    private IGrid getPatternProviderGrid() {
        IGridNode node = this.host.getGridNode();
        if (node != null && node.isActive()) {
            return node.grid();
        }
        return null;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isClientSide()) {
            return;
        }

        this.showPatternProviders =
            this.host.getConfigManager().getSetting(Settings.TERMINAL_SHOW_PATTERN_PROVIDERS);
        this.patternAccessSupport.updateProviderVisibility();
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (!this.patternAccessSupport.doAction(player, action, slot, id)) {
            super.doAction(player, action, slot, id);
        }
    }

    @Override
    public void quickMovePattern(EntityPlayerMP player, Slot sourceSlot, LongList allowedPatternContainerIds,
                                  LongList allowedPatternSlots) {
        this.patternAccessSupport.quickMovePattern(player, sourceSlot, allowedPatternContainerIds,
            allowedPatternSlots);
    }

    @Override
    protected ItemStack transferStackToContainerWithRemainder(ItemStack input) {
        return input;
    }

    public boolean isEncodedPatternSlot(Slot slot) {
        return getSlots(SlotSemantics.ENCODED_PATTERN).contains(slot);
    }
}
