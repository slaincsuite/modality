package cn.slainc.job.support;

public interface JobRecorder {

    void record(JobContext context, JobResult result);
}
