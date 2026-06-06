/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 TeamAppliedEnergistics
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

package ae2.api.integrations.igtooltip;

import ae2.core.localization.LocalizationEnum;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.ApiStatus;

/**
 * Abstraction for building an in-game tooltip for supported integrations.
 */
@ApiStatus.Experimental
public interface TooltipBuilder {
    String localize(LocalizationEnum text);

    String localize(String translationKey);

    void addLine(String line);

    void addLine(LocalizationEnum line);

    void addLine(LocalizationEnum line, TextFormatting formatting);

    void addLabel(LocalizationEnum label, String value);

    void addLabel(LocalizationEnum label, String value, TextFormatting valueFormatting);
}
