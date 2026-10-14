package cn.slainc.workorder.spi;

import cn.slainc.workorder.domain.WorkOrderTypeDefinition;
import java.util.List;


public interface WorkOrderTypeProvider {

    List<WorkOrderTypeDefinition> register();
}
