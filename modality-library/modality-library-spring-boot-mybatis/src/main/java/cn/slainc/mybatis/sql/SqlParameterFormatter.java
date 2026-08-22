package cn.slainc.mybatis.sql;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.time.temporal.TemporalAccessor;
import java.util.Date;

public final class SqlParameterFormatter {

    private SqlParameterFormatter() {
    }

    public static String format(Configuration configuration, BoundSql boundSql) {
        String sql = boundSql.getSql();
        if (sql == null) {
            return null;
        }
        String singleLine = sql.replaceAll("\\s+", " ").trim();
        java.util.List<ParameterMapping> mappings = boundSql.getParameterMappings();
        if (mappings == null || mappings.isEmpty()) {
            return singleLine;
        }
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        StringBuilder builder = new StringBuilder(singleLine.length() + mappings.size() * 16);
        int index = 0;
        for (int i = 0; i < singleLine.length(); i++) {
            char current = singleLine.charAt(i);
            if (current == '?' && index < mappings.size()) {
                builder.append(value(configuration, registry, boundSql, mappings.get(index)));
                index++;
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static String value(Configuration configuration, TypeHandlerRegistry registry,
                                BoundSql boundSql, ParameterMapping mapping) {
        Object parameter = null;
        try {
            String property = mapping.getProperty();
            if (boundSql.hasAdditionalParameter(property)) {
                parameter = boundSql.getAdditionalParameter(property);
            } else {
                Object parameterObject = boundSql.getParameterObject();
                if (parameterObject != null && property != null) {
                    MetaObject metaObject = configuration.newMetaObject(parameterObject);
                    if (metaObject.hasGetter(property)) {
                        parameter = metaObject.getValue(property);
                    }
                }
            }
        } catch (RuntimeException exception) {
            return "?";
        }
        return literal(parameter);
    }

    private static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Date || value instanceof TemporalAccessor) {
            return "'" + value + "'";
        }
        if (value instanceof byte[] bytes) {
            return "0x" + cn.slainc.utils.codec.HexUtils.encode(bytes);
        }
        if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?>) {
            String text = String.valueOf(value).replace("'", "''");
            if (text.length() > 512) {
                text = text.substring(0, 512) + "...";
            }
            return "'" + text + "'";
        }
        String text = String.valueOf(value);
        return text.length() > 512 ? text.substring(0, 512) + "..." : text;
    }
}
