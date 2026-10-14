package cn.slainc.workorder.engine;

import cn.slainc.core.exception.BizException;
import cn.slainc.workorder.assign.WorkOrderAssignStrategy;
import cn.slainc.workorder.event.WorkOrderAssignedEvent;
import cn.slainc.workorder.event.WorkOrderClaimedEvent;
import cn.slainc.workorder.event.WorkOrderClosedEvent;
import cn.slainc.workorder.event.WorkOrderCreatedEvent;
import cn.slainc.workorder.event.WorkOrderDeletedEvent;
import cn.slainc.workorder.event.WorkOrderEscalatedEvent;
import cn.slainc.workorder.event.WorkOrderReopenedEvent;
import cn.slainc.workorder.event.WorkOrderResumedEvent;
import cn.slainc.workorder.event.WorkOrderSuspendedEvent;
import cn.slainc.workorder.event.WorkOrderTransitionedEvent;
import cn.slainc.workorder.event.WorkOrderTransferredEvent;
import cn.slainc.workorder.event.WorkOrderUpdatedEvent;
import cn.slainc.workorder.event.WorkOrderUrgedEvent;
import cn.slainc.workorder.domain.WorkOrder;
import cn.slainc.workorder.domain.WorkOrderActivity;
import cn.slainc.workorder.domain.WorkOrderSource;
import cn.slainc.workorder.domain.WorkOrderTransitionDefinition;
import cn.slainc.workorder.number.WorkOrderNumberGenerator;
import cn.slainc.workorder.spi.WorkOrderActivityStore;
import cn.slainc.workorder.spi.WorkOrderStore;
import cn.slainc.workorder.statemachine.WorkOrderTransitionContext;
import cn.slainc.workorder.template.WorkOrderFormValidator;
import cn.slainc.workorder.template.WorkOrderTemplateService;
import cn.slainc.workorder.type.WorkOrderTypeRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;


public class WorkOrderEngine {

    private final WorkOrderStore store;

    private final WorkOrderNumberGenerator numberGenerator;

    private final WorkOrderTypeRegistry typeRegistry;

    private final WorkOrderTemplateService templateService;

    private final WorkOrderFormValidator formValidator;

    private final WorkOrderActivityStore activityStore;

    private final Map<String, WorkOrderAssignStrategy> assignStrategies;

    private final ApplicationEventPublisher publisher;

    public WorkOrderEngine(WorkOrderStore store, WorkOrderNumberGenerator numberGenerator,
                           WorkOrderTypeRegistry typeRegistry, WorkOrderTemplateService templateService,
                           WorkOrderFormValidator formValidator, WorkOrderActivityStore activityStore,
                           List<WorkOrderAssignStrategy> assignStrategies, ApplicationEventPublisher publisher) {
        this.store = store;
        this.numberGenerator = numberGenerator;
        this.typeRegistry = typeRegistry;
        this.templateService = templateService;
        this.formValidator = formValidator;
        this.activityStore = activityStore;
        this.assignStrategies = assignStrategies == null ? Map.of() : assignStrategies.stream()
                .collect(Collectors.toMap(WorkOrderAssignStrategy::name, Function.identity()));
        this.publisher = publisher;
    }

    public WorkOrder create(String typeCode, String title, String content, String priority, String sourceCode,
                            String sourceSystem, String sourceBizId, Long parentOrderId, Long submitterId) {
        return create(typeCode, title, content, priority, sourceCode, sourceSystem, sourceBizId, parentOrderId,
                submitterId, null);
    }

