package cn.slainc.excel.csv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CsvUtils {

    public static final char DEFAULT_DELIMITER = ',';

    private static final char QUOTE = '"';

    private CsvUtils() {
    }

    public static void write(OutputStream outputStream, List<String> headers, List<List<String>> rows) {
        write(outputStream, headers, rows, DEFAULT_DELIMITER, StandardCharsets.UTF_8);
    }

    public static void write(OutputStream outputStream, List<String> headers, List<List<String>> rows,
                             char delimiter, Charset charset) {
        Charset actualCharset = charset == null ? StandardCharsets.UTF_8 : charset;
        try {
            outputStream.write(charset == null ? new byte[0] : bom(actualCharset));
            Writer writer = new OutputStreamWriter(outputStream, actualCharset);
            if (headers != null) {
                writeRow(writer, headers, delimiter);
            }
            if (rows != null) {
                for (List<String> row : rows) {
                    writeRow(writer, row, delimiter);
                }
            }
            writer.flush();
        } catch (IOException ex) {
            throw new IllegalStateException("写入 CSV 失败", ex);
        }
    }

    public static List<List<String>> read(InputStream inputStream) {
        return read(inputStream, DEFAULT_DELIMITER, StandardCharsets.UTF_8);
    }

    public static List<List<String>> read(InputStream inputStream, char delimiter, Charset charset) {
        Charset actualCharset = charset == null ? StandardCharsets.UTF_8 : charset;
        try (Reader reader = new InputStreamReader(inputStream, actualCharset)) {
            String content = readAll(reader);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            return parse(content, delimiter);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 CSV 失败", ex);
        }
    }

    public static List<List<String>> parse(String content, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == QUOTE) {
                    if (i + 1 < content.length() && content.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE);
                        i += 2;
                        continue;
                    }
                    quoted = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }
            if (c == QUOTE) {
                quoted = true;
                i++;
                continue;
            }
            if (c == delimiter) {
                current.add(field.toString());
                field.setLength(0);
                i++;
                continue;
            }
            if (c == '\r' || c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                rows.add(current);
                current = new ArrayList<>();
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                i++;
                continue;
            }
            field.append(c);
            i++;
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }
        return rows;
    }

    public static String escape(String value, char delimiter) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.indexOf(delimiter) >= 0 || value.indexOf(QUOTE) >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return QUOTE + value.replace(String.valueOf(QUOTE), String.valueOf(QUOTE) + QUOTE) + QUOTE;
    }

    private static void writeRow(Writer writer, List<String> row, char delimiter) throws IOException {
        if (row == null) {
            writer.write("\r\n");
            return;
        }
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                writer.write(delimiter);
            }
            writer.write(escape(row.get(i), delimiter));
        }
        writer.write("\r\n");
    }

    private static byte[] bom(Charset charset) {
        return StandardCharsets.UTF_8.equals(charset)
                ? new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}
                : new byte[0];
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, read);
        }
        return builder.toString();
    }
}
