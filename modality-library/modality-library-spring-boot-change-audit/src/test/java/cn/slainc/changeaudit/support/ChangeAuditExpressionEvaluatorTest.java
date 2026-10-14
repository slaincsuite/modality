package cn.slainc.changeaudit.support;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeAuditExpressionEvaluatorTest {

    private final ChangeAuditExpressionEvaluator evaluator = new ChangeAuditExpressionEvaluator();

    @Test
    void resolvesParametersByNameAndByIndex() throws Exception {
        Method method = Sample.class.getDeclaredMethod("update", Long.class, SamplePayload.class);

        assertThat(evaluator.evaluate("#id", method, new Object[]{7L, new SamplePayload("n")}, null))
                .isEqualTo(7L);
        assertThat(evaluator.evaluate("#p0", method, new Object[]{7L, new SamplePayload("n")}, null))
                .isEqualTo(7L);
        assertThat(evaluator.evaluate("#payload.name", method, new Object[]{7L, new SamplePayload("n")}, null))
                .isEqualTo("n");
    }

    @Test
    void exposesTheResultVariable() throws Exception {
        Method method = Sample.class.getDeclaredMethod("update", Long.class, SamplePayload.class);

        assertThat(evaluator.evaluate("#result.name", method, new Object[]{7L, new SamplePayload("n")},
                new SamplePayload("updated"))).isEqualTo("updated");
    }

    @Test
    void returnsNullWhenTheExpressionCannotBeResolved() throws Exception {
        Method method = Sample.class.getDeclaredMethod("update", Long.class, SamplePayload.class);

        assertThat(evaluator.evaluate("#missing", method, new Object[]{7L, new SamplePayload("n")}, null)).isNull();
        assertThat(evaluator.evaluate("", method, new Object[]{7L, new SamplePayload("n")}, null)).isNull();
        assertThat(evaluator.evaluate("#id", null, new Object[0], null)).isNull();
    }

    enum Sample {
        ;

        static void update(Long id, SamplePayload payload) {
        }
    }

    record SamplePayload(String name) {
    }
}
