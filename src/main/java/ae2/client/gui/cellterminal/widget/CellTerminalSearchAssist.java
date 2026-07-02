/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026, TeamAppliedEnergistics, All rights reserved.
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

package ae2.client.gui.cellterminal.widget;

import ae2.client.gui.widgets.AETextField;
import ae2.core.localization.LocalizationEnum;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static ae2.core.localization.GuiText.CellTerminalSearchAssistAvailableOperators;
import static ae2.core.localization.GuiText.CellTerminalSearchAssistCompletions;
import static ae2.core.localization.GuiText.CellTerminalSearchAssistHoldCtrlExamples;
import static ae2.core.localization.GuiText.CellTerminalSearchAssistHoldShiftOperators;
import static ae2.core.localization.GuiText.CellTerminalSearchAssistNoMatches;
import static ae2.core.localization.GuiText.CellTerminalSearchError;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpAdvanced;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpAdvancedDesc;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpExamples;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpExamplesDesc1;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpExamplesDesc2;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpExamplesDesc3;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpExamplesDesc5;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpExamplesDesc6;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpExamplesDesc7;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpExamplesDesc8;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpFieldDoubleClick;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdContainer;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdContent;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdDir;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdItems;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdName;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdPart;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdPartition;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdPriority;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdRenamed;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpIdentifiers;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpOpCompare;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpOpGroup;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpOpLogic;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpOpMulti;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpOpWildcard;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpOperators;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpSimple;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpSimpleDesc;
import static ae2.core.localization.GuiText.CellTerminalSearchHelpTitle;

/**
 * Keeps Cell Terminal search help, advanced identifier completion, and contextual search panel text in one place.
 */
public final class CellTerminalSearchAssist {
    private static final List<FieldInfo> FIELDS = List.of(
        new FieldInfo("$name", CellTerminalSearchHelpIdName, FieldKind.STRING),
        new FieldInfo("$content", CellTerminalSearchHelpIdContent, FieldKind.STRING),
        new FieldInfo("$part", CellTerminalSearchHelpIdPart, FieldKind.STRING),
        new FieldInfo("$container", CellTerminalSearchHelpIdContainer, FieldKind.STRING),
        new FieldInfo("$renamed", CellTerminalSearchHelpIdRenamed, FieldKind.STRING),
        new FieldInfo("$priority", CellTerminalSearchHelpIdPriority, FieldKind.NUMBER),
        new FieldInfo("$partition", CellTerminalSearchHelpIdPartition, FieldKind.NUMBER),
        new FieldInfo("$items", CellTerminalSearchHelpIdItems, FieldKind.NUMBER),
        new FieldInfo("$dir", CellTerminalSearchHelpIdDir, FieldKind.STRING));

    private String lastCompletedText = "";
    private String lastInserted = "";
    private int lastTokenStart = -1;
    private int lastCandidateIndex = -1;
    private List<String> lastCandidates = List.of();

    public static List<ITextComponent> shortTooltip() {
        return List.of(
            searchHelpLine("%s", CellTerminalSearchHelpTitle),
            searchHelpLine("§f%s", CellTerminalSearchHelpSimple),
            searchHelpLine("§7%s", CellTerminalSearchHelpSimpleDesc),
            searchHelpLine("§f%s", CellTerminalSearchHelpAdvanced),
            searchHelpLine("§7%s", CellTerminalSearchHelpAdvancedDesc),
            searchHelpLine("§7%s", CellTerminalSearchHelpFieldDoubleClick));
    }

    private static void appendCompletionLines(List<String> lines, IdentifierToken token) {
        List<String> candidates = completionCandidates(token.prefix());
        lines.add(CellTerminalSearchAssistCompletions.getLocal());
        if (candidates.isEmpty()) {
            lines.add("  " + CellTerminalSearchAssistNoMatches.getLocal("$" + token.prefix()));
        } else {
            for (String candidate : candidates) {
                FieldInfo info = fieldByName(candidate);
                String description = info == null ? "" : info.helpKey().getLocal();
                lines.add("  " + candidate + " - " + description);
            }
        }

        lines.add("");
        lines.add(CellTerminalSearchAssistAvailableOperators.getLocal());
        FieldInfo exact = fieldByName("$" + token.prefix());
        if (exact != null && exact.kind() == FieldKind.NUMBER) {
            lines.add("  = != < > <= >= ,");
        } else {
            lines.add("  = != ~ * ? ,");
            lines.add("  & | ( )");
        }
    }

