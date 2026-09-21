package cn.slainc.web.bind;

import cn.slainc.core.enums.EnabledStatus;
import cn.slainc.core.enums.LoginType;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConverterTest {

    private DefaultFormattingConversionService conversionService() {
        DefaultFormattingConversionService service = new DefaultFormattingConversionService();
        new DateConverterConfig().addFormatters(service);
        return service;
    }

    @Test
    void convertsDates() {
        DefaultFormattingConversionService service = conversionService();
        assertEquals(LocalDate.of(2024, 1, 15), service.convert("2024-01-15", LocalDate.class));
        assertEquals(LocalDate.of(2024, 1, 15), service.convert("20240115", LocalDate.class));
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                service.convert("2024-01-15 10:30:00", LocalDateTime.class));
        assertEquals(LocalTime.of(10, 30, 0), service.convert("10:30:00", LocalTime.class));
        assertEquals(LocalTime.of(10, 30), service.convert("10:30", LocalTime.class));
        assertNull(service.convert("", LocalDate.class));
    }

    @Test
    void registeredConverterHandlesEnumValue() {
        DefaultFormattingConversionService service = conversionService();
        try {
            assertEquals(EnabledStatus.ENABLED, service.convert("1", EnabledStatus.class));
        } catch (RuntimeException ex) {
            throw new AssertionError("枚举按 value 绑定未生效（被 Spring 默认转换器抢先）: " + ex.getMessage(), ex);
        }
    }

    @Test
    void convertsEnumsByNameAndValue() {
        StringToEnumConverterFactory factory = new StringToEnumConverterFactory();
        assertEquals(LoginType.PLATFORM_ADMIN, factory.getConverter(LoginType.class).convert("platform_admin"));
        assertEquals(LoginType.APP, factory.getConverter(LoginType.class).convert(" APP "));
        assertEquals(EnabledStatus.ENABLED, factory.getConverter(EnabledStatus.class).convert("1"));
        assertEquals(EnabledStatus.DISABLED, factory.getConverter(EnabledStatus.class).convert("0"));
        assertNull(factory.getConverter(EnabledStatus.class).convert(""));
        assertThrows(IllegalArgumentException.class,
                () -> factory.getConverter(EnabledStatus.class).convert("9"));
    }
}
