package cn.slainc.workorder.template;

import cn.slainc.core.exception.BizException;
import cn.slainc.workorder.spi.WorkOrderTemplateStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class WorkOrderTemplateService {

    private final WorkOrderTemplateStore store;

    public WorkOrderTemplateService(WorkOrderTemplateStore store) {
        this.store = store;
    }

    public WorkOrderTemplate create(String code, String name, String typeCode,
                                    List<WorkOrderFieldDefinition> fields) {
        checkDefinitions(code, fields);
        if (store.findCurrent(code).isPresent()) {
            throw new BizException("工单模板编码已存在：" + code);
        }
        WorkOrderTemplate template = new WorkOrderTemplate(code, name, typeCode, 1, true, fields);
        store.save(template);
        return template;
    }

    public WorkOrderTemplate update(String code, String name, List<WorkOrderFieldDefinition> fields, boolean enabled) {
        WorkOrderTemplate current = current(code);
        checkDefinitions(code, fields);
        WorkOrderTemplate next = new WorkOrderTemplate(code, name, current.typeCode(), current.version() + 1,
                enabled, fields);
        store.save(next);
        return next;
    }

    public WorkOrderTemplate rollback(String code, int targetVersion) {
        WorkOrderTemplate target = store.history(code).stream()
                .filter(t -> t.version() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new BizException("模板历史版本不存在：" + targetVersion));
        WorkOrderTemplate current = current(code);
        WorkOrderTemplate restored = new WorkOrderTemplate(code, target.name(), target.typeCode(),
                current.version() + 1, true, target.fields());
        store.save(restored);
        return restored;
    }

    public WorkOrderTemplate current(String code) {
        return store.findCurrent(code).orElseThrow(() -> new BizException("工单模板不存在：" + code));
    }

    public Optional<WorkOrderTemplate> findEnabledByType(String typeCode) {
        return store.findAll().stream()
                .filter(WorkOrderTemplate::enabled)
                .filter(t -> t.typeCode().equals(typeCode))
                .reduce((left, right) -> left.version() >= right.version() ? left : right);
    }

    public List<String> diff(String code, int leftVersion, int rightVersion) {
        Map<Integer, WorkOrderTemplate> history = store.history(code).stream()
                .collect(Collectors.toMap(WorkOrderTemplate::version, Function.identity()));
        WorkOrderTemplate left = history.get(leftVersion);
        WorkOrderTemplate right = history.get(rightVersion);
        if (left == null || right == null) {
            throw new BizException("模板历史版本不存在");
        }
        List<String> differences = new ArrayList<>();
        Map<String, WorkOrderFieldDefinition> leftFields = byCode(left);
        Map<String, WorkOrderFieldDefinition> rightFields = byCode(right);
        for (String codeKey : rightFields.keySet()) {
            if (!leftFields.containsKey(codeKey)) {
                differences.add("字段[" + codeKey + "] 新增");
            }
        }
        for (String codeKey : leftFields.keySet()) {
            WorkOrderFieldDefinition before = leftFields.get(codeKey);
            WorkOrderFieldDefinition after = rightFields.get(codeKey);
            if (!rightFields.containsKey(codeKey)) {
                differences.add("字段[" + codeKey + "] 删除");
            } else if (!before.equals(after)) {
                differences.add("字段[" + codeKey + "] 定义变更");
            }
        }
        if (!left.name().equals(right.name())) {
            differences.add("模板名称由「" + left.name() + "」变更为「" + right.name() + "」");
        }
        return differences;
    }

    private Map<String, WorkOrderFieldDefinition> byCode(WorkOrderTemplate template) {
        return template.fields().stream()
                .collect(Collectors.toMap(WorkOrderFieldDefinition::code, Function.identity()));
    }

    private void checkDefinitions(String code, List<WorkOrderFieldDefinition> fields) {
        Set<String> codes = new HashSet<>();
        for (WorkOrderFieldDefinition field : fields) {
            if (!codes.add(field.code())) {
                throw new BizException("模板 " + code + " 字段编码重复：" + field.code());
            }
            if ((field.type() == WorkOrderFieldType.SELECT || field.type() == WorkOrderFieldType.MULTISELECT)
                    && field.options().isEmpty()) {
                throw new BizException("字段 " + field.code() + " 为选择类型，必须配置选项");
            }
        }
    }
}
