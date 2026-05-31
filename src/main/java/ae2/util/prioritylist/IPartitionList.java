/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package ae2.util.prioritylist;

import ae2.api.config.FuzzyMode;
import ae2.api.config.IncludeExclude;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

public interface IPartitionList {
    static Builder builder() {
        return new Builder();
    }

    boolean isListed(AEKey input);

    boolean isEmpty();

    Iterable<AEKey> getItems();

    /**
     * Checks if the given stack matches this partition list assuming a given WHITELIST/BLACKLIST mode.
     */
    default boolean matchesFilter(AEKey key, IncludeExclude mode) {
        if (!isEmpty()) {
            switch (mode) {
                case WHITELIST -> {
                    if (!isListed(key)) {
                        return false;
                    }
                }
                case BLACKLIST -> {
                    if (isListed(key)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    class Builder {
        private final KeyCounter keys = new KeyCounter();

        @Nullable
        private FuzzyMode fuzzyMode;

        private Builder() {
            this.fuzzyMode = null;
        }

        public void add(@Nullable AEKey key) {
            if (key != null) {
                this.keys.add(key, 1);
            }
        }

        public void addAll(Iterable<AEKey> keys) {
            for (AEKey key : keys) {
                this.keys.add(key, 1);
            }
        }

        public void fuzzyMode(FuzzyMode mode) {
            this.fuzzyMode = mode;
        }

        public IPartitionList build() {
            if (this.keys.isEmpty()) {
                return DefaultPriorityList.INSTANCE;
            }

            if (this.fuzzyMode != null) {
                return new FuzzyPriorityList(this.keys, this.fuzzyMode);
            }

            return new PrecisePriorityList(this.keys);
        }
    }
}
