package cn.slainc.tenant.isolation;

public record DatabaseIsolationTemplate(String urlTemplate, String usernameTemplate, String passwordTemplate,
                                       String driverClassName, String groupName) {

    public static final String TENANT_ID_PLACEHOLDER = "{tenantId}";

    public static final String TENANT_CODE_PLACEHOLDER = "{tenantCode}";

    public boolean isConfigured() {
        return urlTemplate != null && !urlTemplate.isBlank();
    }

    public String renderUrl(TenantInfo tenant) {
        return render(urlTemplate, tenant);
    }

    public String renderUsername(TenantInfo tenant) {
        return render(usernameTemplate, tenant);
    }

    public String renderPassword(TenantInfo tenant) {
        return render(passwordTemplate, tenant);
    }

    public String resolveGroupName() {
        return groupName == null || groupName.isBlank() ? "tenant" : groupName;
    }

    private static String render(String template, TenantInfo tenant) {
        if (template == null) {
            return null;
        }
        return template
                .replace(TENANT_ID_PLACEHOLDER, String.valueOf(tenant.getTenantId()))
                .replace(TENANT_CODE_PLACEHOLDER, String.valueOf(tenant.getTenantCode()));
    }
}
