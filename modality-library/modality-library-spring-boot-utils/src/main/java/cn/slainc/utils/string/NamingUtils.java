package cn.slainc.utils.string;

import java.util.ArrayList;
import java.util.List;

public final class NamingUtils {

    private NamingUtils() {
    }

    public static String toCamel(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder(tokens.get(0).toLowerCase());
        for (int i = 1; i < tokens.size(); i++) {
            builder.append(capitalize(tokens.get(i)));
        }
        return builder.toString();
    }

    public static String toPascal(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            builder.append(capitalize(token));
        }
        return builder.toString();
    }

    public static String toSnake(String text) {
        return joinTokens(tokenize(text), "_", false);
    }

    public static String toUpperSnake(String text) {
        return joinTokens(tokenize(text), "_", true);
    }

    public static String toKebab(String text) {
        return joinTokens(tokenize(text), "-", false);
    }

    private static String joinTokens(List<String> tokens, String separator, boolean upper) {
        if (tokens.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(upper ? token.toUpperCase() : token.toLowerCase());
        }
        return builder.toString();
    }

    private static String capitalize(String token) {
        if (token.isEmpty()) {
            return token;
        }
        return Character.toUpperCase(token.charAt(0)) + token.substring(1).toLowerCase();
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '_' || c == '-' || c == ' ' || c == '.') {
                flush(tokens, current);
                continue;
            }
            if (Character.isUpperCase(c) && current.length() > 0) {
                boolean lowerBefore = Character.isLowerCase(text.charAt(i - 1));
                boolean lowerAfter = i + 1 < text.length() && Character.isLowerCase(text.charAt(i + 1));
                if (lowerBefore || lowerAfter) {
                    flush(tokens, current);
                }
            }
            current.append(c);
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }
}
