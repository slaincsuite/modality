package cn.slainc.utils.random;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomUtilsTest {

    @Test
    void randomStrings() {
        assertEquals(16, RandomUtils.randomNumeric(16).length());
        assertTrue(RandomUtils.randomNumeric(16).chars().allMatch(Character::isDigit));
        assertEquals(8, RandomUtils.randomAlphabetic(8).length());
        assertEquals(12, RandomUtils.randomAlphanumeric(12).length());
        assertEquals(0, RandomUtils.randomString(0, RandomUtils.DIGITS).length());
        assertEquals(4, RandomUtils.randomString(4, "ab").length());
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomString(4, ""));
        assertNotEquals(RandomUtils.randomString(20, RandomUtils.ALPHANUMERIC),
                RandomUtils.randomString(20, RandomUtils.ALPHANUMERIC));
    }

    @Test
    void numericRanges() {
        for (int i = 0; i < 200; i++) {
            int value = RandomUtils.randomInt(10, 20);
            assertTrue(value >= 10 && value < 20);
        }
        assertEquals(5L, RandomUtils.randomLong(5L, 6L));
        assertEquals(3, RandomUtils.randomBytes(3).length);
        assertEquals(0, RandomUtils.randomBytes(0).length);
    }

    @Test
    void collectionHelpers() {
        List<Integer> source = List.of(1, 2, 3, 4, 5);
        assertEquals(5, RandomUtils.shuffle(source).size());
        assertEquals(2, RandomUtils.sample(source, 2).size());
        assertEquals(0, RandomUtils.sample(source, 0).size());
        assertTrue(source.contains(RandomUtils.randomElement(source)));
        assertNull(RandomUtils.randomElement(List.of()));
    }

    @Test
    void uuidVariants() {
        assertEquals(36, UuidUtils.uuidString().length());
        assertEquals(32, UuidUtils.uuidCompact().length());
        assertEquals(32, UuidUtils.uuidV7Compact().length());
        assertEquals(7, UuidUtils.uuidV7().version());
        assertEquals(4, UuidUtils.uuid().version());
        assertTrue(UuidUtils.isValid(UuidUtils.uuidString()));
        assertTrue(UuidUtils.isValid(UuidUtils.uuidV7String()));
        assertTrue(UuidUtils.isValid(UuidUtils.fromCompact(UuidUtils.uuidCompact()).toString()));
        String compact = UuidUtils.uuidCompact();
        assertEquals(compact, UuidUtils.fromCompact(compact).toString().replace("-", ""));
    }
}
