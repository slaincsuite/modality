package cn.slainc.excel.print;

import org.apache.poi.ss.usermodel.PrintSetup;

public enum PaperSize {

    A4(PrintSetup.A4_PAPERSIZE, "A4"),
    A3(PrintSetup.A3_PAPERSIZE, "A3"),
    A5(PrintSetup.A5_PAPERSIZE, "A5"),
    B4(PrintSetup.B4_PAPERSIZE, "B4"),
    B5(PrintSetup.B5_PAPERSIZE, "B5"),
    LETTER(PrintSetup.LETTER_PAPERSIZE, "Letter"),
    LEGAL(PrintSetup.LEGAL_PAPERSIZE, "Legal");

    private final short code;
    private final String description;

    PaperSize(short code, String description) {
        this.code = code;
        this.description = description;
    }

    public short code() {
        return code;
    }

    public String description() {
        return description;
    }
}
