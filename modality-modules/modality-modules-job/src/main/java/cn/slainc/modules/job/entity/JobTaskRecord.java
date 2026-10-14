package cn.slainc.modules.job.entity;

import cn.slainc.modules.job.JobKeys;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Duration;

// 定时任务定义表实体：时间统一为毫秒时间戳，重试间隔与超时以秒为单位存储
@TableName(JobTaskRecord.TABLE_NAME)
public class JobTaskRecord {

    public static final String TABLE_NAME = JobKeys.TASK_TABLE;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 64, message = "任务名称长度不能超过64")
    private String jobName;

    @NotBlank(message = "处理器名称不能为空")
    @Size(max = 128, message = "处理器名称长度不能超过128")
    private String handlerName;

    @Size(max = 128, message = "Cron 表达式长度不能超过128")
    private String cron;

    @Size(max = 2000, message = "任务参数长度不能超过2000")
    private String params;

    private Boolean enabled;

    private String status;

    @Min(value = 0, message = "重试次数不能为负数")
    private Integer retryCount;

    @Min(value = 0, message = "重试间隔不能为负数")
    private Integer retryInterval;

    @Min(value = 0, message = "超时时间不能为负数")
    @TableField("timeout_seconds")
    private Integer timeout;

    private Boolean concurrent;

    @Size(max = 32, message = "错过触发策略长度不能超过32")
    private String misfirePolicy;

    @Size(max = 64, message = "任务分组长度不能超过64")
    private String jobGroup;

    @Size(max = 256, message = "备注长度不能超过256")
    private String remark;

    private Long createTime;

    private Long updateTime;

    public Duration retryIntervalDuration() {
        return retryInterval == null ? null : Duration.ofSeconds(Math.max(retryInterval, 0));
    }

    public Duration timeoutDuration() {
        return timeout == null || timeout <= 0 ? null : Duration.ofSeconds(timeout);
    }

    public boolean concurrentOrTrue() {
        return concurrent != null && concurrent;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Integer retryInterval) {
        this.retryInterval = retryInterval;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Boolean getConcurrent() {
        return concurrent;
    }

    public void setConcurrent(Boolean concurrent) {
        this.concurrent = concurrent;
    }

    public String getMisfirePolicy() {
        return misfirePolicy;
    }

    public void setMisfirePolicy(String misfirePolicy) {
        this.misfirePolicy = misfirePolicy;
    }

    public String getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(String jobGroup) {
        this.jobGroup = jobGroup;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
