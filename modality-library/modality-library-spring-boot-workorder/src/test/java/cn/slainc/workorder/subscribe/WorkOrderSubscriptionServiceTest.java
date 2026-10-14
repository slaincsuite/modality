package cn.slainc.workorder.subscribe;

import cn.slainc.workorder.spi.WorkOrderStore;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class WorkOrderSubscriptionServiceTest {

    private WorkOrderSubscriptionService service;

    @BeforeEach
    void setUp() {
        WorkOrderStore orderStore = new cn.slainc.workorder.store.MemoryWorkOrderStore();
        cn.slainc.workorder.domain.WorkOrder order = new cn.slainc.workorder.domain.WorkOrder();
        order.setId(1L);
        order.setNumber("WO2026101300001");
        orderStore.save(order);
        service = new WorkOrderSubscriptionService(
                new cn.slainc.workorder.store.MemoryWorkOrderSubscriptionStore(), orderStore);
    }

    @Test
    void subscribeIsIdempotentAndUnsubscribeRemoves() {
        service.subscribeOrder(1L, 101L);
        service.subscribeOrder(1L, 101L);
        assertThat(service.orderSubscribers(1L)).containsExactly(101L);
        assertThat(service.unsubscribeOrder(1L, 101L)).isTrue();
        assertThat(service.orderSubscribers(1L)).isEmpty();
        assertThat(service.unsubscribeOrder(1L, 101L)).isFalse();
    }

    @Test
    void typeSubscriptionsListDistinctTypeCodes() {
        service.subscribeType("generic", 101L);
        service.subscribeType("generic", 101L);
        service.subscribeType("fault", 101L);
        assertThat(service.myTypeSubscriptions(101L)).containsExactlyInAnyOrder("generic", "fault");
        service.unsubscribeType("generic", 101L);
        assertThat(service.myTypeSubscriptions(101L)).containsExactly("fault");
    }
}