    private static void appendOperatorLines(List<String> lines) {
        lines.add(CellTerminalSearchHelpOperators.getLocal());
        lines.add("  = != < > <= >= ~ - " + CellTerminalSearchHelpOpCompare.getLocal());
        lines.add("  & | - " + CellTerminalSearchHelpOpLogic.getLocal());
        lines.add("  ( ) - " + CellTerminalSearchHelpOpGroup.getLocal());
        lines.add("  * ? - " + CellTerminalSearchHelpOpWildcard.getLocal());
        lines.add("  , - " + CellTerminalSearchHelpOpMulti.getLocal());
    }

    private static void appendIdentifierLines(List<String> lines) {
        lines.add(CellTerminalSearchHelpIdentifiers.getLocal());
        for (FieldInfo field : FIELDS) {
            lines.add("  " + field.name() + " - " + field.helpKey.getLocal());
        }
    }

    private static void appendExampleLines(List<String> lines) {
        lines.add(CellTerminalSearchHelpExamples.getLocal());
        lines.add("  ? $priority>0 - " + CellTerminalSearchHelpExamplesDesc1.getLocal());
        lines.add("  ? $items=0 & $partition>0 - " + CellTerminalSearchHelpExamplesDesc2.getLocal());
        lines.add("  ? $name~iron,gold,diamond - " + CellTerminalSearchHelpExamplesDesc3.getLocal());
        lines.add("  ? $content~drive - " + CellTerminalSearchHelpExamplesDesc5.getLocal());
        lines.add("  ? $renamed - " + CellTerminalSearchHelpExamplesDesc6.getLocal());
        lines.add("  ? $content~iron & $part~gold - " + CellTerminalSearchHelpExamplesDesc7.getLocal());
        lines.add("  ? $dir=outbound - " + CellTerminalSearchHelpExamplesDesc8.getLocal());
    }

    private static void appendAssistHintLines(List<String> lines) {
        lines.add("§9" + CellTerminalSearchAssistHoldShiftOperators.getLocal());
        lines.add("§9" + CellTerminalSearchAssistHoldCtrlExamples.getLocal());
    }

    private static List<String> completionCandidates(String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ObjectArrayList<>();
        for (FieldInfo field : FIELDS) {
            if (field.name().substring(1).startsWith(normalizedPrefix)) {
                result.add(field.name());
            }
        }
        return result;
    }

    private static IdentifierToken findIdentifierToken(AETextField field) {
        String text = field.getText();
        if (!isAdvancedSearch(text)) {
            return null;
        }
        int cursor = field.getCursorPosition();
        int start = cursor;
        while (start > 0 && isIdentifierChar(text.charAt(start - 1))) {
            start--;
        }
        int end = cursor;
        while (end < text.length() && isIdentifierChar(text.charAt(end))) {
            end++;
        }
        if (start >= text.length() || text.charAt(start) != '$') {
            return null;
        }
        int prefixEnd = Math.clamp(cursor, start + 1, end);
        String prefix = text.substring(start + 1, prefixEnd).toLowerCase(Locale.ROOT);
        return new IdentifierToken(start, end, prefix);
    }

    private static boolean isAdvancedSearch(String text) {
        return text != null && text.trim().startsWith("?");
    }

    private static boolean isEmptyAdvancedSearch(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("?") && trimmed.substring(1).trim().isEmpty();
    }

    private static boolean isIdentifierChar(char c) {
        return c == '$' || c == '_' || Character.isLetterOrDigit(c);
    }

    private static int nextIndex(int current, int size, boolean backwards) {
        if (size <= 0) {
            return -1;
        }
        if (backwards) {
            return current <= 0 ? size - 1 : current - 1;
        }
        return current >= size - 1 ? 0 : current + 1;
    }

