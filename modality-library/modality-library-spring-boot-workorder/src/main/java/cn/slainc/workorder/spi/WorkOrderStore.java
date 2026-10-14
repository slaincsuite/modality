package cn.slainc.workorder.spi;

import cn.slainc.workorder.domain.WorkOrder;
import java.util.Optional;


public interface WorkOrderStore {

    WorkOrder save(WorkOrder order);

    void update(WorkOrder order);

    Optional<WorkOrder> findById(Long id);

    Optional<WorkOrder> findByNumber(String number);

    long countActiveByAssignee(Long assigneeId);
}
