package cn.slainc.modules.workorder.store;

import cn.slainc.modules.workorder.entity.WorkOrderCommentRecord;
import cn.slainc.modules.workorder.mapper.WorkOrderCommentMapper;
import cn.slainc.workorder.comment.WorkOrderComment;
import cn.slainc.workorder.spi.WorkOrderCommentStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import java.util.Optional;


public class MpWorkOrderCommentStore implements WorkOrderCommentStore {

    private final WorkOrderCommentMapper commentMapper;

    public MpWorkOrderCommentStore(WorkOrderCommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    @Override
    public WorkOrderComment save(WorkOrderComment comment) {
        WorkOrderCommentRecord record = toRecord(comment);
        if (comment.getId() == null) {
            commentMapper.insert(record);
            comment.setId(record.getId());
            return comment;
        }
        commentMapper.update(record, new LambdaUpdateWrapper<WorkOrderCommentRecord>()
                .eq(WorkOrderCommentRecord::getId, comment.getId())
                .set(WorkOrderCommentRecord::getDeleted, record.getDeleted()));
        return comment;
    }

    @Override
    public Optional<WorkOrderComment> findById(Long id) {
        WorkOrderCommentRecord record = commentMapper.selectById(id);
        return record == null ? Optional.empty() : Optional.of(toDomain(record));
    }

    @Override
    public List<WorkOrderComment> findByOrder(Long orderId) {
        return commentMapper.selectList(new LambdaQueryWrapper<WorkOrderCommentRecord>()
                        .eq(WorkOrderCommentRecord::getOrderId, orderId)
                        .orderByAsc(WorkOrderCommentRecord::getId))
                .stream().map(this::toDomain).toList();
    }

    private WorkOrderCommentRecord toRecord(WorkOrderComment comment) {
        WorkOrderCommentRecord record = new WorkOrderCommentRecord();
        record.setId(comment.getId());
        record.setOrderId(comment.getOrderId());
        record.setAuthorId(comment.getAuthorId());
        record.setContent(comment.getContent());
        record.setVisibility(comment.getVisibility());
        record.setParentId(comment.getParentId());
        record.setQuotedCommentId(comment.getQuotedCommentId());
        record.setLikeCount(comment.getLikeCount());
        record.setDeleted(comment.isDeleted() ? 1 : 0);
        record.setCreateTime(comment.getCreateTime());
        record.setUpdateTime(comment.getUpdateTime());
        return record;
    }

    private WorkOrderComment toDomain(WorkOrderCommentRecord record) {
        WorkOrderComment comment = new WorkOrderComment();
        comment.setId(record.getId());
        comment.setOrderId(record.getOrderId());
        comment.setAuthorId(record.getAuthorId());
        comment.setContent(record.getContent());
        comment.setVisibility(record.getVisibility());
        comment.setParentId(record.getParentId());
        comment.setQuotedCommentId(record.getQuotedCommentId());
        comment.setLikeCount(record.getLikeCount());
        comment.setDeleted(record.getDeleted() != null && record.getDeleted() == 1);
        comment.setCreateTime(record.getCreateTime());
        comment.setUpdateTime(record.getUpdateTime());
        return comment;
    }
}