    public WorkOrder create(String typeCode, String title, String content, String priority, String sourceCode,
                            String sourceSystem, String sourceBizId, Long parentOrderId, Long submitterId,
                            Map<String, Object> customFields) {
        if (title == null || title.isBlank()) {
            throw new BizException("工单标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException("工单内容不能为空");
        }
        typeRegistry.type(typeCode);
        templateService.findEnabledByType(typeCode).ifPresent(template -> {
            List<String> violations = formValidator.validate(template, customFields);
            if (!violations.isEmpty()) {
                throw new BizException(String.join("；", violations));
            }
        });
        long now = System.currentTimeMillis();
        WorkOrder order = new WorkOrder();
        order.setTypeCode(typeCode);
        order.setTitle(title.trim());
        order.setContent(content.trim());
        order.setPriority(priority);
        order.setStatus("OPEN");
        order.setSource(sourceCode == null ? WorkOrderSource.MANUAL.name() : sourceCode);
        order.setSourceSystem(sourceSystem);
        order.setSourceBizId(sourceBizId);
        order.setParentOrderId(parentOrderId);
        order.setCustomFields(customFields);
        order.setSubmitterId(submitterId);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setNumber(numberGenerator.generate(order));
        store.save(order);
        publisher.publishEvent(new WorkOrderCreatedEvent(order.getId(), order.getNumber(), typeCode, submitterId));
        return order;
    }

    public WorkOrder get(Long id) {
        return store.findById(id).orElseThrow(() -> new BizException("工单不存在"));
    }

    public WorkOrder findByNumber(String number) {
        return store.findByNumber(number).orElseThrow(() -> new BizException("工单不存在"));
    }

    public WorkOrder updateBasics(Long id, String title, String content, String priority) {
        WorkOrder order = get(id);
        if (title != null && !title.isBlank()) {
            order.setTitle(title.trim());
        }
        if (content != null && !content.isBlank()) {
            order.setContent(content.trim());
        }
        if (priority != null && !priority.isBlank()) {
            order.setPriority(priority);
        }
        order.setUpdateTime(System.currentTimeMillis());
        store.update(order);
        publisher.publishEvent(new WorkOrderUpdatedEvent(order.getId(), order.getNumber()));
        return order;
    }

    public void delete(Long id) {
        WorkOrder order = get(id);
        order.setDeleted(true);
        order.setUpdateTime(System.currentTimeMillis());
        store.update(order);
        publisher.publishEvent(new WorkOrderDeletedEvent(order.getId(), order.getNumber()));
    }

    public WorkOrder transition(Long id, String action, Long operatorId,
                                Map<String, Object> variables) {
        return doTransition(id, action, operatorId, variables, null, null);
    }

    public WorkOrder assign(Long id, Long assigneeId, Long operatorId) {
        if (assigneeId == null) {
            throw new BizException("处理人不能为空");
        }
        WorkOrder order = doTransition(id, "assign", operatorId, Map.of(), assigneeId, null);
        order.setAssigneeId(assigneeId);
        store.update(order);
        publisher.publishEvent(new WorkOrderAssignedEvent(order.getId(), order.getNumber(), assigneeId, operatorId));
        return order;
    }

    public WorkOrder claim(Long id, Long claimantId) {
        if (claimantId == null) {
            throw new BizException("认领人不能为空");
        }
        WorkOrder order = doTransition(id, "claim", claimantId, Map.of(), claimantId, null);
        order.setAssigneeId(claimantId);
        store.update(order);
        publisher.publishEvent(new WorkOrderClaimedEvent(order.getId(), order.getNumber(), claimantId));
        return order;
    }

    public WorkOrder transfer(Long id, Long toAssigneeId, String reason, Long operatorId) {
        if (toAssigneeId == null) {
            throw new BizException("转派目标处理人不能为空");
        }
        WorkOrder order = get(id);
        Long fromAssigneeId = order.getAssigneeId();
        doTransition(id, "transfer", operatorId, Map.of(), toAssigneeId, reason);
        order.setAssigneeId(toAssigneeId);
        store.update(order);
        publisher.publishEvent(new WorkOrderTransferredEvent(order.getId(), order.getNumber(), fromAssigneeId,
                toAssigneeId, reason, operatorId));
        return order;
    }

    public WorkOrder escalate(Long id, Long toAssigneeId, String reason, Long operatorId) {
        if (toAssigneeId == null) {
            throw new BizException("升级目标处理人不能为空");
        }
        WorkOrder order = get(id);
        Long fromAssigneeId = order.getAssigneeId();
        doTransition(id, "escalate", operatorId, Map.of(), toAssigneeId, reason);
        order.setAssigneeId(toAssigneeId);
        store.update(order);
        publisher.publishEvent(new WorkOrderEscalatedEvent(order.getId(), order.getNumber(), fromAssigneeId,
                toAssigneeId, reason));
        return order;
    }

    public WorkOrder suspend(Long id, String reason, Long operatorId) {
        WorkOrder order = doTransition(id, "suspend", operatorId, Map.of(), null, reason);
        publisher.publishEvent(new WorkOrderSuspendedEvent(order.getId(), order.getNumber(), reason, operatorId));
        return order;
    }

    public WorkOrder resume(Long id, Long operatorId) {
        WorkOrder order = doTransition(id, "resume", operatorId, Map.of(), null, null);
        publisher.publishEvent(new WorkOrderResumedEvent(order.getId(), order.getNumber(), operatorId));
        return order;
    }

    public WorkOrder urge(Long id, String message, Long operatorId) {
        WorkOrder order = get(id);
        Long urgedUserId = order.getAssigneeId();
        if (urgedUserId == null) {
            throw new BizException("工单尚未指定处理人，无法催办");
        }
        activityStore.save(new WorkOrderActivity(id, "urge", operatorId, urgedUserId, message,
                System.currentTimeMillis()));
        publisher.publishEvent(new WorkOrderUrgedEvent(id, order.getNumber(), urgedUserId, operatorId, message));
        return order;
    }

    public BatchTransitionResult batchTransition(List<Long> ids, String action, Long operatorId,
                                                 Map<String, Object> variables) {
        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            try {
                doTransition(id, action, operatorId, variables, null, null);
                succeeded.add(id);
            } catch (BizException exception) {
                failures.put(id, exception.getMessage());
            }
        }
        return new BatchTransitionResult(succeeded, failures);
    }

