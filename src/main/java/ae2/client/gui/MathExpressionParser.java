package ae2.client.gui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public class MathExpressionParser {
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
    private static final BigDecimal ONE_BILLION = BigDecimal.valueOf(1e9);
    private static final BigDecimal KILO = BigDecimal.valueOf(1_000L);
    private static final BigDecimal MEGA = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal GIGA = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal TERA = BigDecimal.valueOf(1_000_000_000_000L);
    private static final BigDecimal PETA = BigDecimal.valueOf(1_000_000_000_000_000L);
    private static final BigDecimal EXA = BigDecimal.valueOf(1_000_000_000_000_000_000L);

    public static Optional<BigDecimal> parse(String expression, DecimalFormat decimalFormat) {
        List<Object> output = new ObjectArrayList<>();
        Deque<Character> operatorStack = new ArrayDeque<>();
        boolean wasNumberOrRightBracket = false;

        for (int i = 0; i < expression.length(); ) {
            if (Character.isWhitespace(expression.charAt(i))) {
                i++;
                continue;
            }

            if (!wasNumberOrRightBracket && expression.charAt(i) != '-') {
                var parsedNumber = parseNumber(expression, i, decimalFormat);
                if (parsedNumber.isPresent()) {
                    output.add(parsedNumber.get().Value());
                    i = parsedNumber.get().nextIndex();
                    wasNumberOrRightBracket = true;
                    continue;
                }
            }

            char currentOperator = expression.charAt(i);
            if (currentOperator == '-' && !wasNumberOrRightBracket) {
                currentOperator = 'u';
            }

            wasNumberOrRightBracket = false;

            switch (currentOperator) {
                case '(', 'u' -> operatorStack.push(currentOperator);
                case ')' -> {
                    while (true) {
                        if (operatorStack.isEmpty()) {
                            return Optional.empty();
                        }
                        char operator = operatorStack.pop();
                        if (operator == '(') {
                            break;
                        } else {
                            output.add(operator);
                        }
                    }
                    wasNumberOrRightBracket = true;
                }
                case '+', '-', '*', '/', '^' -> {
                    while (!operatorStack.isEmpty()) {
                        char operator = operatorStack.peek();
                        if (operator != '(' && precedenceCheck(operator, currentOperator)) {
                            operatorStack.pop();
                            output.add(operator);
                        } else {
                            break;
                        }
                    }
                    operatorStack.push(currentOperator);
                }
                default -> {
                    return Optional.empty();
                }
            }
            i++;
        }

        while (!operatorStack.isEmpty()) {
            output.add(operatorStack.pop());
        }

        Deque<BigDecimal> number = new ArrayDeque<>();

        for (Object object : output) {
            if (object instanceof BigDecimal bigDecimal) {
                number.push(bigDecimal);
            } else {
                char currentOperator = (char) object;
                if (currentOperator != 'u') {
                    if (number.size() < 2) {
                        return Optional.empty();
                    } else {
                        BigDecimal right = number.pop();
                        BigDecimal left = number.pop();
                        switch (currentOperator) {
                            case '+' -> number.push(right.add(left));
                            case '*' -> number.push(right.multiply(left));
                            case '-' -> number.push(left.subtract(right));
                            case '/' -> {
                                if (right.compareTo(BigDecimal.ZERO) == 0) {
                                    return Optional.empty();
                                } else {
                                    number.push(left.divide(right, 8, RoundingMode.FLOOR));
                                }
                            }
                            case '^' -> {
                                right = right.stripTrailingZeros();
                                if (right.scale() > 0 || right.compareTo(BigDecimal.ZERO) < 0) {
                                    return Optional.empty();
                                }
                                if (right.compareTo(THIRTY) > 0) {
                                    return Optional.empty();
                                }
                                if (left.compareTo(ONE_BILLION) > 0) {
                                    return Optional.empty();
                                }
                                number.push(left.pow(right.intValueExact()));
                            }
                            case '(', ')' -> {
                                return Optional.empty();
                            }
                            default -> throw new IllegalStateException("Unreachable character : " + currentOperator);
                        }
                    }
                } else {
                    if (number.isEmpty()) {
                        return Optional.empty();
                    } else {
                        number.push(number.pop().negate());
                    }
                }
            }
        }

        if (number.size() != 1) {
            return Optional.empty();
        } else {
            return Optional.of(number.pop().stripTrailingZeros());
        }
    }

    private static int getPrecedence(char operator) {
        return switch (operator) {
            case '^' -> -1;
            case 'u' -> 0;
            case '/', '*' -> 1;
            case '+', '-' -> 2;
            default -> throw new IllegalArgumentException("Invalid Operator : " + operator);
        };
    }

    private static boolean precedenceCheck(char first, char second) {
        return getPrecedence(first) <= getPrecedence(second);
    }

    private static Optional<ParsedNumber> parseNumber(String expression, int start, DecimalFormat decimalFormat) {
        var position = new ParsePosition(start);
        Number parsedNumber = decimalFormat.parse(expression, position);
        if (position.getErrorIndex() != -1 || !(parsedNumber instanceof BigDecimal decimal)) {
            return Optional.empty();
        }

        int next = position.getIndex();
        if (next < expression.length()
            && (expression.charAt(next) == 'e' || expression.charAt(next) == 'E')
            && isScientificNotationExponent(expression, next + 1)) {
            int exponentStart = next + 1;
            int exponentIndex = exponentStart;
            if (exponentIndex < expression.length()
                && (expression.charAt(exponentIndex) == '+' || expression.charAt(exponentIndex) == '-')) {
                exponentIndex++;
            }

            int exponentDigitsStart = exponentIndex;
            while (exponentIndex < expression.length() && Character.isDigit(expression.charAt(exponentIndex))) {
                exponentIndex++;
            }

            if (exponentIndex == exponentDigitsStart) {
                return Optional.empty();
            }

            try {
                int exponent = Integer.parseInt(expression.substring(exponentStart, exponentIndex));
                decimal = decimal.scaleByPowerOfTen(exponent);
                next = exponentIndex;
            } catch (NumberFormatException | ArithmeticException ignored) {
                return Optional.empty();
            }
        }

        if (next < expression.length()) {
            BigDecimal suffixMultiplier = getSuffixMultiplier(expression.charAt(next));
            if (suffixMultiplier != null) {
                decimal = decimal.multiply(suffixMultiplier);
                next++;
            }
        }

        return Optional.of(new ParsedNumber(decimal, next));
    }

    private static boolean isScientificNotationExponent(String expression, int exponentStart) {
        int exponentIndex = exponentStart;
        if (exponentIndex < expression.length()
            && (expression.charAt(exponentIndex) == '+' || expression.charAt(exponentIndex) == '-')) {
            exponentIndex++;
        }

        return exponentIndex < expression.length() && Character.isDigit(expression.charAt(exponentIndex));
    }

    private static BigDecimal getSuffixMultiplier(char suffix) {
        return switch (Character.toLowerCase(suffix)) {
            case 'k' -> KILO;
            case 'm' -> MEGA;
            case 'g' -> GIGA;
            case 't' -> TERA;
            case 'p' -> PETA;
            case 'e' -> EXA;
            default -> null;
        };
    }

    private record ParsedNumber(BigDecimal Value, int nextIndex) {
    }
}
