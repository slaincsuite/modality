package cn.slainc.job.support;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuartzCronSupportTest {

    @Test
    void shouldKeepExpressionsThatAreAlreadyQuartzCompatible() {
        assertThat(QuartzCronSupport.toQuartz("0 0/5 * * * ?")).isEqualTo("0 0/5 * * * ?");
        assertThat(QuartzCronSupport.toQuartz("0 0 3 * * ?")).isEqualTo("0 0 3 * * ?");
        assertThat(QuartzCronSupport.toQuartz("  0 0 3 * * ?  ")).isEqualTo("0 0 3 * * ?");
    }

    @Test
    void shouldTurnSpringWildcardDayOfWeekIntoQuestionMark() {
        assertThat(QuartzCronSupport.toQuartz("0 0 2 * * *")).isEqualTo("0 0 2 * * ?");
        assertThat(QuartzCronSupport.toQuartz("0 0 2 * * MON-SUN")).isEqualTo("0 0 2 ? * SUN-SAT");
    }

    @Test
    void shouldRestrictDayOfMonthWhenDayOfWeekIsRestricted() {
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * MON-FRI")).isEqualTo("0 0 9 ? * MON-FRI");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 1-5")).isEqualTo("0 0 9 ? * MON-FRI");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 15 * *")).isEqualTo("0 0 9 15 * ?");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 L * *")).isEqualTo("0 0 9 L * ?");
    }

    @Test
    void shouldShiftNumericDayOfWeekValues() {
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 0")).isEqualTo("0 0 9 ? * SUN");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 7")).isEqualTo("0 0 9 ? * SUN");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 1")).isEqualTo("0 0 9 ? * MON");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 6,7")).isEqualTo("0 0 9 ? * SUN,SAT");
    }

    @Test
    void shouldExpandStepsInDayOfWeek() {
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 1/2")).isEqualTo("0 0 9 ? * SUN-MON,WED,FRI");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * */2")).isEqualTo("0 0 9 ? * SUN-MON,WED,FRI");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * MON-FRI/2")).isEqualTo("0 0 9 ? * MON,WED,FRI");
    }

    @Test
    void shouldShiftLastAndNthDayOfWeekTokens() {
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 5L")).isEqualTo("0 0 9 ? * FRIL");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * THUL")).isEqualTo("0 0 9 ? * THUL");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 5#2")).isEqualTo("0 0 9 ? * FRI#2");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * 0#1")).isEqualTo("0 0 9 ? * SUN#1");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 * * MON#3")).isEqualTo("0 0 9 ? * MON#3");
    }

    @Test
    void shouldRejectDayOfMonthAndDayOfWeekRestrictedTogether() {
        assertThatThrownBy(() -> QuartzCronSupport.toQuartz("0 0 3 15 * MON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("day-of-month");
        assertThatThrownBy(() -> QuartzCronSupport.toQuartz("0 0 3 15 * 1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMalformedExpressions() {
        assertThatThrownBy(() -> QuartzCronSupport.toQuartz(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuartzCronSupport.toQuartz("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuartzCronSupport.toQuartz("not a cron")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuartzCronSupport.toQuartz("0 0 3 * *")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuartzCronSupport.toQuartz("0 0 3 * * ? 2026 1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(QuartzCronSupport.isConvertible("not a cron")).isFalse();
        assertThat(QuartzCronSupport.isConvertible("0 0 3 * * ?")).isTrue();
    }

    @Test
    void shouldExpandSpringMacros() {
        assertThat(QuartzCronSupport.toQuartz("@daily")).isEqualTo("0 0 0 * * ?");
        assertThat(QuartzCronSupport.toQuartz("@weekly")).isEqualTo("0 0 0 ? * SUN");
        assertThat(QuartzCronSupport.toQuartz("@monthly")).isEqualTo("0 0 0 1 * ?");
        assertThat(QuartzCronSupport.toQuartz("@hourly")).isEqualTo("0 0 * * * ?");
    }

    @Test
    void shouldAcceptQuartzNativeExpressions() {
        assertThat(QuartzCronSupport.toQuartz("0 0 9 ? * 1-5 2026")).isEqualTo("0 0 9 ? * 1-5 2026");
        assertThat(QuartzCronSupport.toQuartz("0 0 9 ? * L")).isEqualTo("0 0 9 ? * L");
        assertThatThrownBy(() -> QuartzCronSupport.toQuartz("0 0 9 * * L"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPreserveSpringFireTimesAfterConversion() throws Exception {
        List<String> expressions = List.of(
                "0 0/5 * * * ?",
                "0 0 2 * * *",
                "0 0 9 * * MON-FRI",
                "0 0 9 * * 1-5",
                "0 0 9 15 * *",
                "0 0 9 * * 0",
                "0 0 9 * * 7",
                "0 0 9 * * 5L",
                "0 0 9 * * 5#2",
                "0 0 9 * * */2",
                "0 0 9 1,15 * *",
                "30 5 8 ? * *",
                "0 0/30 8-10 * * *");
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);

        for (String expression : expressions) {
            String converted = QuartzCronSupport.toQuartz(expression);
            assertThat(quartzFireTimes(converted, from, 8))
                    .as("表达式 %s 转换后（%s）的触发时间必须与 Spring 完全一致", expression, converted)
                    .isEqualTo(springFireTimes(expression, from, 8));
        }
    }

    @Test
    void shouldPreviewNextExecutionsWithQuartzSemantics() {
        List<LocalDateTime> next = QuartzCronSupport.nextExecutions("0 0 2 * * *", 3);

        assertThat(next).hasSize(3);
        assertThat(next).allSatisfy(time -> assertThat(time.getHour()).isEqualTo(2));
        assertThat(next).isSorted();
        assertThat(QuartzCronSupport.nextExecutions("bad", 3)).isEmpty();
        assertThat(QuartzCronSupport.nextExecutions("0 0 2 * * *", 0)).isEmpty();
    }

    private static List<LocalDateTime> springFireTimes(String expression, LocalDateTime from, int count) {
        CronExpression spring = CronExpression.parse(expression);
        List<LocalDateTime> result = new ArrayList<>();
        LocalDateTime current = from;
        for (int i = 0; i < count; i++) {
            current = spring.next(current);
            result.add(current);
        }
        return result;
    }

    private static List<LocalDateTime> quartzFireTimes(String expression, LocalDateTime from, int count)
            throws ParseException {
        org.quartz.CronExpression quartz = new org.quartz.CronExpression(expression);
        quartz.setTimeZone(TimeZone.getDefault());
        List<LocalDateTime> result = new ArrayList<>();
        Date current = Date.from(from.atZone(ZoneId.systemDefault()).toInstant());
        for (int i = 0; i < count; i++) {
            current = quartz.getNextValidTimeAfter(current);
            result.add(LocalDateTime.ofInstant(current.toInstant(), ZoneId.systemDefault()));
        }
        return result;
    }
}