    public WorkOrderTransitionPreview preview(Long id, String action) {
        WorkOrder order = get(id);
        WorkOrderTransitionDefinition definition = typeRegistry.machineFor(order.getTypeCode())
                .findTransition(order.getStatus(), action)
                .orElse(null);
        if (definition == null) {
            return new WorkOrderTransitionPreview(null, false);
        }
        boolean allowed = typeRegistry.machineFor(order.getTypeCode())
                .canTransition(order, action, Map.of());
        return new WorkOrderTransitionPreview(definition, allowed);
    }

    public WorkOrder pickAndAssign(Long id, List<Long> candidates, String strategyName, Long operatorId) {
        WorkOrderAssignStrategy strategy = assignStrategies.get(strategyName);
        if (strategy == null) {
            throw new BizException("未知的分配策略：" + strategyName);
        }
        Long assigneeId = strategy.pick(get(id), candidates, store::countActiveByAssignee);
        if (assigneeId == null) {
            throw new BizException("候选处理人为空，无法分配");
        }
        return assign(id, assigneeId, operatorId);
    }

    public List<WorkOrderActivity> activities(Long id) {
        return activityStore.findByOrder(id);
    }

    private WorkOrder doTransition(Long id, String action, Long operatorId, Map<String, Object> variables,
                                   Long targetUserId, String reason) {
        WorkOrder order = get(id);
        WorkOrderTransitionDefinition definition = typeRegistry.machineFor(order.getTypeCode())
                .transition(new WorkOrderTransitionContext(order, operatorId, variables), action);
        store.update(order);
        activityStore.save(new WorkOrderActivity(id, action, operatorId, targetUserId, reason,
                System.currentTimeMillis()));
        publisher.publishEvent(new WorkOrderTransitionedEvent(id, order.getNumber(), action,
                definition.source(), definition.target(), operatorId));
        if ("close".equals(action)) {
            publisher.publishEvent(new WorkOrderClosedEvent(id, order.getNumber(), operatorId));
        }
        if ("reopen".equals(action)) {
            publisher.publishEvent(new WorkOrderReopenedEvent(id, order.getNumber(), operatorId));
        }
        return order;
    }

}
