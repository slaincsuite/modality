package cn.slainc.workorder.store;

import cn.slainc.workorder.comment.WorkOrderComment;
import cn.slainc.workorder.spi.WorkOrderCommentStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class MemoryWorkOrderCommentStore implements WorkOrderCommentStore {

    private final Map<Long, WorkOrderComment> comments = new ConcurrentHashMap<>();

    private final AtomicLong ids = new AtomicLong();

    @Override
    public WorkOrderComment save(WorkOrderComment comment) {
        if (comment.getId() == null) {
            comment.setId(ids.incrementAndGet());
        }
        comments.put(comment.getId(), comment);
        return comment;
    }

    @Override
    public Optional<WorkOrderComment> findById(Long id) {
        WorkOrderComment comment = comments.get(id);
        return comment == null || comment.isDeleted() ? Optional.empty() : Optional.of(comment);
    }

    @Override
    public List<WorkOrderComment> findByOrder(Long orderId) {
        return comments.values().stream()
                .filter(c -> c.getOrderId().equals(orderId) && !c.isDeleted())
                .sorted(java.util.Comparator.comparingLong(WorkOrderComment::getId))
                .toList();
    }
}
