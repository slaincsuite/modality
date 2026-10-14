package cn.slainc.modules.workorder.service.order;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.workorder.entity.WorkOrderRecord;
import cn.slainc.modules.workorder.entity.WorkOrderReplyRecord;
import cn.slainc.workorder.enums.WorkOrderPriority;
import cn.slainc.workorder.enums.WorkOrderStatus;
import cn.slainc.modules.workorder.mapper.WorkOrderMapper;
import cn.slainc.modules.workorder.mapper.WorkOrderReplyMapper;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.workorder.engine.BatchTransitionResult;
import cn.slainc.workorder.engine.WorkOrderEngine;
import cn.slainc.workorder.engine.WorkOrderTransitionPreview;
import cn.slainc.workorder.domain.WorkOrderActivity;
import cn.slainc.workorder.engine.WorkOrderEngine;
import cn.slainc.workorder.domain.WorkOrder;
import cn.slainc.workorder.domain.WorkOrderSource;
import cn.slainc.workorder.spi.WorkOrderActivityStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;


public class WorkOrderService {

    public static final String DEFAULT_TYPE_CODE = "generic";

    public static final int MAX_EXPORT_LIMIT = 50000;

    private final WorkOrderMapper workOrderMapper;

    private final WorkOrderReplyMapper replyMapper;

    private final WorkOrderEngine engine;

    private final WorkOrderActivityStore activityStore;

    public WorkOrderService(WorkOrderMapper workOrderMapper, WorkOrderReplyMapper replyMapper,
                            WorkOrderEngine engine, WorkOrderActivityStore activityStore) {
        this.workOrderMapper = workOrderMapper;
        this.replyMapper = replyMapper;
        this.engine = engine;
        this.activityStore = activityStore;
    }

    public WorkOrderRecord create(String title, String content, String priorityCode) {
        return create(title, content, priorityCode, null);
    }

    public WorkOrderRecord create(String title, String content, String priorityCode,
                                  Map<String, Object> customFields) {
        if (priorityCode != null && !priorityCode.isBlank()) {
            WorkOrderPriority.parse(priorityCode);
        }
        WorkOrder created = engine.create(DEFAULT_TYPE_CODE, title, content,
                priorityCode == null || priorityCode.isBlank() ? null : priorityCode,
                WorkOrderSource.MANUAL.name(), null, null, null, UserContext.userId(), customFields);
        return requireOrder(created.getId());
    }

