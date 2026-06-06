package ae2.client.patternimport;

import ae2.api.client.PatternImportPriority;
import ae2.api.client.PatternImportPriorityContext;
import ae2.api.stacks.GenericStack;
import ae2.core.localization.GuiText;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

final class DefaultPatternImportPriorities {
    static final String HEI_BOOKMARKS_ID = "ae2:hei_bookmarks";
    static final String CRAFTABLES_ID = "ae2:craftables";
    static final String STORED_ID = "ae2:stored";

    private static final List<PatternImportPriority> DEFAULTS = List.of(
        simple(HEI_BOOKMARKS_ID, GuiText.PatternImportPriorityHeiBookmarks, GuiText.PatternImportPriorityHeiBookmarksDesc,
            PatternImportPriorityContext::isBookmarked),
        simple(CRAFTABLES_ID, GuiText.PatternImportPriorityCraftables, GuiText.PatternImportPriorityCraftablesDesc,
            PatternImportPriorityContext::isCraftable),
        simple(STORED_ID, GuiText.PatternImportPriorityStored, GuiText.PatternImportPriorityStoredDesc,
            PatternImportPriorityContext::isStored)
    );

    private DefaultPatternImportPriorities() {
    }

    static List<PatternImportPriority> getDefaults() {
        return DEFAULTS;
    }

    private static PatternImportPriority simple(String id, GuiText label, GuiText description, Matcher matcher) {
        return new PatternImportPriority() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public ITextComponent getDisplayName() {
                return label.text();
            }

            @Override
            public List<ITextComponent> getTooltipMessage() {
                return List.of(
                    getDisplayName(),
                    description.text()
                );
            }

            @Override
            public boolean matches(PatternImportPriorityContext context, GenericStack candidate) {
                return matcher.matches(context, candidate);
            }
        };
    }

    @FunctionalInterface
    private interface Matcher {
        boolean matches(PatternImportPriorityContext context, GenericStack candidate);
    }
}
