package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalBusPartitionMode;
import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalCapability;
import ae2.api.cellterminal.CellTerminalCellSlotTarget;
import ae2.api.cellterminal.CellTerminalContentSnapshot;
import ae2.api.cellterminal.CellTerminalPartitionSnapshot;
import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.storage.cells.StorageCell;
import ae2.core.AELog;
import ae2.me.cells.CreativeCellInventory;
import ae2.me.cells.VoidCellInventory;
import ae2.me.helpers.BaseActionSource;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default conservative implementation of the Cell Terminal network-tool backend.
 */
public final class CellTerminalNetworkToolImpl implements CellTerminalNetworkTool {
    private static final String GUI_KEY_PREFIX = "gui.ae2.CellTerminal.";
    private static final String UNIQUE_TYPE_PREVIEW_TYPE_PREFIX =
        "gui.ae2.CellTerminal.networktools.attribute_unique.preview.type.";
    private final CellTerminalTargetLookup targetAccess;

    public CellTerminalNetworkToolImpl() {
        this(new CellTerminalTargetAccess());
    }

    public CellTerminalNetworkToolImpl(CellTerminalTargetLookup targetAccess) {
        this.targetAccess = Objects.requireNonNull(targetAccess, "targetAccess");
    }

    private static void validatePreviewToken(CellTerminalSession session,
                                             CellTerminalNetworkToolPreview preview,
                                             CellTerminalActionToken token,
                                             CellTerminalNetworkToolOperation operation) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(token, "token");
        if (preview.operation() != operation) {
            throw new IllegalArgumentException("Preview operation mismatch. expected=" + operation
                + ", actual=" + preview.operation());
        }
        if (!preview.token().equals(token)) {
            throw new IllegalArgumentException("Cell Terminal action token mismatch");
        }
        String expectedSignature = signPlans(preview.operation(), preview.contextId(), preview.plans());
        if (!preview.planSignature().equals(expectedSignature)) {
            throw new IllegalArgumentException("Cell Terminal action preview signature mismatch");
        }
        session.consumePendingAction(operation, preview.contextId(), expectedSignature, token);
    }

    private static boolean canPreviewAndWritePartition(CellTerminalCellSlotTarget slot) {
        return slot.supportsCapability(CellTerminalCapability.CONTENT_PREVIEW)
            && slot.supportsCapability(CellTerminalCapability.PARTITION_WRITE);
    }

    private static boolean canPreviewAndAutoPartitionFromContent(CellTerminalCellSlotTarget slot) {
        return canPreviewAndWritePartition(slot)
            && supportsAutoPartition(slot);
    }

    private static boolean canPreviewAndWritePartition(CellTerminalBusTarget bus) {
        return bus.supportsCapability(CellTerminalCapability.CONTENT_PREVIEW)
            && bus.supportsCapability(CellTerminalCapability.PARTITION_WRITE);
    }

    private static void requirePreviewAndPartitionWrite(CellTerminalCellSlotHandle handle,
                                                        CellTerminalCellSlotTarget slot) {
        if (!canPreviewAndWritePartition(slot)) {
            throw new IllegalStateException("Cell slot does not support content preview and partition writes. target="
                + handle.stableTargetId()
                + ", slot="
                + handle.slotIndex());
        }
    }

    private static void requirePreviewAndAutoPartitionFromContent(CellTerminalCellSlotHandle handle,
                                                                  CellTerminalCellSlotTarget slot) {
        if (!canPreviewAndAutoPartitionFromContent(slot)) {
            throw new IllegalStateException("Cell slot does not support auto partition from content. target="
                + handle.stableTargetId()
                + ", slot="
                + handle.slotIndex());
        }
    }

    private static boolean supportsAutoPartition(CellTerminalCellSlotTarget slot) {
        ItemStack stack = slot.getCellStack();
        return !stack.isEmpty()
            && stack.getItem() instanceof ICellWorkbenchItem workbenchItem
            && workbenchItem.supportsAutoPartition(stack);
    }

    private static void requirePreviewAndPartitionWrite(CellTerminalTargetHandle handle, CellTerminalBusTarget bus) {
        if (!canPreviewAndWritePartition(bus)) {
            throw new IllegalStateException("Storage bus target does not support content preview and partition writes. target="
                + handle.stableTargetId());
        }
    }

    private static void requireSafeUniqueTypeReallocationCapability(CellTerminalCellSlotHandle handle,
                                                                    CellTerminalCellSlotTarget slot) {
        if (!slot.supportsCapability(CellTerminalCapability.SAFE_UNIQUE_TYPE_REALLOCATION)) {
            throw new IllegalStateException("Cell slot does not support safe unique type reallocation. target="
                + handle.stableTargetId()
                + ", slot="
                + handle.slotIndex());
        }
    }

    private static StorageCell requireMountedCell(CellTerminalCellSlotHandle handle, CellTerminalCellSlotTarget slot) {
        if (!slot.isMounted()) {
            throw new IllegalStateException("Cell slot is not mounted. target=" + handle.stableTargetId()
                + ", slot=" + handle.slotIndex());
        }
        StorageCell cell = slot.getCellInventory();
        if (cell == null) {
            throw new IllegalStateException("Cell slot has no live storage inventory. target=" + handle.stableTargetId()
                + ", slot=" + handle.slotIndex());
        }
        return cell;
    }

    private static void requireSafeUniqueTypeReallocationCell(CellTerminalCellSlotHandle handle, StorageCell cell) {
        if (isUnsafeUniqueTypeReallocationCell(cell)) {
            throw new IllegalStateException("Unsafe cell type for unique type reallocation. target="
                + handle.stableTargetId()
                + ", slot="
                + handle.slotIndex()
                + ", cell="
                + cell.getClass().getName());
        }
    }

    private static boolean isUnsafeUniqueTypeReallocationCell(StorageCell cell) {
        return cell instanceof VoidCellInventory || cell instanceof CreativeCellInventory;
    }

    private static void validateUniqueTypeReallocationInsertCapacity(List<ResolvedCellPlan> livePlans,
                                                                     Map<CellTerminalCellSlotHandle, ResolvedCellPlan> liveCellByHandle,
                                                                     IActionSource source) {
        var requirementsByTarget = new Object2ObjectOpenHashMap<CellTerminalCellSlotHandle, List<TargetInsertRequirement>>();
        for (var sourcePlan : livePlans) {
            for (var movement : sourcePlan.plan.resourceMovements()) {
                ResolvedCellPlan targetPlan = liveCellByHandle.get(movement.targetCellSlotHandle());
                if (targetPlan == null) {
                    throw new IllegalStateException("Movement target is not part of the validated plan: "
                        + movement.targetCellSlotHandle());
                }
                List<TargetInsertRequirement> requirements = requirementsByTarget.computeIfAbsent(
                    movement.targetCellSlotHandle(),
                    ignored -> new ObjectArrayList<>());
                TargetInsertRequirement requirement = findRequirement(requirements, movement.what());
                if (requirement == null) {
                    requirement = new TargetInsertRequirement(targetPlan, movement.what());
                    requirements.add(requirement);
                }
                requirement.add(movement.amount());
            }
        }

        for (var entry : requirementsByTarget.entrySet()) {
            ResolvedCellPlan target = liveCellByHandle.get(entry.getKey());
            if (target == null) {
                throw new IllegalStateException("Movement target is not part of the validated plan: " + entry.getKey());
            }
            target.slot.simulateWithPartition(target.plan.partitionSlots(), cell -> {
                for (var requirement : entry.getValue()) {
                    long simulated = cell.insert(
                        requirement.what,
                        requirement.amount,
                        Actionable.SIMULATE,
                        source);
                    if (simulated != requirement.amount) {
                        throw new IllegalStateException("Target cell cannot accept planned resource. target="
                            + requirement.target.plan.stableTargetId()
                            + ", slot="
                            + requirement.target.plan.slotIndex()
                            + ", key="
                            + requirement.what
                            + ", amount="
                            + requirement.amount
                            + ", simulated="
                            + simulated);
                    }
                }
                return Boolean.TRUE;
            });
        }
    }

    private static @Nullable TargetInsertRequirement findRequirement(List<TargetInsertRequirement> requirements,
                                                                     AEKey what) {
        for (var requirement : requirements) {
            if (requirement.what.equals(what)) {
                return requirement;
            }
        }
        return null;
    }

    private static void extractPlannedResources(List<ResolvedCellPlan> livePlans,
                                                Map<CellTerminalCellSlotHandle, ResolvedCellPlan> liveCellByHandle,
                                                IActionSource source,
                                                List<ExtractedMovement> extractedMovements) {
        for (var sourcePlan : livePlans) {
            extractPlannedResources(sourcePlan, liveCellByHandle, source, extractedMovements);
        }
    }

    private static void extractPlannedResources(ResolvedCellPlan sourcePlan,
                                                Map<CellTerminalCellSlotHandle, ResolvedCellPlan> liveCellByHandle,
                                                IActionSource source,
                                                List<ExtractedMovement> extractedMovements) {
        for (var movement : sourcePlan.plan.resourceMovements()) {
            ResolvedCellPlan targetPlan = liveCellByHandle.get(movement.targetCellSlotHandle());
            if (targetPlan == null) {
                throw new IllegalStateException("Movement target is not part of the validated plan: "
                    + movement.targetCellSlotHandle());
            }
            long extracted = sourcePlan.cell.extract(movement.what(), movement.amount(), Actionable.MODULATE, source);
            if (extracted != movement.amount()) {
                throw new IllegalStateException("Source cell extraction changed during execute. source="
                    + sourcePlan.plan.stableTargetId()
                    + ", slot="
                    + sourcePlan.plan.slotIndex()
                    + ", key="
                    + movement.what()
                    + ", expected="
                    + movement.amount()
                    + ", extracted="
                    + extracted);
            }
            extractedMovements.add(new ExtractedMovement(sourcePlan, targetPlan, movement.what(), extracted, 0));
        }
    }

    private static void insertExtractedResources(List<ExtractedMovement> extractedMovements, IActionSource source) {
        for (int index = 0; index < extractedMovements.size(); index++) {
            ExtractedMovement movement = extractedMovements.get(index);
            long inserted = movement.target.cell.insert(movement.what, movement.extractedAmount, Actionable.MODULATE,
                source);
            extractedMovements.set(index, movement.withInsertedAmount(inserted));
            long leftover = movement.extractedAmount - inserted;
            if (leftover > 0) {
                throw new IllegalStateException("Target insert changed during execute. target="
                    + movement.target.plan.stableTargetId()
                    + ", slot="
                    + movement.target.plan.slotIndex()
                    + ", key="
                    + movement.what
                    + ", expected="
                    + movement.extractedAmount
                    + ", inserted="
                    + inserted);
            }
        }
    }

    private static void applyUniqueTypePartitions(List<ResolvedCellPlan> livePlans,
                                                  List<ResolvedCellPlan> partitionedTargets) {
        for (var livePlan : livePlans) {
            livePlan.slot.setPartition(livePlan.plan.partitionSlots());
            partitionedTargets.add(livePlan);
        }
    }

    private static void restoreUniqueTypePartitions(List<ResolvedCellPlan> partitionedTargets) {
        for (int index = partitionedTargets.size() - 1; index >= 0; index--) {
            ResolvedCellPlan livePlan = partitionedTargets.get(index);
            try {
                livePlan.slot.setPartition(livePlan.plan.baselinePartitionSlots());
            } catch (RuntimeException e) {
                AELog.error(e, String.format(
                    "Cell Terminal unique type reallocation failed to restore partition. target=%s, slot=%d",
                    livePlan.plan.stableTargetId(),
                    livePlan.plan.slotIndex()));
            }
        }
    }

    private static void restoreExtractedResources(List<ExtractedMovement> extractedMovements, IActionSource source) {
        for (int index = extractedMovements.size() - 1; index >= 0; index--) {
            ExtractedMovement movement = extractedMovements.get(index);
            long toRestore = movement.extractedAmount;
            if (movement.insertedAmount > 0) {
                long removedFromTarget = movement.target.cell.extract(
                    movement.what,
                    movement.insertedAmount,
                    Actionable.MODULATE,
                    source);
                if (removedFromTarget != movement.insertedAmount) {
                    AELog.error(
                        "Cell Terminal unique type reallocation failed to roll back target resource. source=%s, slot=%d, target=%s, targetSlot=%d, key=%s, inserted=%d, extracted=%d",
                        movement.source.plan.stableTargetId(),
                        movement.source.plan.slotIndex(),
                        movement.target.plan.stableTargetId(),
                        movement.target.plan.slotIndex(),
                        movement.what,
                        movement.insertedAmount,
                        removedFromTarget);
                }
                toRestore = movement.extractedAmount - movement.insertedAmount + removedFromTarget;
            }
            if (toRestore <= 0) {
                continue;
            }
            long restored = movement.source.cell.insert(movement.what, toRestore, Actionable.MODULATE, source);
            if (restored != toRestore) {
                AELog.error(
                    "Cell Terminal unique type reallocation failed to restore extracted resource. source=%s, slot=%d, target=%s, targetSlot=%d, key=%s, amount=%d, restored=%d",
                    movement.source.plan.stableTargetId(),
                    movement.source.plan.slotIndex(),
                    movement.target.plan.stableTargetId(),
                    movement.target.plan.slotIndex(),
                    movement.what,
                    toRestore,
                    restored);
            }
        }
        persistMovedCells(extractedMovements);
    }

    private static void persistMovedCells(List<ExtractedMovement> extractedMovements) {
        var persisted = Collections.newSetFromMap(new Reference2ObjectOpenHashMap<StorageCell, Boolean>());
        for (var movement : extractedMovements) {
            if (persisted.add(movement.source.cell)) {
                movement.source.cell.persist();
            }
            if (persisted.add(movement.target.cell)) {
                movement.target.cell.persist();
            }
        }
    }

    private static void validateSnapshot(CellTerminalPartitionPlan plan, CellTerminalContentSnapshot snapshot,
                                         CellTerminalPartitionSnapshot partitionSnapshot) {
        int liveCapacity = partitionSnapshot.slots().size();
        if (!plan.expectedContentRevision().equals(snapshot.contentRevision())) {
            throw new IllegalStateException("Target content changed since preview");
        }
        if (plan.expectedCapacity() != liveCapacity) {
            throw new IllegalStateException("Target partition capacity changed since preview");
        }
        if (!plan.baselinePartitionSlots().equals(partitionSnapshot.slots())) {
            throw new IllegalStateException("Target partition changed since preview");
        }
        if (plan.partitionSlots().size() > liveCapacity) {
            throw new IllegalStateException("Planned partition exceeds live capacity");
        }
    }

    private static CellTerminalPartitionPlan cellPlan(CellTerminalNetworkToolOperation operation,
                                                      CellTerminalCellSlotHandle handle,
                                                      String contentRevision,
                                                      int capacity,
                                                      List<@Nullable GenericStack> baselinePartitionSlots,
                                                      List<@Nullable GenericStack> partitionSlots) {
        return cellPlan(
            operation,
            handle,
            contentRevision,
            capacity,
            baselinePartitionSlots,
            partitionSlots,
            List.of());
    }

    private static CellTerminalPartitionPlan cellPlan(CellTerminalNetworkToolOperation operation,
                                                      CellTerminalCellSlotHandle handle,
                                                      String contentRevision,
                                                      int capacity,
                                                      List<@Nullable GenericStack> baselinePartitionSlots,
                                                      List<@Nullable GenericStack> partitionSlots,
                                                      List<CellTerminalPartitionPlan.ResourceMovement> movements) {
        return new CellTerminalPartitionPlan(
            operation,
            handle.stableTargetId(),
            handle.locator(),
            handle.slotIndex(),
            contentRevision,
            capacity,
            baselinePartitionSlots,
            partitionSlots,
            movements);
    }

    private static List<@Nullable GenericStack> firstKeysAsPartition(CellTerminalContentSnapshot snapshot, int capacity,
                                                                     boolean preserveAmounts) {
        var result = new ObjectArrayList<@Nullable GenericStack>(Math.max(0, capacity));
        List<GenericStack> stacks = snapshot.firstUniqueStacks(capacity, preserveAmounts);
        for (int slot = 0; slot < capacity; slot++) {
            result.add(slot < stacks.size() ? stacks.get(slot) : null);
        }
        return Collections.unmodifiableList(result);
    }

    private static Map<AEKey, CellTerminalCellSlotHandle> assignUniqueTypeTargets(
        List<CellTerminalCellSlotHandle> handles,
        List<CellTerminalCellSlotTarget> slots,
        List<AEKey> orderedUniqueKeys) {
        var nextSlotByType = new Object2ObjectOpenHashMap<String, Integer>();
        var targetByKey = new Object2ObjectOpenHashMap<AEKey, CellTerminalCellSlotHandle>(orderedUniqueKeys.size());
        for (AEKey key : orderedUniqueKeys) {
            String keyType = keyTypeId(key.getType());
            int startIndex = nextSlotByType.getOrDefault(keyType, 0);
            int assignedIndex = -1;
            for (int index = startIndex; index < slots.size(); index++) {
                String slotType = cellTypeId(slots.get(index));
                if (Objects.equals(keyType, slotType)) {
                    assignedIndex = index;
                    break;
                }
            }
            if (assignedIndex < 0) {
                throw new IllegalStateException("No matching target cell for unique key type: " + keyType);
            }
            targetByKey.put(key, handles.get(assignedIndex));
            nextSlotByType.put(keyType, assignedIndex + 1);
        }
        return targetByKey;
    }

    private static @Nullable AEKey findAssignedKey(Map<AEKey, CellTerminalCellSlotHandle> targetByKey,
                                                   CellTerminalCellSlotHandle handle) {
        for (var entry : targetByKey.entrySet()) {
            if (entry.getValue().equals(handle)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void incrementBreakdown(Map<String, Integer> breakdown, String label) {
        breakdown.merge(label, 1, Integer::sum);
    }

    private static List<CellTerminalNetworkToolPreview.TargetBreakdown> targetBreakdown(Map<String, Integer> breakdown) {
        var labels = new ObjectArrayList<>(breakdown.keySet());
        labels.sort(String::compareToIgnoreCase);
        var result = new ObjectArrayList<CellTerminalNetworkToolPreview.TargetBreakdown>(labels.size());
        for (var label : labels) {
            result.add(new CellTerminalNetworkToolPreview.TargetBreakdown(label, breakdown.get(label)));
        }
        return List.copyOf(result);
    }

    private static void recordAvailableCell(Map<String, UniqueTypeStats> typeStatsById, CellTerminalCellSlotTarget slot) {
        String typeId = cellTypeId(slot);
        if (typeId == null) {
            return;
        }
        typeStatsById.computeIfAbsent(typeId, ignored -> new UniqueTypeStats()).availableCellCount++;
    }

    private static void recordUniqueType(Map<String, UniqueTypeStats> typeStatsById,
                                         CellTerminalCellSlotTarget slot,
                                         AEKey key) {
        String cellTypeId = cellTypeId(slot);
        String keyTypeId = keyTypeId(key.getType());
        if (!Objects.equals(cellTypeId, keyTypeId)) {
            AELog.error("Cell Terminal unique type preview type mismatch. cellType=%s, keyType=%s, target=%s, slot=%d",
                cellTypeId, keyTypeId, slot.getStorageTarget().stableTargetId(), slot.slotIndex());
            throw new IllegalStateException("Cell type does not match previewed content type");
        }
        typeStatsById.computeIfAbsent(keyTypeId, ignored -> new UniqueTypeStats()).uniqueKeys.add(key);
    }

    private static @Nullable String cellTypeId(CellTerminalCellSlotTarget slot) {
        ItemStack stack = slot.getCellStack();
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof IBasicCellItem cellItem) {
            return keyTypeId(cellItem.getKeyType());
        }
        StorageCell inventory = slot.getCellInventory();
        if (inventory != null) {
            CellTerminalContentSnapshot snapshot = slot.previewContent();
            if (!snapshot.entries().isEmpty()) {
                return keyTypeId(snapshot.entries().getFirst().what().getType());
            }
        }
        AELog.error("Cell Terminal unique type preview encountered unsupported cell stack type. item=%s, target=%s, slot=%d",
            stack.getItem().getRegistryName(), slot.getStorageTarget().stableTargetId(), slot.slotIndex());
        throw new IllegalStateException("Unsupported cell type for unique type preview");
    }

    private static String cellDisplayLabel(CellTerminalCellSlotTarget slot) {
        return slot.getCellStack().getDisplayName();
    }

    private static String keyTypeId(AEKeyType keyType) {
        String path = keyType.getId().getPath();
        return switch (path) {
            case "i" -> "item";
            case "f" -> "fluid";
            case "essentia" -> "essentia";
            case "gas" -> "gas";
            default -> {
                AELog.error("Cell Terminal unique type preview encountered unknown AEKeyType id %s", keyType.getId());
                throw new IllegalStateException("Unknown AEKeyType id: " + keyType.getId());
            }
        };
    }

    private static String guiKey(String suffix) {
        return GUI_KEY_PREFIX + suffix;
    }

    private static String uniqueTypeLabelKey(String typeId) {
        return UNIQUE_TYPE_PREVIEW_TYPE_PREFIX + typeId;
    }

    private static CellTerminalNetworkToolPreview.UniqueTypeSummary uniqueTypeSummary(
        Map<String, UniqueTypeStats> typeStatsById) {
        var order = List.of("item", "fluid", "essentia", "gas");
        var result = new ObjectArrayList<CellTerminalNetworkToolPreview.TypeBreakdown>(typeStatsById.size());
        int available = 0;
        int unique = 0;
        for (var typeId : order) {
            var stats = typeStatsById.get(typeId);
            if (stats == null || !stats.hasContent()) {
                continue;
            }
            available += stats.availableCellCount;
            unique += stats.uniqueKeys.size();
            result.add(new CellTerminalNetworkToolPreview.TypeBreakdown(
                typeId,
                stats.availableCellCount,
                stats.uniqueKeys.size()));
        }
        return result.isEmpty() ? null : new CellTerminalNetworkToolPreview.UniqueTypeSummary(available, unique, result);
    }

    private static List<CellTerminalActionFailure> uniqueTypeCapacityFailures(List<CellTerminalCellSlotTarget> resolvedSlots,
                                                                              Map<String, UniqueTypeStats> typeStatsById) {
        var summary = uniqueTypeSummary(typeStatsById);
        if (summary == null) {
            return List.of();
        }
        var failingTypes = new ObjectArrayList<CellTerminalNetworkToolPreview.TypeBreakdown>();
        for (var typeBreakdown : summary.breakdown()) {
            if (typeBreakdown.uniqueTypeCount() > typeBreakdown.availableCellCount()) {
                failingTypes.add(typeBreakdown);
            }
        }
        if (failingTypes.isEmpty()) {
            return List.of();
        }
        String summaryMessage = failingTypes.size() == 1
            ? guiKey("networktools.attribute_unique.error.not_enough_cells")
            : guiKey("networktools.attribute_unique.error.not_enough_cells_by_type");
        var failures = new ObjectArrayList<CellTerminalActionFailure>(1 + failingTypes.size());
        failures.add(genericFailure("not_enough_cells", summaryMessage));
        for (var type : failingTypes) {
            failures.add(new CellTerminalActionFailure(
                resolvedSlots.isEmpty() ? null : resolvedSlots.getFirst().getStorageTarget().stableTargetId(),
                resolvedSlots.isEmpty() ? null : resolvedSlots.getFirst().getStorageTarget().locator(),
                -1,
                "type_detail",
                guiKey("networktools.attribute_unique.error.type_detail") + "|" + uniqueTypeLabelKey(type.typeId())
                    + "|" + type.availableCellCount() + "|" + type.uniqueTypeCount()));
        }
        return failures;
    }

    private static CellTerminalActionStatus status(List<CellTerminalPartitionPlan> applied,
                                                   List<CellTerminalActionFailure> failures) {
        if (failures.isEmpty()) {
            return CellTerminalActionStatus.SUCCESS;
        }
        return applied.isEmpty() ? CellTerminalActionStatus.FAILURE : CellTerminalActionStatus.PARTIAL_FAILURE;
    }

    private static String signPlans(CellTerminalNetworkToolOperation operation, String contextId,
                                    List<CellTerminalPartitionPlan> plans) {
        var builder = new StringBuilder(256);
        builder.append(operation.name()).append('\n').append(contextId).append('\n');
        for (var plan : plans) {
            builder.append(plan.operation())
                   .append('|')
                   .append(plan.stableTargetId())
                   .append('|')
                   .append(plan.locator())
                   .append('|')
                   .append(plan.slotIndex())
                   .append('|')
                   .append(plan.expectedContentRevision())
                   .append('|')
                   .append(plan.expectedCapacity())
                   .append('|');
            for (var stack : plan.baselinePartitionSlots()) {
                builder.append(stack == null ? "empty" : stackFingerprint(stack))
                       .append(',');
            }
            builder.append('|');
            for (var stack : plan.partitionSlots()) {
                builder.append(stack == null ? "empty" : stackFingerprint(stack))
                       .append(',');
            }
            builder.append('|');
            for (var movement : plan.resourceMovements()) {
                builder.append(movement.targetStableTargetId())
                       .append('#')
                       .append(movement.targetLocator())
                       .append('#')
                       .append(movement.targetSlotIndex())
                       .append('#')
                       .append(keyFingerprint(movement.what()))
                       .append('@')
                       .append(movement.amount())
                       .append(',');
            }
            builder.append('\n');
        }
        return CellTerminalActionToken.sha256(builder.toString());
    }

    private static String stackFingerprint(GenericStack stack) {
        return keyFingerprint(stack.what()) + "@" + stack.amount();
    }

    private static String keyFingerprint(AEKey key) {
        return key.getType().getId()
            + "#"
            + Objects.toString(key.getId(), "")
            + "#"
            + key.toTag();
    }

    private static CellTerminalActionFailure cellFailure(CellTerminalCellSlotHandle handle, String reason,
                                                         RuntimeException exception) {
        AELog.error(exception, "Cell Terminal network-tool cell plan failed");
        return CellTerminalActionFailure.cellSlot(handle, reason, failureMessage(exception));
    }

    private static CellTerminalActionFailure targetFailure(CellTerminalTargetHandle handle, String reason,
                                                           RuntimeException exception) {
        AELog.error(exception, "Cell Terminal network-tool target plan failed");
        return CellTerminalActionFailure.target(handle, reason, failureMessage(exception));
    }

    private static CellTerminalActionFailure planFailure(CellTerminalPartitionPlan plan, String reason,
                                                         RuntimeException exception) {
        String message = failureMessage(exception);
        if (plan.isCellSlotPlan()) {
            return CellTerminalActionFailure.cellSlot(plan.cellSlotHandle(), reason, message);
        }
        return CellTerminalActionFailure.target(plan.targetHandle(), reason, message);
    }

    private static CellTerminalActionFailure genericFailure(String reason, String message) {
        return new CellTerminalActionFailure(null, null, -1, reason, message);
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isEmpty()) {
            message = exception.getClass().getName();
        }
        return message;
    }

    private static String failureMessage(RuntimeException exception) {
        String message = messageOf(exception);
        if (message.startsWith(GUI_KEY_PREFIX)) {
            return message;
        }
        return failureKey(message);
    }

    private static String failureKey(String message) {
        if (message.startsWith("Cell slot does not expose content preview and partition write capabilities.")
            || message.startsWith("Cell slot does not support content preview and partition writes.")
            || message.startsWith("Storage bus target does not support content preview and partition writes.")) {
            return guiKey("networktools.failure.content_preview_partition_write_required");
        }
        if (message.startsWith("Cell slot does not support auto partition from content.")) {
            return guiKey("networktools.failure.auto_partition_required");
        }
        if (message.startsWith("Cell slot does not declare safe unique type reallocation support.")
            || message.startsWith("Cell slot does not support safe unique type reallocation.")) {
            return guiKey("networktools.failure.safe_unique_reallocation_required");
        }
        if (message.startsWith("Cell type cannot safely participate in resource reallocation.")
            || message.startsWith("Unsafe cell type for unique type reallocation.")) {
            return guiKey("networktools.failure.unsafe_unique_reallocation_cell");
        }
        if (message.startsWith("Cell slot is not mounted.")) {
            return guiKey("networktools.failure.cell_not_mounted");
        }
        if (message.startsWith("Cell slot has no live storage inventory.")) {
            return guiKey("networktools.failure.cell_inventory_missing");
        }
        if (message.startsWith("Unique type reallocation requires cell-slot plans")) {
            return guiKey("networktools.failure.unique_reallocation_cell_plan_required");
        }
        if (message.startsWith("Movement target is not part of the validated plan:")) {
            return guiKey("networktools.failure.movement_target_missing");
        }
        if (message.startsWith("Target cell cannot accept planned resource.")) {
            return guiKey("networktools.failure.target_cannot_accept_resource");
        }
        if (message.startsWith("Source cell extraction changed during execute.")) {
            return guiKey("networktools.failure.source_extraction_changed");
        }
        if (message.startsWith("Target insert changed during execute.")) {
            return guiKey("networktools.failure.target_insert_changed");
        }
        if (message.startsWith("Target content changed since preview")) {
            return guiKey("networktools.failure.target_content_changed");
        }
        if (message.startsWith("Target partition capacity changed since preview")) {
            return guiKey("networktools.failure.target_partition_capacity_changed");
        }
        if (message.startsWith("Target partition changed since preview")) {
            return guiKey("networktools.failure.target_partition_changed");
        }
        if (message.startsWith("Planned partition exceeds live capacity")) {
            return guiKey("networktools.failure.planned_partition_exceeds_capacity");
        }
        if (message.startsWith("No matching target cell for unique key type:")) {
            return guiKey("networktools.failure.no_matching_unique_type_cell");
        }
        if (message.startsWith("Cell type does not match previewed content type")) {
            return guiKey("networktools.failure.cell_type_mismatch");
        }
        if (message.startsWith("Unsupported cell type for unique type preview")) {
            return guiKey("networktools.failure.unsupported_cell_type");
        }
        if (message.startsWith("Unknown AEKeyType id:")) {
            return guiKey("networktools.failure.unknown_key_type");
        }
        if (message.startsWith("Unique type reallocation target missing for key ")) {
            return guiKey("networktools.failure.unique_reallocation_target_missing");
        }
        return guiKey("networktools.failure.generic");
    }

    @Override
    public CellTerminalNetworkToolPreview previewUniqueTypeReallocation(CellTerminalSession session,
                                                                        List<CellTerminalCellSlotHandle> slots) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(slots, "slots");

        var resolvedSlots = new ObjectArrayList<CellTerminalCellSlotTarget>(slots.size());
        var failures = new ObjectArrayList<CellTerminalActionFailure>();
        var typeStatsById = new Object2ObjectOpenHashMap<String, UniqueTypeStats>();
        for (var handle : slots) {
            try {
                var slot = this.targetAccess.resolveCellSlot(handle);
                resolvedSlots.add(slot);
                recordAvailableCell(typeStatsById, slot);
            } catch (RuntimeException e) {
                failures.add(cellFailure(handle, "target_resolve_failed", e));
            }
        }

        if (!failures.isEmpty()) {
            return createPreview(session, CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION,
                uniqueTypeSummary(typeStatsById), List.of(), List.of(), failures);
        }

        var orderedUniqueKeys = new ObjectArrayList<AEKey>();
        var seenKeys = new ObjectLinkedOpenHashSet<AEKey>();
        var contentSnapshots = new ObjectArrayList<CellTerminalContentSnapshot>(resolvedSlots.size());
        var partitionSnapshots = new ObjectArrayList<CellTerminalPartitionSnapshot>(resolvedSlots.size());
        for (int index = 0; index < resolvedSlots.size(); index++) {
            var slot = resolvedSlots.get(index);
            StorageCell cell = slot.getCellInventory();
            if (cell == null) {
                failures.add(CellTerminalActionFailure.cellSlot(
                    slots.get(index),
                    "no_cells",
                    guiKey("networktools.error.no_cells")));
                continue;
            }
            if (!canPreviewAndWritePartition(slot)) {
                failures.add(CellTerminalActionFailure.cellSlot(
                    slots.get(index),
                    "capability_unsupported",
                    guiKey("networktools.failure.content_preview_partition_write_required")));
                continue;
            }
            if (!slot.supportsCapability(CellTerminalCapability.SAFE_UNIQUE_TYPE_REALLOCATION)) {
                failures.add(CellTerminalActionFailure.cellSlot(
                    slots.get(index),
                    "unsafe_cell_type",
                    guiKey("networktools.failure.safe_unique_reallocation_required")));
                continue;
            }
            if (isUnsafeUniqueTypeReallocationCell(cell)) {
                failures.add(CellTerminalActionFailure.cellSlot(
                    slots.get(index),
                    "unsafe_cell_type",
                    guiKey("networktools.failure.unsafe_unique_reallocation_cell")));
                continue;
            }
            var snapshot = slot.previewContent();
            var partitionSnapshot = slot.getPartitionSnapshot();
            contentSnapshots.add(snapshot);
            partitionSnapshots.add(partitionSnapshot);
            for (var entry : snapshot.entries()) {
                recordUniqueType(typeStatsById, slot, entry.what());
                if (seenKeys.add(entry.what())) {
                    orderedUniqueKeys.add(entry.what());
                }
            }
        }

        if (!failures.isEmpty()) {
            return createPreview(session, CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION,
                uniqueTypeSummary(typeStatsById), List.of(), List.of(), failures);
        }

        if (resolvedSlots.isEmpty()) {
            return createPreview(session, CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION,
                uniqueTypeSummary(typeStatsById), List.of(), List.of(),
                List.of(genericFailure("no_cells", guiKey("networktools.error.no_cells"))));
        }

        if (orderedUniqueKeys.isEmpty()) {
            return createPreview(session, CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION,
                uniqueTypeSummary(typeStatsById), List.of(), List.of(),
                List.of(genericFailure("no_items", guiKey("networktools.error.no_items"))));
        }

        var capacityFailures = uniqueTypeCapacityFailures(resolvedSlots, typeStatsById);
        if (!capacityFailures.isEmpty()) {
            return createPreview(session, CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION,
                uniqueTypeSummary(typeStatsById), List.of(), List.of(), capacityFailures);
        }

        var targetByKey = assignUniqueTypeTargets(slots, resolvedSlots, orderedUniqueKeys);
        var partitionSlotsByCell = new ObjectArrayList<List<@Nullable GenericStack>>(resolvedSlots.size());
        for (int index = 0; index < resolvedSlots.size(); index++) {
            var handle = slots.get(index);
            int capacity = partitionSnapshots.get(index).slots().size();
            var assignedPartition = new ObjectArrayList<@Nullable GenericStack>(capacity);
            AEKey assignedKey = findAssignedKey(targetByKey, handle);
            for (int partitionSlot = 0; partitionSlot < capacity; partitionSlot++) {
                if (partitionSlot == 0 && assignedKey != null) {
                    assignedPartition.add(new GenericStack(assignedKey, 0));
                } else {
                    assignedPartition.add(null);
                }
            }
            partitionSlotsByCell.add(Collections.unmodifiableList(assignedPartition));
        }

        var movementsBySource = new ObjectArrayList<List<CellTerminalPartitionPlan.ResourceMovement>>(resolvedSlots.size());
        for (int index = 0; index < resolvedSlots.size(); index++) {
            var sourceHandle = slots.get(index);
            var sourceMovements = new ObjectArrayList<CellTerminalPartitionPlan.ResourceMovement>();
            for (var entry : contentSnapshots.get(index).entries()) {
                if (entry.amount() <= 0) {
                    continue;
                }
                CellTerminalCellSlotHandle targetHandle = targetByKey.get(entry.what());
                if (targetHandle == null) {
                    throw new IllegalStateException("Unique type reallocation target missing for key " + entry.what());
                }
                if (sourceHandle.equals(targetHandle)) {
                    continue;
                }
                sourceMovements.add(new CellTerminalPartitionPlan.ResourceMovement(
                    targetHandle.stableTargetId(),
                    targetHandle.locator(),
                    targetHandle.slotIndex(),
                    entry.what(),
                    entry.amount()));
            }
            movementsBySource.add(List.copyOf(sourceMovements));
        }

        var plans = new ObjectArrayList<CellTerminalPartitionPlan>(resolvedSlots.size());
        for (int index = 0; index < resolvedSlots.size(); index++) {
            var handle = slots.get(index);
            plans.add(cellPlan(
                CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION,
                handle,
                contentSnapshots.get(index).contentRevision(),
                partitionSnapshots.get(index).slots().size(),
                partitionSnapshots.get(index).slots(),
                partitionSlotsByCell.get(index),
                movementsBySource.get(index)));
        }

        return createPreview(session, CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION,
            uniqueTypeSummary(typeStatsById), List.of(), plans, List.of());
    }

    @Override
    public CellTerminalActionResult executeUniqueTypeReallocation(CellTerminalSession session,
                                                                  CellTerminalNetworkToolPreview preview,
                                                                  CellTerminalActionToken token) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(token, "token");
        return executeUniqueTypeReallocationPlans(session, preview, token);
    }

    @Override
    public CellTerminalNetworkToolPreview previewPartitionCellsByContent(CellTerminalSession session,
                                                                         List<CellTerminalCellSlotHandle> slots) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(slots, "slots");

        var plans = new ObjectArrayList<CellTerminalPartitionPlan>();
        var failures = new ObjectArrayList<CellTerminalActionFailure>();
        var breakdown = new Object2ObjectOpenHashMap<String, Integer>();
        for (var handle : slots) {
            try {
                CellTerminalCellSlotTarget slot = this.targetAccess.resolveCellSlot(handle);
                if (!canPreviewAndAutoPartitionFromContent(slot)) {
                    continue;
                }
                requirePreviewAndAutoPartitionFromContent(handle, slot);
                int capacity = slot.getPartitionSnapshot().slots().size();
                CellTerminalContentSnapshot snapshot = slot.previewContent();
                if (snapshot.uniqueKeyCount() > capacity) {
                    failures.add(CellTerminalActionFailure.cellSlot(
                        handle,
                        "partition_capacity_exceeded",
                        guiKey("networktools.failure.partition_capacity_exceeded")));
                    continue;
                }
                List<@Nullable GenericStack> partitionSlots = firstKeysAsPartition(snapshot, capacity, false);
                incrementBreakdown(breakdown, cellDisplayLabel(slot));
                plans.add(cellPlan(
                    CellTerminalNetworkToolOperation.PARTITION_CELLS_BY_CONTENT,
                    handle,
                    snapshot.contentRevision(),
                    capacity,
                    slot.getPartitionSnapshot().slots(),
                    partitionSlots));
            } catch (RuntimeException e) {
                failures.add(cellFailure(handle, "preview_failed", e));
            }
        }

        if (plans.isEmpty() && failures.isEmpty()) {
            failures.add(genericFailure("no_cells", guiKey("networktools.error.no_cells")));
        }

        return createPreview(session, CellTerminalNetworkToolOperation.PARTITION_CELLS_BY_CONTENT,
            null, targetBreakdown(breakdown), plans, failures);
    }

    @Override
    public CellTerminalActionResult executePartitionCellsByContent(CellTerminalSession session,
                                                                   CellTerminalNetworkToolPreview preview,
                                                                   CellTerminalActionToken token) {
        return executePartitionPlans(
            session,
            preview,
            token,
            CellTerminalNetworkToolOperation.PARTITION_CELLS_BY_CONTENT);
    }

    @Override
    public CellTerminalNetworkToolPreview previewPartitionStorageBusesByContent(CellTerminalSession session,
                                                                                List<CellTerminalTargetHandle> targets) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(targets, "targets");

        if (targets.isEmpty()) {
            return createPreview(
                session,
                CellTerminalNetworkToolOperation.PARTITION_STORAGE_BUSES_BY_CONTENT,
                null,
                List.of(),
                List.of(),
                List.of(genericFailure("no_buses", guiKey("networktools.error.no_buses"))));
        }

        var plans = new ObjectArrayList<CellTerminalPartitionPlan>();
        var failures = new ObjectArrayList<CellTerminalActionFailure>();
        var breakdown = new Object2ObjectOpenHashMap<String, Integer>();
        for (var handle : targets) {
            try {
                CellTerminalBusTarget bus = this.targetAccess.resolveStorageBus(handle);
                requirePreviewAndPartitionWrite(handle, bus);
                int capacity = bus.getPartitionSnapshot().slots().size();
                CellTerminalContentSnapshot snapshot = bus.previewContent();
                if (snapshot.uniqueKeyCount() > capacity) {
                    failures.add(CellTerminalActionFailure.target(
                        handle,
                        "partition_capacity_exceeded",
                        guiKey("networktools.failure.partition_capacity_exceeded")));
                    continue;
                }
                List<@Nullable GenericStack> partitionSlots = firstKeysAsPartition(
                    snapshot,
                    capacity,
                    bus.getPartitionMode() == CellTerminalBusPartitionMode.PRECISE_SLOTS);
                incrementBreakdown(breakdown, bus.displayName().getFormattedText());
                plans.add(new CellTerminalPartitionPlan(
                    CellTerminalNetworkToolOperation.PARTITION_STORAGE_BUSES_BY_CONTENT,
                    handle.stableTargetId(),
                    handle.locator(),
                    -1,
                    snapshot.contentRevision(),
                    capacity,
                    bus.getPartitionSnapshot().slots(),
                    partitionSlots));
            } catch (RuntimeException e) {
                failures.add(targetFailure(handle, "preview_failed", e));
            }
        }

        return createPreview(
            session,
            CellTerminalNetworkToolOperation.PARTITION_STORAGE_BUSES_BY_CONTENT,
            null,
            targetBreakdown(breakdown),
            plans,
            failures);
    }

    @Override
    public CellTerminalActionResult executePartitionStorageBusesByContent(CellTerminalSession session,
                                                                          CellTerminalNetworkToolPreview preview,
                                                                          CellTerminalActionToken token) {
        return executePartitionPlans(
            session,
            preview,
            token,
            CellTerminalNetworkToolOperation.PARTITION_STORAGE_BUSES_BY_CONTENT);
    }

    private CellTerminalNetworkToolPreview createPreview(CellTerminalSession session,
                                                         CellTerminalNetworkToolOperation operation,
                                                         @Nullable CellTerminalNetworkToolPreview.UniqueTypeSummary uniqueTypeSummary,
                                                         List<CellTerminalNetworkToolPreview.TargetBreakdown> targetBreakdown,
                                                         List<CellTerminalPartitionPlan> plans,
                                                         List<CellTerminalActionFailure> failures) {
        Objects.requireNonNull(session, "session");
        String contextId = session.nextContextId();
        String signature = signPlans(operation, contextId, plans);
        CellTerminalActionToken token = CellTerminalActionToken.create(operation.name(), contextId, signature);
        session.registerPendingAction(operation, contextId, signature, token);
        return new CellTerminalNetworkToolPreview(
            operation,
            contextId,
            token,
            signature,
            uniqueTypeSummary,
            targetBreakdown,
            plans,
            failures,
            false);
    }

    private CellTerminalActionResult executePartitionPlans(CellTerminalSession session,
                                                           CellTerminalNetworkToolPreview preview,
                                                           CellTerminalActionToken token,
                                                           CellTerminalNetworkToolOperation operation) {
        return executeValidatedPlans(session, preview, token, operation, false,
            operation == CellTerminalNetworkToolOperation.PARTITION_CELLS_BY_CONTENT);
    }

    private CellTerminalActionResult executeUniqueTypeReallocationPlans(CellTerminalSession session,
                                                                        CellTerminalNetworkToolPreview preview,
                                                                        CellTerminalActionToken token) {
        validatePreviewToken(session, preview, token, CellTerminalNetworkToolOperation.UNIQUE_TYPE_REALLOCATION);

        var validationFailures = new ObjectArrayList<>(preview.failures());
        var livePlans = new ObjectArrayList<ResolvedCellPlan>(preview.plans().size());
        var liveCellByHandle = new Object2ObjectOpenHashMap<CellTerminalCellSlotHandle, ResolvedCellPlan>(preview.plans().size());
        for (var plan : preview.plans()) {
            try {
                if (!plan.isCellSlotPlan()) {
                    throw new IllegalStateException("Unique type reallocation requires cell-slot plans");
                }
                CellTerminalCellSlotTarget slot = this.targetAccess.resolveCellSlot(plan.cellSlotHandle());
                requirePreviewAndPartitionWrite(plan.cellSlotHandle(), slot);
                validateSnapshot(plan, slot.previewContent(), slot.getPartitionSnapshot());
                StorageCell cell = requireMountedCell(plan.cellSlotHandle(), slot);
                requireSafeUniqueTypeReallocationCapability(plan.cellSlotHandle(), slot);
                requireSafeUniqueTypeReallocationCell(plan.cellSlotHandle(), cell);
                var livePlan = new ResolvedCellPlan(plan, slot, cell);
                livePlans.add(livePlan);
                liveCellByHandle.put(plan.cellSlotHandle(), livePlan);
            } catch (RuntimeException e) {
                validationFailures.add(planFailure(plan, "live_validation_failed", e));
                AELog.error(e, "Cell Terminal unique type reallocation plan failed live validation");
                break;
            }
        }

        if (!validationFailures.isEmpty()) {
            return new CellTerminalActionResult(CellTerminalActionStatus.FAILURE, List.of(), validationFailures, false);
        }

        IActionSource source = new BaseActionSource();
        try {
            validateUniqueTypeReallocationInsertCapacity(livePlans, liveCellByHandle, source);
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal unique type reallocation insert simulation failed");
            return new CellTerminalActionResult(
                CellTerminalActionStatus.FAILURE,
                List.of(),
                List.of(genericFailure("reallocation_insert_simulation_failed", failureMessage(e))),
                false);
        }

        var extractedMovements = new ObjectArrayList<ExtractedMovement>();
        var partitionedTargets = new ObjectArrayList<ResolvedCellPlan>();
        boolean invalidated = false;
        try {
            applyUniqueTypePartitions(livePlans, partitionedTargets);
            extractPlannedResources(livePlans, liveCellByHandle, source, extractedMovements);
            invalidated = !extractedMovements.isEmpty() || !partitionedTargets.isEmpty();
            insertExtractedResources(extractedMovements, source);
            persistMovedCells(extractedMovements);
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal unique type reallocation resource migration failed");
            invalidated |= !extractedMovements.isEmpty() || !partitionedTargets.isEmpty();
            restoreUniqueTypePartitions(partitionedTargets);
            restoreExtractedResources(extractedMovements, source);
            if (invalidated) {
                session.markCacheStale();
            }
            return new CellTerminalActionResult(
                CellTerminalActionStatus.FAILURE,
                List.of(),
                List.of(genericFailure("resource_migration_failed", failureMessage(e))),
                invalidated);
        }

        if (invalidated) {
            session.markCacheStale();
        }
        return new CellTerminalActionResult(status(preview.plans(), preview.failures()), preview.plans(),
            preview.failures(), invalidated);
    }

    private CellTerminalActionResult executeValidatedPlans(CellTerminalSession session,
                                                           CellTerminalNetworkToolPreview preview,
                                                           CellTerminalActionToken token,
                                                           CellTerminalNetworkToolOperation operation,
                                                           boolean failOnFirstValidationError,
                                                           boolean requireAutoPartitionForCellSlots) {
        validatePreviewToken(session, preview, token, operation);

        var validationFailures = new ObjectArrayList<>(preview.failures());
        var livePlans = new ObjectArrayList<CellTerminalPartitionPlan>(preview.plans().size());
        for (var plan : preview.plans()) {
            try {
                validateLivePlan(plan, requireAutoPartitionForCellSlots);
                livePlans.add(plan);
            } catch (RuntimeException e) {
                validationFailures.add(planFailure(plan, "live_validation_failed", e));
                AELog.error(e, "Cell Terminal network-tool plan failed live validation");
                if (failOnFirstValidationError) {
                    break;
                }
            }
        }

        if (!validationFailures.isEmpty()) {
            return new CellTerminalActionResult(CellTerminalActionStatus.FAILURE, List.of(), validationFailures, false);
        }

        var applied = new ObjectArrayList<CellTerminalPartitionPlan>(livePlans.size());
        var failures = new ObjectArrayList<>(preview.failures());
        for (var plan : livePlans) {
            try {
                if (plan.isCellSlotPlan()) {
                    this.targetAccess.resolveCellSlot(plan.cellSlotHandle()).setPartition(plan.partitionSlots());
                } else {
                    this.targetAccess.resolveStorageBus(plan.targetHandle()).setPartition(plan.partitionSlots());
                }
                applied.add(plan);
            } catch (RuntimeException e) {
                failures.add(planFailure(plan, "execute_failed", e));
                AELog.error(e, "Cell Terminal network-tool plan failed during execute");
            }
        }

        boolean invalidated = !applied.isEmpty();
        if (invalidated) {
            session.markCacheStale();
        }
        return new CellTerminalActionResult(status(applied, failures), applied, failures, invalidated);
    }

    private void validateLivePlan(CellTerminalPartitionPlan plan, boolean requireAutoPartitionForCellSlots) {
        if (plan.isCellSlotPlan()) {
            CellTerminalCellSlotTarget slot = this.targetAccess.resolveCellSlot(plan.cellSlotHandle());
            if (requireAutoPartitionForCellSlots) {
                requirePreviewAndAutoPartitionFromContent(plan.cellSlotHandle(), slot);
            } else {
                requirePreviewAndPartitionWrite(plan.cellSlotHandle(), slot);
            }
            validateSnapshot(plan, slot.previewContent(), slot.getPartitionSnapshot());
            return;
        }

        CellTerminalBusTarget bus = this.targetAccess.resolveStorageBus(plan.targetHandle());
        requirePreviewAndPartitionWrite(plan.targetHandle(), bus);
        validateSnapshot(plan, bus.previewContent(), bus.getPartitionSnapshot());
    }

    private static final class UniqueTypeStats {
        private final ObjectLinkedOpenHashSet<AEKey> uniqueKeys = new ObjectLinkedOpenHashSet<>();
        private int availableCellCount;

        private boolean hasContent() {
            return !this.uniqueKeys.isEmpty();
        }
    }

    private record ResolvedCellPlan(CellTerminalPartitionPlan plan, CellTerminalCellSlotTarget slot, StorageCell cell) {
        private ResolvedCellPlan {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(cell, "cell");
        }
    }

    private record ExtractedMovement(ResolvedCellPlan source,
                                     ResolvedCellPlan target,
                                     AEKey what,
                                     long extractedAmount,
                                     long insertedAmount) {
        private ExtractedMovement {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(what, "what");
            if (extractedAmount <= 0) {
                throw new IllegalArgumentException("extractedAmount must be > 0");
            }
            if (insertedAmount < 0 || insertedAmount > extractedAmount) {
                throw new IllegalArgumentException("insertedAmount must be between 0 and extractedAmount");
            }
        }

        private ExtractedMovement withInsertedAmount(long insertedAmount) {
            return new ExtractedMovement(source, target, what, extractedAmount, insertedAmount);
        }
    }

    private static final class TargetInsertRequirement {
        private final ResolvedCellPlan target;
        private final AEKey what;
        private long amount;

        private TargetInsertRequirement(ResolvedCellPlan target, AEKey what) {
            this.target = Objects.requireNonNull(target, "target");
            this.what = Objects.requireNonNull(what, "what");
        }

        private void add(long amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be > 0");
            }
            if (Long.MAX_VALUE - this.amount < amount) {
                throw new IllegalStateException("Planned movement amount overflow");
            }
            this.amount += amount;
        }
    }

}
