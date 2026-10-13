package cn.slainc.workorder.number;

import cn.slainc.workorder.model.WorkOrder;


public interface WorkOrderNumberGenerator {

    String generate(WorkOrder order);
}
