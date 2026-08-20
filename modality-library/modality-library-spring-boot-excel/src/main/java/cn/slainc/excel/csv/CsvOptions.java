package cn.slainc.excel.csv;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CsvOptions {

    private char delimiter = CsvUtils.DEFAULT_DELIMITER;
    private Charset charset = StandardCharsets.UTF_8;
    private boolean bom = true;

    public static CsvOptions defaults() {
        return new CsvOptions();
    }

    public static CsvOptions of(char delimiter, Charset charset) {
        return new CsvOptions().delimiter(delimiter).charset(charset);
    }

    public char getDelimiter() {
        return delimiter;
    }

    public CsvOptions delimiter(char value) {
        if (value == '\n' || value == '\r' || value == '"') {
            throw new IllegalArgumentException("分隔符不能是换行符或双引号");
        }
        this.delimiter = value;
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    public CsvOptions charset(Charset value) {
        this.charset = value == null ? StandardCharsets.UTF_8 : value;
        return this;
    }

    public boolean isBom() {
        return bom;
    }

    public CsvOptions bom(boolean value) {
        this.bom = value;
        return this;
    }
}
