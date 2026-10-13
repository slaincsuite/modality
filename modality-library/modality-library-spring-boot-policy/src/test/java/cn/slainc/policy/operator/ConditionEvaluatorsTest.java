package cn.slainc.policy.operator;

import cn.slainc.policy.model.ConditionOperator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEvaluatorsTest {

    @Test
    void stringEqualWithMultiValueAnyMatch() {
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_EQUAL, "prod", List.of("prod")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_EQUAL, "uat", List.of("prod", "uat")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_EQUAL, "dev", List.of("prod", "uat")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_EQUAL, null, List.of("prod")));
    }

    @Test
    void stringNotEqualRequiresAllMiss() {
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_NOT_EQUAL, "dev", List.of("prod", "uat")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_NOT_EQUAL, "prod", List.of("prod", "uat")));
    }

    @Test
    void stringLikeWildcardMatch() {
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "dept-07", List.of("dept-*")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "07-dept", List.of("*-dept")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "aXb", List.of("a*b")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "ab", List.of("a*b")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "ba", List.of("a*b")));
    }

    @Test
    void stringLikeTreatsSqlWildcardsLiterally() {
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "dept_%", List.of("dept_%")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "dept-07", List.of("dept_%")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "deptA", List.of("dept_")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.STRING_LIKE, "100%", List.of("*%")));
    }

    @Test
    void numericComparisons() {
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_EQUAL, 100, List.of("100")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_EQUAL, "100.00", List.of("100")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_EQUAL, 101, List.of("100")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_LESS_THAN, 99, List.of("100")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_LESS_THAN, 99, List.of("500", "100")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_LESS_THAN, 100, List.of("100")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_GREATER_THAN, 101, List.of("100")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_GREATER_THAN, "abc", List.of("100")));
    }

    @Test
    void invalidNumericInputFails() {
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_EQUAL, "abc", List.of("100")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.NUMERIC_LESS_THAN, null, List.of("100")));
    }

    @Test
    void dateComparisonSupportsMultipleFormats() {
        String deadline = "2099-01-01T00:00:00Z";
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.DATE_LESS_THAN,
                "2026-01-01T00:00:00Z", List.of(deadline)));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.DATE_GREATER_THAN,
                "2026-01-01T00:00:00Z", List.of(deadline)));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.DATE_GREATER_THAN,
                4102444800000L, List.of(deadline)));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.DATE_LESS_THAN,
                "2026-01-01T00:00:00", List.of(deadline)));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.DATE_LESS_THAN,
                "2026-01-01", List.of(deadline)));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.DATE_LESS_THAN,
                "not-a-date", List.of(deadline)));
    }

    @Test
    void boolEqual() {
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.BOOL_EQUAL, true, List.of("true")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.BOOL_EQUAL, "false", List.of("false")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.BOOL_EQUAL, "yes", List.of("true")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.BOOL_EQUAL, null, List.of("true")));
    }

    @Test
    void ipEqualAndRangeContainment() {
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.IP_EQUAL, "10.0.0.1", List.of("10.0.0.1")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.IP_EQUAL, "10.0.0.1", List.of("10.0.0.2")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.IP_IN_RANGE, "10.1.2.3", List.of("10.0.0.0/8")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.IP_IN_RANGE, "192.168.1.1",
                List.of("10.0.0.0/8", "192.168.0.0/16")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.IP_IN_RANGE, "8.8.8.8", List.of("10.0.0.0/8")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.IP_IN_RANGE, "not-ip", List.of("10.0.0.0/8")));
    }

    @Test
    void ipOperatorsTolerateInvalidInput() {
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.IP_IN_RANGE, "10.0.0.1", List.of("not-a-cidr")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.IP_EQUAL, "10.0.0.1", List.of("not-an-ip")));
    }

    @Test
    void nullJudgement() {
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.NULL_EQUAL, null, List.of("true")));
        assertTrue(ConditionEvaluators.evaluate(ConditionOperator.NULL_EQUAL, "value", List.of("false")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.NULL_EQUAL, "value", List.of("true")));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.NULL_EQUAL, null, List.of("false")));
    }

    @Test
    void emptyExpectedListFails() {
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_EQUAL, "x", List.of()));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_EQUAL, "x", null));
        assertFalse(ConditionEvaluators.evaluate(ConditionOperator.STRING_EQUAL, "x", List.of(" ", "")));
    }
}
