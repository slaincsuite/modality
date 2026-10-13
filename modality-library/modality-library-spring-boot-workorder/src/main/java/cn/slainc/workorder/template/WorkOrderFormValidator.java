package cn.slainc.workorder.template;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class WorkOrderFormValidator {

    public List<String> validate(WorkOrderTemplate template, Map<String, Object> values) {
        List<String> violations = new ArrayList<>();
        Map<String, Object> safe = values == null ? Map.of() : values;
        for (WorkOrderFieldDefinition field : template.fields()) {
            Object value = safe.get(field.code());
            if (value == null || (value instanceof String text && text.isBlank())) {
                if (field.required()) {
                    violations.add("字段「" + field.name() + "」必填");
                }
                continue;
            }
            validateValue(field, value, violations);
        }
        return violations;
    }

    private void validateValue(WorkOrderFieldDefinition field, Object value, List<String> violations) {
        String label = "字段「" + field.name() + "」";
        switch (field.type()) {
            case NUMBER -> validateNumber(field, value, label, violations);
            case SELECT -> {
                if (!field.options().contains(value.toString())) {
                    violations.add(label + "选项不在允许范围");
                }
            }
            case MULTISELECT -> {
                if (!(value instanceof List<?> items)) {
                    violations.add(label + "必须为选项列表");
                    return;
                }
                for (Object item : items) {
                    if (!field.options().contains(item.toString())) {
                        violations.add(label + "包含不允许的选项：" + item);
                    }
                }
            }
            case DATE -> requireParseable(value, violations, label, "必须为 yyyy-MM-dd 日期格式",
                    text -> LocalDate.parse(text));
            case DATETIME -> requireParseable(value, violations, label, "必须为 ISO 日期时间格式",
                    text -> LocalDateTime.parse(text));
            default -> validateTextual(field, value, label, violations);
        }
    }

    private void validateNumber(WorkOrderFieldDefinition field, Object value, String label,
                                List<String> violations) {
        if (!(value instanceof Number number)) {
            violations.add(label + "必须为数字");
            return;
        }
        if (field.minValue() != null && number.doubleValue() < field.minValue().doubleValue()) {
            violations.add(label + "不能小于 " + field.minValue());
        }
        if (field.maxValue() != null && number.doubleValue() > field.maxValue().doubleValue()) {
            violations.add(label + "不能大于 " + field.maxValue());
        }
    }

    private void validateTextual(WorkOrderFieldDefinition field, Object value, String label,
                                 List<String> violations) {
        if (!(value instanceof String text)) {
            violations.add(label + "必须为字符串");
            return;
        }
        if (field.minLength() != null && text.length() < field.minLength()) {
            violations.add(label + "长度不能少于 " + field.minLength());
        }
        if (field.maxLength() != null && text.length() > field.maxLength()) {
            violations.add(label + "长度不能超过 " + field.maxLength());
        }
        if (field.pattern() != null && !text.matches(field.pattern())) {
            violations.add(label + "格式不正确");
        }
    }

    private void requireParseable(Object value, List<String> violations, String label, String message,
                                  java.util.function.Function<String, ?> parser) {
        try {
            parser.apply(value.toString());
        } catch (DateTimeParseException exception) {
            violations.add(label + message);
        }
    }
}
