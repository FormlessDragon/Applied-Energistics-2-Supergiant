package ae2.core.network.serverbound;

import ae2.api.config.Actionable;
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
import ae2.util.Platform;
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
import org.jetbrains.annotations.Nullable;

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

    private static boolean canMergeIntoIngredientStack(ItemStack currentItem, ItemStack candidate) {
        return currentItem.isEmpty()
            || ItemStack.areItemsEqual(currentItem, candidate)
            && ItemStack.areItemStackTagsEqual(currentItem, candidate);
    }

    private ItemStack growIngredientFromGridCache(List<ItemStack> gridCache, Ingredient ingredient,
                                                  ItemStack currentItem, int targetAmount) {
        int missing = targetAmount - currentItem.getCount();
        if (missing <= 0) {
            return currentItem;
        }

        ItemStack result = currentItem.copy();
        for (ItemStack cachedStack : gridCache) {
            if (cachedStack.isEmpty() || !ingredient.apply(cachedStack)
                || !canMergeIntoIngredientStack(result, cachedStack)) {
                continue;
            }

            int taken = Math.min(missing, cachedStack.getCount());
            ItemStack takenStack = cachedStack.copy();
            takenStack.setCount(taken);
            if (result.isEmpty()) {
                result = takenStack;
            } else {
                result.grow(taken);
            }
            cachedStack.shrink(taken);

            missing = targetAmount - result.getCount();
            if (missing <= 0) {
                break;
            }
        }
        return result;
    }

    private ItemStack growIngredientFromNetworkPlan(KeyCounter networkAvailable, KeyCounter networkRequests,
                                                    IPartitionList filter, Ingredient ingredient,
                                                    ItemStack currentItem, int targetAmount) {
        int missing = targetAmount - currentItem.getCount();
        if (missing <= 0) {
            return currentItem;
        }

        ItemStack result = currentItem.copy();
        for (AEItemKey what : findBestMatchingItemStack(ingredient, filter, networkAvailable)) {
            ItemStack candidate = what.toStack();
            if (!canMergeIntoIngredientStack(result, candidate)) {
                continue;
            }

            int taken = Ints.saturatedCast(Math.min(missing, networkAvailable.get(what)));
            if (taken <= 0) {
                continue;
            }
            ItemStack extractedStack = what.toStack(taken);
            if (result.isEmpty()) {
                result = extractedStack;
            } else {
                result.grow(taken);
            }
            networkAvailable.remove(what, taken);
            networkRequests.add(what, taken);

            missing = targetAmount - result.getCount();
            if (missing <= 0) {
                break;
            }
        }
        return result;
    }

    private ItemStack growIngredientFromPlayerPlan(ICraftingGridContainer container, EntityPlayerMP player,
                                                   int[] reservedPlayerItems, List<PlayerTake> playerTakes,
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
            int available = item.getCount() - reservedPlayerItems[i];
            if (!item.isEmpty() && available > 0 && ingredient.apply(item)
                && canMergeIntoIngredientStack(result, item)) {
                int taken = Math.min(missing, available);
                ItemStack takenStack = item.copy();
                takenStack.setCount(taken);
                if (result.isEmpty()) {
                    result = takenStack;
                } else {
                    result.grow(taken);
                }
                reservedPlayerItems[i] += taken;
                playerTakes.add(new PlayerTake(i, taken));
                missing = targetAmount - result.getCount();
                if (missing <= 0) {
                    break;
                }
            }
        }
        return result;
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

    @Nullable
    private PlannedTransfer planTransfer(ICraftingGridContainer container, EntityPlayerMP player,
                                         InternalInventory craftMatrix, KeyCounter cachedStorage,
                                         IPartitionList filter, NonNullList<Ingredient> ingredients,
                                         boolean useTemporaryPseudoCraft, @Nullable ICraftingService craftingService) {
        NonNullList<ItemStack> plannedGrid = NonNullList.withSize(craftMatrix.size(), ItemStack.EMPTY);
        List<ItemStack> gridCache = new ObjectArrayList<>(craftMatrix.size());
        for (int slot = 0; slot < craftMatrix.size(); slot++) {
            ItemStack stack = craftMatrix.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                gridCache.add(stack.copy());
            }
        }

        KeyCounter networkAvailable = new KeyCounter();
        networkAvailable.addAll(cachedStorage);
        KeyCounter networkRequests = new KeyCounter();
        int[] reservedPlayerItems = new int[player.inventory.mainInventory.size()];
        List<PlayerTake> playerTakes = new ObjectArrayList<>();
        Object2ObjectMap<AEItemKey, AutoCraftRequest> toAutoCraft = new Object2ObjectLinkedOpenHashMap<>();

        int slotsToFill = Math.min(craftMatrix.size(), ingredients.size());
        for (int slot = 0; slot < slotsToFill; slot++) {
            Ingredient ingredient = ingredients.get(slot);
            if (ingredient == Ingredient.EMPTY) {
                continue;
            }

            int targetAmount = getDesiredAmount(slot);
            ItemStack plannedItem = ItemStack.EMPTY;
            plannedItem = growIngredientFromGridCache(gridCache, ingredient, plannedItem, targetAmount);
            plannedItem = growIngredientFromNetworkPlan(networkAvailable, networkRequests, filter, ingredient,
                plannedItem, targetAmount);
            plannedItem = growIngredientFromPlayerPlan(container, player, reservedPlayerItems, playerTakes,
                ingredient, plannedItem, targetAmount);
            plannedGrid.set(slot, plannedItem);

            int missingAmount = targetAmount - (!plannedItem.isEmpty() && ingredient.apply(plannedItem)
                ? plannedItem.getCount()
                : 0);
            if (missingAmount <= 0) {
                continue;
            }

            boolean scheduledCraft = false;
            if (this.craftMissing && !useTemporaryPseudoCraft && craftingService != null) {
                Optional<AEItemKey> craftableKey = findCraftableKey(ingredient, craftingService);
                if (craftableKey.isPresent()) {
                    AutoCraftRequest request = toAutoCraft.computeIfAbsent(craftableKey.get(),
                        ignored -> new AutoCraftRequest());
                    request.add(slot, missingAmount);
                    scheduledCraft = true;
                }
            }
            if (!scheduledCraft && !useTemporaryPseudoCraft) {
                return null;
            }
        }

        gridCache.removeIf(ItemStack::isEmpty);
        return new PlannedTransfer(plannedGrid, gridCache, networkRequests, playerTakes, toAutoCraft);
    }

    private boolean canApplyPlayerTakes(EntityPlayerMP player, List<PlayerTake> playerTakes) {
        int[] required = new int[player.inventory.mainInventory.size()];
        for (PlayerTake take : playerTakes) {
            if (take.slot < 0 || take.slot >= player.inventory.mainInventory.size()) {
                return false;
            }
            required[take.slot] += take.amount;
        }

        for (int slot = 0; slot < required.length; slot++) {
            if (required[slot] <= 0) {
                continue;
            }
            ItemStack stack = player.inventory.mainInventory.get(slot);
            if (stack.isEmpty() || stack.getCount() < required[slot]) {
                return false;
            }
        }
        return true;
    }

    private void applyPlayerTakes(EntityPlayerMP player, List<PlayerTake> playerTakes) {
        for (PlayerTake take : playerTakes) {
            ItemStack stack = player.inventory.mainInventory.get(take.slot);
            stack.shrink(take.amount);
            if (stack.isEmpty()) {
                player.inventory.mainInventory.set(take.slot, ItemStack.EMPTY);
            }
        }
    }

    @Nullable
    private List<NetworkExtraction> extractPlannedNetworkRequests(ICraftingGridContainer container,
                                                                  IEnergySource energy,
                                                                  MEStorage networkStorage,
                                                                  KeyCounter networkRequests,
                                                                  EntityPlayerMP player) {
        for (Object2LongMap.Entry<AEKey> entry : networkRequests) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            long available = StorageHelper.poweredExtraction(energy, networkStorage, entry.getKey(), amount,
                container.getActionSource(), Actionable.SIMULATE);
            if (available < amount) {
                return null;
            }
        }

        List<NetworkExtraction> extracted = new ObjectArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : networkRequests) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            long extractedAmount = StorageHelper.poweredExtraction(energy, networkStorage, entry.getKey(), amount,
                container.getActionSource(), Actionable.MODULATE);
            if (extractedAmount < amount) {
                returnExtractedNetworkItems(container, energy, networkStorage, player, extracted);
                if (extractedAmount > 0) {
                    returnExtractedNetworkItems(container, energy, networkStorage, player,
                        List.of(new NetworkExtraction(entry.getKey(), extractedAmount)));
                }
                return null;
            }
            extracted.add(new NetworkExtraction(entry.getKey(), extractedAmount));
        }
        return extracted;
    }

    private void returnExtractedNetworkItems(ICraftingGridContainer container, IEnergySource energy,
                                             MEStorage networkStorage, EntityPlayerMP player,
                                             List<NetworkExtraction> extractions) {
        for (NetworkExtraction extraction : extractions) {
            if (extraction.amount <= 0 || !(extraction.what instanceof AEItemKey itemKey)) {
                continue;
            }
            recycleStack(container, energy, networkStorage, player, itemKey.toStack(Ints.saturatedCast(extraction.amount)));
        }
    }

    private boolean recycleGridCache(ICraftingGridContainer container, IEnergySource energy, MEStorage networkStorage,
                                     EntityPlayerMP player, List<ItemStack> gridCache) {
        boolean touchedGridStorage = false;
        for (ItemStack stack : gridCache) {
            if (!stack.isEmpty() && recycleStack(container, energy, networkStorage, player, stack.copy())) {
                touchedGridStorage = true;
            }
        }
        return touchedGridStorage;
    }

    private boolean recycleStack(ICraftingGridContainer container, IEnergySource energy, MEStorage networkStorage,
                                 EntityPlayerMP player, ItemStack stack) {
        boolean touchedGridStorage = false;
        AEItemKey key = AEItemKey.of(stack);
        if (key != null) {
            long inserted = StorageHelper.poweredInsert(energy, networkStorage, key, stack.getCount(),
                container.getActionSource());
            if (inserted > 0) {
                touchedGridStorage = true;
                stack.shrink(Ints.saturatedCast(inserted));
            }
        }
        if (!stack.isEmpty()) {
            player.inventory.addItemStackToInventory(stack);
        }
        if (!stack.isEmpty()) {
            Platform.spawnDrops(player.world, player.getPosition(), List.of(stack.copy()));
            stack.setCount(0);
        }
        return touchedGridStorage;
    }

    private TransferCommit applyPlannedTransfer(ICraftingGridContainer container, EntityPlayerMP player,
                                                IEnergySource energy, MEStorage networkStorage,
                                                InternalInventory craftMatrix, PlannedTransfer plannedTransfer) {
        if (!canApplyPlayerTakes(player, plannedTransfer.playerTakes)) {
            return TransferCommit.failed();
        }
        List<NetworkExtraction> extractedNetwork = extractPlannedNetworkRequests(container, energy, networkStorage,
            plannedTransfer.networkRequests, player);
        if (extractedNetwork == null) {
            return TransferCommit.failed();
        }

        applyPlayerTakes(player, plannedTransfer.playerTakes);
        for (int slot = 0; slot < craftMatrix.size(); slot++) {
            craftMatrix.setItemDirect(slot, ItemStack.EMPTY);
        }
        boolean recycledToNetwork = recycleGridCache(container, energy, networkStorage, player,
            plannedTransfer.gridCacheRemainders);
        for (int slot = 0; slot < Math.min(craftMatrix.size(), plannedTransfer.grid.size()); slot++) {
            craftMatrix.setItemDirect(slot, plannedTransfer.grid.get(slot).copy());
        }
        return new TransferCommit(true, !plannedTransfer.networkRequests.isEmpty() || recycledToNetwork);
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
        boolean useTemporaryPseudoCraft = craftingService != null && this.craftMissing
            && !this.temporaryPseudoInputs.isEmpty()
            && !this.temporaryPseudoOutputs.isEmpty();

        PlannedTransfer plannedTransfer = planTransfer(container, player, craftMatrix, cachedStorage, filter,
            ingredients, useTemporaryPseudoCraft, craftingService);
        if (plannedTransfer == null) {
            return;
        }
        TransferCommit transferCommit = applyPlannedTransfer(container, player, energy, networkStorage, craftMatrix,
            plannedTransfer);
        if (!transferCommit.success) {
            return;
        }

        if (player.openContainer == baseContainer) {
            baseContainer.onCraftMatrixChanged(craftMatrix.toContainer());
        }

        if (useTemporaryPseudoCraft) {
            if (transferCommit.touchedGridStorage && storageService != null) {
                storageService.invalidateCache();
            }

            container.startTemporaryPseudoCrafting(this.temporaryPseudoInputs, this.temporaryPseudoOutputs);
            return;
        }

        if (!plannedTransfer.toAutoCraft.isEmpty()) {
            if (transferCommit.touchedGridStorage && storageService != null) {
                storageService.invalidateCache();
            }

            List<ICraftingGridContainer.AutoCraftEntry> stacks = new ObjectArrayList<>(
                plannedTransfer.toAutoCraft.size());
            for (Object2ObjectMap.Entry<AEItemKey, AutoCraftRequest> entry : plannedTransfer.toAutoCraft.object2ObjectEntrySet()) {
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

    private record PlannedTransfer(NonNullList<ItemStack> grid, List<ItemStack> gridCacheRemainders,
                                   KeyCounter networkRequests, List<PlayerTake> playerTakes,
                                   Object2ObjectMap<AEItemKey, AutoCraftRequest> toAutoCraft) {
    }

    private record PlayerTake(int slot, int amount) {
    }

    private record NetworkExtraction(AEKey what, long amount) {
    }

    private record TransferCommit(boolean success, boolean touchedGridStorage) {
        private static TransferCommit failed() {
            return new TransferCommit(false, false);
        }
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
