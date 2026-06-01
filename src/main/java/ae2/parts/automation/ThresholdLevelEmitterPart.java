package ae2.parts.automation;

import ae2.api.config.FuzzyMode;
import ae2.api.config.RedstoneMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.IStackWatcher;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingWatcherNode;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.util.IConfigManagerBuilder;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.helpers.IConfigInvHost;
import ae2.hooks.ticking.TickHandler;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.util.ConfigInventory;
import ae2.util.SettingsFrom;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class ThresholdLevelEmitterPart extends AbstractLevelEmitterPart
    implements IConfigInvHost, ICraftingProvider {

    @PartModels
    public static final ResourceLocation MODEL_BASE_OFF = AppEng.makeId("part/threshold_level_emitter_base_off");
    @PartModels
    public static final ResourceLocation MODEL_BASE_ON = AppEng.makeId("part/threshold_level_emitter_base_on");
    public static final PartModel MODEL_OFF_OFF = new PartModel(MODEL_BASE_OFF,
        StorageLevelEmitterPart.MODEL_STATUS_OFF);
    public static final PartModel MODEL_OFF_ON = new PartModel(MODEL_BASE_OFF,
        StorageLevelEmitterPart.MODEL_STATUS_ON);
    public static final PartModel MODEL_OFF_HAS_CHANNEL = new PartModel(MODEL_BASE_OFF,
        StorageLevelEmitterPart.MODEL_STATUS_HAS_CHANNEL);
    public static final PartModel MODEL_ON_OFF = new PartModel(MODEL_BASE_ON,
        StorageLevelEmitterPart.MODEL_STATUS_OFF);
    public static final PartModel MODEL_ON_ON = new PartModel(MODEL_BASE_ON,
        StorageLevelEmitterPart.MODEL_STATUS_ON);
    public static final PartModel MODEL_ON_HAS_CHANNEL = new PartModel(MODEL_BASE_ON,
        StorageLevelEmitterPart.MODEL_STATUS_HAS_CHANNEL);
    private IStackWatcher storageWatcher;    private final ConfigInventory config = ConfigInventory.configTypes(1)
                                                          .changeListener(this::configureWatchers)
                                                          .build();
    private IStackWatcher craftingWatcher;
    private long lastUpdateTick = -1;
    private long upperValue;
    private long lowerValue;
    public ThresholdLevelEmitterPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().addService(IStorageWatcherNode.class, new IStorageWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                storageWatcher = newWatcher;
                configureWatchers();
            }

            @Override
            public void onStackChange(AEKey what, long amount) {
                if (what.equals(getConfiguredKey()) && !isUpgradedWith(AEItems.FUZZY_CARD)) {
                    lastReportedValue = amount;
                    updateState();
                } else {
                    long currentTick = TickHandler.instance().getCurrentTick();
                    if (currentTick != lastUpdateTick) {
                        lastUpdateTick = currentTick;
                        getMainNode().ifPresent(ThresholdLevelEmitterPart.this::updateReportingValue);
                    }
                }
            }
        });
        getMainNode().addService(ICraftingWatcherNode.class, new ICraftingWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                craftingWatcher = newWatcher;
                configureWatchers();
            }

            @Override
            public void onRequestChange(AEKey what) {
                updateState();
            }

            @Override
            public void onCraftableChange(AEKey what) {
            }
        });
        getMainNode().addService(ICraftingProvider.class, this);
    }

    static boolean shouldEmit(long value, long lowerValue, long upperValue, boolean previousState) {
        if (lowerValue > upperValue) {
            return false;
        }
        return previousState ? value >= lowerValue : value >= upperValue;
    }

    static boolean shouldOutput(long value, long lowerValue, long upperValue, boolean inverted, boolean previousOutput) {
        boolean previousState = inverted != previousOutput;
        boolean active = shouldEmit(value, lowerValue, upperValue, previousState);
        return inverted != active;
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.CRAFT_VIA_REDSTONE, YesNo.NO);
        builder.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Nullable
    private AEKey getConfiguredKey() {
        return config.getKey(0);
    }

    @Override
    protected final int getUpgradeSlots() {
        return 1;
    }

    @Override
    public final void upgradesChanged() {
        configureWatchers();
    }

    @Override
    protected boolean hasDirectOutput() {
        return isUpgradedWith(AEItems.CRAFTING_CARD);
    }

    @Override
    protected boolean getDirectOutput() {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        var key = getConfiguredKey();
        return key == null ? grid.getCraftingService().isRequestingAny() : grid.getCraftingService().isRequesting(key);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        return false;
    }

    @Override
    public boolean canMergePatternPush(IPatternDetails patternDetails) {
        return false;
    }

    @Override
    public int getMaxPatternPushMultiplier(IPatternDetails patternDetails, int maxMultiplier) {
        return 0;
    }

    @Override
    public boolean isBusy() {
        return true;
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        if (isUpgradedWith(AEItems.CRAFTING_CARD)
            && getConfigManager().getSetting(Settings.CRAFT_VIA_REDSTONE) == YesNo.YES
            && getConfiguredKey() != null) {
            return Set.of(getConfiguredKey());
        }
        return Set.of();
    }

    @Override
    protected void onReportingValueChanged() {
        getMainNode().ifPresent(this::updateReportingValue);
    }

    @Override
    protected void configureWatchers() {
        var key = getConfiguredKey();
        if (this.storageWatcher != null) {
            this.storageWatcher.reset();
        }
        if (this.craftingWatcher != null) {
            this.craftingWatcher.reset();
        }
        ICraftingProvider.requestUpdate(getMainNode());
        if (isUpgradedWith(AEItems.CRAFTING_CARD)) {
            if (this.craftingWatcher != null) {
                if (key == null) {
                    this.craftingWatcher.setWatchAll(true);
                } else {
                    this.craftingWatcher.add(key);
                }
            }
        } else {
            if (this.storageWatcher != null) {
                if (isUpgradedWith(AEItems.FUZZY_CARD) || key == null) {
                    this.storageWatcher.setWatchAll(true);
                } else {
                    this.storageWatcher.add(key);
                }
            }
            getMainNode().ifPresent(this::updateReportingValue);
        }
        updateState();
    }

    private void updateReportingValue(IGrid grid) {
        var stacks = grid.getStorageService().getCachedInventory();
        var key = getConfiguredKey();
        if (key == null) {
            this.lastReportedValue = 0;
            for (var stack : stacks) {
                this.lastReportedValue += stack.getLongValue();
                if (this.lastReportedValue > this.upperValue) {
                    break;
                }
            }
        } else if (isUpgradedWith(AEItems.FUZZY_CARD)) {
            this.lastReportedValue = 0;
            var fuzzyMode = this.getConfigManager().getSetting(Settings.FUZZY_MODE);
            for (var stack : stacks.findFuzzy(key, fuzzyMode)) {
                this.lastReportedValue += stack.getLongValue();
                if (this.lastReportedValue > this.upperValue) {
                    break;
                }
            }
        } else {
            this.lastReportedValue = stacks.get(key);
        }
        updateState();
    }

    @Override
    protected boolean isLevelEmitterOn() {
        if (isClientSide()) {
            return super.isLevelEmitterOn();
        }
        if (!this.getMainNode().isActive()) {
            return false;
        }
        if (hasDirectOutput()) {
            return getDirectOutput();
        }
        boolean flipState = this.getConfigManager().getSetting(Settings.REDSTONE_EMITTER) == RedstoneMode.LOW_SIGNAL;
        return shouldOutput(this.lastReportedValue, this.lowerValue, this.upperValue, flipState,
            this.isProvidingWeakPower() > 0);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.upperValue = data.getLong("upperValue");
        this.lowerValue = data.getLong("lowerValue");
        this.config.readFromChildTag(data, "config");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setLong("upperValue", this.upperValue);
        data.setLong("lowerValue", this.lowerValue);
        this.config.writeToChildTag(data, "config");
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        super.importSettings(mode, input, player);
        if (input.hasKey("upperValue")) {
            this.upperValue = input.getLong("upperValue");
        }
        if (input.hasKey("lowerValue")) {
            this.lowerValue = input.getLong("lowerValue");
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        super.exportSettings(mode, output);
        output.setLong("upperValue", this.upperValue);
        output.setLong("lowerValue", this.lowerValue);
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!isClientSide()) {
            GuiOpener.openPartGui(player, GuiIds.GuiKey.THRESHOLD_LEVEL_EMITTER, this);
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return this.onUseWithoutItem(player, pos);
    }

    public ConfigInventory getConfig() {
        return this.config;
    }

    public long getUpperValue() {
        return this.upperValue;
    }

    public void setUpperValue(long upperValue) {
        this.upperValue = upperValue;
        onReportingValueChanged();
        updateState();
    }

    public long getLowerValue() {
        return this.lowerValue;
    }

    public void setLowerValue(long lowerValue) {
        this.lowerValue = lowerValue;
        onReportingValueChanged();
        updateState();
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_HAS_CHANNEL : MODEL_OFF_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_ON : MODEL_OFF_ON;
        } else {
            return this.isLevelEmitterOn() ? MODEL_ON_OFF : MODEL_OFF_OFF;
        }
    }


}
