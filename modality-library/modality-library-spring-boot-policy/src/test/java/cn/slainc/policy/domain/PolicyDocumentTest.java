package cn.slainc.policy.domain;

import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyDocumentTest {

    private static PolicyStatement statement(Effect effect, List<String> actions, List<String> resources) {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("s1");
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(resources);
        return statement;
    }

    @Test
    void validDocumentValidatesAndRoundTrips() {
        PolicyStatement statement = statement(Effect.ALLOW, List.of("modality:user:*"), List.of("*"));
        statement.setCondition(Map.of("string_equal", Map.of("modality:UserId", List.of("1001"))));
        PolicyDocument document = PolicyDocument.of(statement);
        String json = document.toJson();
        PolicyDocument parsed = PolicyDocument.parse(json);
        assertEquals("2.0", parsed.getVersion());
        assertEquals(1, parsed.getStatements().size());
        assertEquals(Effect.ALLOW, parsed.getStatements().get(0).effectEnum());
        assertTrue(parsed.getStatements().get(0).hasCondition());
        assertNotNull(parsed.getStatements().get(0).getCondition().get("string_equal"));
    }

    @Test
    void versionMustBeTwoPointZero() {
        PolicyDocument document = new PolicyDocument();
        document.setVersion("1.0");
        document.getStatements().add(statement(Effect.ALLOW, List.of("*"), List.of("*")));
        BizException exception = assertThrows(BizException.class, document::validate);
        assertTrue(exception.getMessage().contains("2.0"));
    }

    @Test
    void emptyStatementListRejected() {
        PolicyDocument document = new PolicyDocument();
        BizException exception = assertThrows(BizException.class, document::validate);
        assertTrue(exception.getMessage().contains("至少需要一条语句"));
    }

    @Test
    void invalidEffectAndUnknownOperatorGiveChineseError() {
        PolicyStatement statement = statement(Effect.ALLOW, List.of("*"), List.of("*"));
        statement.setEffect("maybe");
        PolicyDocument document = new PolicyDocument();
        document.getStatements().add(statement);
        BizException exception = assertThrows(BizException.class, document::validate);
        assertTrue(exception.getMessage().contains("第 1 条语句"));

        PolicyDocument withUnknown = PolicyDocument.of(statement(Effect.DENY, List.of("*"), List.of("*")));
        withUnknown.getStatements().get(0)
                .setCondition(Map.of("string_equals", Map.of("k", List.of("v"))));
        BizException operatorException = assertThrows(BizException.class, withUnknown::validate);
        assertTrue(operatorException.getMessage().contains("非法的条件运算符"));
    }

    @Test
    void missingActionOrResourceRejected() {
        PolicyDocument missingAction = new PolicyDocument();
        PolicyStatement statement = statement(Effect.ALLOW, List.of(), List.of("*"));
        missingAction.getStatements().add(statement);
        assertThrows(BizException.class, missingAction::validate);

        PolicyDocument missingResource = new PolicyDocument();
        missingResource.getStatements().add(statement(Effect.ALLOW, List.of("*"), List.of()));
        assertThrows(BizException.class, missingResource::validate);
    }

    @Test
    void parsingEmptyJsonGivesChineseError() {
        assertThrows(BizException.class, () -> PolicyDocument.parse(null));
        assertThrows(BizException.class, () -> PolicyDocument.parse("  "));
    }

    @Test
    void parsingMalformedJsonGivesChineseError() {
        BizException exception = assertThrows(BizException.class, () -> PolicyDocument.parse("{not-json"));
        assertTrue(exception.getMessage().contains("不是合法的 JSON"));
    }
}
