package cn.slainc.excel.formula;

import java.util.List;

public record FormulaSheet(String sheetName, List<String> headers, List<List<Object>> rows) {

    public static FormulaSheet of(String sheetName, List<String> headers, List<List<Object>> rows) {
        return new FormulaSheet(sheetName, headers, rows);
    }
}
