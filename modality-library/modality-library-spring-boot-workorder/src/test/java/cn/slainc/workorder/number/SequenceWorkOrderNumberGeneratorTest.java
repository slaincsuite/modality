package cn.slainc.workorder.number;

import cn.slainc.workorder.domain.WorkOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;


class SequenceWorkOrderNumberGeneratorTest {

    private Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Shanghai"));
    }

    private WorkOrder order() {
        WorkOrder order = new WorkOrder();
        order.setTitle("任意");
        return order;
    }

    @Test
    void dailyNumbersIncreaseWithinDayAndResetNextDay() {
        MemoryWorkOrderSequenceStore store = new MemoryWorkOrderSequenceStore();
        SequenceWorkOrderNumberGenerator generator = new SequenceWorkOrderNumberGenerator(store, "WO",
                SequenceWorkOrderNumberGenerator.ResetPeriod.DAILY, 5, at("2026-10-13T03:00:00Z"));
        assertThat(generator.generate(order())).isEqualTo("WO2026101300001");
        assertThat(generator.generate(order())).isEqualTo("WO2026101300002");
        Clock nextDay = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("Asia/Shanghai"));
        SequenceWorkOrderNumberGenerator nextDayGenerator = new SequenceWorkOrderNumberGenerator(store, "WO",
                SequenceWorkOrderNumberGenerator.ResetPeriod.DAILY, 5, nextDay);
        assertThat(nextDayGenerator.generate(order())).isEqualTo("WO2026101400001");
    }

    @Test
    void monthlyPeriodSharesCounterWithinMonth() {
        MemoryWorkOrderSequenceStore store = new MemoryWorkOrderSequenceStore();
        SequenceWorkOrderNumberGenerator generator = new SequenceWorkOrderNumberGenerator(store, "GD",
                SequenceWorkOrderNumberGenerator.ResetPeriod.MONTHLY, 4, at("2026-10-13T03:00:00Z"));
        assertThat(generator.generate(order())).isEqualTo("GD2026100001");
        assertThat(generator.generate(order())).isEqualTo("GD2026100002");
    }

    @Test
    void prefixSeparatesSequenceKeys() {
        MemoryWorkOrderSequenceStore store = new MemoryWorkOrderSequenceStore();
        Clock clock = at("2026-10-13T03:00:00Z");
        SequenceWorkOrderNumberGenerator wo = new SequenceWorkOrderNumberGenerator(store, "WO",
                SequenceWorkOrderNumberGenerator.ResetPeriod.DAILY, 5, clock);
        SequenceWorkOrderNumberGenerator gd = new SequenceWorkOrderNumberGenerator(store, "GD",
                SequenceWorkOrderNumberGenerator.ResetPeriod.DAILY, 5, clock);
        assertThat(wo.generate(order())).isEqualTo("WO2026101300001");
        assertThat(gd.generate(order())).isEqualTo("GD2026101300001");
    }
}
