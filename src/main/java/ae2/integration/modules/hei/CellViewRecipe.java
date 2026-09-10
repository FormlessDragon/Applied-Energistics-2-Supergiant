package ae2.integration.modules.hei;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.StorageCell;
import ae2.api.storage.cells.StorageCellStatistics;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class CellViewRecipe implements IRecipeWrapper {
    static final int PAGE_SIZE = 63;

    /**
     * Bounds work after cell enumeration. The 9x7 layout turns this into at most 66 JEI pages.
     */
    static final int MAX_VIEW_TYPES = 4096;

    private static final Comparator<SortableStack> BASE_CONTENT_ORDER = (left, right) -> {
        int comparison = Long.compare(right.stack().amount(), left.stack().amount());
        if (comparison != 0) {
            return comparison;
        }

        comparison = left.typeId().compareTo(right.typeId());
        if (comparison != 0) {
            return comparison;
        }

        return left.resourceId().compareTo(right.resourceId());
    };

    private static final Comparator<SerializedSortableStack> SERIALIZED_CONTENT_ORDER = Comparator
        .comparing(SerializedSortableStack::serializedKey)
        .thenComparing(SerializedSortableStack::className);

    private static final CellViewRecipe PLACEHOLDER = new CellViewRecipe(List.of(), 0, FormattedText.EMPTY);

    private final List<GenericStack> contents;
    private final int slotCount;
    private final FormattedText text;

    private CellViewRecipe(List<GenericStack> contents, int slotCount, FormattedText text) {
        this.contents = List.copyOf(contents);
        this.slotCount = slotCount;
        this.text = text;
    }

    static List<CellViewRecipe> createPages(ItemStack cell) {
        StorageCell inventory;
        try {
            inventory = StorageCells.getCellInventory(cell, null);
        } catch (RuntimeException | LinkageError e) {
            AELog.warn(e, "Failed to read storage cell contents for HEI: %s", cell);
            return List.of();
        }
        if (inventory == null) {
            AELog.warn("A registered storage cell did not provide an inventory for HEI: %s", cell);
            return List.of();
        }

        Statistics statistics = null;
        if (inventory instanceof StorageCellStatistics cellStatistics) {
            try {
                statistics = Statistics.copyOf(cellStatistics);
                statistics.validateStoredTypeLimit();
            } catch (RuntimeException | LinkageError e) {
                AELog.warn(e, "Storage cell returned invalid statistics for HEI: %s", cell);
                return List.of();
            }
        }

        var available = new KeyCounter();
        try {
            // The legacy API fills a caller-owned KeyCounter and cannot stop midway. Statistics allow rejecting known
            // oversized cells before this call; sources without statistics can only be rejected after it returns.
            inventory.getAvailableStacks(available);
        } catch (RuntimeException | LinkageError e) {
            AELog.warn(e, "Failed to enumerate storage cell contents for HEI: %s", cell);
            return List.of();
        }
        if (available.size() > MAX_VIEW_TYPES) {
            AELog.warn("Storage cell exposes %d resource entries to HEI, exceeding the limit of %d: %s",
                available.size(), MAX_VIEW_TYPES, cell);
            return List.of();
        }

        var contents = new ObjectArrayList<GenericStack>(available.size());
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (entry.getKey() == null || entry.getLongValue() <= 0) {
                AELog.warn("Storage cell exposed an invalid HEI resource entry (%s, %d): %s",
                    entry.getKey(), entry.getLongValue(), cell);
                return List.of();
            }
            contents.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }

        try {
            Pagination pagination = paginate(contents, statistics);
            FormattedText text = FormattedText.create(pagination.totalStoredTypes(), statistics);
            var recipes = new ObjectArrayList<CellViewRecipe>(pagination.pages().size());
            for (int i = 0; i < pagination.pages().size(); i++) {
                PageLayout page = pagination.pages().get(i);
                recipes.add(new CellViewRecipe(page.contents(), page.slotCount(), text));
            }
            return Collections.unmodifiableList(recipes);
        } catch (RuntimeException | LinkageError e) {
            AELog.warn(e, "Storage cell returned inconsistent contents for HEI: %s", cell);
            return List.of();
        }
    }

    static Pagination paginate(List<GenericStack> contents, @Nullable Statistics statistics) {
        List<GenericStack> sorted = sortContents(contents);
        long storedTypes = sorted.size();
        if (statistics != null) {
            statistics.validateEnumeratedTypes(storedTypes);
        }
        long slotCapacity = statistics == null ? storedTypes : statistics.totalTypes();
        validateLayout(storedTypes, slotCapacity);

        int pageCount = pageCountForLayout(storedTypes, slotCapacity);
        var pages = new ObjectArrayList<PageLayout>(pageCount);
        for (int page = 0; page < pageCount; page++) {
            int fromIndex = page * PAGE_SIZE;
            int toIndex = Math.min(fromIndex + PAGE_SIZE, sorted.size());
            List<GenericStack> pageContents = fromIndex < toIndex
                ? Collections.unmodifiableList(sorted.subList(fromIndex, toIndex))
                : List.of();
            pages.add(new PageLayout(pageContents, slotCountForPage(slotCapacity, page)));
        }
        return new Pagination(Collections.unmodifiableList(pages), storedTypes);
    }

    static List<GenericStack> sortContents(List<GenericStack> contents) {
        if (contents.size() > MAX_VIEW_TYPES) {
            throw new IllegalArgumentException("Stored type count exceeds HEI cell view limit: " + contents.size());
        }

        var sorted = new ObjectArrayList<SortableStack>(contents.size());
        for (int i = 0; i < contents.size(); i++) {
            GenericStack stack = contents.get(i);
            if (stack == null || stack.amount() <= 0) {
                throw new IllegalArgumentException("Cell view contents must contain positive, non-null stacks");
            }
            AEKey what = stack.what();
            ResourceLocation resourceId = what.getId();
            sorted.add(new SortableStack(
                stack,
                what.getType().getId().toString(),
                resourceId == null ? "" : resourceId.toString()));
        }
        sorted.sort(BASE_CONTENT_ORDER);

        int groupStart = 0;
        while (groupStart < sorted.size()) {
            int groupEnd = groupStart + 1;
            while (groupEnd < sorted.size()
                && BASE_CONTENT_ORDER.compare(sorted.get(groupStart), sorted.get(groupEnd)) == 0) {
                groupEnd++;
            }
            if (groupEnd - groupStart > 1) {
                sortSerializedGroup(sorted, groupStart, groupEnd);
            }
            groupStart = groupEnd;
        }

        var result = new ObjectArrayList<GenericStack>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            result.add(sorted.get(i).stack());
        }
        return Collections.unmodifiableList(result);
    }

    private static void sortSerializedGroup(ObjectArrayList<SortableStack> sorted, int fromIndex, int toIndex) {
        var group = new ObjectArrayList<SerializedSortableStack>(toIndex - fromIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            SortableStack entry = sorted.get(i);
            AEKey what = entry.stack().what();
            group.add(new SerializedSortableStack(entry, what.toTag().toString(), what.getClass().getName()));
        }
        group.sort(SERIALIZED_CONTENT_ORDER);

        int sameKeyStart = 0;
        while (sameKeyStart < group.size()) {
            int sameKeyEnd = sameKeyStart + 1;
            SerializedSortableStack first = group.get(sameKeyStart);
            while (sameKeyEnd < group.size()
                && SERIALIZED_CONTENT_ORDER.compare(first, group.get(sameKeyEnd)) == 0) {
                if (!first.entry().stack().what().equals(group.get(sameKeyEnd).entry().stack().what())) {
                    throw new IllegalArgumentException("AE keys of class " + first.className()
                        + " serialize identically but are not equal");
                }
                sameKeyEnd++;
            }
            sameKeyStart = sameKeyEnd;
        }

        for (int i = 0; i < group.size(); i++) {
            sorted.set(fromIndex + i, group.get(i).entry());
        }
    }

    static CellViewRecipe placeholder() {
        return PLACEHOLDER;
    }

    static int pageCountForTypeCount(long typeCount) {
        if (typeCount < 0 || typeCount > MAX_VIEW_TYPES) {
            throw new IllegalArgumentException("Type count is outside the HEI cell view limit: " + typeCount);
        }
        return typeCount == 0 ? 1 : (int) ((typeCount - 1) / PAGE_SIZE + 1);
    }

    static int pageCountForLayout(long storedTypes, long totalTypes) {
        validateLayout(storedTypes, totalTypes);
        return pageCountForTypeCount(storedTypes);
    }

    static int slotCountForPage(long totalTypes, int page) {
        if (totalTypes < 0 || page < 0) {
            throw new IllegalArgumentException("Storage cell layout values are outside the HEI cell view limit");
        }
        long firstType = (long) page * PAGE_SIZE;
        if (firstType >= totalTypes) {
            return 0;
        }
        return (int) Math.min(PAGE_SIZE, totalTypes - firstType);
    }

    private static void validateLayout(long storedTypes, long totalTypes) {
        if (storedTypes < 0 || storedTypes > MAX_VIEW_TYPES || totalTypes < storedTypes) {
            throw new IllegalArgumentException("Invalid HEI cell view layout: stored=" + storedTypes
                + ", total=" + totalTypes);
        }
    }

    @Override
    public void getIngredients(@NonNull IIngredients ingredients) {
        var wrapped = new ObjectArrayList<ItemStack>(this.contents.size());
        for (int i = 0; i < this.contents.size(); i++) {
            wrapped.add(GenericStack.wrapInItemStack(this.contents.get(i)));
        }
        ingredients.setInputs(VanillaTypes.ITEM, wrapped);
    }

    @Override
    public void drawInfo(@NonNull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        if (!this.text.firstLine().isEmpty()) {
            drawHeaderLine(minecraft, this.text.firstLine(), recipeWidth, this.text.firstLineY());
        }
        if (!this.text.secondLine().isEmpty()) {
            drawHeaderLine(minecraft, this.text.secondLine(), recipeWidth, 14);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (this.text.headerTooltip().isEmpty()
            || mouseX < 0 || mouseX >= CellViewRecipeCategory.WIDTH
            || mouseY < 0 || mouseY >= CellViewRecipeCategory.HEADER_HEIGHT) {
            return List.of();
        }
        return this.text.headerTooltip();
    }

    private static void drawHeaderLine(Minecraft minecraft, String text, int recipeWidth, int y) {
        minecraft.fontRenderer.drawString(
            minecraft.fontRenderer.trimStringToWidth(text, recipeWidth - 8), 4, y, 0x404040);
    }

    List<GenericStack> contents() {
        return this.contents;
    }

    int slotCount() {
        return this.slotCount;
    }

    record Statistics(long usedBytes, long totalBytes, long storedTypes, long totalTypes, int bytesPerType) {
        Statistics {
            if (usedBytes < 0 || totalBytes < 0 || storedTypes < 0 || totalTypes < 0 || bytesPerType < 0) {
                throw new IllegalArgumentException("Storage cell statistics must not be negative");
            }
            if (usedBytes > totalBytes) {
                throw new IllegalArgumentException("Used bytes exceed total bytes");
            }
            if (storedTypes > totalTypes) {
                throw new IllegalArgumentException("Stored types exceed total types");
            }
        }

        static Statistics copyOf(StorageCellStatistics statistics) {
            return new Statistics(
                statistics.getUsedBytes(),
                statistics.getTotalBytes(),
                statistics.getStoredTypes(),
                statistics.getTotalTypes(),
                statistics.getBytesPerType());
        }

        void validateStoredTypeLimit() {
            if (this.storedTypes > MAX_VIEW_TYPES) {
                throw new IllegalArgumentException("Stored type count exceeds HEI cell view limit: "
                    + this.storedTypes);
            }
        }

        void validateEnumeratedTypes(long actualTypes) {
            if (actualTypes != this.storedTypes) {
                throw new IllegalArgumentException("Reported stored types " + this.storedTypes
                    + " do not match enumerated types " + actualTypes);
            }
        }
    }

    record PageLayout(List<GenericStack> contents, int slotCount) {
    }

    record Pagination(List<PageLayout> pages, long totalStoredTypes) {
    }

    private record SortableStack(GenericStack stack, String typeId, String resourceId) {
    }

    private record SerializedSortableStack(SortableStack entry, String serializedKey, String className) {
    }

    private record FormattedText(String firstLine, int firstLineY, String secondLine,
                                 List<String> headerTooltip) {
        private static final FormattedText EMPTY = new FormattedText("", 0, "", List.of());

        static FormattedText create(long totalStoredTypes, @Nullable Statistics statistics) {
            NumberFormat numberFormat = NumberFormat.getIntegerInstance();
            if (statistics == null) {
                return new FormattedText(
                    GuiText.CellViewStoredTypes.getLocal(numberFormat.format(totalStoredTypes)),
                    9,
                    "",
                    List.of());
            }

            List<String> tooltip = List.of();
            if (statistics.bytesPerType() > 0) {
                long typeBytes = saturatedMultiply(statistics.storedTypes(), statistics.bytesPerType());
                tooltip = List.of(
                    GuiText.CellViewTypeCost.getLocal(numberFormat.format(statistics.bytesPerType())),
                    GuiText.CellViewTypeCostDetails.getLocal(
                        numberFormat.format(statistics.bytesPerType()),
                        numberFormat.format(statistics.storedTypes()),
                        numberFormat.format(typeBytes)));
            }
            return new FormattedText(
                GuiText.CellViewBytes.getLocal(
                    numberFormat.format(statistics.usedBytes()), numberFormat.format(statistics.totalBytes())),
                4,
                GuiText.CellViewTypes.getLocal(
                    numberFormat.format(statistics.storedTypes()), numberFormat.format(statistics.totalTypes())),
                tooltip);
        }

        private static long saturatedMultiply(long left, long right) {
            if (left <= 0 || right <= 0) {
                return 0;
            }
            return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
        }
    }
}