    private static void replaceToken(AETextField field, int start, int end, String replacement) {
        String text = field.getText();
        String next = text.substring(0, start) + replacement + text.substring(end);
        int cursor = start + replacement.length();
        field.setTextFromClient(next);
        field.setCursorPosition(cursor);
        field.setSelectionPos(cursor);
    }

    private static FieldInfo fieldByName(String name) {
        for (FieldInfo field : FIELDS) {
            if (field.name().equals(name)) {
                return field;
            }
        }
        return null;
    }

    private static ITextComponent searchHelpLine(String pattern, LocalizationEnum suffix) {
        return new TextComponentString(String.format(pattern, suffix.getLocal()));
    }

    public List<String> buildPanelLines(AETextField field, CellTerminalSearchQuery query, boolean focused,
                                        boolean showOperators, boolean showExamples) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(query, "query");
        String text = field.getText();
        boolean advanced = isAdvancedSearch(text);
        boolean emptyAdvanced = isEmptyAdvancedSearch(text);
        if (!focused || !advanced) {
            return Collections.emptyList();
        }
        if (!emptyAdvanced && !query.hasParseError()) {
            return Collections.emptyList();
        }

        List<String> lines = new ObjectArrayList<>();
        IdentifierToken token = advanced ? findIdentifierToken(field) : null;
        if (token != null) {
            appendCompletionLines(lines, token);
        } else if (focused && advanced && showOperators) {
            appendOperatorLines(lines);
        } else if (focused && advanced && showExamples) {
            appendExampleLines(lines);
        } else if (focused && advanced && (emptyAdvanced || query.hasParseError())) {
            appendIdentifierLines(lines);
            lines.add("");
            appendAssistHintLines(lines);
        }

        if (query.hasParseError()) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add("§4" + CellTerminalSearchError.getLocal());
            lines.add(new TextComponentTranslation(
                query.getParseErrorTranslationKey(),
                query.getParseErrorTranslationArgs()).getFormattedText());
        }
        return lines;
    }

    public boolean handleTab(AETextField field, boolean backwards) {
        Objects.requireNonNull(field, "field");
        String text = field.getText();
        if (!isAdvancedSearch(text)) {
            resetCompletion();
            return false;
        }

        IdentifierToken token = findIdentifierToken(field);
        if (token == null) {
            resetCompletion();
            return false;
        }

        List<String> candidates;
        int candidateIndex;
        if (matchesCompletionSession(text, token)) {
            candidates = this.lastCandidates;
            candidateIndex = nextIndex(this.lastCandidateIndex, candidates.size(), backwards);
        } else {
            candidates = completionCandidates(token.prefix());
            if (candidates.isEmpty()) {
                resetCompletion();
                return true;
            }
            candidateIndex = backwards ? candidates.size() - 1 : 0;
        }

        String candidate = candidates.get(candidateIndex);
        replaceToken(field, token.start(), token.end(), candidate);
        rememberCompletion(field.getText(), token.start(), candidate, candidates, candidateIndex);
        return true;
    }

    private boolean matchesCompletionSession(String text, IdentifierToken token) {
        String currentToken = text.substring(token.start(), token.end());
        return token.start() == this.lastTokenStart
            && text.equals(this.lastCompletedText)
            && currentToken.equals(this.lastInserted)
            && !this.lastCandidates.isEmpty();
    }

    private void rememberCompletion(String text, int tokenStart, String inserted, List<String> candidates, int index) {
        this.lastCompletedText = text;
        this.lastTokenStart = tokenStart;
        this.lastInserted = inserted;
        this.lastCandidates = List.copyOf(candidates);
        this.lastCandidateIndex = index;
    }

    private void resetCompletion() {
        this.lastCompletedText = "";
        this.lastInserted = "";
        this.lastTokenStart = -1;
        this.lastCandidateIndex = -1;
        this.lastCandidates = List.of();
    }

    private enum FieldKind {
        STRING,
        NUMBER
    }

    private record IdentifierToken(int start, int end, String prefix) {
    }

    private record FieldInfo(String name, LocalizationEnum helpKey, FieldKind kind) {
    }
}
