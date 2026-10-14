package cn.slainc.workorder.comment;

import cn.slainc.core.exception.BizException;
import cn.slainc.workorder.spi.WorkOrderCommentStore;
import cn.slainc.workorder.spi.WorkOrderStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class WorkOrderCommentServiceTest {

    private List<Object> events;

    private cn.slainc.workorder.store.MemoryWorkOrderStore orderStore;

    private cn.slainc.workorder.store.MemoryWorkOrderCommentStore commentStore;

    private WorkOrderCommentService service;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        orderStore = new cn.slainc.workorder.store.MemoryWorkOrderStore();
        commentStore = new cn.slainc.workorder.store.MemoryWorkOrderCommentStore();
        orderStore.save(orderWithNumber("WO2026101300001"));
        cn.slainc.workorder.domain.WorkOrder other = orderWithNumber("WO2026101300002");
        other.setId(2L);
        orderStore.save(other);
        service = new WorkOrderCommentService(commentStore, orderStore, events::add);
    }

    private cn.slainc.workorder.domain.WorkOrder orderWithNumber(String number) {
        cn.slainc.workorder.domain.WorkOrder order = new cn.slainc.workorder.domain.WorkOrder();
        order.setNumber(number);
        order.setStatus("OPEN");
        return order;
    }

    private void seedSecondOrder() {
        cn.slainc.workorder.domain.WorkOrder other = new cn.slainc.workorder.domain.WorkOrder();
        other.setId(2L);
        other.setNumber("WO2026101300002");
        other.setStatus("OPEN");
    }

    @Test
    void addInternalCommentPublishesCommentedEvent() {
        WorkOrderComment comment = service.add(1L, 101L, "已经复现，正在排查",
                WorkOrderComment.VISIBILITY_INTERNAL, null, null);
        assertThat(comment.getVisibility()).isEqualTo(WorkOrderComment.VISIBILITY_INTERNAL);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(cn.slainc.workorder.event.WorkOrderCommentedEvent.class);
    }

    @Test
    void editWindowExpiresAfterLimit() {
        WorkOrderComment comment = service.add(1L, 101L, "初版内容",
                WorkOrderComment.VISIBILITY_INTERNAL, null, null);
        WorkOrderCommentService strict = new WorkOrderCommentService(commentStore, orderStore, events::add, -1);
        assertThatThrownBy(() -> strict.edit(comment.getId(), 101L, "修改内容"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("可编辑时限");
    }

    @Test
    void onlyAuthorCanEditOrDelete() {
        WorkOrderComment comment = service.add(1L, 101L, "内容", WorkOrderComment.VISIBILITY_INTERNAL, null, null);
        assertThatThrownBy(() -> service.edit(comment.getId(), 202L, "改内容"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能编辑本人评论");
        assertThatThrownBy(() -> service.delete(comment.getId(), 202L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能删除本人评论");
    }

    @Test
    void replyAndQuoteMustBelongToSameOrder() {
        WorkOrderComment other = service.add(2L, 101L, "别的工单的评论",
                WorkOrderComment.VISIBILITY_INTERNAL, null, null);
        assertThatThrownBy(() -> service.add(1L, 101L, "回复", WorkOrderComment.VISIBILITY_INTERNAL,
                other.getId(), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于该工单");
        assertThatThrownBy(() -> service.add(1L, 101L, "引用", WorkOrderComment.VISIBILITY_INTERNAL,
                null, other.getId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于该工单");
    }

    @Test
    void likeIncreasesAndCancelClampsAtZero() {
        WorkOrderComment comment = service.add(1L, 101L, "内容", WorkOrderComment.VISIBILITY_INTERNAL, null, null);
        service.like(comment.getId(), false);
        service.like(comment.getId(), false);
        assertThat(service.like(comment.getId(), true).getLikeCount()).isEqualTo(1);
        assertThat(service.like(comment.getId(), true).getLikeCount()).isZero();
    }

    @Test
    void externalVisibilityFilteredForInternalOnlyView() {
        service.add(1L, 101L, "内部评论", WorkOrderComment.VISIBILITY_INTERNAL, null, null);
        service.add(1L, 101L, "外部评论", WorkOrderComment.VISIBILITY_EXTERNAL, null, null);
        assertThat(service.listVisible(1L, true)).hasSize(2);
        assertThat(service.listVisible(1L, false)).hasSize(1);
    }

    @Test
    void blankContentIsRejected() {
        assertThatThrownBy(() -> service.add(1L, 101L, " ", WorkOrderComment.VISIBILITY_INTERNAL, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("评论内容不能为空");
    }
}