    public PageResult<WorkOrderRecord> pageManage(String status, String priority, String keyword,
                                                  long current, long size) {
        LambdaQueryWrapper<WorkOrderRecord> wrapper = baseWrapper(status, priority, keyword)
                .orderByDesc(WorkOrderRecord::getId);
        Page<WorkOrderRecord> page = workOrderMapper.selectPage(Page.of(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<WorkOrderRecord> pageMine(long current, long size) {
        Page<WorkOrderRecord> page = workOrderMapper.selectPage(Page.of(current, size),
                new LambdaQueryWrapper<WorkOrderRecord>()
                        .eq(WorkOrderRecord::getSubmitterId, UserContext.userId())
                        .orderByDesc(WorkOrderRecord::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<WorkOrderRecord> list(String status, String priority, String keyword, int limit) {
        return workOrderMapper.selectList(baseWrapper(status, priority, keyword)
                .orderByDesc(WorkOrderRecord::getId)
                .last("LIMIT " + clampExportLimit(limit)));
    }

    public static int clampExportLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_EXPORT_LIMIT);
    }

    public WorkOrderRecord requireOrder(Long id) {
        WorkOrderRecord record = workOrderMapper.selectById(id);
        if (record == null) {
            throw new BizException("工单不存在");
        }
        return record;
    }

    public WorkOrderRecord requireOwnOrder(Long id) {
        WorkOrderRecord record = requireOrder(id);
        if (!record.getSubmitterId().equals(UserContext.userId())) {
            throw new BizException("只能操作本人提交的工单");
        }
        return record;
    }

    public List<WorkOrderReplyRecord> replies(Long orderId) {
        return replyMapper.selectList(new LambdaQueryWrapper<WorkOrderReplyRecord>()
                .eq(WorkOrderReplyRecord::getWorkOrderId, orderId)
                .orderByAsc(WorkOrderReplyRecord::getId));
    }

    public WorkOrderReplyRecord reply(Long orderId, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("回复内容不能为空");
        }
        WorkOrderRecord record = requireOrder(orderId);
        requireNotClosed(record);
        WorkOrderReplyRecord reply = new WorkOrderReplyRecord();
        reply.setWorkOrderId(orderId);
        reply.setReplierId(UserContext.userId());
        reply.setContent(content.trim());
        reply.setCreateTime(System.currentTimeMillis());
        replyMapper.insert(reply);
        return reply;
    }

    public WorkOrderRecord assign(Long orderId, Long assigneeId) {
        engine.assign(orderId, assigneeId, UserContext.userId());
        return requireOrder(orderId);
    }

    public WorkOrderRecord resolve(Long orderId) {
        engine.transition(orderId, "resolve", UserContext.userId(), Map.of());
        return requireOrder(orderId);
    }

    public WorkOrderRecord close(Long orderId) {
        engine.transition(orderId, "close", UserContext.userId(), Map.of());
        return requireOrder(orderId);
    }

    public WorkOrderRecord closeOwn(Long orderId) {
        WorkOrderRecord record = requireOwnOrder(orderId);
        requireNotClosed(record);
        record.setStatus(WorkOrderStatus.CLOSED.name());
        touch(record);
        workOrderMapper.updateById(record);
        return record;
    }

    public WorkOrderRecord claim(Long id) {
        engine.claim(id, UserContext.userId());
        return requireOrder(id);
    }

    public WorkOrderRecord transfer(Long id, Long toAssigneeId, String reason) {
        engine.transfer(id, toAssigneeId, reason, UserContext.userId());
        return requireOrder(id);
    }

    public WorkOrderRecord escalate(Long id, Long toAssigneeId, String reason) {
        engine.escalate(id, toAssigneeId, reason, UserContext.userId());
        return requireOrder(id);
    }

    public WorkOrderRecord suspend(Long id, String reason) {
        engine.suspend(id, reason, UserContext.userId());
        return requireOrder(id);
    }

    public WorkOrderRecord resume(Long id) {
        engine.resume(id, UserContext.userId());
        return requireOrder(id);
    }

    public WorkOrderRecord reopen(Long id) {
        engine.transition(id, "reopen", UserContext.userId(), Map.of());
        return requireOrder(id);
    }

    public void urge(Long id, String message) {
        engine.urge(id, message, UserContext.userId());
    }

    public WorkOrderRecord autoAssign(Long id, List<Long> candidates, String strategyName) {
        engine.pickAndAssign(id, candidates, strategyName, UserContext.userId());
        return requireOrder(id);
    }

    public BatchTransitionResult batchReopen(List<Long> ids) {
        return engine.batchTransition(ids, "reopen", UserContext.userId(), Map.of());
    }

    public WorkOrderTransitionPreview preview(Long id, String action) {
        return engine.preview(id, action);
    }

    public List<WorkOrderActivity> activities(Long id) {
        return engine.activities(id);
    }

    private LambdaQueryWrapper<WorkOrderRecord> baseWrapper(String status, String priority, String keyword) {
        return new LambdaQueryWrapper<WorkOrderRecord>()
                .eq(status != null && !status.isBlank(), WorkOrderRecord::getStatus, status)
                .eq(priority != null && !priority.isBlank(), WorkOrderRecord::getPriority, priority)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(WorkOrderRecord::getTitle, keyword)
                                .or()
                                .like(WorkOrderRecord::getContent, keyword));
    }

    private void requireNotClosed(WorkOrderRecord record) {
        if (WorkOrderStatus.CLOSED.name().equals(record.getStatus())) {
            throw new BizException("工单已关闭，不允许该操作");
        }
    }

    private void touch(WorkOrderRecord record) {
        record.setUpdateTime(System.currentTimeMillis());
    }
}
