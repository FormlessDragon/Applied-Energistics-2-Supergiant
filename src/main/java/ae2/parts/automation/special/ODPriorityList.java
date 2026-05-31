package ae2.parts.automation.special;

import ae2.api.config.IncludeExclude;
import ae2.api.stacks.AEKey;
import ae2.util.prioritylist.IPartitionList;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

import java.util.List;
import java.util.function.Predicate;

final class ODPriorityList implements IPartitionList {
    private final String whiteExpression;
    private final String blackExpression;
    private final Predicate<AEKey> whitePredicate;
    private final Predicate<AEKey> blackPredicate;
    private final Object2BooleanOpenHashMap<Object> memory = new Object2BooleanOpenHashMap<>();

    ODPriorityList(String whiteExpression, String blackExpression) {
        this.whiteExpression = normalize(whiteExpression);
        this.blackExpression = normalize(blackExpression);
        this.whitePredicate = ODExpressionParser.compile(this.whiteExpression, true);
        this.blackPredicate = ODExpressionParser.compile(this.blackExpression, false);
    }

    private static String normalize(String expression) {
        return expression == null ? "" : expression.replaceAll("\\s+", "");
    }

    @Override
    public boolean isListed(AEKey input) {
        if (isEmpty()) {
            return true;
        }
        Object cacheKey = input.getPrimaryKey();
        if (this.memory.containsKey(cacheKey)) {
            return this.memory.getBoolean(cacheKey);
        }
        boolean result = evaluate(input);
        this.memory.put(cacheKey, result);
        return result;
    }

    @Override
    public boolean isEmpty() {
        return this.whiteExpression.isEmpty() && this.blackExpression.isEmpty();
    }

    @Override
    public Iterable<AEKey> getItems() {
        return List.of();
    }

    @Override
    public boolean matchesFilter(AEKey key, IncludeExclude mode) {
        return isEmpty() || isListed(key);
    }

    private boolean evaluate(AEKey input) {
        boolean hasWhite = !this.whiteExpression.isEmpty();
        boolean hasBlack = !this.blackExpression.isEmpty();
        if (hasWhite && hasBlack) {
            return this.whitePredicate.test(input) && this.blackPredicate.test(input);
        }
        if (hasWhite) {
            return this.whitePredicate.test(input);
        }
        return !hasBlack || this.blackPredicate.test(input);
    }
}
