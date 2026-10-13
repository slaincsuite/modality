package cn.slainc.workorder.sla;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;


class WorkOrderSlaCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-10-13T04:00:00Z");

    private SlaPolicyDefinition policy() {
        return new SlaPolicyDefinition("std-8h", "标准 8 小时", null, null,
                Duration.ofHours(1).toMillis(), Duration.ofHours(8).toMillis(), 75);
    }

    private WorkOrderSlaCalculator roundClockCalculator() {
        return new WorkOrderSlaCalculator(new cn.slainc.workorder.calendar.WorkCalendarDefinition("247", "全时",
                ZoneId.of("Asia/Shanghai"), Set.of(java.time.DayOfWeek.values()), LocalTime.MIN, LocalTime.MAX,
                Set.of(), Set.of()));
    }

    @Test
    void onTrackInsideWarnThreshold() {
        SlaSnapshot snapshot = roundClockCalculator().snapshot(policy(), T0, null, null,
                T0.plus(Duration.ofMinutes(30)));
        assertThat(snapshot.resolveState()).isEqualTo(SlaState.ON_TRACK);
        assertThat(snapshot.resolveRemainingMillis()).isEqualTo(Duration.ofHours(7).plusMinutes(30).toMillis());
        assertThat(snapshot.responseState()).isEqualTo(SlaState.ON_TRACK);
    }

    @Test
    void responseWarnsAtThresholdButResolveStaysOnTrack() {
        SlaSnapshot snapshot = roundClockCalculator().snapshot(policy(), T0, null, null,
                T0.plus(Duration.ofMinutes(50)));
        assertThat(snapshot.responseState()).isEqualTo(SlaState.WARNING);
        assertThat(snapshot.resolveState()).isEqualTo(SlaState.ON_TRACK);
    }

    @Test
    void responseBreachesWhileResolveStillOnTrack() {
        SlaSnapshot snapshot = roundClockCalculator().snapshot(policy(), T0, null, null,
                T0.plus(Duration.ofHours(2)));
        assertThat(snapshot.responseState()).isEqualTo(SlaState.BREACHED);
        assertThat(snapshot.resolveState()).isEqualTo(SlaState.ON_TRACK);
    }

    @Test
    void resolutionStopsTheClock() {
        SlaSnapshot snapshot = roundClockCalculator().snapshot(policy(), T0,
                T0.plus(Duration.ofMinutes(30)), T0.plus(Duration.ofHours(6)),
                T0.plus(Duration.ofHours(9)));
        assertThat(snapshot.resolveState()).isEqualTo(SlaState.ON_TRACK);
        assertThat(snapshot.resolveRemainingMillis()).isZero();
        assertThat(snapshot.responseRemainingMillis()).isZero();
    }

    @Test
    void resolveDeadlineFollowsCalendar() {
        cn.slainc.workorder.calendar.WorkCalendarDefinition nineToSix = new cn.slainc.workorder.calendar.WorkCalendarDefinition("std",
                "标准日历", ZoneId.of("Asia/Shanghai"),
                Set.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY),
                LocalTime.of(9, 0), LocalTime.of(18, 0), Set.of(), Set.of());
        WorkOrderSlaCalculator calculator = new WorkOrderSlaCalculator(nineToSix);
        Instant fridayEvening = java.time.OffsetDateTime.parse("2026-10-16T17:00:00+08:00").toInstant();
        Instant deadline = calculator.snapshot(policy(), fridayEvening, null, null, fridayEvening)
                .resolveDeadline();
        assertThat(deadline).isEqualTo(java.time.OffsetDateTime.parse("2026-10-19T17:00:00+08:00").toInstant());
    }
}
