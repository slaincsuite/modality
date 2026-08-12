package cn.slainc.utils.enums;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumUtilsTest {

    enum Color {
        RED(1, "红色"),
        GREEN(2, "绿色");

        private final int value;
        private final String label;

        Color(int value, String label) {
            this.value = value;
            this.label = label;
        }

        public int getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }
    }

    @Test
    void lookupByName() {
        assertEquals(Color.RED, EnumUtils.getEnum(Color.class, "RED"));
        assertEquals(Color.RED, EnumUtils.getEnumIgnoreCase(Color.class, "red"));
        assertNull(EnumUtils.getEnum(Color.class, "BLUE"));
        assertNull(EnumUtils.getEnum(null, "RED"));
        assertTrue(EnumUtils.contains(Color.class, "GREEN"));
        assertFalse(EnumUtils.contains(Color.class, "BLUE"));
    }

    @Test
    void lookupByValue() {
        Function<Color, Object> extractor = Color::getValue;
        assertEquals(Color.GREEN, EnumUtils.getEnumByValue(Color.class, 2, extractor));
        assertNull(EnumUtils.getEnumByValue(Color.class, 9, extractor));
        assertEquals(Color.RED, EnumUtils.getEnumByValueIgnoreCase(Color.class, "红色", Color::getLabel));
    }

    @Test
    void dictionaryMapping() {
        Map<Object, String> dict = EnumUtils.toDict(Color.class, Color::getValue, Color::getLabel);
        assertEquals("红色", dict.get(1));
        assertEquals("绿色", dict.get(2));
        assertEquals(2, EnumUtils.toMap(Color.class).size());
        assertEquals(List.of("RED", "GREEN"), EnumUtils.names(Color.class));
    }
}
