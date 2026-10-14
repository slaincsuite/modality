package cn.slainc.policy.operator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import cn.slainc.policy.match.WildcardMatcher;
import cn.slainc.policy.domain.ConditionOperator;
import inet.ipaddr.IPAddressString;

public final class ConditionEvaluators {

    private static final Map<ConditionOperator, BiPredicate<Object, List<String>>> EVALUATORS =
            new EnumMap<>(ConditionOperator.class);

    static {
        EVALUATORS.put(ConditionOperator.STRING_EQUAL, (actual, expected) ->
                actual != null && expected.stream().anyMatch(value -> value.equals(String.valueOf(actual))));
        EVALUATORS.put(ConditionOperator.STRING_NOT_EQUAL, (actual, expected) ->
                actual != null && expected.stream().noneMatch(value -> value.equals(String.valueOf(actual))));
        EVALUATORS.put(ConditionOperator.STRING_LIKE, (actual, expected) ->
                actual != null && expected.stream().anyMatch(value -> WildcardMatcher.match(value, String.valueOf(actual))));
        EVALUATORS.put(ConditionOperator.NUMERIC_EQUAL, (actual, expected) -> compareNumeric(actual, expected, 0));
        EVALUATORS.put(ConditionOperator.NUMERIC_LESS_THAN, (actual, expected) -> compareNumeric(actual, expected, -1));
        EVALUATORS.put(ConditionOperator.NUMERIC_GREATER_THAN, (actual, expected) -> compareNumeric(actual, expected, 1));
        EVALUATORS.put(ConditionOperator.DATE_LESS_THAN, (actual, expected) -> compareDate(actual, expected, -1));
        EVALUATORS.put(ConditionOperator.DATE_GREATER_THAN, (actual, expected) -> compareDate(actual, expected, 1));
        EVALUATORS.put(ConditionOperator.BOOL_EQUAL, (actual, expected) -> {
            Boolean actualValue = parseBoolean(actual);
            return actualValue != null && expected.stream().map(ConditionEvaluators::parseBoolean)
                    .anyMatch(value -> value != null && value.equals(actualValue));
        });
        EVALUATORS.put(ConditionOperator.IP_EQUAL, (actual, expected) ->
                safeIpCheck(() -> ipEquals(actual, expected)));
        EVALUATORS.put(ConditionOperator.IP_IN_RANGE, (actual, expected) ->
                safeIpCheck(() -> ipInRange(actual, expected)));
        EVALUATORS.put(ConditionOperator.NULL_EQUAL, (actual, expected) -> expected.stream()
                .map(ConditionEvaluators::parseBoolean)
                .anyMatch(value -> value != null && value == (actual == null)));
    }

    private ConditionEvaluators() {
    }

    public static boolean evaluate(ConditionOperator operator, Object actual, List<String> expected) {
        List<String> values = expected == null ? List.of()
                : expected.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
        if (values.isEmpty()) {
            return false;
        }
        return EVALUATORS.getOrDefault(operator, (a, e) -> false).test(actual, values);
    }

    public static BigDecimal parseNumeric(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static Instant parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.chars().allMatch(Character::isDigit)) {
            return Instant.ofEpochMilli(Long.parseLong(text));
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static Boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    public static IPAddressString parseAddress(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new IPAddressString(String.valueOf(value).trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean ipEquals(Object actual, List<String> expected) {
        if (actual == null) {
            return false;
        }
        IPAddressString actualAddress = parseAddress(String.valueOf(actual));
        if (actualAddress == null || actualAddress.getAddress() == null) {
            return false;
        }
        return expected.stream().map(ConditionEvaluators::parseAddress)
                .anyMatch(address -> address != null && address.getAddress() != null
                        && address.getAddress().equals(actualAddress.getAddress()));
    }

    private static boolean ipInRange(Object actual, List<String> expected) {
        if (actual == null) {
            return false;
        }
        IPAddressString actualAddress = parseAddress(String.valueOf(actual));
        if (actualAddress == null) {
            return false;
        }
        return expected.stream().map(ConditionEvaluators::parseAddress)
                .anyMatch(network -> network != null && network.contains(actualAddress));
    }

    private static boolean safeIpCheck(java.util.function.BooleanSupplier check) {
        try {
            return check.getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean compareNumeric(Object actual, List<String> expected, int expectedSignum) {
        BigDecimal actualValue = parseNumeric(actual);
        if (actualValue == null) {
            return false;
        }
        return expected.stream().map(ConditionEvaluators::parseNumeric)
                .anyMatch(value -> value != null && Integer.signum(actualValue.compareTo(value)) == expectedSignum);
    }

    private static boolean compareDate(Object actual, List<String> expected, int expectedSignum) {
        Instant actualValue = parseDate(actual);
        if (actualValue == null) {
            return false;
        }
        return expected.stream().map(ConditionEvaluators::parseDate)
                .anyMatch(value -> value != null
                        && Integer.signum(actualValue.compareTo(value)) == expectedSignum);
    }
}
