package cn.slainc.web.bind;

import cn.slainc.utils.date.DateUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DateConverterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToLocalDateConverter());
        registry.addConverter(new StringToLocalDateTimeConverter());
        registry.addConverter(new StringToLocalTimeConverter());
        registry.addConverterFactory(new StringToEnumConverterFactory());
    }

    public static class StringToLocalDateConverter implements Converter<String, LocalDate> {

        @Override
        public LocalDate convert(String source) {
            return source == null || source.isBlank() ? null : DateUtils.parseDate(source);
        }
    }

    public static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

        @Override
        public LocalDateTime convert(String source) {
            return source == null || source.isBlank() ? null : DateUtils.parseDateTime(source);
        }
    }

    public static class StringToLocalTimeConverter implements Converter<String, LocalTime> {

        @Override
        public LocalTime convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            String text = source.trim();
            for (String pattern : new String[]{"HH:mm:ss", "HH:mm", "HHmmss"}) {
                try {
                    return LocalTime.parse(text, DateTimeFormatter.ofPattern(pattern));
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
            return null;
        }
    }
}
