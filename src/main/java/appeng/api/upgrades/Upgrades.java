package appeng.api.upgrades;

import appeng.items.materials.EnergyCardItem;
import appeng.items.materials.UpgradeCardItem;
import appeng.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Upgrades {
    private static final Reference2ObjectMap<Item, List<Association>> ASSOCIATIONS = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectMap<IUpgradeableItem, Set<Item>> SUPPORTED_ITEM_UPGRADES = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectMap<Item, List<ITextComponent>> UPGRADE_CARD_TOOLTIP_LINES = new Reference2ObjectOpenHashMap<>();

    private Upgrades() {
    }

    public static synchronized void add(Item upgradeCard, Item upgradableObject, int maxSupported) {
        add(upgradeCard, upgradableObject, maxSupported, null);
    }

    public static synchronized void add(Item upgradeCard, Item upgradableObject, int maxSupported,
                                        @Nullable String tooltipGroup) {
        if (upgradableObject instanceof IUpgradeableItem upgradeableItem) {
            var upgrades = SUPPORTED_ITEM_UPGRADES.get(upgradeableItem);
            if (upgrades == null) {
                SUPPORTED_ITEM_UPGRADES.put(upgradeableItem, Set.of(upgradeCard));
            } else {
                var newSet = new ObjectOpenHashSet<>(upgrades);
                newSet.add(upgradeCard);
                SUPPORTED_ITEM_UPGRADES.put(upgradeableItem, Set.copyOf(newSet));
            }
        }

        var association = new Association(upgradeCard, upgradableObject, maxSupported, tooltipGroup);
        ASSOCIATIONS.computeIfAbsent(upgradeCard, ignored -> new ObjectArrayList<>()).add(association);
        UPGRADE_CARD_TOOLTIP_LINES.remove(upgradeCard);
    }

    public static synchronized int getMaxInstallable(Item card, Item upgradableItem) {
        var associations = ASSOCIATIONS.get(card);
        if (associations == null) {
            return 0;
        }

        for (var association : associations) {
            if (association.upgradableItem() == upgradableItem) {
                return association.maxCount();
            }
        }

        return 0;
    }

    @SuppressWarnings("unused")
    public static synchronized Map<IUpgradeableItem, Set<Item>> getUpgradableItems() {
        return Map.copyOf(SUPPORTED_ITEM_UPGRADES);
    }

    public static int getEnergyCardMultiplier(IUpgradeInventory upgrades) {
        int multiplier = 0;
        for (var card : upgrades) {
            if (card.getItem() instanceof EnergyCardItem energyCardItem) {
                multiplier += energyCardItem.getEnergyMultiplier();
            }
        }
        return multiplier;
    }

    public static Item createUpgradeCardItem() {
        return new UpgradeCardItem();
    }

    public static boolean isUpgradeCardItem(Item card) {
        return card instanceof UpgradeCardItem;
    }

    public static boolean isUpgradeCardItem(ItemStack stack) {
        return stack.getItem() instanceof UpgradeCardItem;
    }

    public static synchronized List<ITextComponent> getTooltipLinesForCard(Item card) {
        return UPGRADE_CARD_TOOLTIP_LINES.computeIfAbsent(card, Upgrades::createTooltipLinesForCard);
    }

    @SuppressWarnings("unused")
    public static synchronized List<ITextComponent> getTooltipLinesForMachine(Item upgradableItem) {
        var result = new ObjectArrayList<ITextComponent>();

        for (var cardAssociations : ASSOCIATIONS.values()) {
            for (var association : cardAssociations) {
                if (association.upgradableItem() == upgradableItem) {
                    ITextComponent upgradeName = TextComponentItemStack.of(new ItemStack(association.upgradeCard()));
                    if (association.maxCount() > 1) {
                        upgradeName = upgradeName.createCopy()
                            .appendSibling(new TextComponentString(" (" + association.maxCount() + ")"));
                    }
                    result.add(upgradeName);
                    break;
                }
            }
        }

        return result;
    }

    public static synchronized List<ITextComponent> getTooltipLinesForInventory(IUpgradeInventory upgrades) {
        var result = new ObjectArrayList<ITextComponent>();

        for (var cardAssociations : ASSOCIATIONS.values()) {
            Item upgradeCard = cardAssociations.getFirst().upgradeCard();
            int maxSupported = upgrades.getMaxInstalled(upgradeCard);
            if (maxSupported > 0) {
                ITextComponent upgradeName = TextComponentItemStack.of(new ItemStack(upgradeCard));
                if (maxSupported > 1) {
                    upgradeName = upgradeName.createCopy()
                                             .appendSibling(new TextComponentString(" (" + maxSupported + ")"));
                }
                result.add(upgradeName);
            }
        }

        return result;
    }

    private static List<ITextComponent> createTooltipLinesForCard(Item card) {
        ObjectList<Association> associations = new ObjectArrayList<>(ASSOCIATIONS.getOrDefault(card, Collections.emptyList()));
        associations.sort(Comparator.comparingInt(Association::maxCount));
        ObjectList<ITextComponent> supportedTooltipLines = new ObjectArrayList<>(associations.size());
        ObjectSet<String> namesAdded = new ObjectOpenHashSet<>();

        for (int i = 0; i < associations.size(); i++) {
            Association association = associations.get(i);
            var ii = new ItemStack(association.upgradableItem());
            ITextComponent name = TextComponentItemStack.of(ii);
            String dedupeKey = association.upgradableItem().getTranslationKey(ii) + ".name";

            if (association.tooltipGroup() != null && namesAdded.contains(association.tooltipGroup())) {
                continue;
            }

            if (association.tooltipGroup() != null) {
                for (int j = i + 1; j < associations.size(); j++) {
                    String otherGroup = associations.get(j).tooltipGroup();
                    if (association.tooltipGroup().equals(otherGroup)) {
                        name = new TextComponentTranslation(association.tooltipGroup());
                        dedupeKey = association.tooltipGroup();
                        break;
                    }
                }
            }

            if (namesAdded.add(dedupeKey)) {
                if (association.maxCount() > 1) {
                    name.appendSibling(new TextComponentString(" (" + association.maxCount() + ")"));
                }
                supportedTooltipLines.add(name);
            }
        }

        return supportedTooltipLines;
    }

    private record Association(Item upgradeCard, Item upgradableItem, int maxCount, @Nullable String tooltipGroup) {
    }
}
