package ae2.parts.automation.special;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Predicate;

final class ODExpressionParser {

    private ODExpressionParser() {
    }

    static Predicate<AEKey> compile(String expression, boolean whitelist) {
        var tokens = tokenize(clean(expression));
        if (tokens.isEmpty()) {
            return ignored -> false;
        }
        var rpn = toRpn(tokens);
        if (rpn.isEmpty()) {
            return ignored -> false;
        }
        return key -> {
            int result = eval(tagStrings(key), rpn);
            if (result == 0) {
                return false;
            }
            return whitelist ? result == 1 : result == -1;
        };
    }

    private static String clean(String expression) {
        return (expression == null ? "" : expression).trim().replace("&&", "&").replace("||", "|");
    }

    private static List<String> tagStrings(AEKey key) {
        var result = new ArrayList<String>();
        ResourceLocation id = key.getId();
        if (id != null) {
            result.add(id.toString());
            result.add(id.getPath());
        }

        if (key instanceof AEItemKey itemKey) {
            ItemStack stack = itemKey.getReadOnlyStack();
            for (int oreId : OreDictionary.getOreIDs(stack)) {
                result.add(OreDictionary.getOreName(oreId));
            }
        }
        return result;
    }

    private static int eval(List<String> tags, List<Token> rpn) {
        Stack<Boolean> stack = new Stack<>();
        for (Token token : rpn) {
            if (token.kind == Kind.TAG) {
                stack.push(matches(tags, token.content));
            } else if (token.kind == Kind.UNARY) {
                if (stack.isEmpty()) {
                    return 0;
                }
                stack.push(!stack.pop());
            } else if (token.kind == Kind.BINARY) {
                if (stack.size() < 2) {
                    return 0;
                }
                boolean right = stack.pop();
                boolean left = stack.pop();
                stack.push(switch (token.op) {
                    case AND -> left && right;
                    case OR -> left || right;
                    case XOR -> left ^ right;
                    case NOT -> false;
                });
            }
        }
        return stack.size() == 1 ? stack.pop() ? 1 : -1 : 0;
    }

    private static boolean matches(List<String> tags, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }
        String regex = pattern.replace("*", ".*");
        for (String tag : tags) {
            if (tag.equals(pattern) || tag.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    private static List<Token> tokenize(String input) {
        var tokens = new ArrayList<Token>();
        var tag = new StringBuilder();
        String withTerminator = input + " ";
        for (int i = 0; i < withTerminator.length(); i++) {
            char c = withTerminator.charAt(i);
            if (isTagChar(c)) {
                tag.append(c);
                continue;
            }
            if (!tag.isEmpty()) {
                tokens.add(Token.tag(tag.toString()));
                tag.setLength(0);
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            switch (c) {
                case '&' -> tokens.add(Token.binary(Op.AND));
                case '|' -> tokens.add(Token.binary(Op.OR));
                case '^' -> tokens.add(Token.binary(Op.XOR));
                case '!' -> tokens.add(Token.unary(Op.NOT));
                case '(' -> tokens.add(new Token(Kind.LEFT, null, null));
                case ')' -> tokens.add(new Token(Kind.RIGHT, null, null));
                default -> {
                    return List.of();
                }
            }
        }
        return tokens;
    }

    private static List<Token> toRpn(List<Token> tokens) {
        var output = new ArrayList<Token>();
        var ops = new Stack<Token>();
        Kind previous = Kind.START;
        for (Token token : tokens) {
            if (!validAfter(previous, token.kind)) {
                return List.of();
            }
            previous = token.kind;
            switch (token.kind) {
                case TAG -> output.add(token);
                case UNARY, BINARY -> {
                    while (!ops.isEmpty() && ops.peek().op != null
                        && ops.peek().op.priority >= token.op.priority
                        && !token.op.rightAssociative) {
                        output.add(ops.pop());
                    }
                    ops.push(token);
                }
                case LEFT -> ops.push(token);
                case RIGHT -> {
                    while (!ops.isEmpty() && ops.peek().kind != Kind.LEFT) {
                        output.add(ops.pop());
                    }
                    if (ops.isEmpty()) {
                        return List.of();
                    }
                    ops.pop();
                }
                case START -> {
                }
            }
        }
        while (!ops.isEmpty()) {
            Token token = ops.pop();
            if (token.kind == Kind.LEFT) {
                return List.of();
            }
            output.add(token);
        }
        return output;
    }

    private static boolean validAfter(Kind previous, Kind next) {
        return switch (previous) {
            case START, UNARY, BINARY, LEFT -> next == Kind.UNARY || next == Kind.LEFT || next == Kind.TAG;
            case TAG, RIGHT -> next == Kind.BINARY || next == Kind.RIGHT;
        };
    }

    private static boolean isTagChar(char c) {
        return c == ':' || c == '*' || c == '_' || c == '-' || c == '/' || c == '.'
            || Character.isLetterOrDigit(c);
    }

    private enum Kind {
        START,
        UNARY,
        BINARY,
        TAG,
        LEFT,
        RIGHT
    }

    private enum Op {
        OR(0, false),
        XOR(1, false),
        AND(2, false),
        NOT(3, true);

        final int priority;
        final boolean rightAssociative;

        Op(int priority, boolean rightAssociative) {
            this.priority = priority;
            this.rightAssociative = rightAssociative;
        }
    }

    private record Token(Kind kind, Op op, String content) {
        static Token tag(String content) {
            return new Token(Kind.TAG, null, content);
        }

        static Token unary(Op op) {
            return new Token(Kind.UNARY, op, null);
        }

        static Token binary(Op op) {
            return new Token(Kind.BINARY, op, null);
        }
    }
}
