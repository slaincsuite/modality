package cn.slainc.mybatis.sql;

import java.util.Collection;
import java.util.Map;

public final class SqlResultFormatter {

    private SqlResultFormatter() {
    }

    public static String format(Object result, int maxRows) {
        if (result == null) {
            return "null";
        }
        if (result instanceof Number || result instanceof Boolean) {
            return String.valueOf(result);
        }
        if (result instanceof Collection<?> collection) {
            return "共 " + collection.size() + " 条: " + abbreviate(collection, maxRows);
        }
        if (result instanceof Map<?, ?> map) {
            return abbreviate(map, maxRows);
        }
        return String.valueOf(result);
    }

    private static String abbreviate(Object value, int maxRows) {
        String text = String.valueOf(value);
        int limit = Math.max(maxRows, 1) * 200;
        return text.length() > limit ? text.substring(0, limit) + "...(已截断)" : text;
    }
}
