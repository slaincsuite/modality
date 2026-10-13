package cn.slainc.workorder.spi;

import cn.slainc.workorder.template.WorkOrderTemplate;
import java.util.List;
import java.util.Optional;


public interface WorkOrderTemplateStore {

    void save(WorkOrderTemplate template);

    List<WorkOrderTemplate> history(String code);

    List<WorkOrderTemplate> findAll();

    default Optional<WorkOrderTemplate> findCurrent(String code) {
        return history(code).stream()
                .reduce((left, right) -> left.version() >= right.version() ? left : right);
    }
}
