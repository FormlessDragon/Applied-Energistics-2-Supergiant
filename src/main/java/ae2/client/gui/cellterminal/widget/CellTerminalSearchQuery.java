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

import ae2.api.config.CellTerminalSearchMode;
import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parses and evaluates Cell Terminal search text.
 * <p>
 * Plain text keeps the compact AE2-style contains matching. Text prefixed with {@code ?} uses the DiskTerminal
 * advanced query syntax exposed in the Cell Terminal search help.
 */
public final class CellTerminalSearchQuery {
    private static final String FIELD_NAME = "name";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_PART = "part";
    private static final String FIELD_CONTAINER = "container";
    private static final String FIELD_RENAMED = "renamed";
    private static final String FIELD_PRIORITY = "priority";
    private static final String FIELD_PARTITION = "partition";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_DIR = "dir";
    private static final Expression ALWAYS_FALSE = source -> MatchResult.matched(false);
    private final String raw;
    private final boolean advanced;
    private final List<Term> terms;
    private final Expression expression;
    private final SearchParseError parseError;

    private CellTerminalSearchQuery(String raw, List<Term> terms) {
        this.raw = raw;
        this.advanced = false;
        this.terms = terms;
        this.expression = null;
        this.parseError = null;
    }

    private CellTerminalSearchQuery(String raw, Expression expression, SearchParseError parseError) {
        this.raw = raw;
        this.advanced = true;
        this.terms = List.of();
        this.expression = expression;
        this.parseError = parseError;
    }

