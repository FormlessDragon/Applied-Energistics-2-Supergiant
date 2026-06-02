package ae2.parts.encoding;

import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.core.definitions.AEItems;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.IPatternTerminalLogicHost;
import ae2.util.ConfigInventory;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.filter.AEItemDefinitionFilter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

public class PatternEncodingLogic implements InternalInventoryHost {
    private static final int MAX_INPUT_SLOTS = Math.max(AECraftingPattern.CRAFTING_GRID_SLOTS,
        AEProcessingPattern.MAX_INPUT_SLOTS);
    private static final int MAX_OUTPUT_SLOTS = AEProcessingPattern.MAX_OUTPUT_SLOTS;

    private final IPatternTerminalLogicHost host;
    private final AppEngInternalInventory blankPatternInv = new AppEngInternalInventory(this, 1);
    private final AppEngInternalInventory encodedPatternInv = new AppEngInternalInventory(this, 1);
    private EncodingMode mode = EncodingMode.CRAFTING;
    private boolean substitute;
    private boolean substituteFluids = true;
    private final ConfigInventory encodedInputInv = ConfigInventory.configStacks(MAX_INPUT_SLOTS)
                                                                   .changeListener(this::onEncodedInputChanged)
                                                                   .allowOverstacking(true)
                                                                   .build();
    private boolean isLoading;
    private final ConfigInventory encodedOutputInv = ConfigInventory.configStacks(MAX_OUTPUT_SLOTS)
                                                                    .changeListener(this::onEncodedOutputChanged)
                                                                    .allowOverstacking(true)
                                                                    .build();
    public PatternEncodingLogic(IPatternTerminalLogicHost host) {
        this.host = host;
        this.blankPatternInv.setFilter(new AEItemDefinitionFilter(AEItems.BLANK_PATTERN));
        this.encodedPatternInv.setMaxStackSize(0, 1);
    }

    private static void fillInventoryFromSparseStacks(ConfigInventory inv, List<GenericStack> stacks) {
        inv.beginBatch();
        try {
            for (int i = 0; i < inv.size(); i++) {
                inv.setStack(i, i < stacks.size() ? stacks.get(i) : null);
            }
        } finally {
            inv.endBatch();
        }
    }

    private static void fixItemOnlyInventory(ConfigInventory inv) {
        for (int slot = 0; slot < inv.size(); slot++) {
            var stack = inv.getStack(slot);
            if (stack == null) {
                continue;
            }
            if (!(stack.what() instanceof AEItemKey)) {
                inv.setStack(slot, null);
                continue;
            }
            if (stack.amount() != 1) {
                inv.setStack(slot, new GenericStack(stack.what(), 1));
            }
        }
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.encodedPatternInv) {
            loadEncodedPattern(this.encodedPatternInv.getStackInSlot(0));
        }
        saveChanges();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return this.host.getLevel() == null || this.host.getLevel().isRemote;
    }

    public void saveChanges() {
        if (!this.isLoading) {
            this.host.markForSave();
        }
    }

    private void onEncodedInputChanged() {
        fixCraftingRecipes();
        saveChanges();
    }

    private void onEncodedOutputChanged() {
        saveChanges();
    }

    private void loadEncodedPattern(ItemStack pattern) {
        if (pattern.isEmpty()) {
            return;
        }

        var details = PatternDetailsHelper.decodePattern(pattern, this.host.getLevel());
        if (details instanceof AECraftingPattern craftingPattern) {
            loadCraftingPattern(craftingPattern);
        } else if (details instanceof AEProcessingPattern processingPattern) {
            loadProcessingPattern(processingPattern);
        }

        saveChanges();
    }

    private void loadCraftingPattern(AECraftingPattern pattern) {
        setMode(EncodingMode.CRAFTING);
        this.substitute = pattern.canSubstitute();
        this.substituteFluids = pattern.canSubstituteFluids();
        fillInventoryFromSparseStacks(this.encodedInputInv, pattern.getSparseInputs());
        fillInventoryFromSparseStacks(this.encodedOutputInv, pattern.getSparseOutputs());
    }

    private void loadProcessingPattern(AEProcessingPattern pattern) {
        setMode(EncodingMode.PROCESSING);
        fillInventoryFromSparseStacks(this.encodedInputInv, pattern.getSparseInputs());
        fillInventoryFromSparseStacks(this.encodedOutputInv, pattern.getSparseOutputs());
    }

    public EncodingMode getMode() {
        return this.mode;
    }

    public void setMode(EncodingMode mode) {
        this.mode = mode;
        fixCraftingRecipes();
        saveChanges();
    }

    public boolean isSubstitution() {
        return this.substitute;
    }

    public void setSubstitution(boolean canSubstitute) {
        this.substitute = canSubstitute;
        saveChanges();
    }

    public boolean isFluidSubstitution() {
        return this.substituteFluids;
    }

    public void setFluidSubstitution(boolean canSubstitute) {
        this.substituteFluids = canSubstitute;
        saveChanges();
    }

    public ConfigInventory getEncodedInputInv() {
        return this.encodedInputInv;
    }

    public ConfigInventory getEncodedOutputInv() {
        return this.encodedOutputInv;
    }

    public InternalInventory getBlankPatternInv() {
        return this.blankPatternInv;
    }

    public InternalInventory getEncodedPatternInv() {
        return this.encodedPatternInv;
    }

    public void readFromNBT(NBTTagCompound data) {
        this.isLoading = true;
        try {
            if (data.hasKey("mode", 8)) {
                try {
                    this.mode = EncodingMode.valueOf(data.getString("mode"));
                } catch (IllegalArgumentException ignored) {
                    this.mode = EncodingMode.CRAFTING;
                }
            } else {
                this.mode = EncodingMode.CRAFTING;
            }
            this.substitute = data.getBoolean("substitute");
            this.substituteFluids = !data.hasKey("substituteFluids", 1) || data.getBoolean("substituteFluids");
            this.blankPatternInv.readFromNBT(data, "blankPattern");
            this.encodedPatternInv.readFromNBT(data, "encodedPattern");
            this.encodedInputInv.readFromChildTag(data, "encodedInputs");
            this.encodedOutputInv.readFromChildTag(data, "encodedOutputs");
            fixCraftingRecipes();
        } finally {
            this.isLoading = false;
        }
    }

    public void writeToNBT(NBTTagCompound data) {
        data.setString("mode", this.mode.name());
        data.setBoolean("substitute", this.substitute);
        data.setBoolean("substituteFluids", this.substituteFluids);
        this.blankPatternInv.writeToNBT(data, "blankPattern");
        this.encodedPatternInv.writeToNBT(data, "encodedPattern");
        this.encodedInputInv.writeToChildTag(data, "encodedInputs");
        this.encodedOutputInv.writeToChildTag(data, "encodedOutputs");
    }

    private void fixCraftingRecipes() {
        if (this.host.getLevel() == null || this.host.getLevel().isRemote || this.mode == EncodingMode.PROCESSING) {
            return;
        }

        fixItemOnlyInventory(this.encodedInputInv);
    }




}
