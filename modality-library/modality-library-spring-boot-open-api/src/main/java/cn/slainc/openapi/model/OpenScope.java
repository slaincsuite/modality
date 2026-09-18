package cn.slainc.openapi.model;

public class OpenScope {

    private String name;

    private String title;

    private String description = "";

    private String group = "默认";

    private ScopeRiskLevel riskLevel = ScopeRiskLevel.LOW;

    private boolean requiresAudit;

    public boolean risky() {
        return riskLevel != ScopeRiskLevel.LOW || requiresAudit;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public boolean isRequiresAudit() {
        return requiresAudit;
    }

    public void setRequiresAudit(boolean requiresAudit) {
        this.requiresAudit = requiresAudit;
    }
}
