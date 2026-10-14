package cn.slainc.modules.job.support;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CronSupportTest {

    @Test
    void shouldValidateCronExpression() {
        assertThat(CronSupport.isValid("0 */5 * * * ?")).isTrue();
        assertThat(CronSupport.isValid("0 0 2 * * ?")).isTrue();
        assertThat(CronSupport.isValid("invalid cron")).isFalse();
        assertThat(CronSupport.isValid("  ")).isFalse();
        assertThat(CronSupport.isValid(null)).isFalse();
    }

    @Test
    void shouldValidateSpringCronsThatQuartzCanRun() {
        assertThat(CronSupport.isValid("0 0 2 * * *")).isTrue();
        assertThat(CronSupport.isValid("0 0 9 * * 1-5")).isTrue();
        assertThat(CronSupport.isValid("0 0 9 * * MON-FRI")).isTrue();
        assertThat(CronSupport.isValid("@daily")).isTrue();
    }

    @Test
    void shouldRejectCronsThatQuartzCannotRunWithTheSameSemantics() {
        assertThat(CronSupport.isValid("0 0 3 15 * MON")).isFalse();
        assertThat(CronSupport.isValid("0 0 3 ? * 1-5 2026 1")).isFalse();
    }

    @Test
    void shouldCalculateNextExecutions() {
        assertThat(CronSupport.nextExecutions("0 */5 * * * ?", 3)).hasSize(3);
        assertThat(CronSupport.nextExecutions("bad", 3)).isEmpty();
        assertThat(CronSupport.nextExecutions("0 */5 * * * ?", 0)).isEmpty();
    }

    @Test
    void shouldPreviewExecutionsWithSpringDayOfWeekSemantics() {
        List<LocalDateTime> next = CronSupport.nextExecutions("0 0 9 * * 1-5", 6);

        assertThat(next).hasSize(6).isSorted();
        assertThat(next).allSatisfy(time -> {
            assertThat(time.getDayOfWeek()).isBetween(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
            assertThat(time.getHour()).isEqualTo(9);
        });
    }
}
