package cn.slainc.excel.center;

import java.util.List;

public record ExportTaskQuery(List<ExportTaskStatus> statuses, String nameContains, Integer limit) {

    public static ExportTaskQuery all() {
        return new ExportTaskQuery(List.of(), null, null);
    }

    public static ExportTaskQuery of(ExportTaskStatus... statuses) {
        return new ExportTaskQuery(List.of(statuses), null, null);
    }

    public ExportTaskQuery withNameContains(String value) {
        return new ExportTaskQuery(statuses, value, limit);
    }

    public ExportTaskQuery withLimit(int value) {
        return new ExportTaskQuery(statuses, nameContains, value);
    }
}
