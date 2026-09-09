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

package ae2.me;

import java.util.ArrayDeque;

final class GridSplitDetector {
    // Searches invoke no callbacks. One workspace per thread can serve every grid and start node.
    private static final ThreadLocal<ArrayDeque<GridNode>> SEARCH_QUEUE = ThreadLocal.withInitial(ArrayDeque::new);

    private GridSplitDetector() {
    }

    static boolean isConnected(GridNode start, GridNode pivot) {
        if (start == pivot) {
            return true;
        }

        var open = SEARCH_QUEUE.get();
        if (!open.isEmpty()) {
            throw new IllegalStateException("Grid split search queue is already in use");
        }

        var marker = new Object();
        open.add(start);
        start.markVisited(marker);

        try {
            while (!open.isEmpty()) {
                GridNode node = open.removeFirst();
                for (var other : node.connections.keySet()) {
                    if (other == pivot) {
                        return true;
                    }
                    if (other.markVisited(marker)) {
                        open.addLast(other);
                    }
                }
            }
            return false;
        } finally {
            open.clear();
        }
    }
}
