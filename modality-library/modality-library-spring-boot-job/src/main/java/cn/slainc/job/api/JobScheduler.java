package cn.slainc.job.api;

import cn.slainc.job.support.JobDefinition;
import cn.slainc.job.support.JobRecorder;
import cn.slainc.job.support.JobResult;
import cn.slainc.job.support.JobTriggerAck;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Set;

public interface JobScheduler {

    String TRACE_ID_PARAM = "traceId";

    void setRecorderProvider(ObjectProvider<JobRecorder> recorderProvider);

    void register(JobDefinition definition);

    void registerOnStartup(JobDefinition definition);

    Map<String, JobDefinition> definitions();

    Set<String> runningJobs();

    JobTriggerAck trigger(String name, Map<String, Object> params);

    JobResult triggerNow(String name, Map<String, Object> params);

    void cancel(String name);

    JobResult execute(JobDefinition definition, Map<String, Object> params);

    JobResult lastResult(String name);

    void destroy();
}
