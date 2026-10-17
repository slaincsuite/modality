package cn.slainc.core.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionMatcherTest {

    @Test
    void matchesExactAndWildcardPermissions() {
        assertTrue(PermissionMatcher.matches("upms:user:query", "upms:user:query"));
        assertTrue(PermissionMatcher.matches("upms:user:*", "upms:user:query"));
        assertTrue(PermissionMatcher.matches("modality:*:*", "modality:upms:user:query"));
        assertTrue(PermissionMatcher.matches("*", "anything"));
        assertFalse(PermissionMatcher.matches("upms:role:*", "upms:user:query"));
        assertFalse(PermissionMatcher.matches("upms:user:query", "upms:user:query:extra"));
    }

    @Test
    void wildcardAlgorithmDoesNotBacktrackExponentially() {
        String granted = "*a*a*a*a*a*a*a*a*a*a*a*a*a*a*a*a*a*b";
        String required = "a".repeat(200);
        assertFalse(PermissionMatcher.matches(granted, required));
    }

    @Test
    void matchesAnyHandlesAliasesAndNulls() {
        assertTrue(PermissionMatcher.matchesAny(
                List.of("upms:user:query", "modality:upms:user:query"), "upms:user:query"));
        assertFalse(PermissionMatcher.matchesAny(List.of(), "upms:user:query"));
        assertFalse(PermissionMatcher.matchesAny(null, "upms:user:query"));
    }

    @Test
    void deniedPatternsTakePrecedenceOverGrantedPatterns() {
        List<String> permissions = List.of("*", "!upms:user:delete", "!modality:upms:user:delete");
        assertTrue(PermissionMatcher.granted(permissions, "upms:user:query"));
        assertFalse(PermissionMatcher.granted(permissions, "upms:user:delete"));
        assertFalse(PermissionMatcher.granted(permissions, "modality:upms:user:delete"));
    }
}
