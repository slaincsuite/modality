package cn.slainc.workorder.store;

import cn.slainc.workorder.spi.WorkOrderTemplateStore;
import cn.slainc.workorder.template.WorkOrderTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class MemoryWorkOrderTemplateStore implements WorkOrderTemplateStore {

    private final Map<String, List<WorkOrderTemplate>> histories = new ConcurrentHashMap<>();

    @Override
    public void save(WorkOrderTemplate template) {
        histories.computeIfAbsent(template.code(), k -> new ArrayList<>()).add(template);
    }

    @Override
    public List<WorkOrderTemplate> history(String code) {
        return List.copyOf(histories.getOrDefault(code, List.of()));
    }

    @Override
    public List<WorkOrderTemplate> findAll() {
        return histories.values().stream()
                .flatMap(List::stream)
                .toList();
    }
}
