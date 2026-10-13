package cn.slainc.workorder.autoconfigure;

import cn.slainc.workorder.number.SequenceWorkOrderNumberGenerator;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "modality.workorder-engine")
public class WorkorderEngineProperties {

    private boolean enabled = true;

    private final Number number = new Number();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Number getNumber() {
        return number;
    }

    public static class Number {

        private String prefix = "WO";

        private SequenceWorkOrderNumberGenerator.ResetPeriod reset = SequenceWorkOrderNumberGenerator.ResetPeriod.DAILY;

        private int padWidth = 5;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public SequenceWorkOrderNumberGenerator.ResetPeriod getReset() {
            return reset;
        }

        public void setReset(SequenceWorkOrderNumberGenerator.ResetPeriod reset) {
            this.reset = reset;
        }

        public int getPadWidth() {
            return padWidth;
        }

        public void setPadWidth(int padWidth) {
            this.padWidth = padWidth;
        }
    }
}
