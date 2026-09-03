package cn.slainc.logger.sample;

import java.util.concurrent.ThreadLocalRandom;

public class RateLogSampler implements LogSampler {

    private volatile double rate;

    public RateLogSampler(double rate) {
        setRate(rate);
    }

    public void setRate(double rate) {
        this.rate = Math.max(0.0d, Math.min(1.0d, rate));
    }

    @Override
    public double currentRate() {
        return rate;
    }

    @Override
    public boolean accept(String loggerName) {
        double current = rate;
        if (current >= 1.0d) {
            return true;
        }
        if (current <= 0.0d) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < current;
    }
}
