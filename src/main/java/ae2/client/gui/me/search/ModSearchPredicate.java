package ae2.client.gui.me.search;

import ae2.api.stacks.AEKey;
import ae2.util.Platform;

import java.util.Locale;
import java.util.function.Predicate;

final class ModSearchPredicate implements Predicate<AEKey> {
    private final String term;

    public ModSearchPredicate(String term) {
        this.term = normalize(term);
    }

    private static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean test(AEKey what) {
        String modId = what.getModId();

        if (modId != null) {
            if (modId.contains(term)) {
                return true;
            }

            String modName = Platform.getModName(modId);
            modName = normalize(modName);
            return modName.contains(term);
        }

        return false;
    }
}
