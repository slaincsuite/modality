package cn.slainc.workorder.spi;

import cn.slainc.workorder.domain.WorkOrderActivity;
import java.util.List;


public interface WorkOrderActivityStore {

    void save(WorkOrderActivity activity);

    List<WorkOrderActivity> findByOrder(Long orderId);
}
