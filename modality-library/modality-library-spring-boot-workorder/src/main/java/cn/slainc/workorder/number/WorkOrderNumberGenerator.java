package cn.slainc.workorder.number;

import cn.slainc.workorder.domain.WorkOrder;


public interface WorkOrderNumberGenerator {

    String generate(WorkOrder order);
}
