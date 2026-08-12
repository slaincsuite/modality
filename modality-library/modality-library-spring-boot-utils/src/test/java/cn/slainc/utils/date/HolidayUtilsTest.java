package cn.slainc.utils.date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolidayUtilsTest {

    @AfterEach
    void tearDown() {
        HolidayUtils.clear();
    }

    @Test
    void weekendDetection() {
        assertTrue(HolidayUtils.isWeekend(LocalDate.of(2024, 1, 6)));
        assertTrue(HolidayUtils.isWeekend(LocalDate.of(2024, 1, 7)));
        assertFalse(HolidayUtils.isWeekend(LocalDate.of(2024, 1, 8)));
    }

    @Test
    void registeredHolidayAndMakeUpWorkday() {
        LocalDate holiday = LocalDate.of(2024, 1, 1);
        LocalDate makeUp = LocalDate.of(2024, 1, 6);
        HolidayUtils.registerHolidays(List.of(holiday));
        HolidayUtils.registerWorkdays(List.of(makeUp));
        assertTrue(HolidayUtils.isHoliday(holiday));
        assertFalse(HolidayUtils.isWorkday(holiday));
        assertTrue(HolidayUtils.isWorkday(makeUp));
    }

    @Test
    void workdaysBetween() {
        assertEquals(5L, HolidayUtils.workdaysBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)));
        assertEquals(0L, HolidayUtils.workdaysBetween(LocalDate.of(2024, 1, 7), LocalDate.of(2024, 1, 1)));
    }

    @Test
    void plusWorkdays() {
        assertEquals(LocalDate.of(2024, 1, 5), HolidayUtils.plusWorkdays(LocalDate.of(2024, 1, 1), 4));
        assertEquals(LocalDate.of(2024, 1, 1), HolidayUtils.plusWorkdays(LocalDate.of(2024, 1, 1), 0));
    }
}
