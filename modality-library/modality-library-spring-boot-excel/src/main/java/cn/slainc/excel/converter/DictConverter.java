package cn.slainc.excel.converter;

import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import cn.slainc.excel.annotation.ExcelDict;

import java.lang.reflect.Field;

public class DictConverter implements Converter<Object> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return Object.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public Object convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
                                    GlobalConfiguration globalConfiguration) {
        String text = cellData == null ? null : cellData.getStringValue();
        if (text == null || text.isBlank()) {
            return null;
        }
        ExcelDict annotation = resolve(contentProperty);
        if (annotation == null) {
            return text;
        }
        String trimmed = text.trim();
        Object value = DictRegistry.toValue(annotation.value(), trimmed);
        if (value != null) {
            return value;
        }
        Class<?> fieldType = fieldType(contentProperty);
        if (fieldType == null || fieldType == String.class) {
            return annotation.keepOriginalWhenMissing() ? trimmed : null;
        }
        if (annotation.keepOriginalWhenMissing()) {
            throw new IllegalArgumentException("字典 [" + annotation.value() + "] 中不存在标签「" + trimmed + "」");
        }
        return null;
    }

    @Override
    public WriteCellData<?> convertToExcelData(Object value, ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        if (value == null) {
            return new WriteCellData<>("");
        }
        ExcelDict annotation = resolve(contentProperty);
        String label = annotation == null ? null : DictRegistry.toLabel(annotation.value(), value);
        return new WriteCellData<>(label == null ? String.valueOf(value) : label);
    }

    private Class<?> fieldType(ExcelContentProperty contentProperty) {
        if (contentProperty == null) {
            return null;
        }
        Field field = contentProperty.getField();
        return field == null ? null : field.getType();
    }

    private ExcelDict resolve(ExcelContentProperty contentProperty) {
        if (contentProperty == null) {
            return null;
        }
        Field field = contentProperty.getField();
        return field == null ? null : field.getAnnotation(ExcelDict.class);
    }
}
