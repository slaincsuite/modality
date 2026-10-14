package cn.slainc.recycle.registry;

import cn.slainc.utils.string.StringUtils;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.function.Function;
import java.util.regex.Pattern;

public interface RecyclableType {

    Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    String type();

    Class<?> entityClass();

    BaseMapper<?> mapper();

    Object idOf(Object entity);

    String nameOf(Object entity);

    boolean logicDelete();

    default String tableName() {
        TableName annotation = entityClass() == null ? null : entityClass().getAnnotation(TableName.class);
        String table = annotation == null ? null : annotation.value();
        if (StringUtils.isBlank(table) || !TABLE_NAME_PATTERN.matcher(table).matches()) {
            throw new IllegalStateException("可回收类型 " + type() + " 的实体缺少合法的 @TableName: " + table);
        }
        return table;
    }

    static <T> RecyclableType of(String type, Class<T> entityClass, BaseMapper<T> mapper,
                                 Function<T, Object> idFunction, Function<T, String> nameFunction,
                                 boolean logicDelete) {
        return new SimpleRecyclableType<>(type, entityClass, mapper, idFunction, nameFunction, logicDelete);
    }
}

record SimpleRecyclableType<T>(String type, Class<T> entityClass, BaseMapper<T> mapper,
                               Function<T, Object> idFunction, Function<T, String> nameFunction,
                               boolean logicDelete) implements RecyclableType {

    SimpleRecyclableType {
        if (StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("可回收类型名不能为空");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("可回收类型 " + type + " 必须指定实体类");
        }
        type = type.trim();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object idOf(Object entity) {
        if (entity == null || idFunction == null) {
            return null;
        }
        return idFunction.apply((T) entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String nameOf(Object entity) {
        if (entity == null || nameFunction == null) {
            return null;
        }
        return nameFunction.apply((T) entity);
    }
}
