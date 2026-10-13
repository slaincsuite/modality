package cn.slainc.workorder.spi;

import cn.slainc.workorder.comment.WorkOrderComment;
import java.util.Optional;


public interface WorkOrderCommentStore {

    WorkOrderComment save(WorkOrderComment comment);

    Optional<WorkOrderComment> findById(Long id);

    java.util.List<WorkOrderComment> findByOrder(Long orderId);
}
