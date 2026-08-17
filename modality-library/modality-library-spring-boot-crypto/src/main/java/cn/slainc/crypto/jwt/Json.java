package cn.slainc.crypto.jwt;

import cn.slainc.crypto.CryptoException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {

    private final String text;

    private int index;

    private Json(String text) {
        this.text = text;
    }

    static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value);
        return builder.toString();
    }

    static Object parse(String text) {
        if (text == null) {
            throw new CryptoException("JSON 文本不能为空");
        }
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index < text.length()) {
            throw parser.error("存在多余内容");
        }
        return value;
    }

    private static void writeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String text) {
            writeString(builder, text);
        } else if (value instanceof Boolean || value instanceof BigInteger || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            builder.append(value);
        } else if (value instanceof BigDecimal decimal) {
            builder.append(decimal.toPlainString());
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new CryptoException("JSON 不支持非有限数值");
            }
            builder.append(number);
        } else if (value instanceof Number number) {
            builder.append(number);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(builder, map);
        } else if (value instanceof Iterable<?> iterable) {
            writeArray(builder, iterable);
        } else if (value.getClass().isArray()) {
            writeArray(builder, toList(value));
        } else if (value instanceof Enum<?> enumeration) {
            writeString(builder, enumeration.name());
        } else if (value instanceof Character character) {
            writeString(builder, character.toString());
        } else {
            writeString(builder, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder builder, Map<?, ?> map) {
        builder.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            writeString(builder, String.valueOf(entry.getKey()));
            builder.append(':');
            writeValue(builder, entry.getValue());
        }
        builder.append('}');
    }

    private static void writeArray(StringBuilder builder, Iterable<?> iterable) {
        builder.append('[');
        boolean first = true;
        for (Object element : iterable) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            writeValue(builder, element);
        }
        builder.append(']');
    }

    private static void writeString(StringBuilder builder, String text) {
        builder.append('"');
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static List<Object> toList(Object array) {
        int length = Array.getLength(array);
        List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(Array.get(array, i));
        }
        return list;
    }

    private Object readValue() {
        char character = peek();
        return switch (character) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        index++;
        skipWhitespace();
        if (peek() == '}') {
            index++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("对象的键必须是字符串");
            }
            String key = readString();
            skipWhitespace();
            if (peek() != ':') {
                throw error("对象缺少冒号");
            }
            index++;
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char character = peek();
            if (character == ',') {
                index++;
                continue;
            }
            if (character == '}') {
                index++;
                return map;
            }
            throw error("对象格式非法");
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        index++;
        skipWhitespace();
        if (peek() == ']') {
            index++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char character = peek();
            if (character == ',') {
                index++;
                continue;
            }
            if (character == ']') {
                index++;
                return list;
            }
            throw error("数组格式非法");
        }
    }

    private String readString() {
        index++;
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (index >= text.length()) {
                throw error("字符串未结束");
            }
            char character = text.charAt(index++);
            if (character == '"') {
                return builder.toString();
            }
            if (character != '\\') {
                if (character < 0x20) {
                    throw error("字符串包含非法控制字符");
                }
                builder.append(character);
                continue;
            }
            if (index >= text.length()) {
                throw error("转义字符未结束");
            }
            char escaped = text.charAt(index++);
            switch (escaped) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (index + 4 > text.length()) {
                        throw error("Unicode 转义不完整");
                    }
                    builder.append((char) parseHex(text.substring(index, index + 4)));
                    index += 4;
                }
                default -> throw error("非法的转义字符");
            }
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, index)) {
            throw error("非法的字面量");
        }
        index += literal.length();
        return value;
    }

    private Object readNumber() {
        int start = index;
        while (index < text.length() && "+-.eE0123456789".indexOf(text.charAt(index)) >= 0) {
            index++;
        }
        String number = text.substring(start, index);
        if (number.isEmpty()) {
            throw error("非法的数值");
        }
        try {
            if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
                return Double.valueOf(number);
            }
            return Long.valueOf(number);
        } catch (NumberFormatException exception) {
            try {
                return new BigInteger(number);
            } catch (NumberFormatException nested) {
                throw error("非法的数值：" + number);
            }
        }
    }

    private int parseHex(String value) {
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException exception) {
            throw error("非法的 Unicode 转义：" + value);
        }
    }

    private char peek() {
        if (index >= text.length()) {
            throw error("内容意外结束");
        }
        return text.charAt(index);
    }

    private void skipWhitespace() {
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                index++;
            } else {
                return;
            }
        }
    }

    private CryptoException error(String message) {
        return new CryptoException(message + "（位置 " + index + "）");
    }
}
