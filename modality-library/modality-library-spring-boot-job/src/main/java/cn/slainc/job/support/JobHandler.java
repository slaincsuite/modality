package cn.slainc.job.support;

@FunctionalInterface
public interface JobHandler {

    JobResult execute(JobContext context);
}
