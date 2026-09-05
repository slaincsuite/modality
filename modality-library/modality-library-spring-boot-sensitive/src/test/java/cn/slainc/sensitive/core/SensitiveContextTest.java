package cn.slainc.sensitive.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveContextTest {

    @AfterEach
    void tearDown() {
        SensitiveContext.clear();
    }

    @Test
    void enabledByDefault() {
        assertTrue(SensitiveContext.isEnabled());
        assertEquals(Set.of(), SensitiveContext.groups());
    }

    @Test
    void matchesUngroupedRuleAlways() {
        assertTrue(SensitiveContext.matches(List.of()));
        assertTrue(SensitiveContext.matches(null));
    }

    @Test
    void matchesGroupedRuleOnlyWhenGroupActive() {
        assertFalse(SensitiveContext.matches(List.of("contact")));
        SensitiveContext.setGroups("contact", "audit");
        assertTrue(SensitiveContext.matches(List.of("audit")));
        assertFalse(SensitiveContext.matches(List.of("finance")));
    }

    @Test
    void restoresPreviousStateAfterCallWith() {
        SensitiveContext.setGroups("outer");
        SensitiveContext.setEnabled(false);
        String result = SensitiveContext.callWith(true, List.of("inner"), () -> {
            assertTrue(SensitiveContext.isEnabled());
            assertTrue(SensitiveContext.matches(List.of("inner")));
            return "ok";
        });
        assertEquals("ok", result);
        assertFalse(SensitiveContext.isEnabled());
        assertTrue(SensitiveContext.matches(List.of("outer")));
        assertFalse(SensitiveContext.matches(List.of("inner")));
    }

    @Test
    void restoresEmptyStateAfterRunWithoutSensitive() {
        SensitiveContext.runWithoutSensitive(() -> assertFalse(SensitiveContext.isEnabled()));
        assertTrue(SensitiveContext.isEnabled());
        assertEquals(Set.of(), SensitiveContext.groups());
    }

    @Test
    void clearResetsEverything() {
        SensitiveContext.disable();
        SensitiveContext.setGroups("contact");
        SensitiveContext.clear();
        assertTrue(SensitiveContext.isEnabled());
        assertEquals(Set.of(), SensitiveContext.groups());
    }
}
