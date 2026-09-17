package cn.slainc.policy.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PolicyStatement {

    private String sid;

    private String effect;

    private List<String> actions = new ArrayList<>();

    private List<String> resources = new ArrayList<>();

    private Map<String, Map<String, List<String>>> condition;

    public Effect effectEnum() {
        return Effect.of(effect);
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public void setEffect(Effect effect) {
        this.effect = effect == null ? null : effect.code();
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }

    public List<String> getResources() {
        return resources;
    }

    public void setResources(List<String> resources) {
        this.resources = resources;
    }

    public Map<String, Map<String, List<String>>> getCondition() {
        return condition;
    }

    public void setCondition(Map<String, Map<String, List<String>>> condition) {
        this.condition = condition;
    }
}
