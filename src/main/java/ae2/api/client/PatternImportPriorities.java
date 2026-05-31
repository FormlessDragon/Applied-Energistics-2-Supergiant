/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.client;

import ae2.client.patternimport.PatternImportPriorityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client-only registry for HEI/JEI pattern import priorities used by the pattern encoding terminal.
 * <p>
 * Register priorities from client setup only. Dedicated server registration is rejected at runtime.
 */
@SideOnly(Side.CLIENT)
public final class PatternImportPriorities {
    private PatternImportPriorities() {
    }

    public static void register(PatternImportPriority priority) {
        PatternImportPriorityRegistry.register(priority);
    }

    public static List<PatternImportPriority> getRegistered() {
        return PatternImportPriorityRegistry.getRegistered();
    }

    @Nullable
    public static PatternImportPriority getById(String id) {
        return PatternImportPriorityRegistry.getById(id);
    }
}
