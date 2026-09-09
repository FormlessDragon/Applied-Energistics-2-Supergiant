/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2026, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ae2.me.pathfinding;

import ae2.api.networking.IGridNode;

public final class PathingCalculationTestAccess {
    private PathingCalculationTestAccess() {
    }

    public static void compute(PathingCalculation calculation, Iterable<? extends IGridNode> controllers) {
        calculation.compute(controllers);
    }
}
