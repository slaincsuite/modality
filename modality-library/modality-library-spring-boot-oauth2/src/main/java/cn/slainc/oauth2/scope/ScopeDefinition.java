package cn.slainc.oauth2.scope;

public class ScopeDefinition {

    private String name;

    private String description = "";

    private String group = "默认";

    private ScopeRiskLevel riskLevel = ScopeRiskLevel.LOW;

    public boolean requiresConfirm() {
        return riskLevel != ScopeRiskLevel.LOW;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public ScopeRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(ScopeRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = ScopeRiskLevel.of(riskLevel);
    }
}
