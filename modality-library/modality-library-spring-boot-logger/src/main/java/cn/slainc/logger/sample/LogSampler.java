package cn.slainc.logger.sample;

public interface LogSampler {

    boolean accept(String loggerName);

    default String strategy() {
        return "FIXED";
    }

    default double currentRate() {
        return 1.0d;
    }
}
