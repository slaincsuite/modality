package cn.slainc.mybatis.support;

import cn.slainc.mybatis.exception.MybatisException;

public final class LikeUtils {

    public static final char DEFAULT_ESCAPE = '\\';

    private LikeUtils() {
    }

    public static String escapeLike(String value) {
        return escapeLike(value, DEFAULT_ESCAPE);
    }

    public static String escapeLike(String value, char escape) {
        requireUsableEscape(escape);
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            // 转义符自身必须一并转义，否则用户输入的反斜杠会把后一个字符吞成通配符
            if (current == '%' || current == '_' || current == escape) {
                builder.append(escape);
            }
            builder.append(current);
        }
        return builder.toString();
    }

    public static String contains(String value) {
        return value == null ? null : "%" + escapeLike(value) + "%";
    }

    public static String startsWith(String value) {
        return value == null ? null : escapeLike(value) + "%";
    }

    public static String endsWith(String value) {
        return value == null ? null : "%" + escapeLike(value);
    }

    public static String escapeClause() {
        return escapeClause(DEFAULT_ESCAPE);
    }

    public static String escapeClause(char escape) {
        requireUsableEscape(escape);
        return " ESCAPE '" + escape + "'";
    }

    private static void requireUsableEscape(char escape) {
        if (escape == '\'' || escape == '%' || escape == '_' || Character.isWhitespace(escape)) {
            throw new MybatisException("LIKE 转义符不能使用引号、通配符或空白字符: " + escape);
        }
    }
}
