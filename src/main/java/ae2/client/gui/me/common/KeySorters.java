/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.me.common;

import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.integration.Integrations;
import ae2.integration.abstraction.HeiAdapter;
import ae2.integration.modules.bogosorter.InventoryBogoSortModule;
import ae2.integration.modules.inventorytweaks.InventoryTweaksModule;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Comparator;

final class KeySorters {

    public static final Comparator<AEKey> NAME_ASC = Comparator.comparing(
        is -> is.getDisplayName().getFormattedText(),
        String::compareToIgnoreCase);
    public static final Comparator<AEKey> NAME_DESC = NAME_ASC.reversed();
    public static final Comparator<AEKey> MOD_ASC = Comparator.comparing(
        AEKey::getModId,
        String::compareToIgnoreCase).thenComparing(NAME_ASC);
    public static final Comparator<AEKey> MOD_DESC = MOD_ASC.reversed();
    private static final Comparator<AEKey> FALLBACK_ASC = KeySorters::compareByFallback;
    private static final Comparator<AEKey> FALLBACK_DESC = FALLBACK_ASC.reversed();
    public static final Comparator<AEKey> INVTWEAKS_ASC = invTweaksComparator(SortDir.ASCENDING);
    public static final Comparator<AEKey> INVTWEAKS_DESC = invTweaksComparator(SortDir.DESCENDING);

    private KeySorters() {
    }

    public static Comparator<AEKey> getComparator(SortOrder order, SortDir dir) {
        return switch (order) {
            case NAME -> dir == SortDir.ASCENDING ? NAME_ASC : NAME_DESC;
            case MOD -> dir == SortDir.ASCENDING ? MOD_ASC : MOD_DESC;
            case INVTWEAKS -> dir == SortDir.ASCENDING ? INVTWEAKS_ASC : INVTWEAKS_DESC;
            case HEI -> {
                HeiAdapter hei = Integrations.hei();
                var componentWeights = new Object2IntOpenHashMap<AEKey>();
                componentWeights.defaultReturnValue(-1);
                yield ExternalSortFallback.comparator(
                    (left, right) -> compareHei(left, right, hei, componentWeights, dir),
                    getHeiMissingRankComparator(dir));
            }
            case AMOUNT -> throw new UnsupportedOperationException();
        };
    }

    static Comparator<AEKey> getFallbackComparator(SortDir dir) {
        return dir == SortDir.ASCENDING ? FALLBACK_ASC : FALLBACK_DESC;
    }

    static Comparator<AEKey> getInvTweaksFallbackComparator(SortDir dir) {
        return typeGroupedFallback(dir);
    }

    private static Comparator<AEKey> invTweaksComparator(SortDir dir) {
        return ExternalSortFallback.comparator(
            (left, right) -> compareInvTweaks(left, right, dir),
            typeGroupedFallback(dir));
    }

    static Comparator<AEKey> getHeiMissingRankComparator(SortDir dir) {
        return typeGroupedFallback(dir);
    }

    private static Comparator<AEKey> typeGroupedFallback(SortDir dir) {
        return (left, right) -> {
            int typeCompare = compareTypeGroup(left, right);
            if (typeCompare != 0) {
                return typeCompare;
            }
            int fallback = compareByFallback(left, right);
            return dir == SortDir.DESCENDING ? -fallback : fallback;
        };
    }

    private static int compareTypeGroup(AEKey left, AEKey right) {
        boolean leftItem = left instanceof AEItemKey;
        boolean rightItem = right instanceof AEItemKey;
        if (leftItem != rightItem) {
            return leftItem ? 1 : -1;
        }
        if (!leftItem) {
            return left.getType().getId().compareTo(right.getType().getId());
        }
        return 0;
    }

    private static int compareInvTweaks(AEKey left, AEKey right, SortDir dir) {
        int typeCompare = compareTypeGroup(left, right);
        if (typeCompare != 0) {
            return typeCompare;
        }

        int compared;
        if (left instanceof AEItemKey leftItem && right instanceof AEItemKey rightItem) {
            var bogoComparator = InventoryBogoSortModule.getComparator();
            if (bogoComparator != null) {
                compared = bogoComparator.compare(leftItem.getReadOnlyStack(), rightItem.getReadOnlyStack());
            } else if (InventoryTweaksModule.isLoaded()) {
                compared = InventoryTweaksModule.compareItems(leftItem.getReadOnlyStack(), rightItem.getReadOnlyStack());
            } else {
                compared = compareByFallback(left, right);
            }
        } else {
            compared = compareByFallback(left, right);
        }
        return dir == SortDir.DESCENDING ? -compared : compared;
    }

    private static int compareHei(AEKey left, AEKey right, HeiAdapter hei, Object2IntMap<AEKey> componentWeights,
                                  SortDir dir) {
        int leftRank = hei.getIngredientSortRank(left);
        int rightRank = hei.getIngredientSortRank(right);

        if (leftRank != -1 && rightRank != -1) {
            int rankCompare = Integer.compare(leftRank, rightRank);
            if (dir == SortDir.DESCENDING) {
                rankCompare = -rankCompare;
            }
            if (rankCompare != 0) {
                return rankCompare;
            }

            if (left.getPrimaryKey() == right.getPrimaryKey()) {
                int componentCompare = Integer.compare(
                    getComponentWeight(left, componentWeights),
                    getComponentWeight(right, componentWeights));
                if (componentCompare != 0) {
                    return componentCompare;
                }
            }
            return compareByFallback(left, right);
        }

        if (leftRank != -1) {
            return dir == SortDir.ASCENDING ? -1 : 1;
        }
        if (rightRank != -1) {
            return dir == SortDir.ASCENDING ? 1 : -1;
        }

        int typeCompare = compareTypeGroup(left, right);
        if (typeCompare != 0) {
            return typeCompare;
        }
        int fallback = compareByFallback(left, right);
        return dir == SortDir.DESCENDING ? -fallback : fallback;
    }

    private static int getComponentWeight(AEKey key, Object2IntMap<AEKey> componentWeights) {
        if (componentWeights.containsKey(key)) {
            return componentWeights.getInt(key);
        }

        int weight = 0;
        if (key.hasTagCompound()) {
            weight = key.getTagCompoundSize();
        }
        componentWeights.put(key, weight);
        return weight;
    }

    private static int compareByFallback(AEKey left, AEKey right) {
        int nameCompare = NAME_ASC.compare(left, right);
        if (nameCompare != 0) {
            return nameCompare;
        }
        return MOD_ASC.compare(left, right);
    }

}
