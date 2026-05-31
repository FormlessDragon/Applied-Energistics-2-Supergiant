package ae2.client.patternimport;

import ae2.api.client.PatternImportPriority;
import ae2.api.client.PatternImportPriorityContext;
import ae2.api.stacks.GenericStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.List;

final class DefaultPatternImportPriorities {
    static final String HEI_BOOKMARKS_ID = "ae2:hei_bookmarks";
    static final String CRAFTABLES_ID = "ae2:craftables";
    static final String STORED_ID = "ae2:stored";

    private static final List<PatternImportPriority> DEFAULTS = List.of(
        simple(HEI_BOOKMARKS_ID, "gui.ae2.PatternImportPriorityHeiBookmarks", PatternImportPriorityContext::isBookmarked),
        simple(CRAFTABLES_ID, "gui.ae2.PatternImportPriorityCraftables", PatternImportPriorityContext::isCraftable),
        simple(STORED_ID, "gui.ae2.PatternImportPriorityStored", PatternImportPriorityContext::isStored)
    );

    private DefaultPatternImportPriorities() {
    }

    static List<PatternImportPriority> getDefaults() {
        return DEFAULTS;
    }

    private static PatternImportPriority simple(String id, String translationKey, Matcher matcher) {
        return new PatternImportPriority() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public ITextComponent getDisplayName() {
                return new TextComponentTranslation(translationKey);
            }

            @Override
            public List<ITextComponent> getTooltipMessage() {
                return List.of(
                    getDisplayName(),
                    new TextComponentTranslation(translationKey + ".desc")
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
