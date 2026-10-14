package cn.slainc.job.support;

import org.quartz.CronExpression;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

// Spring 六段 cron 到 Quartz 七段 cron 的安全转换：仅当触发时间语义完全一致时才转换
public final class QuartzCronSupport {

    private static final String[] SPRING_DAY_NAMES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

    private static final String[] QUARTZ_DAY_NAMES = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};

    private static final String[][] MACROS = {
            {"@yearly", "0 0 0 1 1 *"},
            {"@annually", "0 0 0 1 1 *"},
            {"@monthly", "0 0 0 1 * *"},
            {"@weekly", "0 0 0 * * 0"},
            {"@daily", "0 0 0 * * *"},
            {"@midnight", "0 0 0 * * *"},
            {"@hourly", "0 0 * * * *"}
    };

    private static final int SPRING_DAY_MAX = 7;

    private QuartzCronSupport() {
    }

    public static String toQuartz(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("cron 表达式不能为空");
        }
        String expression = resolveMacros(cron.trim());
        String[] fields = expression.split("\\s+");
        if (fields.length == 7) {
            requireQuartz(expression, cron);
            return expression;
        }
        if (fields.length != 6) {
            throw new IllegalArgumentException("cron 表达式必须是 6 段（Spring）或 7 段（带年份的 Quartz）：\"" + cron + "\"");
        }
        if (!isSpringExpression(expression)) {
            requireQuartz(expression, cron);
            return expression;
        }
        return convertSpring(fields, cron);
    }

    public static boolean isConvertible(String cron) {
        try {
            toQuartz(cron);
            return true;
        } catch (RuntimeException e) {
            // 转换失败即视为不可用
            return false;
        }
    }

    public static List<LocalDateTime> nextExecutions(String cron, int count) {
        List<LocalDateTime> result = new ArrayList<>();
        if (count <= 0) {
            return result;
        }
        CronExpression expression;
        try {
            expression = new CronExpression(toQuartz(cron));
        } catch (IllegalArgumentException | ParseException e) {
            // 解析失败返回空列表，由调用方展示校验错误
            return result;
        }
        expression.setTimeZone(TimeZone.getDefault());
        Date next = new Date();
        for (int i = 0; i < count; i++) {
            next = expression.getNextValidTimeAfter(next);
            if (next == null) {
                break;
            }
            result.add(LocalDateTime.ofInstant(next.toInstant(), ZoneId.systemDefault()));
        }
        return result;
    }

    private static String convertSpring(String[] fields, String cron) {
        String dayOfMonth = fields[3];
        String dayOfWeek = fields[5];
        boolean monthAny = isAny(dayOfMonth);
        boolean weekAny = isAny(dayOfWeek);
        if (!monthAny && !weekAny) {
            throw new IllegalArgumentException("cron 表达式 \"" + cron + "\" 不能同时限定日（day-of-month）与星期"
                    + "（day-of-week）：Spring 两者取并集，Quartz 只支持其一，请把另一项写成 '*'");
        }
        String quartzDayOfMonth = weekAny ? (monthAny ? "*" : dayOfMonth) : "?";
        String quartzDayOfWeek = weekAny ? "?" : convertDayOfWeek(dayOfWeek, cron);
        String converted = fields[0] + ' ' + fields[1] + ' ' + fields[2] + ' ' + quartzDayOfMonth + ' '
                + fields[4] + ' ' + quartzDayOfWeek;
        requireQuartz(converted, cron);
        return converted;
    }

    private static String convertDayOfWeek(String field, String cron) {
        String upper = replaceNames(field.toUpperCase(Locale.ROOT));
        String[] tokens = upper.split(",");
        boolean hasLast = upper.indexOf('L') >= 0;
        boolean hasNth = upper.indexOf('#') >= 0;
        if (hasLast && hasNth) {
            throw unsupported(cron, "星期字段不能同时使用 'L' 和 '#'");
        }
        if (hasLast) {
            if (tokens.length != 1) {
                throw unsupported(cron, "Quartz 不支持 'L' 与其它星期值混用");
            }
            return quartzDayName(parseSpringDay(upper.substring(0, upper.length() - 1), cron)) + 'L';
        }
        if (hasNth) {
            if (tokens.length != 1) {
                throw unsupported(cron, "存在 '#' 时 Quartz 会忽略其它星期值，拒绝转换以避免语义变化");
            }
            int hash = upper.indexOf('#');
            if (hash <= 0 || hash == upper.length() - 1) {
                throw unsupported(cron, "非法的 '#' 星期片段：" + upper);
            }
            int day = parseSpringDay(upper.substring(0, hash), cron);
            int ordinal = parseNumber(upper.substring(hash + 1), cron);
            if (ordinal < 1) {
                throw unsupported(cron, "'#' 序号必须为正数：" + upper);
            }
            return quartzDayName(day) + '#' + ordinal;
        }
        boolean[] springDays = new boolean[SPRING_DAY_MAX + 1];
        for (String token : tokens) {
            expandDayToken(token, springDays, cron);
        }
        if (springDays[0]) {
            springDays[SPRING_DAY_MAX] = true;
        }
        boolean[] quartzDays = new boolean[QUARTZ_DAY_NAMES.length + 1];
        for (int day = 1; day <= SPRING_DAY_MAX; day++) {
            if (springDays[day]) {
                quartzDays[toQuartzDay(day)] = true;
            }
        }
        return renderDays(quartzDays, cron);
    }

    private static void expandDayToken(String token, boolean[] days, String cron) {
        if (token.isEmpty()) {
            throw unsupported(cron, "星期字段存在空片段");
        }
        int slash = token.indexOf('/');
        String range = slash < 0 ? token : token.substring(0, slash);
        int step = 1;
        if (slash >= 0) {
            step = parseNumber(token.substring(slash + 1), cron);
            if (step < 1) {
                throw unsupported(cron, "步长必须为正数：" + token);
            }
        }
        int from;
        int to;
        int dash = range.indexOf('-');
        if ("*".equals(range)) {
            from = 1;
            to = SPRING_DAY_MAX;
        } else if (dash < 0) {
            from = parseSpringDay(range, cron);
            to = slash < 0 ? from : SPRING_DAY_MAX;
        } else {
            from = parseSpringDay(range.substring(0, dash), cron);
            to = parseSpringDay(range.substring(dash + 1), cron);
            if (from == SPRING_DAY_MAX) {
                from = 0;
            }
        }
        if (to < from) {
            throw unsupported(cron, "星期区间必须递增：" + token);
        }
        for (int day = from; day <= to; day += step) {
            days[day] = true;
        }
    }

    private static String renderDays(boolean[] days, String cron) {
        StringBuilder builder = new StringBuilder();
        int day = 1;
        while (day <= QUARTZ_DAY_NAMES.length) {
            if (!days[day]) {
                day++;
                continue;
            }
            int end = day;
            while (end < QUARTZ_DAY_NAMES.length && days[end + 1]) {
                end++;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(QUARTZ_DAY_NAMES[day - 1]);
            if (end > day) {
                builder.append('-').append(QUARTZ_DAY_NAMES[end - 1]);
            }
            day = end + 1;
        }
        if (builder.length() == 0) {
            throw unsupported(cron, "星期字段没有选中任何一天");
        }
        return builder.toString();
    }

    private static int toQuartzDay(int springDay) {
        return springDay == SPRING_DAY_MAX ? 1 : springDay + 1;
    }

    private static String quartzDayName(int springDay) {
        int normalized = springDay == 0 ? SPRING_DAY_MAX : springDay;
        return QUARTZ_DAY_NAMES[toQuartzDay(normalized) - 1];
    }

    private static int parseSpringDay(String value, String cron) {
        int day = parseNumber(value, cron);
        if (day < 0 || day > SPRING_DAY_MAX) {
            throw unsupported(cron, "星期取值超出 0-7 范围：" + value);
        }
        return day;
    }

    private static int parseNumber(String value, String cron) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw unsupported(cron, "不是数字：\"" + value + "\"");
        }
    }

    private static String replaceNames(String value) {
        String result = value;
        for (int i = 0; i < SPRING_DAY_NAMES.length; i++) {
            result = result.replace(SPRING_DAY_NAMES[i], Integer.toString(i + 1));
        }
        return result;
    }

    private static String resolveMacros(String expression) {
        for (String[] macro : MACROS) {
            if (macro[0].equalsIgnoreCase(expression)) {
                return macro[1];
            }
        }
        return expression;
    }

    private static boolean isAny(String field) {
        return "*".equals(field) || "?".equals(field);
    }

    private static boolean isSpringExpression(String expression) {
        return org.springframework.scheduling.support.CronExpression.isValidExpression(expression);
    }

    private static void requireQuartz(String expression, String cron) {
        try {
            new CronExpression(expression);
        } catch (ParseException e) {
            throw new IllegalArgumentException("cron 表达式 \"" + cron + "\" 不是合法的 Quartz cron：" + e.getMessage(), e);
        }
    }

    private static IllegalArgumentException unsupported(String cron, String reason) {
        return new IllegalArgumentException("cron 表达式 \"" + cron + "\" 无法安全转换：" + reason);
    }
}
