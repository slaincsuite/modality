package cn.slainc.core.event;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigChangedEvent extends DomainEvent {

    private final String configKey;

    private final String configGroup;

    private final String oldValue;

    private final String newValue;

    private final boolean removed;

    public ConfigChangedEvent(String configKey, String configGroup, String oldValue, String newValue, boolean removed) {
        super("config");
        this.configKey = configKey;
        this.configGroup = configGroup;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.removed = removed;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public boolean isRemoved() {
        return removed;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configKey", configKey);
        payload.put("configGroup", configGroup);
        payload.put("oldValue", oldValue);
        payload.put("newValue", newValue);
        payload.put("removed", removed);
        return payload;
    }
}
