package cn.slainc.modules.infra.excel;

import cn.idev.excel.annotation.ExcelProperty;

public class DictItemRow {

    @ExcelProperty("字典编码")
    private String typeCode;

    @ExcelProperty("标签")
    private String label;

    @ExcelProperty("值")
    private String value;

    @ExcelProperty("排序")
    private Integer sort;

    @ExcelProperty("启用")
    private String enabled;

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }
}
