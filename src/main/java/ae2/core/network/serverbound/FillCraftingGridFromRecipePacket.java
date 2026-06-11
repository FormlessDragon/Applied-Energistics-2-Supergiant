package ae2.core.network.serverbound;

import ae2.api.config.FuzzyMode;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.container.AEBaseContainer;
import ae2.container.interfaces.ICraftingGridContainer;
import ae2.core.network.ServerboundPacket;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.items.storage.ViewCellItem;
import ae2.me.storage.NullInventory;
import ae2.util.CraftingRecipeUtil;
import ae2.util.EmptyArrays;
import ae2.util.prioritylist.IPartitionList;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class FillCraftingGridFromRecipePacket extends ServerboundPacket {
    private static final int CRAFTING_GRID_SIZE = 9;
    private static final int MAX_PACKET_BYTES = 1024 * 1024;
    private static final int MAX_INGREDIENT_ALTERNATIVES = 256;
    private static final int MAX_TOTAL_INGREDIENT_ALTERNATIVES = CRAFTING_GRID_SIZE * MAX_INGREDIENT_ALTERNATIVES;

    private int windowId;
    private ResourceLocation recipeId;
    private List<List<ItemStack>> ingredientTemplates = createEmptyIngredientTemplates();
    private boolean craftMissing;
    private List<GenericStack> temporaryPseudoInputs = List.of();
    private List<GenericStack> temporaryPseudoOutputs = List.of();
    private boolean invalidRequest;

    public FillCraftingGridFromRecipePacket() {
    }

    public FillCraftingGridFromRecipePacket(int windowId, ResourceLocation recipeId, List<List<ItemStack>> ingredientTemplates,
                                            boolean craftMissing, List<GenericStack> temporaryPseudoInputs,
                                            List<GenericStack> temporaryPseudoOutputs) {
        this.windowId = windowId;
        this.recipeId = recipeId;
        this.ingredientTemplates = copyIngredientTemplates(ingredientTemplates);
        this.craftMissing = craftMissing;
        this.temporaryPseudoInputs = copyGenericStacks(temporaryPseudoInputs, AEProcessingPattern.MAX_INPUT_SLOTS);
        this.temporaryPseudoOutputs = copyGenericStacks(temporaryPseudoOutputs, AEProcessingPattern.MAX_OUTPUT_SLOTS);
    }

    private static List<List<ItemStack>> createEmptyIngredientTemplates() {
        List<List<ItemStack>> ingredientTemplates = new ObjectArrayList<>(CRAFTING_GRID_SIZE);
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            ingredientTemplates.add(new ObjectArrayList<>());
        }
        return ingredientTemplates;
    }

    private static List<List<ItemStack>> convertSingleTemplates(NonNullList<ItemStack> ingredientTemplates) {
        Preconditions.checkArgument(ingredientTemplates.size() == CRAFTING_GRID_SIZE,
            "Got %s ingredient templates from client, expected %s",
            ingredientTemplates.size(), CRAFTING_GRID_SIZE);
        List<List<ItemStack>> result = createEmptyIngredientTemplates();
        for (int i = 0; i < ingredientTemplates.size(); i++) {
            ItemStack stack = ingredientTemplates.get(i);
            if (!stack.isEmpty()) {
                result.get(i).add(stack.copy());
            }
        }
        return result;
    }

    private static List<List<ItemStack>> copyIngredientTemplates(List<List<ItemStack>> ingredientTemplates) {
        Preconditions.checkArgument(ingredientTemplates.size() == CRAFTING_GRID_SIZE,
            "Got %s ingredient template slots from client, expected %s",
            ingredientTemplates.size(), CRAFTING_GRID_SIZE);
        List<List<ItemStack>> result = createEmptyIngredientTemplates();
        for (int i = 0; i < ingredientTemplates.size(); i++) {
            List<ItemStack> slotTemplates = ingredientTemplates.get(i);
            if (slotTemplates == null) {
                continue;
            }
            List<ItemStack> copiedSlotTemplates = result.get(i);
            var seenKeys = new ObjectOpenHashSet<AEItemKey>();
            for (ItemStack stack : slotTemplates) {
                AEItemKey key = stack != null && !stack.isEmpty() ? AEItemKey.of(stack) : null;
                if (key != null && seenKeys.add(key)) {
                    copiedSlotTemplates.add(stack.copy());
                    if (copiedSlotTemplates.size() >= MAX_INGREDIENT_ALTERNATIVES) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static List<GenericStack> copyGenericStacks(List<GenericStack> stacks, int maxSize) {
        List<GenericStack> result = new ObjectArrayList<>(Math.min(stacks.size(), maxSize));
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0
                && stack.amount() <= PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT) {
                result.add(stack);
                if (result.size() >= maxSize) {
                    break;
                }
            }
        }
        return result;
    }

    private static List<GenericStack> readGenericStacks(PacketBuffer packetBuffer, int maxSize) {
        if (!packetBuffer.readBoolean()) {
            return List.of();
        }

        int size = packetBuffer.readVarInt();
        if (size < 0 || size > maxSize) {
            return null;
        }

        List<GenericStack> result = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            GenericStack stack;
            try {
                stack = GenericStack.readBuffer(packetBuffer);
            } catch (RuntimeException e) {
                return null;
            }
            if (stack == null || stack.amount() <= 0
                || stack.amount() > PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT) {
                return null;
            }
            result.add(stack);
        }
        return result;
    }

    private static void writeGenericStacks(PacketBuffer packetBuffer, List<GenericStack> stacks) {
        packetBuffer.writeBoolean(!stacks.isEmpty());
        if (stacks.isEmpty()) {
            return;
        }

        packetBuffer.writeVarInt(stacks.size());
        for (GenericStack stack : stacks) {
            GenericStack.writeBuffer(stack, packetBuffer);
        }
    }

    private static List<ItemStack> deduplicateTemplates(List<ItemStack> slotTemplates) {
        var seenKeys = new ObjectOpenHashSet<AEItemKey>();
        List<ItemStack> result = new ObjectArrayList<>(slotTemplates.size());
        for (ItemStack stack : slotTemplates) {
            AEItemKey key = stack != null && !stack.isEmpty() ? AEItemKey.of(stack) : null;
            if (key != null && seenKeys.add(key)) {
                result.add(stack);
            }
        }
        return result;
    }

    @Override
    protected void read(ByteBuf buf) {
        if (buf.readableBytes() > MAX_PACKET_BYTES) {
            invalidateRequest(buf, new IllegalArgumentException("Recipe transfer packet exceeds maximum size"));
            return;
        }

        try {
            PacketBuffer packetBuffer = new PacketBuffer(buf);
            this.windowId = packetBuffer.readInt();
            this.recipeId = packetBuffer.readBoolean() ? packetBuffer.readResourceLocation() : null;
            int size = packetBuffer.readInt();
            if (size != CRAFTING_GRID_SIZE) {
                invalidateRequest(buf, new IllegalArgumentException("Invalid recipe transfer grid size"));
                return;
            }
            this.ingredientTemplates = createEmptyIngredientTemplates();
            int totalAlternatives = 0;
            for (int i = 0; i < size; i++) {
                int alternatives = packetBuffer.readInt();
                if (alternatives < 0 || alternatives > MAX_INGREDIENT_ALTERNATIVES) {
                    invalidateRequest(buf, new IllegalArgumentException("Invalid recipe transfer alternative count"));
                    return;
                }
                totalAlternatives += alternatives;
                if (totalAlternatives > MAX_TOTAL_INGREDIENT_ALTERNATIVES) {
                    invalidateRequest(buf, new IllegalArgumentException("Too many recipe transfer alternatives"));
                    return;
                }
                List<ItemStack> slotTemplates = this.ingredientTemplates.get(i);
                for (int j = 0; j < alternatives; j++) {
                    ItemStack stack = packetBuffer.readItemStack();
                    if (!stack.isEmpty()) {
                        slotTemplates.add(stack);
                    }
                }
            }
            this.craftMissing = packetBuffer.readBoolean();
            this.temporaryPseudoInputs = readGenericStacks(packetBuffer, AEProcessingPattern.MAX_INPUT_SLOTS);
            if (this.temporaryPseudoInputs == null) {
                invalidateRequest(buf, new IllegalArgumentException("Invalid recipe transfer pseudo inputs"));
                return;
            }
            this.temporaryPseudoOutputs = readGenericStacks(packetBuffer, AEProcessingPattern.MAX_OUTPUT_SLOTS);
            if (this.temporaryPseudoOutputs == null) {
                invalidateRequest(buf, new IllegalArgumentException("Invalid recipe transfer pseudo outputs"));
                return;
            }
            if (packetBuffer.isReadable()) {
                invalidateRequest(buf, new IllegalArgumentException("Trailing recipe transfer payload bytes: "
                    + packetBuffer.readableBytes()));
            }
        } catch (IOException | RuntimeException e) {
            invalidateRequest(buf, e);
        }
    }

    private void invalidateRequest(ByteBuf buf, Exception exception) {
        this.recipeId = null;
        this.ingredientTemplates = createEmptyIngredientTemplates();
        this.craftMissing = false;
        this.temporaryPseudoInputs = List.of();
        this.temporaryPseudoOutputs = List.of();
        this.invalidRequest = true;
        invalidateMalformed(buf, exception instanceof RuntimeException runtimeException
            ? runtimeException
            : new IllegalArgumentException("Could not read recipe transfer packet", exception));
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.windowId);
        packetBuffer.writeBoolean(this.recipeId != null);
        if (this.recipeId != null) {
            packetBuffer.writeResourceLocation(this.recipeId);
        }
        packetBuffer.writeInt(this.ingredientTemplates.size());
        for (List<ItemStack> slotTemplates : this.ingredientTemplates) {
            packetBuffer.writeInt(slotTemplates.size());
            for (ItemStack stack : slotTemplates) {
                packetBuffer.writeItemStack(stack);
            }
        }
        packetBuffer.writeBoolean(this.craftMissing);
        writeGenericStacks(packetBuffer, this.temporaryPseudoInputs);
        writeGenericStacks(packetBuffer, this.temporaryPseudoOutputs);
    }

    private ItemStack growIngredientFromNetwork(ICraftingGridContainer container, IEnergySource energy,
                                                MEStorage networkStorage, KeyCounter cachedStorage,
                                                IPartitionList filter, Ingredient ingredient, ItemStack currentItem,
                                                int targetAmount) {
        int missing = targetAmount - currentItem.getCount();
        if (missing <= 0) {
            return currentItem;
        }

        ItemStack result = currentItem.copy();
        for (AEItemKey what : findBestMatchingItemStack(ingredient, filter, cachedStorage)) {
            ItemStack candidate = what.toStack();
            if (!result.isEmpty() && (!ItemStack.areItemsEqual(result, candidate)
                || !ItemStack.areItemStackTagsEqual(result, candidate))) {
                continue;
            }
            long extracted = StorageHelper.poweredExtraction(energy, networkStorage, what, missing,
                container.getActionSource());
            if (extracted <= 0) {
                continue;
            }

            ItemStack extractedStack = what.toStack(Ints.saturatedCast(extracted));
            if (result.isEmpty()) {
                result = extractedStack;
            } else if (ItemStack.areItemsEqual(result, extractedStack)
                && ItemStack.areItemStackTagsEqual(result, extractedStack)) {
                result.grow(extractedStack.getCount());
            }

            missing = targetAmount - result.getCount();
            if (missing <= 0) {
                break;
            }
        }
        return result;
    }

    private ItemStack growIngredientFromPlayer(ICraftingGridContainer container, EntityPlayerMP player,
                                               Ingredient ingredient, ItemStack currentItem, int targetAmount) {
        ItemStack result = currentItem.copy();
        int missing = targetAmount - result.getCount();
        if (missing <= 0) {
            return result;
        }

        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            if (container.isPlayerInventorySlotLocked(i)) {
                continue;
            }

            ItemStack item = player.inventory.mainInventory.get(i);
            if (!item.isEmpty() && ingredient.apply(item)) {
                if (!result.isEmpty() && (!ItemStack.areItemsEqual(result, item)
                    || !ItemStack.areItemStackTagsEqual(result, item))) {
                    continue;
                }
                ItemStack taken = item.splitStack(Math.min(missing, item.getCount()));
                if (result.isEmpty()) {
                    result = taken;
                } else {
                    result.grow(taken.getCount());
                }
                missing = targetAmount - result.getCount();
                if (missing <= 0) {
                    break;
                }
            }
        }
        return result;
    }

    private ItemStack takeIngredientFromPlayer(ICraftingGridContainer container, EntityPlayerMP player,
                                               Ingredient ingredient, int targetAmount) {
        return growIngredientFromPlayer(container, player, ingredient, ItemStack.EMPTY, targetAmount);
    }

    private int getDesiredAmount(int slot) {
        if (slot < 0 || slot >= this.ingredientTemplates.size()) {
            return 1;
        }

        for (ItemStack stack : this.ingredientTemplates.get(slot)) {
            if (stack != null && !stack.isEmpty()) {
                return Math.clamp(stack.getCount(), 1, 64);
            }
        }
        return 1;
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.invalidRequest) {
            return;
        }
        if (!(player.openContainer instanceof AEBaseContainer baseContainer)) {
            return;
        }
        if (baseContainer.windowId != this.windowId) {
            return;
        }
        if (!(baseContainer instanceof ICraftingGridContainer container)) {
            return;
        }
        IEnergySource energy = container.getEnergySource();
        ICraftingService craftingService;
        IStorageService storageService;
        MEStorage networkStorage;
        KeyCounter cachedStorage;

        IGridNode node = container.getGridNode();
        if (node != null && container.getLinkStatus().connected()) {
            craftingService = node.grid().getCraftingService();
            storageService = node.grid().getStorageService();
            networkStorage = storageService.getInventory();
            cachedStorage = storageService.getCachedInventory();
        } else {
            craftingService = null;
            storageService = null;
            networkStorage = NullInventory.of();
            cachedStorage = new KeyCounter();
        }

        InternalInventory craftMatrix = container.getCraftingMatrix();
        IPartitionList filter = ViewCellItem.createItemFilter(container.getViewCells());
        NonNullList<Ingredient> ingredients = getDesiredIngredients();
        boolean useTemporaryPseudoCraft = this.craftMissing
            && !this.temporaryPseudoInputs.isEmpty()
            && !this.temporaryPseudoOutputs.isEmpty();

        Object2ObjectMap<AEItemKey, AutoCraftRequest> toAutoCraft = new Object2ObjectLinkedOpenHashMap<>();
        boolean touchedGridStorage = false;

        int slotsToFill = Math.min(craftMatrix.size(), ingredients.size());
        for (int slot = 0; slot < slotsToFill; slot++) {
            ItemStack currentItem = craftMatrix.getStackInSlot(slot);
            Ingredient ingredient = ingredients.get(slot);
            int targetAmount = ingredient == Ingredient.EMPTY ? 1 : getDesiredAmount(slot);

            if (!currentItem.isEmpty()) {
                if (ingredient.apply(currentItem) && currentItem.getCount() >= targetAmount) {
                    continue;
                }

                if (!ingredient.apply(currentItem)) {
                    AEItemKey in = AEItemKey.of(currentItem);
                    long inserted = in != null
                        ? StorageHelper.poweredInsert(energy, networkStorage, in, currentItem.getCount(),
                        container.getActionSource())
                        : 0;
                    if (inserted > 0) {
                        touchedGridStorage = true;
                    }
                    if (inserted < currentItem.getCount()) {
                        currentItem = currentItem.copy();
                        currentItem.shrink((int) inserted);
                    } else {
                        currentItem = ItemStack.EMPTY;
                    }
                    if (!currentItem.isEmpty()) {
                        player.inventory.addItemStackToInventory(currentItem);
                    }
                    craftMatrix.setItemDirect(slot, currentItem.isEmpty() ? ItemStack.EMPTY : currentItem);
                }
            }

            if (ingredient == Ingredient.EMPTY) {
                continue;
            }

            if (!currentItem.isEmpty() && ingredient.apply(currentItem) && currentItem.getCount() < targetAmount) {
                int beforeCount = currentItem.getCount();
                currentItem = growIngredientFromNetwork(container, energy, networkStorage, cachedStorage, filter,
                    ingredient, currentItem, targetAmount);
                if (currentItem.getCount() > beforeCount) {
                    touchedGridStorage = true;
                }
                if (currentItem.getCount() < targetAmount) {
                    currentItem = growIngredientFromPlayer(container, player, ingredient, currentItem, targetAmount);
                }
                craftMatrix.setItemDirect(slot, currentItem);
                continue;
            }

            if (currentItem.isEmpty()) {
                for (AEItemKey what : findBestMatchingItemStack(ingredient, filter, cachedStorage)) {
                    long extracted = StorageHelper.poweredExtraction(energy, networkStorage, what, targetAmount,
                        container.getActionSource());
                    if (extracted > 0) {
                        touchedGridStorage = true;
                        currentItem = what.toStack(Ints.saturatedCast(extracted));
                        break;
                    }
                }
            }

            if (currentItem.isEmpty()) {
                currentItem = takeIngredientFromPlayer(container, player, ingredient, targetAmount);
            }

            craftMatrix.setItemDirect(slot, currentItem);

            int missingAmount = targetAmount - (!currentItem.isEmpty() && ingredient.apply(currentItem)
                ? currentItem.getCount()
                : 0);
            if (missingAmount > 0 && this.craftMissing && !useTemporaryPseudoCraft && craftingService != null) {
                final int craftSlot = slot;
                final int craftAmount = missingAmount;
                findCraftableKey(ingredient, craftingService).ifPresent(key -> {
                    AutoCraftRequest request = toAutoCraft.computeIfAbsent(key, ignored -> new AutoCraftRequest());
                    request.add(craftSlot, craftAmount);
                });
            }
        }

        if (player.openContainer == baseContainer) {
            baseContainer.onCraftMatrixChanged(craftMatrix.toContainer());
        }

        if (useTemporaryPseudoCraft && craftingService != null) {
            if (touchedGridStorage && storageService != null) {
                storageService.invalidateCache();
            }

            container.startTemporaryPseudoCrafting(this.temporaryPseudoInputs, this.temporaryPseudoOutputs);
            return;
        }

        if (!toAutoCraft.isEmpty()) {
            if (touchedGridStorage && storageService != null) {
                storageService.invalidateCache();
            }

            List<ICraftingGridContainer.AutoCraftEntry> stacks = new ObjectArrayList<>(
                toAutoCraft.size());
            for (Object2ObjectMap.Entry<AEItemKey, AutoCraftRequest> entry : toAutoCraft.object2ObjectEntrySet()) {
                AutoCraftRequest request = entry.getValue();
                stacks.add(new ICraftingGridContainer.AutoCraftEntry(entry.getKey(), request.amount(), request.slots()));
            }
            container.startAutoCrafting(stacks);
        }
    }

    private NonNullList<Ingredient> getDesiredIngredients() {
        if (this.recipeId != null) {
            IRecipe recipe = CraftingManager.REGISTRY.getObject(this.recipeId);
            if (recipe != null) {
                return CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
            }
        }

        NonNullList<Ingredient> ingredients = NonNullList.withSize(CRAFTING_GRID_SIZE, Ingredient.EMPTY);
        Preconditions.checkArgument(ingredients.size() == this.ingredientTemplates.size(),
            "Got %s ingredient template slots from client, expected %s",
            this.ingredientTemplates.size(), ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            List<ItemStack> slotTemplates = this.ingredientTemplates.get(i);
            if (!slotTemplates.isEmpty()) {
                List<ItemStack> deduplicated = deduplicateTemplates(slotTemplates);
                if (!deduplicated.isEmpty()) {
                    ingredients.set(i, Ingredient.fromStacks(deduplicated.toArray(EmptyArrays.EMPTY_ITEM_STACK_ARRAY)));
                }
            }
        }
        return ingredients;
    }

    private List<AEItemKey> findBestMatchingItemStack(Ingredient ingredient, IPartitionList filter, KeyCounter storage) {
        var result = new Object2ObjectOpenHashMap<AEItemKey, Object2LongMap.Entry<AEItemKey>>();
        for (ItemStack stack : ingredient.getMatchingStacks()) {
            AEItemKey key = AEItemKey.of(stack);
            if (key == null || filter != null && !filter.isListed(key)) {
                continue;
            }
            for (Object2LongMap.Entry<AEKey> entry : storage.findFuzzy(key, FuzzyMode.IGNORE_ALL)) {
                AEKey foundKey = entry.getKey();
                if (foundKey instanceof AEItemKey foundItemKey && foundItemKey.matches(ingredient)) {
                    result.merge(foundItemKey,
                        new AbstractObject2LongMap.BasicEntry<>(foundItemKey, entry.getLongValue()),
                        (left, right) -> left.getLongValue() >= right.getLongValue() ? left : right);
                }
            }
        }
        List<Object2LongMap.Entry<AEItemKey>> entries = new ObjectArrayList<>(result.values());
        entries.sort((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()));

        List<AEItemKey> keys = new ObjectArrayList<>(entries.size());
        for (Object2LongMap.Entry<AEItemKey> entry : entries) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    private Optional<AEItemKey> findCraftableKey(Ingredient ingredient, ICraftingService craftingService) {
        for (ItemStack stack : ingredient.getMatchingStacks()) {
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }
            AEKey craftable = craftingService.getFuzzyCraftable(key,
                candidate -> candidate instanceof AEItemKey && ((AEItemKey) candidate).matches(ingredient));
            if (craftable instanceof AEItemKey) {
                return Optional.of((AEItemKey) craftable);
            }
        }
        return Optional.empty();
    }

    private static final class AutoCraftRequest {
        private final IntArrayList slots = new IntArrayList();
        private long amount;

        void add(int slot, int amount) {
            this.slots.add(slot);
            this.amount += amount;
        }

        long amount() {
            return this.amount;
        }

        IntList slots() {
            return this.slots;
        }
    }
}
