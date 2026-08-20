package cn.slainc.excel.format;

import cn.idev.excel.annotation.ExcelProperty;
import cn.slainc.excel.annotation.ExcelDict;
import cn.slainc.excel.converter.DictRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BeanHead<T> {

    public record Column(int index, String name, Field field, Class<?> type, ExcelDict dict) {

        public int position() {
            return index >= PLAIN_OFFSET ? index - PLAIN_OFFSET : index;
        }
    }

    private static final int PLAIN_OFFSET = 1000;

    private final Class<T> head;
    private final List<Column> columns;

    private BeanHead(Class<T> head, List<Column> columns) {
        this.head = head;
        this.columns = columns;
    }

    public static <T> BeanHead<T> of(Class<T> head) {
        if (head == null) {
            throw new IllegalArgumentException("表头类型不能为空");
        }
        List<Column> columns = new ArrayList<>();
        int order = 0;
        for (Field field : head.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            ExcelProperty property = field.getAnnotation(ExcelProperty.class);
            if (property == null) {
                continue;
            }
            field.setAccessible(true);
            String name = property.value().length > 0 && !property.value()[0].isBlank()
                    ? property.value()[0]
                    : field.getName();
            int index = property.index() >= 0 ? property.index() : PLAIN_OFFSET + order;
            columns.add(new Column(index, name, field, field.getType(), field.getAnnotation(ExcelDict.class)));
            order++;
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("类型 " + head.getName() + " 没有任何 @ExcelProperty 字段");
        }
        columns.sort(Comparator.comparingInt(Column::index));
        return new BeanHead<>(head, List.copyOf(columns));
    }

    public Class<T> head() {
        return head;
    }

    public List<Column> columns() {
        return columns;
    }

    public List<String> headers() {
        return columns.stream().map(Column::name).toList();
    }

    public List<String> valuesOf(T bean) {
        List<String> values = new ArrayList<>(columns.size());
        for (Column column : columns) {
            values.add(toText(read(bean, column), column));
        }
        return values;
    }

    public T toBean(List<String> headers, List<String> values) {
        Map<String, Integer> byName = new HashMap<>();
        if (headers != null) {
            for (int i = 0; i < headers.size(); i++) {
                byName.putIfAbsent(normalize(headers.get(i)), i);
            }
        }
        T bean = instantiate();
        for (Column column : columns) {
            Integer position = byName.get(normalize(column.name()));
            int fallback = column.position();
            String text = null;
            if (position != null && position < values.size()) {
                text = values.get(position);
            } else if (fallback < values.size()) {
                text = values.get(fallback);
            }
            setValue(bean, column, text);
        }
        return bean;
    }

    private T instantiate() {
        try {
            return head.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("类型 " + head.getName() + " 必须提供无参构造器", ex);
        }
    }

    private Object read(T bean, Column column) {
        try {
            return column.field().get(bean);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("读取字段失败: " + column.name(), ex);
        }
    }

    private void setValue(T bean, Column column, String text) {
        Object value = convert(column, text);
        if (value == null && column.type().isPrimitive()) {
            return;
        }
        try {
            column.field().set(bean, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("写入字段失败: " + column.name(), ex);
        }
    }

    private Object convert(Column column, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (column.dict() != null) {
            Object dictValue = DictRegistry.toValue(column.dict().value(), trimmed);
            if (dictValue != null) {
                return dictValue;
            }
            Class<?> type = column.type();
            if (type == String.class) {
                return column.dict().keepOriginalWhenMissing() ? trimmed : null;
            }
            if (column.dict().keepOriginalWhenMissing()) {
                throw new IllegalArgumentException("字典 [" + column.dict().value() + "] 中不存在标签「" + trimmed + "」");
            }
            return null;
        }
        Class<?> type = column.type();
        try {
            if (type == String.class) {
                return trimmed;
            }
            if (type == Integer.class || type == int.class) {
                return Integer.valueOf(trimmed);
            }
            if (type == Long.class || type == long.class) {
                return Long.valueOf(trimmed);
            }
            if (type == Double.class || type == double.class) {
                return Double.valueOf(trimmed);
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(trimmed);
            }
            if (type == Boolean.class || type == boolean.class) {
                return Boolean.valueOf(trimmed);
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("字段「" + column.name() + "」的值「" + trimmed + "」无法转换为 " + type.getSimpleName());
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (String.valueOf(constant).equalsIgnoreCase(trimmed)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("字段「" + column.name() + "」不支持的值「" + trimmed + "」");
        }
        return trimmed;
    }

    private String toText(Object value, Column column) {
        if (value == null) {
            return "";
        }
        if (column.dict() != null) {
            String label = DictRegistry.toLabel(column.dict().value(), value);
            if (label != null) {
                return label;
            }
        }
        return String.valueOf(value);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
