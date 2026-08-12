package cn.slainc.utils.date;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateUtilsTest {

    @Test
    void formatAndParse() {
        LocalDateTime time = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        assertEquals("2024-01-15 10:30:00", DateUtils.format(time));
        assertEquals("2024-01-15", DateUtils.format(time.toLocalDate()));
        assertEquals(time, DateUtils.parseDateTime("2024-01-15 10:30:00"));
        assertEquals(LocalDate.of(2024, 1, 15), DateUtils.parseDate("2024-01-15"));
        assertEquals(LocalDate.of(2024, 1, 15), DateUtils.parseDate("20240115"));
        assertNull(DateUtils.parseDateTime("not-a-date"));
        assertNull(DateUtils.parseDate(null));
    }

    @Test
    void betweenCalculations() {
        assertEquals(30L, DateUtils.betweenDays(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));
        assertEquals(1L, DateUtils.betweenMonths(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)));
        assertEquals(1L, DateUtils.betweenYears(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1)));
        assertEquals(60L, DateUtils.betweenSeconds(
                LocalDateTime.of(2024, 1, 1, 0, 0, 0), LocalDateTime.of(2024, 1, 1, 0, 1, 0)));
    }

    @Test
    void relativeTime() {
        LocalDateTime now = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        assertEquals("刚刚", DateUtils.relativeTime(now.minusSeconds(3), now));
        assertEquals("3 分钟前", DateUtils.relativeTime(now.minusMinutes(3), now));
        assertEquals("2 小时前", DateUtils.relativeTime(now.minusHours(2), now));
        assertEquals("1 天前", DateUtils.relativeTime(now.minusDays(1), now));
        assertEquals("3 分钟后", DateUtils.relativeTime(now.plusMinutes(3), now));
    }

    @Test
    void epochRoundTrip() {
        LocalDateTime time = LocalDateTime.of(2024, 6, 1, 8, 30, 15);
        assertEquals(time, DateUtils.fromEpochSecond(DateUtils.toEpochSecond(time)));
        assertEquals(time, DateUtils.fromEpochMilli(DateUtils.toEpochMilli(time)));
    }

    @Test
    void boundaries() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        assertEquals(LocalDate.of(2024, 1, 1), DateUtils.startOfMonth(date));
        assertEquals(LocalDate.of(2024, 1, 31), DateUtils.endOfMonth(date));
        assertEquals(LocalDate.of(2024, 1, 15), DateUtils.startOfWeek(date));
        assertEquals(LocalDate.of(2024, 1, 21), DateUtils.endOfWeek(date));
        assertTrue(DateUtils.isSameDay(date.atStartOfDay(), date.atTime(23, 59)));
        ZoneId shanghai = DateUtils.ZONE_SHANGHAI;
        LocalDateTime utcNoon = LocalDateTime.of(2024, 1, 1, 4, 0, 0);
        assertEquals(LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                DateUtils.convertZone(utcNoon, DateUtils.ZONE_UTC, shanghai));
    }

    @Test
    void isBetween() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 1, 31, 0, 0);
        assertTrue(DateUtils.isBetween(LocalDateTime.of(2024, 1, 15, 0, 0), start, end));
        assertFalse(DateUtils.isBetween(LocalDateTime.of(2024, 2, 1, 0, 0), start, end));
    }
}
