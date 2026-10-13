package cn.slainc.modules.workorder.store;

import cn.slainc.modules.workorder.entity.WorkOrderRecord;
import cn.slainc.modules.workorder.mapper.WorkOrderMapper;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.workorder.model.WorkOrder;
import cn.slainc.workorder.spi.WorkOrderStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;


public class MpWorkOrderStore implements WorkOrderStore {

    private final WorkOrderMapper workOrderMapper;

    public MpWorkOrderStore(WorkOrderMapper workOrderMapper) {
        this.workOrderMapper = workOrderMapper;
    }

    @Override
    public WorkOrder save(WorkOrder order) {
        WorkOrderRecord record = toRecord(order);
        if (order.getId() == null) {
            workOrderMapper.insert(record);
            order.setId(record.getId());
        } else {
            workOrderMapper.updateById(record);
        }
        return order;
    }

    @Override
    public void update(WorkOrder order) {
        workOrderMapper.updateById(toRecord(order));
    }

    @Override
    public Optional<WorkOrder> findById(Long id) {
        return Optional.ofNullable(workOrderMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<WorkOrder> findByNumber(String number) {
        WorkOrderRecord record = workOrderMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderRecord>().eq(WorkOrderRecord::getNumber, number));
        return record == null ? Optional.empty() : Optional.of(toDomain(record));
    }

    @Override
    public long countActiveByAssignee(Long assigneeId) {
        return workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderRecord>()
                .eq(WorkOrderRecord::getAssigneeId, assigneeId)
                .notIn(WorkOrderRecord::getStatus, "CLOSED", "RESOLVED"));
    }

    private WorkOrderRecord toRecord(WorkOrder order) {
        WorkOrderRecord record = new WorkOrderRecord();
        record.setId(order.getId());
        record.setNumber(order.getNumber());
        record.setTypeCode(order.getTypeCode());
        record.setTitle(order.getTitle());
        record.setContent(order.getContent());
        record.setPriority(order.getPriority());
        record.setStatus(order.getStatus());
        record.setSource(order.getSource());
        record.setSourceSystem(order.getSourceSystem());
        record.setSourceBizId(order.getSourceBizId());
        record.setParentOrderId(order.getParentOrderId());
        record.setCustomFieldsJson(order.getCustomFields() == null || order.getCustomFields().isEmpty()
                ? null
                : JsonUtils.toJson(order.getCustomFields()));
        record.setCollaboratorIdsJson(order.getCollaboratorIds() == null || order.getCollaboratorIds().isEmpty()
                ? null
                : JsonUtils.toJson(order.getCollaboratorIds()));
        record.setSubmitterId(order.getSubmitterId());
        record.setAssigneeId(order.getAssigneeId());
        record.setCreateTime(order.getCreateTime());
        record.setUpdateTime(order.getUpdateTime());
        record.setVersion(order.getVersion());
        return record;
    }

    private WorkOrder toDomain(WorkOrderRecord record) {
        WorkOrder order = new WorkOrder();
        order.setId(record.getId());
        order.setNumber(record.getNumber());
        order.setTypeCode(record.getTypeCode());
        order.setTitle(record.getTitle());
        order.setContent(record.getContent());
        order.setPriority(record.getPriority());
        order.setStatus(record.getStatus());
        order.setSource(record.getSource());
        order.setSourceSystem(record.getSourceSystem());
        order.setSourceBizId(record.getSourceBizId());
        order.setParentOrderId(record.getParentOrderId());
        order.setCustomFields(record.getCustomFieldsJson() == null || record.getCustomFieldsJson().isBlank()
                ? null
                : JsonUtils.parseMap(record.getCustomFieldsJson()));
        order.setCollaboratorIds(record.getCollaboratorIdsJson() == null || record.getCollaboratorIdsJson().isBlank()
                ? null
                : JsonUtils.parseList(record.getCollaboratorIdsJson(), Long.class));
        order.setSubmitterId(record.getSubmitterId());
        order.setAssigneeId(record.getAssigneeId());
        order.setCreateTime(record.getCreateTime());
        order.setUpdateTime(record.getUpdateTime());
        order.setVersion(record.getVersion());
        return order;
    }
}
