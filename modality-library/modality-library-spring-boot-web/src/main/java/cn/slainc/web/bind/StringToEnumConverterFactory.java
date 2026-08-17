package cn.slainc.web.bind;

import cn.slainc.core.enums.DictEnum;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

@SuppressWarnings({"rawtypes", "unchecked"})
public class StringToEnumConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> convert(targetType, source);
    }

    private <T extends Enum> T convert(Class<T> type, String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String text = source.trim();
        for (Object constant : type.getEnumConstants()) {
            Enum<?> candidate = (Enum<?>) constant;
            if (candidate.name().equalsIgnoreCase(text)) {
                return (T) candidate;
            }
            if (candidate instanceof DictEnum dict) {
                Object value = dict.value();
                if (value != null && value.toString().equalsIgnoreCase(text)) {
                    return (T) candidate;
                }
            }
        }
        throw new IllegalArgumentException("无法将 " + text + " 转换为枚举 " + type.getSimpleName());
    }
}
