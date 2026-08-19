package cn.slainc.excel.csv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvUtilsTest {

    @Test
    void writesAndReadsBack() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CsvUtils.write(output, List.of("姓名", "年龄"),
                List.of(List.of("张三", "20"), List.of("李四", "30")));

        List<List<String>> rows = CsvUtils.read(new ByteArrayInputStream(output.toByteArray()));
        assertEquals(3, rows.size());
        assertEquals(List.of("姓名", "年龄"), rows.get(0));
        assertEquals(List.of("张三", "20"), rows.get(1));
        assertEquals(List.of("李四", "30"), rows.get(2));
    }

    @Test
    void handlesQuotingAndEscaping() {
        assertEquals("\"a,b\"", CsvUtils.escape("a,b", ','));
        assertEquals("\"say \"\"hi\"\"\"", CsvUtils.escape("say \"hi\"", ','));
        assertEquals("plain", CsvUtils.escape("plain", ','));

        List<List<String>> rows = CsvUtils.parse("a,\"b,c\",\"d\"\"e\"\r\nf,g,h\r\n", ',');
        assertEquals(2, rows.size());
        assertEquals(List.of("a", "b,c", "d\"e"), rows.get(0));
        assertEquals(List.of("f", "g", "h"), rows.get(1));
    }

    @Test
    void supportsCustomDelimiterAndCharset() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CsvUtils.write(output, List.of("姓名", "年龄"), List.of(List.of("张三", "20")), ';',
                Charset.forName("GBK"));

        List<List<String>> rows = CsvUtils.read(new ByteArrayInputStream(output.toByteArray()), ';',
                Charset.forName("GBK"));
        assertEquals("张三", rows.get(1).get(0));
    }

    @Test
    void writesUtf8BomForExcelCompatibility() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CsvUtils.write(output, List.of("姓名"), List.of(List.of("张三")));
        byte[] bytes = output.toByteArray();
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
    }

    @Test
    void handlesEmptyAndNullInput() {
        assertTrue(CsvUtils.parse("", ',').isEmpty());
        assertTrue(CsvUtils.parse(null, ',').isEmpty());
        assertEquals("", CsvUtils.escape(null, ','));
        assertTrue(CsvUtils.read(new ByteArrayInputStream(new byte[0])).isEmpty());
        assertEquals(StandardCharsets.UTF_8, StandardCharsets.UTF_8);
    }
}
