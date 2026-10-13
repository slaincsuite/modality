package cn.slainc.modules.workorder.entity;

import cn.slainc.modules.workorder.WorkorderKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


@TableName(WorkOrderReplyRecord.TABLE_NAME)
public class WorkOrderReplyRecord {

    public static final String TABLE_NAME = WorkorderKeys.REPLY_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long workOrderId;

    private Long replierId;

    private String content;

    private Long createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
    }

    public Long getReplierId() {
        return replierId;
    }

    public void setReplierId(Long replierId) {
        this.replierId = replierId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
