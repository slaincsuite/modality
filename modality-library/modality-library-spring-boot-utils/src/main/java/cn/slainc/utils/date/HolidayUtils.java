package cn.slainc.utils.date;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HolidayUtils {

    private static final Set<LocalDate> HOLIDAYS = ConcurrentHashMap.newKeySet();
    private static final Set<LocalDate> WORKDAYS = ConcurrentHashMap.newKeySet();

    private HolidayUtils() {
    }

    public static void registerHolidays(Collection<LocalDate> dates) {
        if (dates != null) {
            HOLIDAYS.addAll(dates);
        }
    }

    public static void registerWorkdays(Collection<LocalDate> dates) {
        if (dates != null) {
            WORKDAYS.addAll(dates);
        }
    }

    public static void clear() {
        HOLIDAYS.clear();
        WORKDAYS.clear();
    }

    public static boolean isWeekend(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    public static boolean isHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (WORKDAYS.contains(date)) {
            return false;
        }
        return HOLIDAYS.contains(date) || isWeekend(date);
    }

    public static boolean isWorkday(LocalDate date) {
        return date != null && !isHoliday(date);
    }

    public static long workdaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            return 0L;
        }
        long count = 0L;
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            if (isWorkday(cursor)) {
                count++;
            }
        }
        return count;
    }

    public static LocalDate plusWorkdays(LocalDate start, int workdays) {
        if (start == null) {
            return null;
        }
        if (workdays == 0) {
            return start;
        }
        int step = workdays > 0 ? 1 : -1;
        int remaining = Math.abs(workdays);
        LocalDate cursor = start;
        while (remaining > 0) {
            cursor = cursor.plusDays(step);
            if (isWorkday(cursor)) {
                remaining--;
            }
        }
        return cursor;
    }
}