    public static CellTerminalSearchQuery parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return new CellTerminalSearchQuery("", List.of());
        }
        if (trimmed.startsWith("?")) {
            return parseAdvanced(trimmed);
        }
        return parseSimple(trimmed);
    }

    private static CellTerminalSearchQuery parseSimple(String trimmed) {
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("\\s+");
        List<Term> terms = new ObjectArrayList<>(parts.length);
        String currentField = "";
        for (String part : parts) {
            int separator = part.indexOf(':');
            if (part.startsWith("$") && separator > 1) {
                currentField = normalizeField(part.substring(1, separator));
                terms.add(new Term(currentField, part.substring(separator + 1)));
            } else if (part.startsWith("$") && part.length() > 1) {
                currentField = normalizeField(part.substring(1));
            } else {
                terms.add(new Term(currentField, part));
            }
        }
        return new CellTerminalSearchQuery(normalized, terms);
    }

    private static CellTerminalSearchQuery parseAdvanced(String trimmed) {
        String expressionText = trimmed.substring(1);
        if (expressionText.isBlank()) {
            SearchParseError error = expressionText.isEmpty()
                ? SearchParseError.of("empty_query")
                : SearchParseError.of("empty_after_prefix");
            AELog.warn("Cell Terminal advanced search query is empty: %s", trimmed);
            return new CellTerminalSearchQuery(trimmed, ALWAYS_FALSE, error);
        }
        try {
            Parser parser = new Parser(Tokenizer.tokenize(expressionText.trim()));
            Expression expression = parser.parse();
            return new CellTerminalSearchQuery(trimmed.toLowerCase(Locale.ROOT), expression, null);
        } catch (SearchParseException e) {
            AELog.warn("Invalid Cell Terminal advanced search query '%s': %s", trimmed, e.getMessage());
            return new CellTerminalSearchQuery(trimmed, ALWAYS_FALSE, e.error());
        }
    }

    private static String normalizeField(String field) {
        String normalized = field == null ? "" : field.trim().toLowerCase(Locale.ROOT);
        if ("partition".equals(normalized)) {
            return FIELD_PARTITION;
        }
        return normalized;
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean isNumericField(String field) {
        return FIELD_PRIORITY.equals(field) || FIELD_PARTITION.equals(field) || FIELD_ITEMS.equals(field);
    }

    private static boolean isKnownField(String field) {
        return FIELD_NAME.equals(field) || FIELD_CONTENT.equals(field) || FIELD_PART.equals(field)
            || FIELD_CONTAINER.equals(field) || FIELD_RENAMED.equals(field) || FIELD_PRIORITY.equals(field)
            || FIELD_PARTITION.equals(field) || FIELD_ITEMS.equals(field) || FIELD_DIR.equals(field);
    }

    private static boolean compareString(String actual, Operator operator, String expectedRaw) {
        List<String> values = splitValues(expectedRaw);
        if (operator == Operator.NOT_EQUALS) {
            for (String expected : values) {
                if (compareSingleString(actual, Operator.EQUALS, expected)) {
                    return false;
                }
            }
            return true;
        }
        for (String expected : values) {
            if (compareSingleString(actual, operator, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean compareSingleString(String actual, Operator operator, String expected) {
        return switch (operator) {
            case EQUALS -> hasWildcards(expected)
                ? wildcardPattern(expected, false).matcher(actual).matches()
                : actual.equals(expected);
            case CONTAINS -> hasWildcards(expected)
                ? wildcardPattern(expected, true).matcher(actual).matches()
                : actual.contains(expected);
            case NOT_EQUALS -> !compareSingleString(actual, Operator.EQUALS, expected);
            default -> false;
        };
    }

    private static boolean compareInt(int actual, Operator operator, String expectedRaw) {
        List<String> values = splitValues(expectedRaw);
        if (operator == Operator.NOT_EQUALS) {
            for (String value : values) {
                if (actual == Integer.parseInt(value.trim())) {
                    return false;
                }
            }
            return true;
        }
        if (operator == Operator.LESS_THAN || operator == Operator.GREATER_THAN
            || operator == Operator.LESS_THAN_OR_EQUALS || operator == Operator.GREATER_THAN_OR_EQUALS) {
            return compareSingleInt(actual, operator, Integer.parseInt(values.getFirst().trim()));
        }
        for (String value : values) {
            if (compareSingleInt(actual, operator, Integer.parseInt(value.trim()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean compareSingleInt(int actual, Operator operator, int expected) {
        return switch (operator) {
            case EQUALS -> actual == expected;
            case NOT_EQUALS -> actual != expected;
            case LESS_THAN -> actual < expected;
            case GREATER_THAN -> actual > expected;
            case LESS_THAN_OR_EQUALS -> actual <= expected;
            case GREATER_THAN_OR_EQUALS -> actual >= expected;
            case CONTAINS -> false;
        };
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<String> splitValues(String value) {
        List<String> values = new ObjectArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!inQuote && (c == '"' || c == '\'')) {
                inQuote = true;
                quote = c;
            } else if (inQuote && c == quote) {
                inQuote = false;
            } else if (!inQuote && c == ',') {
                addSplitValue(values, current);
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        addSplitValue(values, current);
        if (values.isEmpty()) {
            values.add("");
        }
        return values;
    }

    private static void addSplitValue(List<String> values, StringBuilder current) {
        String value = current.toString().trim().toLowerCase(Locale.ROOT);
        if (!value.isEmpty()) {
            values.add(value);
        }
    }

    private static boolean hasWildcards(String value) {
        return value.indexOf('*') >= 0 || value.indexOf('?') >= 0;
    }

    private static Pattern wildcardPattern(String glob, boolean contains) {
        StringBuilder regex = new StringBuilder();
        if (contains) {
            regex.append(".*");
        }
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '+', '^', '$', '[', ']', '(', ')', '{', '}', '|', '\\' -> regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        if (contains) {
            regex.append(".*");
        }
        return Pattern.compile(regex.toString());
    }

    private static MatchResult combineOr(MatchResult left, MatchResult right) {
        return MatchResult.matched(left.matches() || right.matches());
    }

    private static MatchResult combineAnd(MatchResult left, MatchResult right) {
        return MatchResult.matched(left.matches() && right.matches());
    }

    public boolean isEmpty() {
        return this.raw.isEmpty();
    }

    public boolean hasParseError() {
        return this.parseError != null;
    }

    public String getParseErrorTranslationKey() {
        return this.parseError == null ? "" : this.parseError.translationKey();
    }

    public Object[] getParseErrorTranslationArgs() {
        return this.parseError == null ? new Object[0] : this.parseError.translationArgs();
    }

    public boolean matches(SearchSource source) {
        if (this.raw.isEmpty()) {
            return true;
        }
        if (this.advanced) {
            if (this.parseError != null || this.expression == null) {
                return false;
            }
            MatchResult result = this.expression.evaluate(source);
            return result.matches();
        }
        for (Term term : this.terms) {
            if (!source.mode().allowsField(term.field()) || !source.supportsField(term.field())) {
                return false;
            }
            boolean matched = false;
            for (String candidate : source.getValues(term.field())) {
                if (normalizeValue(candidate).contains(term.value())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private enum Operator {
        EQUALS,
        NOT_EQUALS,
        LESS_THAN,
        GREATER_THAN,
        LESS_THAN_OR_EQUALS,
        GREATER_THAN_OR_EQUALS,
        CONTAINS
    }

    private enum TokenType {
        IDENTIFIER,
        VALUE,
        OPERATOR,
        AND,
        OR,
        LEFT_PAREN,
        RIGHT_PAREN
    }

    public interface SearchSource {
        CellTerminalSearchMode mode();

        boolean supportsField(String field);

        List<String> getValues(String field);
    }

    private interface Expression {
        MatchResult evaluate(SearchSource source);
    }

    private record Term(String field, String value) {
    }

    public record SearchParseError(String key, List<String> args) {
        public SearchParseError {
            Objects.requireNonNull(key, "key");
            args = List.copyOf(Objects.requireNonNull(args, "args"));
        }

        private static SearchParseError of(String key, String... args) {
            return new SearchParseError(key, List.of(args));
        }

        public String translationKey() {
            return "gui.ae2.CellTerminal.search.error." + this.key;
        }

        public Object[] translationArgs() {
            return this.args.toArray(String[]::new);
        }
    }

    private record Comparison(String field, Operator operator, String expected) implements Expression {
        @Override
        public MatchResult evaluate(SearchSource source) {
            if (!source.supportsField(this.field)) {
                return MatchResult.matched(false);
            }

            List<String> actualValues = source.getValues(this.field);
            if (actualValues.isEmpty()) {
                return MatchResult.matched(false);
            }

            if (isNumericField(this.field)) {
                for (String actual : actualValues) {
                    if (compareInt(parseInt(actual), this.operator, this.expected)) {
                        return MatchResult.matched(true);
                    }
                }
                return MatchResult.matched(false);
            }

            for (String actual : actualValues) {
                String normalized = normalizeValue(actual);
                if (FIELD_RENAMED.equals(this.field) && normalized.isEmpty()) {
                    continue;
                }
                if (compareString(normalized, this.operator, this.expected)) {
                    return MatchResult.matched(true);
                }
            }
            return MatchResult.matched(false);
        }
    }

    private record MatchResult(boolean matches) {
        private static MatchResult matched(boolean matches) {
            return new MatchResult(matches);
        }
    }

    private record Token(TokenType type, String value) {
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int position;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private static void validateComparison(String field, Operator operator, String rawValue) {
            if (!isNumericField(field)) {
                return;
            }
            for (String part : splitValues(rawValue)) {
                try {
                    Integer.parseInt(part.trim());
                } catch (NumberFormatException e) {
                    throw new SearchParseException(SearchParseError.of("expected_number", "$" + field, part.trim()));
                }
            }
            if (operator == Operator.CONTAINS) {
                throw new SearchParseException(SearchParseError.of("unexpected_operator", "~"));
            }
        }

        private static Operator parseOperator(String token) {
            return switch (token) {
                case "=" -> Operator.EQUALS;
                case "!=" -> Operator.NOT_EQUALS;
                case "<" -> Operator.LESS_THAN;
                case ">" -> Operator.GREATER_THAN;
                case "<=" -> Operator.LESS_THAN_OR_EQUALS;
                case ">=" -> Operator.GREATER_THAN_OR_EQUALS;
                case "~" -> Operator.CONTAINS;
                default -> throw new SearchParseException(SearchParseError.of("unexpected_operator", token));
            };
        }

        Expression parse() {
            Expression expression = parseOr();
            if (this.position < this.tokens.size()) {
                Token token = peek();
                throw switch (token.type) {
                    case RIGHT_PAREN -> error("unexpected_paren", token.value);
                    case AND, OR -> error("unexpected_operator", token.value);
                    default -> error("unexpected_token", token.value);
                };
            }
            return expression;
        }

        private Expression parseOr() {
            Expression left = parseAnd();
            while (match(TokenType.OR)) {
                if (isAtEnd()) {
                    throw error("expected_after_or");
                }
                Expression right = parseAnd();
                Expression previous = left;
                left = source -> combineOr(previous.evaluate(source), right.evaluate(source));
            }
            return left;
        }

        private Expression parseAnd() {
            Expression left = parsePrimary();
            while (match(TokenType.AND)) {
                if (isAtEnd()) {
                    throw error("expected_after_and");
                }
                Expression right = parsePrimary();
                Expression previous = left;
                left = source -> combineAnd(previous.evaluate(source), right.evaluate(source));
            }
            return left;
        }

        private Expression parsePrimary() {
            if (match(TokenType.LEFT_PAREN)) {
                if (peekType(TokenType.RIGHT_PAREN)) {
                    throw error("unexpected_paren", ")");
                }
                Expression nested = parseOr();
                consumeRightParen();
                return nested;
            }
            if (match(TokenType.RIGHT_PAREN)) {
                throw error("unexpected_paren", ")");
            }
            if (peekType(TokenType.AND) || peekType(TokenType.OR) || peekType(TokenType.OPERATOR)) {
                throw error("unexpected_operator", peek().value);
            }
            return parseComparison();
        }

        private Expression parseComparison() {
            String field = FIELD_NAME;
            if (peekType(TokenType.IDENTIFIER)) {
                field = normalizeField(advance().value);
                if (!isKnownField(field)) {
                    throw error("unknown_identifier", "$" + field);
                }
            }

            Operator operator = Operator.CONTAINS;
            if (peekType(TokenType.OPERATOR)) {
                operator = parseOperator(advance().value);
            }

            if (isAtEnd() || peekType(TokenType.RIGHT_PAREN) || peekType(TokenType.AND) || peekType(TokenType.OR)) {
                if (isNumericField(field) || operator != Operator.CONTAINS) {
                    throw error("unexpected_end");
                }
                return new Comparison(field, operator, "");
            }
            if (peekType(TokenType.LEFT_PAREN) || peekType(TokenType.OPERATOR)) {
                throw error("unexpected_token", peek().value);
            }
            String value = advance().value;
            validateComparison(field, operator, value);
            return new Comparison(field, operator, value.toLowerCase(Locale.ROOT));
        }

        private boolean match(TokenType type) {
            if (!peekType(type)) {
                return false;
            }
            this.position++;
            return true;
        }

        private void consumeRightParen() {
            if (peekType(TokenType.RIGHT_PAREN)) {
                advance();
                return;
            }
            throw error("missing_paren");
        }

        private boolean peekType(TokenType type) {
            return this.position < this.tokens.size() && this.tokens.get(this.position).type == type;
        }

        private Token advance() {
            return this.tokens.get(this.position++);
        }

        private Token peek() {
            return this.tokens.get(this.position);
        }

        private boolean isAtEnd() {
            return this.position >= this.tokens.size();
        }

        private SearchParseException error(String key, String... args) {
            return new SearchParseException(SearchParseError.of(key, args));
        }
    }

    private static final class Tokenizer {
        private Tokenizer() {
        }

        static List<Token> tokenize(String input) {
            List<Token> tokens = new ObjectArrayList<>();
            int index = 0;
            while (index < input.length()) {
                char c = input.charAt(index);
                if (Character.isWhitespace(c)) {
                    index++;
                    continue;
                }
                switch (c) {
                    case '&' -> {
                        tokens.add(new Token(TokenType.AND, "&"));
                        index++;
                    }
                    case '|' -> {
                        tokens.add(new Token(TokenType.OR, "|"));
                        index++;
                    }
                    case '(' -> {
                        tokens.add(new Token(TokenType.LEFT_PAREN, "("));
                        index++;
                    }
                    case ')' -> {
                        tokens.add(new Token(TokenType.RIGHT_PAREN, ")"));
                        index++;
                    }
                    case '$' -> index = readIdentifier(input, index, tokens);
                    case '"', '\'' -> index = readQuoted(input, index, tokens);
                    default -> {
                        if (isOperatorStart(c)) {
                            index = readOperator(input, index, tokens);
                        } else {
                            index = readValue(input, index, tokens);
                        }
                    }
                }
            }
            if (tokens.isEmpty()) {
                throw new SearchParseException(SearchParseError.of("no_tokens"));
            }
            return tokens;
        }

        private static int readIdentifier(String input, int index, List<Token> tokens) {
            int start = index + 1;
            int end = start;
            while (end < input.length()) {
                char c = input.charAt(end);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    end++;
                } else {
                    break;
                }
            }
            if (end == start) {
                throw new SearchParseException(SearchParseError.of("empty_after_prefix"));
            }
            tokens.add(new Token(TokenType.IDENTIFIER, input.substring(start, end)));
            return end;
        }

        private static int readQuoted(String input, int index, List<Token> tokens) {
            char quote = input.charAt(index);
            StringBuilder value = new StringBuilder();
            int cursor = index + 1;
            while (cursor < input.length()) {
                char c = input.charAt(cursor);
                if (c == quote) {
                    tokens.add(new Token(TokenType.VALUE, value.toString()));
                    return cursor + 1;
                }
                value.append(c);
                cursor++;
            }
            throw new SearchParseException(SearchParseError.of("unexpected_end"));
        }

        private static int readOperator(String input, int index, List<Token> tokens) {
            if (index + 1 < input.length()) {
                String two = input.substring(index, index + 2);
                if ("!=".equals(two) || "<=".equals(two) || ">=".equals(two)) {
                    tokens.add(new Token(TokenType.OPERATOR, two));
                    return index + 2;
                }
            }
            char c = input.charAt(index);
            if (c == '=' || c == '<' || c == '>' || c == '~') {
                tokens.add(new Token(TokenType.OPERATOR, Character.toString(c)));
                return index + 1;
            }
            throw new SearchParseException(SearchParseError.of("unexpected_operator", Character.toString(c)));
        }

        private static int readValue(String input, int index, List<Token> tokens) {
            int end = index;
            while (end < input.length()) {
                char c = input.charAt(end);
                if (Character.isWhitespace(c) || c == '&' || c == '|' || c == '(' || c == ')'
                    || c == '"' || c == '\'' || c == '$' || isOperatorStart(c)) {
                    break;
                }
                end++;
            }
            if (end == index) {
                throw new SearchParseException(SearchParseError.of("unexpected_token",
                    Character.toString(input.charAt(index))));
            }
            tokens.add(new Token(TokenType.VALUE, input.substring(index, end)));
            return end;
        }

        private static boolean isOperatorStart(char c) {
            return c == '=' || c == '!' || c == '<' || c == '>' || c == '~';
        }
    }

    private static final class SearchParseException extends RuntimeException {
        private final SearchParseError error;

        SearchParseException(SearchParseError error) {
            super(error.key() + (error.args().isEmpty() ? "" : " " + String.join(", ", error.args())));
            this.error = error;
        }

        private SearchParseError error() {
            return this.error;
        }
    }
}
