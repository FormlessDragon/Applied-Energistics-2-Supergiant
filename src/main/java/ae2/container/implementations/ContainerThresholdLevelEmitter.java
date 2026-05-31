package ae2.container.implementations;

import ae2.api.config.Settings;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.util.IConfigManager;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.slot.FakeSlot;
import ae2.core.definitions.AEItems;
import ae2.parts.automation.ThresholdLevelEmitterPart;
import net.minecraft.entity.player.InventoryPlayer;
import org.jetbrains.annotations.Nullable;

public class ContainerThresholdLevelEmitter extends UpgradeableContainer<ThresholdLevelEmitterPart> {
    private static final String ACTION_SET_UPPER_VALUE = "setUpperValue";
    private static final String ACTION_SET_LOWER_VALUE = "setLowerValue";

    @GuiSync(7)
    private long upperValue;
    @GuiSync(8)
    private long lowerValue;

    public ContainerThresholdLevelEmitter(InventoryPlayer ip, ThresholdLevelEmitterPart host) {
        super(ip, host);
        registerClientAction(ACTION_SET_UPPER_VALUE, Long.class, this::setUpperValue);
        registerClientAction(ACTION_SET_LOWER_VALUE, Long.class, this::setLowerValue);
    }

    public ContainerThresholdLevelEmitter(InventoryPlayer ip, ThresholdLevelEmitterPart host,
                                          @Nullable GenericStack initialFilter, long initialUpper, long initialLower) {
        this(ip, host);
        if (isClientSide()) {
            getHost().getConfig().setStack(0, initialFilter);
            this.upperValue = initialUpper;
            this.lowerValue = initialLower;
        }
    }

    @Override
    protected void setupConfig() {
        var inv = getHost().getConfig().createGuiWrapper();
        this.addSlot(new FakeSlot(inv, 0, 137, 47), SlotSemantics.CONFIG);
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        if (cm.hasSetting(Settings.FUZZY_MODE)) {
            this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
        }
        this.setRedStoneMode(cm.getSetting(Settings.REDSTONE_EMITTER));
        this.setCraftingMode(cm.getSetting(Settings.CRAFT_VIA_REDSTONE));
    }

    public boolean supportsFuzzySearch() {
        return getHost().getConfigManager().hasSetting(Settings.FUZZY_MODE)
            && hasUpgrade(AEItems.FUZZY_CARD.item());
    }

    @Nullable
    public AEKey getConfiguredFilter() {
        return getHost().getConfig().getKey(0);
    }

    public long getUpperValue() {
        return upperValue;
    }

    public void setUpperValue(long value) {
        if (isClientSide()) {
            if (value != this.upperValue) {
                this.upperValue = value;
                sendClientAction(ACTION_SET_UPPER_VALUE, value);
            }
        } else {
            getHost().setUpperValue(value);
        }
    }

    public long getLowerValue() {
        return lowerValue;
    }

    public void setLowerValue(long value) {
        if (isClientSide()) {
            if (value != this.lowerValue) {
                this.lowerValue = value;
                sendClientAction(ACTION_SET_LOWER_VALUE, value);
            }
        } else {
            getHost().setLowerValue(value);
        }
    }
}
