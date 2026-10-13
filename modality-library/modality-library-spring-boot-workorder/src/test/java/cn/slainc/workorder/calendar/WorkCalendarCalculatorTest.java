package cn.slainc.workorder.calendar;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;


class WorkCalendarCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private WorkCalendarDefinition calendar() {
        return new WorkCalendarDefinition("std", "标准日历", ZONE,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY),
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                Set.of(LocalDate.of(2026, 10, 19)),
                Set.of());
    }

    private Instant at(String localDateTime) {
        return java.time.OffsetDateTime.parse(localDateTime).toInstant();
    }

    @Test
    void weekendGapContributesNothingWithoutSpecialWorkday() {
        WorkCalendarCalculator calculator = new WorkCalendarCalculator(calendar());
        long millis = calculator.workingMillisBetween(at("2026-10-16T17:00:00+08:00"),
                at("2026-10-19T10:00:00+08:00"));
        assertThat(millis).isEqualTo(Duration.ofHours(1).toMillis());
    }

    @Test
    void specialWorkdayCountsWeekend() {
        WorkCalendarDefinition calendar = new WorkCalendarDefinition("special", "含调休", ZONE,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY),
                LocalTime.of(9, 0), LocalTime.of(18, 0), Set.of(), Set.of(LocalDate.of(2026, 10, 17)));
        WorkCalendarCalculator calculator = new WorkCalendarCalculator(calendar);
        long millis = calculator.workingMillisBetween(at("2026-10-16T17:00:00+08:00"),
                at("2026-10-17T10:00:00+08:00"));
        assertThat(millis).isEqualTo(Duration.ofHours(2).toMillis());
    }

    @Test
    void holidayMondayIsSkipped() {
        WorkCalendarCalculator calculator = new WorkCalendarCalculator(calendar());
        long millis = calculator.workingMillisBetween(at("2026-10-16T16:00:00+08:00"),
                at("2026-10-20T10:00:00+08:00"));
        assertThat(millis).isEqualTo(Duration.ofHours(3).toMillis());
    }

    @Test
    void addWorkingMillisCrossesWeekend() {
        WorkCalendarCalculator calculator = new WorkCalendarCalculator(calendar());
        Instant deadline = calculator.addWorkingMillis(at("2026-10-16T17:00:00+08:00"),
                Duration.ofHours(2).toMillis());
        assertThat(deadline).isEqualTo(at("2026-10-20T10:00:00+08:00"));
    }

    @Test
    void startInsideNonWorkingTimeJumpsToWorkStart() {
        WorkCalendarCalculator calculator = new WorkCalendarCalculator(calendar());
        Instant deadline = calculator.addWorkingMillis(at("2026-10-16T03:00:00+08:00"),
                Duration.ofHours(1).toMillis());
        assertThat(deadline).isEqualTo(at("2026-10-16T10:00:00+08:00"));
    }
}
