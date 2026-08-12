package cn.slainc.utils.date;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CronUtilsTest {

    @Test
    void nextDailyAtNoon() {
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        assertEquals(LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                CronUtils.next("0 0 12 * * ?", from).orElseThrow());
        assertEquals(LocalDateTime.of(2024, 1, 2, 12, 0, 0),
                CronUtils.next("0 0 12 * * ?", LocalDateTime.of(2024, 1, 1, 12, 0, 0)).orElseThrow());
    }

    @Test
    void nextEveryFifteenSeconds() {
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 15),
                CronUtils.next("*/15 * * * * ?", from).orElseThrow());
    }

    @Test
    void nextWeeklyOnMonday() {
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        LocalDateTime next = CronUtils.next("0 30 8 ? * MON", from).orElseThrow();
        assertEquals(8, next.getHour());
        assertEquals(30, next.getMinute());
        assertEquals(java.time.DayOfWeek.MONDAY, next.getDayOfWeek());
        assertTrue(next.isAfter(from));
    }

    @Test
    void fiveFieldExpressionSupported() {
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        assertEquals(LocalDateTime.of(2024, 1, 1, 12, 0, 0), CronUtils.next("0 12 * * ?", from).orElseThrow());
    }

    @Test
    void validation() {
        assertTrue(CronUtils.isValid("0 0 12 * * ?"));
        assertTrue(CronUtils.isValid("*/15 * * * * ?"));
        assertFalse(CronUtils.isValid("not a cron"));
        assertFalse(CronUtils.isValid(""));
        assertFalse(CronUtils.isValid("0 0 99 * * ?"));
    }

    @Test
    void matches() {
        assertTrue(CronUtils.matches("0 0 12 * * ?", LocalDateTime.of(2024, 1, 1, 12, 0, 0)));
        assertFalse(CronUtils.matches("0 0 12 * * ?", LocalDateTime.of(2024, 1, 1, 13, 0, 0)));
    }
}
