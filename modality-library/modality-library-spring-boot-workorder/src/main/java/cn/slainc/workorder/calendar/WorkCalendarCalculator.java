package cn.slainc.workorder.calendar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;


public class WorkCalendarCalculator {

    private final WorkCalendarDefinition calendar;

    public WorkCalendarCalculator(WorkCalendarDefinition calendar) {
        this.calendar = calendar;
    }

    public long workingMillisBetween(Instant start, Instant end) {
        if (end.isBefore(start)) {
            return 0;
        }
        long total = 0;
        ZonedDateTime cursor = start.atZone(calendar.timezone());
        ZonedDateTime limit = end.atZone(calendar.timezone());
        while (cursor.isBefore(limit)) {
            if (!calendar.isWorkingDay(cursor.toLocalDate())) {
                cursor = nextDayStart(cursor);
                continue;
            }
            ZonedDateTime dayWorkStart = dayAt(cursor.toLocalDate(), calendar.workStart());
            ZonedDateTime dayWorkEnd = dayAt(cursor.toLocalDate(), calendar.workEnd());
            if (cursor.isBefore(dayWorkStart)) {
                cursor = dayWorkStart;
            }
            if (!cursor.isBefore(dayWorkEnd)) {
                cursor = nextDayStart(cursor);
                continue;
            }
            ZonedDateTime segmentEnd = dayWorkEnd.isBefore(limit) ? dayWorkEnd : limit;
            total += Duration.between(cursor, segmentEnd).toMillis();
            if (segmentEnd.isEqual(limit)) {
                break;
            }
            cursor = nextDayStart(cursor);
        }
        return total;
    }

    public Instant addWorkingMillis(Instant from, long workingMillis) {
        ZonedDateTime cursor = from.atZone(calendar.timezone());
        long remaining = workingMillis;
        while (remaining > 0) {
            if (!calendar.isWorkingDay(cursor.toLocalDate())) {
                cursor = nextDayStart(cursor);
                continue;
            }
            ZonedDateTime dayWorkStart = dayAt(cursor.toLocalDate(), calendar.workStart());
            ZonedDateTime dayWorkEnd = dayAt(cursor.toLocalDate(), calendar.workEnd());
            if (cursor.isBefore(dayWorkStart)) {
                cursor = dayWorkStart;
            }
            if (!cursor.isBefore(dayWorkEnd)) {
                cursor = nextDayStart(cursor);
                continue;
            }
            long available = Duration.between(cursor, dayWorkEnd).toMillis();
            if (available >= remaining) {
                return cursor.plusNanos(remaining * 1_000_000L).toInstant();
            }
            remaining -= available;
            cursor = nextDayStart(cursor);
        }
        return cursor.toInstant();
    }

    private ZonedDateTime dayAt(java.time.LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(calendar.timezone());
    }

    private ZonedDateTime nextDayStart(ZonedDateTime cursor) {
        return cursor.toLocalDate().plusDays(1).atStartOfDay(calendar.timezone());
    }
}
