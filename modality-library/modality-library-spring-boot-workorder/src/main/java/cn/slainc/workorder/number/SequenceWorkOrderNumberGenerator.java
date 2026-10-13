package cn.slainc.workorder.number;

import cn.slainc.workorder.model.WorkOrder;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class SequenceWorkOrderNumberGenerator implements WorkOrderNumberGenerator {

    public enum ResetPeriod {

        DAILY("yyyyMMdd"),
        MONTHLY("yyyyMM");

        private final DateTimeFormatter formatter;

        ResetPeriod(String pattern) {
            this.formatter = DateTimeFormatter.ofPattern(pattern);
        }
    }

    private final WorkOrderSequenceStore store;

    private final String prefix;

    private final ResetPeriod resetPeriod;

    private final int padWidth;

    private final Clock clock;

    public SequenceWorkOrderNumberGenerator(WorkOrderSequenceStore store, String prefix,
                                            ResetPeriod resetPeriod, int padWidth, Clock clock) {
        this.store = store;
        this.prefix = prefix;
        this.resetPeriod = resetPeriod;
        this.padWidth = padWidth;
        this.clock = clock;
    }

    @Override
    public String generate(WorkOrder order) {
        String datePart = resetPeriod.formatter.format(LocalDate.now(clock));
        long sequence = store.next(prefix + ":" + datePart);
        return prefix + datePart + String.format("%0" + padWidth + "d", sequence);
    }
}
