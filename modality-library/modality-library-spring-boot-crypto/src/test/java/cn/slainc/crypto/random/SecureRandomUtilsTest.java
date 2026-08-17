package cn.slainc.crypto.random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureRandomUtilsTest {

    @Test
    void generatesBytesOfRequestedLength() {
        assertEquals(16, SecureRandomUtils.nextBytes(16).length);
        assertEquals(0, SecureRandomUtils.nextBytes(0).length);
        assertNotEquals(SecureRandomUtils.nextHex(8), SecureRandomUtils.nextHex(8));
        assertEquals(64, SecureRandomUtils.nextHex(32).length());
    }

    @Test
    void generatesNumbersWithinBounds() {
        for (int i = 0; i < 200; i++) {
            int value = SecureRandomUtils.nextInt(10);
            assertTrue(value >= 0 && value < 10);
            long longValue = SecureRandomUtils.nextLong(1000);
            assertTrue(longValue >= 0 && longValue < 1000);
            int ranged = SecureRandomUtils.nextInt(5, 10);
            assertTrue(ranged >= 5 && ranged < 10);
        }
    }

    @Test
    void generatesReadableStrings() {
        String text = SecureRandomUtils.nextString(32);
        assertEquals(32, text.length());
        assertTrue(text.chars().allMatch(character -> Character.isLetterOrDigit(character)));
        String numeric = SecureRandomUtils.nextNumeric(6);
        assertEquals(6, numeric.length());
        assertTrue(numeric.chars().allMatch(Character::isDigit));
        assertTrue(SecureRandomUtils.nextUuid().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void producesDistinctValues() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            values.add(SecureRandomUtils.nextHex(16));
        }
        assertEquals(500, values.size());
    }

    @Test
    void rejectsIllegalArguments() {
        assertThrows(IllegalArgumentException.class, () -> SecureRandomUtils.nextBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> SecureRandomUtils.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> SecureRandomUtils.nextLong(0));
        assertThrows(IllegalArgumentException.class, () -> SecureRandomUtils.nextInt(10, 10));
        assertThrows(IllegalArgumentException.class, () -> SecureRandomUtils.nextString(-1));
        assertThrows(IllegalArgumentException.class, () -> SecureRandomUtils.nextNumeric(-1));
    }
}
