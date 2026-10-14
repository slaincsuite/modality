package cn.slainc.utils.date;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DateUtils {

    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATETIME_MS = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String PATTERN_COMPACT_DATE = "yyyyMMdd";
    public static final String PATTERN_COMPACT_DATETIME = "yyyyMMddHHmmss";

    public static final ZoneId ZONE_UTC = ZoneOffset.UTC;
    public static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final List<DateTimeFormatter> DATETIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern(PATTERN_DATETIME_MS),
            DateTimeFormatter.ofPattern(PATTERN_DATETIME),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern(PATTERN_COMPACT_DATETIME),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern(PATTERN_DATE),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern(PATTERN_COMPACT_DATE),
            DateTimeFormatter.ISO_LOCAL_DATE);

    private static final Map<String, DateTimeFormatter> PATTERN_CACHE = new ConcurrentHashMap<>();

    private DateUtils() {
    }

    private static DateTimeFormatter formatterOf(String pattern) {
        return PATTERN_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static String format(LocalDateTime time, String pattern) {
        return time == null ? null : time.format(formatterOf(pattern));
    }

    public static String format(LocalDateTime time) {
        return format(time, PATTERN_DATETIME);
    }

    public static String format(LocalDate date, String pattern) {
        return date == null ? null : date.format(formatterOf(pattern));
    }

    public static String format(LocalDate date) {
        return format(date, PATTERN_DATE);
    }

    public static String format(Date date, String pattern) {
        return date == null ? null : format(toLocalDateTime(date), pattern);
    }

    public static String format(Date date) {
        return format(date, PATTERN_DATETIME);
    }

    public static LocalDateTime parseDateTime(String text) {
        return parseDateTime(text, DATETIME_FORMATTERS);
    }

    public static LocalDateTime parseDateTime(String text, String pattern) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text.trim(), formatterOf(pattern));
    }

    public static LocalDateTime parseDateTime(String text, List<DateTimeFormatter> formatters) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                continue;
            }
        }
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                continue;
            }
        }
        LocalDateTime dateTime = parseDateTime(trimmed);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    public static LocalDate parseDate(String text, String pattern) {
        return text == null ? null : LocalDate.parse(text.trim(), formatterOf(pattern));
    }

    public static Date toDate(LocalDateTime time) {
        return time == null ? null : Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static long betweenDays(LocalDate start, LocalDate end) {
        return start == null || end == null ? 0L : ChronoUnit.DAYS.between(start, end);
    }

    public static long betweenMonths(LocalDate start, LocalDate end) {
        return start == null || end == null ? 0L : ChronoUnit.MONTHS.between(start, end);
    }

    public static long betweenYears(LocalDate start, LocalDate end) {
        return start == null || end == null ? 0L : ChronoUnit.YEARS.between(start, end);
    }

    public static long betweenSeconds(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0L : Duration.between(start, end).getSeconds();
    }

    public static long workdaysBetween(LocalDate start, LocalDate end) {
        return HolidayUtils.workdaysBetween(start, end);
    }

    public static LocalDateTime convertZone(LocalDateTime time, ZoneId from, ZoneId to) {
        if (time == null || from == null || to == null) {
            return time;
        }
        return time.atZone(from).withZoneSameInstant(to).toLocalDateTime();
    }

    public static LocalDateTime toUtc(LocalDateTime time) {
        return convertZone(time, ZoneId.systemDefault(), ZONE_UTC);
    }

    public static LocalDateTime fromUtc(LocalDateTime time) {
        return convertZone(time, ZONE_UTC, ZoneId.systemDefault());
    }

    public static LocalDateTime toShanghai(LocalDateTime time) {
        return convertZone(time, ZONE_UTC, ZONE_SHANGHAI);
    }

    public static String relativeTime(LocalDateTime time) {
        return relativeTime(time, LocalDateTime.now());
    }

    public static String relativeTime(LocalDateTime time, LocalDateTime reference) {
        if (time == null || reference == null) {
            return null;
        }
        boolean future = time.isAfter(reference);
        LocalDateTime from = future ? reference : time;
        LocalDateTime to = future ? time : reference;
        long seconds = Duration.between(from, to).getSeconds();
        String text;
        if (seconds < 60) {
            text = seconds <= 5 ? "刚刚" : seconds + " 秒";
        } else if (seconds < 3600) {
            text = seconds / 60 + " 分钟";
        } else if (seconds < 86400) {
            text = seconds / 3600 + " 小时";
        } else if (seconds < 2592000) {
            text = seconds / 86400 + " 天";
        } else if (seconds < 31536000) {
            text = seconds / 2592000 + " 个月";
        } else {
            text = seconds / 31536000 + " 年";
        }
        if (text.equals("刚刚")) {
            return text;
        }
        return future ? text + "后" : text + "前";
    }

    public static long toEpochSecond(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    public static long toEpochMilli(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static LocalDateTime fromEpochSecond(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault());
    }

    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    public static LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    public static LocalDateTime endOfDay(LocalDate date) {
        return date == null ? null : date.atTime(LocalTime.MAX);
    }

    public static LocalDate startOfMonth(LocalDate date) {
        return date == null ? null : date.with(TemporalAdjusters.firstDayOfMonth());
    }

    public static LocalDate endOfMonth(LocalDate date) {
        return date == null ? null : date.with(TemporalAdjusters.lastDayOfMonth());
    }

    public static LocalDate startOfWeek(LocalDate date) {
        return date == null ? null : date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    public static LocalDate endOfWeek(LocalDate date) {
        return date == null ? null : date.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
    }

    public static boolean isSameDay(LocalDateTime left, LocalDateTime right) {
        return left != null && right != null && left.toLocalDate().isEqual(right.toLocalDate());
    }

    public static boolean isBetween(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        if (time == null || start == null || end == null) {
            return false;
        }
        return !time.isBefore(start) && !time.isAfter(end);
    }
}
