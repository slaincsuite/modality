package cn.slainc.changeaudit.support;

import cn.slainc.changeaudit.autoconfigure.ChangeAuditConfigReader;
import cn.slainc.changeaudit.autoconfigure.ChangeAuditProperties;
import cn.slainc.changeaudit.support.FieldChange;
import cn.slainc.changeaudit.support.TestObjectProviders;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JaVersFieldDifferTest {

    private final FieldDiffer builtin = new FieldDiffer(null,
            new ChangeAuditConfigReader(new ChangeAuditProperties(), TestObjectProviders.empty()));

    private final JaVersFieldDiffer javers = new JaVersFieldDiffer(builtin);

    @Test
    void reportsAScalarChange() {
        List<FieldChange> changes = javers.diff(new Order("A-1", new BigDecimal("10.00")),
                new Order("A-2", new BigDecimal("10.00")));

        assertThat(javers.fellBack()).isFalse();
        assertThat(changes).extracting(FieldChange::field).contains("orderNo");
        assertThat(changes).filteredOn(change -> "orderNo".equals(change.field()))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.before()).contains("A-1");
                    assertThat(change.after()).contains("A-2");
                });
    }

    @Test
    void returnsNothingWhenObjectsAreEqual() {
        Order order = new Order("A-1", new BigDecimal("10.00"));

        assertThat(javers.diff(order, order)).isEmpty();
    }

    @Test
    void keepsTheJsonContractIdenticalToTheBuiltinEngine() {
        List<FieldChange> changes = javers.diff(new Order("A-1", new BigDecimal("1")),
                new Order("A-1", new BigDecimal("2")));

        String json = javers.serialize(changes);

        assertThat(json).startsWith("[").endsWith("]").contains("\"field\"").contains("\"before\"")
                .contains("\"after\"");
    }

    @Test
    void fallsBackInsteadOfThrowingWhenTheTypeIsNotComparable() {
        assertThat(javers.diff(new Object(), new Object())).isNotNull();
        assertThat(javers.fellBack()).isTrue();
        assertThat(javers.diff(null, null)).isEmpty();
    }

    private record Order(String orderNo, BigDecimal amount) {
    }
}
