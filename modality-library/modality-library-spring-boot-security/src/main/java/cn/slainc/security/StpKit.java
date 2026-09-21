package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.stp.SecurityStpLogic;

public final class StpKit {

    public static final String TYPE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    public static final String TYPE_PLATFORM_USER = "PLATFORM_USER";

    public static final String TYPE_TENANT_ADMIN = "TENANT_ADMIN";

    public static final String TYPE_TENANT_USER = "TENANT_USER";

    public static final String TYPE_APP = "APP";

    public static final String TYPE_SERVICE = "SERVICE";

    public static final String TYPE_GUEST = "GUEST";

    private static volatile SecurityStpLogic platformAdmin;

    private static volatile SecurityStpLogic platformUser;

    private static volatile SecurityStpLogic tenantAdmin;

    private static volatile SecurityStpLogic tenantUser;

    private static volatile SecurityStpLogic app;

    private static volatile SecurityStpLogic service;

    private static volatile SecurityStpLogic guest;

    private StpKit() {
    }

    public static void assign(SecurityStpLogic logic) {
        if (logic == null) {
            return;
        }
        switch (logic.getLoginType()) {
            case TYPE_PLATFORM_ADMIN -> platformAdmin = logic;
            case TYPE_PLATFORM_USER -> platformUser = logic;
            case TYPE_TENANT_ADMIN -> tenantAdmin = logic;
            case TYPE_TENANT_USER -> tenantUser = logic;
            case TYPE_APP -> app = logic;
            case TYPE_SERVICE -> service = logic;
            case TYPE_GUEST -> guest = logic;
            default -> throw new IllegalArgumentException("不支持的账号体系：" + logic.getLoginType());
        }
    }

    public static void clearAssignments() {
        platformAdmin = null;
        platformUser = null;
        tenantAdmin = null;
        tenantUser = null;
        app = null;
        service = null;
        guest = null;
    }

    public static SecurityStpLogic platformAdmin() {
        return require(platformAdmin, TYPE_PLATFORM_ADMIN);
    }

    public static SecurityStpLogic platformUser() {
        return require(platformUser, TYPE_PLATFORM_USER);
    }

    public static SecurityStpLogic tenantAdmin() {
        return require(tenantAdmin, TYPE_TENANT_ADMIN);
    }

    public static SecurityStpLogic tenantUser() {
        return require(tenantUser, TYPE_TENANT_USER);
    }

    public static SecurityStpLogic app() {
        return require(app, TYPE_APP);
    }

    public static SecurityStpLogic service() {
        return require(service, TYPE_SERVICE);
    }

    public static SecurityStpLogic guest() {
        return require(guest, TYPE_GUEST);
    }

    public static SecurityStpLogic of(LoginType type) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        return switch (type) {
            case PLATFORM_ADMIN -> platformAdmin();
            case PLATFORM_USER -> platformUser();
            case TENANT_ADMIN -> tenantAdmin();
            case TENANT_USER -> tenantUser();
            case APP -> app();
            case SERVICE -> service();
            case GUEST -> guest();
        };
    }

    private static SecurityStpLogic require(SecurityStpLogic logic, String type) {
        if (logic == null) {
            throw new IllegalStateException("账号体系 " + type + " 的 StpLogic 尚未初始化，请确认安全模块自动配置已生效");
        }
        return logic;
    }
}
