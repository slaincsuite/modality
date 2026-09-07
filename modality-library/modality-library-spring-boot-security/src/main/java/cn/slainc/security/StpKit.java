package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.stp.SecurityStpLogic;

public final class StpKit {

    public static final String TYPE_ADMIN = "ADMIN";

    public static final String TYPE_USER = "USER";

    public static final String TYPE_CLIENT = "CLIENT";

    private static volatile SecurityStpLogic admin;

    private static volatile SecurityStpLogic user;

    private static volatile SecurityStpLogic client;

    private StpKit() {
    }

    public static void assign(SecurityStpLogic logic) {
        if (logic == null) {
            return;
        }
        switch (logic.getLoginType()) {
            case TYPE_ADMIN -> admin = logic;
            case TYPE_USER -> user = logic;
            case TYPE_CLIENT -> client = logic;
            default -> throw new IllegalArgumentException("不支持的账号体系：" + logic.getLoginType());
        }
    }

    public static void clearAssignments() {
        admin = null;
        user = null;
        client = null;
    }

    public static SecurityStpLogic admin() {
        return require(admin, TYPE_ADMIN);
    }

    public static SecurityStpLogic user() {
        return require(user, TYPE_USER);
    }

    public static SecurityStpLogic client() {
        return require(client, TYPE_CLIENT);
    }

    public static SecurityStpLogic of(LoginType type) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        return switch (type) {
            case ADMIN -> admin();
            case USER -> user();
            case CLIENT -> client();
        };
    }

    private static SecurityStpLogic require(SecurityStpLogic logic, String type) {
        if (logic == null) {
            throw new IllegalStateException("账号体系 " + type + " 的 StpLogic 尚未初始化，请确认安全模块自动配置已生效");
        }
        return logic;
    }
}
