package cn.slainc.utils.date;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CronUtils {

    private static final Map<String, Integer> MONTH_NAMES = new HashMap<>();
    private static final Map<String, Integer> DAY_NAMES = new HashMap<>();

    static {
        String[] months = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        for (int i = 0; i < months.length; i++) {
            MONTH_NAMES.put(months[i], i + 1);
        }
        String[] days = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        for (int i = 0; i < days.length; i++) {
            DAY_NAMES.put(days[i], i);
        }
    }

    private CronUtils() {
    }

    public static Optional<LocalDateTime> next(String expression) {
        return next(expression, LocalDateTime.now());
    }

    public static Optional<LocalDateTime> next(String expression, LocalDateTime from) {
        if (from == null) {
            return Optional.empty();
        }
        Spec spec = Spec.parse(expression);
        LocalDateTime candidate = from.plusSeconds(1).withNano(0);
        LocalDateTime limit = candidate.plusYears(4);
        while (!candidate.isAfter(limit)) {
            if (!spec.months.get(candidate.getMonthValue())) {
                candidate = candidate.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }
            if (!spec.matchesDay(candidate)) {
                candidate = candidate.plusDays(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }
            if (!spec.hours.get(candidate.getHour())) {
                candidate = candidate.plusHours(1).withMinute(0).withSecond(0);
                continue;
            }
            if (!spec.minutes.get(candidate.getMinute())) {
                candidate = candidate.plusMinutes(1).withSecond(0);
                continue;
            }
            if (!spec.seconds.get(candidate.getSecond())) {
                candidate = candidate.plusSeconds(1);
                continue;
            }
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public static boolean matches(String expression, LocalDateTime time) {
        if (time == null) {
            return false;
        }
        Spec spec = Spec.parse(expression);
        return spec.matchesDay(time)
                && spec.months.get(time.getMonthValue())
                && spec.hours.get(time.getHour())
                && spec.minutes.get(time.getMinute())
                && spec.seconds.get(time.getSecond());
    }

    public static boolean isValid(String expression) {
        try {
            Spec.parse(expression);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static final class Spec {

        private final BitSet seconds;
        private final BitSet minutes;
        private final BitSet hours;
        private final BitSet daysOfMonth;
        private final BitSet months;
        private final BitSet daysOfWeek;
        private final boolean dayOfMonthRestricted;
        private final boolean dayOfWeekRestricted;

        private Spec(BitSet seconds, BitSet minutes, BitSet hours, BitSet daysOfMonth,
                     BitSet months, BitSet daysOfWeek, boolean dayOfMonthRestricted, boolean dayOfWeekRestricted) {
            this.seconds = seconds;
            this.minutes = minutes;
            this.hours = hours;
            this.daysOfMonth = daysOfMonth;
            this.months = months;
            this.daysOfWeek = daysOfWeek;
            this.dayOfMonthRestricted = dayOfMonthRestricted;
            this.dayOfWeekRestricted = dayOfWeekRestricted;
        }

        private static Spec parse(String expression) {
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException("cron 表达式不能为空");
            }
            String[] fields = expression.trim().split("\\s+");
            String secondField;
            String minuteField;
            String hourField;
            String dayOfMonthField;
            String monthField;
            String dayOfWeekField;
            if (fields.length == 6) {
                secondField = fields[0];
                minuteField = fields[1];
                hourField = fields[2];
                dayOfMonthField = fields[3];
                monthField = fields[4];
                dayOfWeekField = fields[5];
            } else if (fields.length == 5) {
                secondField = "0";
                minuteField = fields[0];
                hourField = fields[1];
                dayOfMonthField = fields[2];
                monthField = fields[3];
                dayOfWeekField = fields[4];
            } else {
                throw new IllegalArgumentException("cron 表达式必须为 5 段或 6 段");
            }
            BitSet seconds = parseField(secondField, 0, 59, null);
            BitSet minutes = parseField(minuteField, 0, 59, null);
            BitSet hours = parseField(hourField, 0, 23, null);
            BitSet daysOfMonth = parseField(dayOfMonthField, 1, 31, null);
            BitSet months = parseField(monthField, 1, 12, MONTH_NAMES);
            BitSet daysOfWeek = parseField(dayOfWeekField, 0, 7, DAY_NAMES);
            if (daysOfWeek.get(7)) {
                daysOfWeek.set(0);
                daysOfWeek.clear(7);
            }
            boolean dayOfMonthRestricted = !isWildcard(dayOfMonthField);
            boolean dayOfWeekRestricted = !isWildcard(dayOfWeekField);
            return new Spec(seconds, minutes, hours, daysOfMonth, months, daysOfWeek,
                    dayOfMonthRestricted, dayOfWeekRestricted);
        }

        private boolean matchesDay(LocalDateTime time) {
            boolean dayOfMonthMatch = daysOfMonth.get(time.getDayOfMonth());
            boolean dayOfWeekMatch = daysOfWeek.get(time.getDayOfWeek().getValue() % 7);
            if (dayOfMonthRestricted && dayOfWeekRestricted) {
                return dayOfMonthMatch || dayOfWeekMatch;
            }
            if (dayOfMonthRestricted) {
                return dayOfMonthMatch;
            }
            if (dayOfWeekRestricted) {
                return dayOfWeekMatch;
            }
            return true;
        }

        private static boolean isWildcard(String field) {
            String trimmed = field.trim();
            return trimmed.equals("*") || trimmed.equals("?");
        }

        private static BitSet parseField(String field, int min, int max, Map<String, Integer> names) {
            BitSet bits = new BitSet(max + 1);
            for (String part : field.split(",")) {
                parsePart(part.trim(), min, max, names, bits);
            }
            if (bits.isEmpty()) {
                throw new IllegalArgumentException("cron 字段解析结果为空: " + field);
            }
            return bits;
        }

        private static void parsePart(String part, int min, int max, Map<String, Integer> names, BitSet bits) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("cron 字段存在空片段");
            }
            int step = 1;
            String range = part;
            int slash = part.indexOf('/');
            if (slash >= 0) {
                range = part.substring(0, slash);
                step = Integer.parseInt(part.substring(slash + 1).trim());
                if (step <= 0) {
                    throw new IllegalArgumentException("cron 步长必须为正数: " + part);
                }
            }
            int from;
            int to;
            if (range.equals("*") || range.equals("?")) {
                from = min;
                to = max;
            } else {
                int dash = range.indexOf('-', 1);
                if (dash > 0) {
                    from = resolve(range.substring(0, dash).trim(), names);
                    to = resolve(range.substring(dash + 1).trim(), names);
                } else {
                    from = resolve(range.trim(), names);
                    to = slash >= 0 ? max : from;
                }
            }
            if (from < min || to > max || from > to) {
                throw new IllegalArgumentException("cron 字段取值超出范围: " + part);
            }
            for (int value = from; value <= to; value += step) {
                bits.set(value);
            }
        }

        private static int resolve(String token, Map<String, Integer> names) {
            if (names != null) {
                Integer named = names.get(token.toUpperCase());
                if (named != null) {
                    return named;
                }
            }
            return Integer.parseInt(token);
        }
    }
}
