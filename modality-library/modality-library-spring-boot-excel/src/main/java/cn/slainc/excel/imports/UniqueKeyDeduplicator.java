package cn.slainc.excel.imports;

import cn.slainc.excel.read.ImportError;
import cn.slainc.excel.read.ImportResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class UniqueKeyDeduplicator<T> {

    private final Function<T, Object> keyExtractor;
    private final Set<Object> seen = new LinkedHashSet<>();
    private int duplicateCount;

    private UniqueKeyDeduplicator(Function<T, Object> keyExtractor) {
        this.keyExtractor = keyExtractor;
    }

    public static <T> UniqueKeyDeduplicator<T> of(Function<T, Object> keyExtractor) {
        if (keyExtractor == null) {
            throw new IllegalArgumentException("唯一键提取器不能为空");
        }
        return new UniqueKeyDeduplicator<>(keyExtractor);
    }

    public static <T> UniqueKeyDeduplicator<T> ofFields(Class<T> head, String... fieldNames) {
        if (head == null) {
            throw new IllegalArgumentException("表头类型不能为空");
        }
        if (fieldNames == null || fieldNames.length == 0) {
            throw new IllegalArgumentException("唯一键字段不能为空");
        }
        List<Field> fields = new ArrayList<>(fieldNames.length);
        for (String fieldName : fieldNames) {
            fields.add(resolve(head, fieldName));
        }
        return of(record -> {
            StringBuilder key = new StringBuilder();
            for (Field field : fields) {
                key.append(read(field, record)).append((char) 1);
            }
            return key.toString();
        });
    }

    public boolean isDuplicate(T record) {
        Object key = keyExtractor.apply(record);
        if (key == null || String.valueOf(key).isBlank()) {
            return false;
        }
        if (seen.add(key)) {
            return false;
        }
        duplicateCount++;
        return true;
    }

    public ImportResult<T> filter(ImportResult<T> source) {
        if (source == null) {
            return new ImportResult<>();
        }
        ImportResult<T> filtered = new ImportResult<>();
        for (ImportError error : source.getErrors()) {
            filtered.increaseTotalRows();
            filtered.addError(error);
        }
        for (T record : source.getRecords()) {
            filtered.increaseTotalRows();
            if (isDuplicate(record)) {
                filtered.addError(ImportError.unknownRow(null, keyExtractor.apply(record), "唯一键已存在，已跳过该记录"));
                continue;
            }
            filtered.addRecord(record);
        }
        if (source.isAborted()) {
            filtered.markAborted();
        }
        return filtered;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void reset() {
        seen.clear();
        duplicateCount = 0;
    }

    private static Field resolve(Class<?> head, String fieldName) {
        try {
            Field field = head.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ex) {
            throw new IllegalArgumentException("类型 " + head.getName() + " 不存在字段: " + fieldName, ex);
        }
    }

    private static Object read(Field field, Object record) {
        try {
            return field.get(record);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("读取唯一键字段失败: " + field.getName(), ex);
        }
    }
}
