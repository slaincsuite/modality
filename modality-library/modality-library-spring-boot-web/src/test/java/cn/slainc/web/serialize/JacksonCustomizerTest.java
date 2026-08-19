package cn.slainc.web.serialize;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonCustomizerTest {

    record Payload(int a) {
    }

    private JsonMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void serializesLongAsString() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", 1800000000000000001L);
        assertEquals("{\"id\":\"1800000000000000001\"}", mapper().writeValueAsString(source));
    }

    @Test
    void serializesDatesAsIsoText() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("date", LocalDate.of(2024, 1, 15));
        source.put("time", LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        String json = mapper().writeValueAsString(source);
        assertTrue(json.contains("2024-01-15"), json);
        assertTrue(json.contains("2024-01-15T10:30:00"), json);
    }

    @Test
    void ignoresUnknownPropertiesOnRead() {
        assertEquals(1, mapper().readValue("{\"a\":1,\"unknown\":true}", Payload.class).a());
    }
}
