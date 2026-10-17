package cn.slainc.modules.policy.service.catalog;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.dto.PolicyActionView;
import cn.slainc.modules.policy.dto.PolicyFeatureView;
import cn.slainc.modules.policy.dto.PolicyProductView;
import cn.slainc.modules.policy.dto.PolicyResourceNode;
import cn.slainc.modules.policy.dto.PolicyServiceView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PolicyCatalogService {

    private static final Map<String, String> TOKEN_LABELS = Map.ofEntries(
            Map.entry("access", "系统访问"), Map.entry("apikey", "API Key"),
            Map.entry("api-log", "接口日志"), Map.entry("app", "应用"),
            Map.entry("assign", "分配"), Map.entry("assign-permission", "分配权限"),
            Map.entry("assign-role", "分配角色"), Map.entry("assign-user", "分配用户"),
            Map.entry("attachment", "附件"), Map.entry("bind", "绑定"),
            Map.entry("boundary", "边界"), Map.entry("cache", "缓存"),
            Map.entry("catalog", "目录"), Map.entry("check", "检测"),
            Map.entry("clean", "清理"), Map.entry("cleanup", "清理"),
            Map.entry("clear", "清空"), Map.entry("close", "关闭"),
            Map.entry("comment", "评论"), Map.entry("config", "配置"),
            Map.entry("consent", "用户授权"), Map.entry("create", "创建"),
            Map.entry("data-scope", "数据范围"), Map.entry("delete", "删除"),
            Map.entry("delivery", "投递"), Map.entry("dept", "部门"),
            Map.entry("dict", "字典"), Map.entry("disable", "停用"),
            Map.entry("execute", "执行"), Map.entry("export", "导出"),
            Map.entry("file", "文件"), Map.entry("grant", "授权"),
            Map.entry("group", "用户组"), Map.entry("import", "导入"),
            Map.entry("issue", "签发"), Map.entry("kick", "强制下线"),
            Map.entry("level", "级别"), Map.entry("log", "日志"),
            Map.entry("login-policy", "登录策略"), Map.entry("manage", "管理"),
            Map.entry("menu", "菜单"), Map.entry("merge", "合并"),
            Map.entry("mfa-policy", "多因素策略"), Map.entry("node", "节点"),
            Map.entry("notice", "公告"), Map.entry("notification", "通知"),
            Map.entry("notify", "通知"), Map.entry("offline", "下线"),
            Map.entry("online", "在线会话"), Map.entry("order", "工单"),
            Map.entry("permission", "权限"), Map.entry("permission-boundary", "权限边界"),
            Map.entry("plan", "套餐"), Map.entry("policy", "策略"),
            Map.entry("policy-log", "策略日志"), Map.entry("preview", "预览"),
            Map.entry("purge", "彻底删除"), Map.entry("query", "查询"),
            Map.entry("recycle", "回收站"), Map.entry("redis", "Redis"),
            Map.entry("remove", "移除"), Map.entry("reset-password", "重置密码"),
            Map.entry("restore", "恢复"), Map.entry("retry", "重试"),
            Map.entry("review", "审核"), Map.entry("revoke", "解除授权"),
            Map.entry("role", "角色"), Map.entry("rollback", "回滚"),
            Map.entry("run", "执行"), Map.entry("scope", "授权范围"),
            Map.entry("search", "搜索"), Map.entry("security", "安全"),
            Map.entry("send", "发送"), Map.entry("session-policy", "会话策略"),
            Map.entry("settings", "设置"), Map.entry("simulate", "仿真"),
            Map.entry("sql", "SQL"), Map.entry("stats", "统计"),
            Map.entry("stream", "直播源"), Map.entry("system", "系统"),
            Map.entry("task", "任务"), Map.entry("template", "模板"),
            Map.entry("tenant", "租户"), Map.entry("test-case", "测试用例"),
            Map.entry("third-config", "第三方配置"), Map.entry("third-sync", "第三方同步"),
            Map.entry("transfer", "转移"), Map.entry("trigger", "触发"),
            Map.entry("unbind", "解绑"), Map.entry("update", "修改"),
            Map.entry("user", "用户"), Map.entry("validate", "校验"),
            Map.entry("version", "版本"), Map.entry("visibility", "可见性"),
            Map.entry("webhook", "Webhook"));

    private static final Map<String, PolicyServiceView> SERVICES = new LinkedHashMap<>();

    private static final Map<String, List<PolicyActionView>> ACTIONS = new LinkedHashMap<>();

    private static final List<PolicyProductView> PRODUCTS;

    private static final List<PolicyResourceNode> RESOURCES;

    private static final Set<String> ACTION_CODES;

    static {
        service("upms", "用户权限中心", "用户、角色、用户组与基础权限管理");
        service("auth", "认证中心", "登录、会话、令牌与多因素认证");
        service("policy", "策略中心", "策略文档、绑定、仿真与策略治理");
        service("audit", "审计中心", "操作审计、登录审计与告警");
        service("open", "开放平台", "应用、范围、Webhook 与开放接口");
        service("workorder", "工单中心", "工单流转、附件与统计");
        service("tenant", "租户中心", "租户、套餐与租户生命周期");
        service("infra", "基础设施", "配置、文件、通知与搜索");
        service("job", "定时任务", "任务定义、触发与执行记录");
        service("live", "实时监控", "直播源登记、健康探测与监控");

        actions("upms", List.of(
                action("upms", "user:query", "查询用户", "READ", "分页或详情读取用户信息"),
                action("upms", "user:create", "创建用户", "WRITE", "创建租户用户账号"),
                action("upms", "user:update", "修改用户", "WRITE", "修改用户资料与状态"),
                action("upms", "user:delete", "删除用户", "WRITE", "删除或停用用户账号"),
                action("upms", "role:query", "查询角色", "READ", "读取角色与角色成员"),
                action("upms", "group:query", "查询用户组", "READ", "读取用户组与成员")));
        actions("auth", List.of());
        actions("policy", List.of(
                action("policy", "manage:query", "查询策略", "READ", "读取策略、版本与审计"),
                action("policy", "manage:create", "创建策略", "WRITE", "创建策略文档"),
                action("policy", "manage:update", "修改策略", "WRITE", "修改策略或切换状态"),
                action("policy", "manage:delete", "删除策略", "WRITE", "删除非系统策略"),
                action("policy", "binding:bind", "绑定策略", "WRITE", "将策略绑定到主体"),
                action("policy", "binding:unbind", "解绑策略", "WRITE", "解除主体策略绑定"),
                action("policy", "simulate:run", "仿真策略", "READ", "执行策略命中仿真")));
        actions("audit", List.of(
                action("audit", "log:query", "查询审计日志", "READ", "读取操作与登录审计"),
                action("audit", "log:export", "导出审计日志", "READ", "导出审计明细")));
        actions("open", List.of(
                action("open", "app:query", "查询应用", "READ", "读取开放平台应用")));
        actions("workorder", List.of(
                action("workorder", "order:query", "查询工单", "READ", "读取工单详情与列表"),
                action("workorder", "order:close", "关闭工单", "WRITE", "完成或关闭工单")));
        actions("tenant", List.of());
        actions("infra", List.of(
                action("infra", "config:query", "查询配置", "READ", "读取动态配置"),
                action("infra", "config:update", "修改配置", "WRITE", "更新动态配置"),
                action("infra", "file:upload", "上传文件", "WRITE", "写入文件存储")));
        actions("job", List.of(
                action("job", "task:query", "查询任务", "READ", "读取任务与执行记录"),
                action("job", "task:create", "创建任务", "WRITE", "创建定时任务"),
                action("job", "task:delete", "删除任务", "WRITE", "删除定时任务")));
        actions("live", List.of(
                action("live", "stream:query", "查询直播源", "READ", "读取直播源与健康状态"),
                action("live", "stream:create", "创建直播源", "WRITE", "登记直播源"),
                action("live", "stream:update", "修改直播源", "WRITE", "更新直播源配置"),
                action("live", "stream:delete", "删除直播源", "WRITE", "删除直播源"),
                action("live", "stream:check", "检测直播源", "WRITE", "执行单源或批量健康探测")));

        extendActions("upms", "access:grant|WRITE", "access:query|READ", "access:revoke|WRITE",
                "dept:create|WRITE", "dept:delete|WRITE", "dept:query|READ", "dept:update|WRITE",
                "group:assign|WRITE", "group:create|WRITE", "group:delete|WRITE", "group:query|READ",
                "group:update|WRITE", "log:export|READ", "log:query|READ",
                "menu:create|WRITE", "menu:delete|WRITE", "menu:query|READ", "menu:update|WRITE",
                "permission:create|WRITE", "permission:delete|WRITE", "permission:export|READ",
                "permission:import|WRITE", "permission:query|READ", "permission:update|WRITE",
                "policy:bind|WRITE", "policy:query|READ", "policy:unbind|WRITE",
                "role:assign-permission|WRITE", "role:assign-user|WRITE", "role:create|WRITE",
                "role:data-scope|WRITE", "role:delete|WRITE", "role:query|READ", "role:update|WRITE",
                "settings:query|READ", "settings:update|WRITE", "stats:query|READ",
                "system:create|WRITE", "system:delete|WRITE", "system:query|READ",
                "system:update|WRITE", "system:visibility|WRITE",
                "user:assign-role|WRITE", "user:create|WRITE", "user:delete|WRITE",
                "user:disable|WRITE", "user:export|READ", "user:import|WRITE", "user:merge|WRITE",
                "user:permission-boundary|WRITE", "user:query|READ", "user:reset-password|WRITE",
                "user:security|WRITE", "user:update|WRITE");
        extendActions("auth", "login-policy:query|READ", "login-policy:update|WRITE",
                "mfa-policy:query|READ", "mfa-policy:update|WRITE",
                "session-policy:query|READ", "session-policy:update|WRITE",
                "third-config:query|READ", "third-config:update|WRITE",
                "third-sync:execute|WRITE", "third-sync:export|READ", "third-sync:query|READ");
        extendActions("policy", "binding:export|READ", "binding:query|READ", "binding:bind|WRITE",
                "binding:unbind|WRITE", "boundary:query|READ", "boundary:update|WRITE",
                "catalog:query|READ", "manage:grant|WRITE", "manage:validate|READ",
                "manage:version|READ", "manage:transfer|WRITE",
                "template:query|READ", "template:save|WRITE",
                "test-case:query|READ", "test-case:save|WRITE", "test-case:delete|WRITE",
                "test-case:run|WRITE");
        extendActions("audit", "api-log:query|READ", "api-log:export|READ",
                "policy-log:query|READ", "policy-log:export|READ",
                "online:query|READ", "online:export|READ", "online:kick|WRITE",
                "maintenance:cleanup|WRITE");
        extendActions("open", "app:query|READ", "app:review|WRITE", "app:offline|WRITE",
                "app:delete|WRITE", "scope:query|READ", "scope:create|WRITE",
                "scope:update|WRITE", "scope:delete|WRITE", "consent:query|READ",
                "consent:export|READ", "consent:revoke|WRITE", "stats:query|READ",
                "webhook:query|READ", "webhook:create|WRITE", "webhook:update|WRITE",
                "webhook:delete|WRITE", "webhook:delivery:query|READ",
                "webhook:delivery:retry|WRITE");
        extendActions("workorder", "order:query|READ", "order:assign|WRITE", "order:close|WRITE",
                "order:export|READ", "stats:query|READ", "comment:manage|WRITE",
                "attachment:manage|WRITE", "notification:manage|WRITE");
        extendActions("tenant", "manage:query|READ", "manage:create|WRITE", "manage:update|WRITE",
                "manage:delete|WRITE", "manage:export|READ", "plan:query|READ",
                "plan:create|WRITE", "plan:update|WRITE", "plan:delete|WRITE",
                "plan:assign|WRITE", "plan:bind|WRITE");
        extendActions("infra", "config:query|READ", "config:create|WRITE", "config:update|WRITE",
                "config:delete|WRITE", "config:rollback|WRITE", "config:transfer|WRITE",
                "dict:query|READ", "dict:create|WRITE", "dict:update|WRITE", "dict:delete|WRITE",
                "dict:transfer|WRITE", "notice:query|READ", "notice:create|WRITE",
                "notice:update|WRITE", "notice:delete|WRITE", "notify:query|READ",
                "notify:create|WRITE", "notify:update|WRITE", "notify:delete|WRITE",
                "notify:send|WRITE", "notify:retry|WRITE", "file:query|READ",
                "file:upload|WRITE", "file:delete|WRITE", "file:manage|WRITE",
                "redis:query|READ", "redis:delete|WRITE", "sql:query|READ",
                "log:query|READ", "log:level|WRITE", "cache:query|READ", "cache:clear|WRITE",
                "apikey:query|READ", "apikey:issue|WRITE", "apikey:manage|WRITE",
                "system:query|READ", "system:alert|WRITE", "search:query|READ",
                "search:index|WRITE", "search:remove|WRITE", "recycle:query|READ",
                "recycle:restore|WRITE", "recycle:purge|WRITE");
        extendActions("job", "task:query|READ", "task:create|WRITE", "task:update|WRITE",
                "task:delete|WRITE", "task:trigger|WRITE", "task:preview|READ",
                "log:query|READ", "log:delete|WRITE", "log:clean|WRITE", "node:query|READ");

        PRODUCTS = List.of(
                new PolicyProductView("access-control", "身份与访问控制", "账号、角色、用户组与策略授权",
                        List.of(feature("users", "用户管理", "用户账号和身份资料",
                                        byPrefix("upms", "user:")),
                                feature("roles", "角色管理", "角色与成员关系",
                                        byPrefix("upms", "role:")),
                                feature("groups", "用户组管理", "用户组及组成员",
                                        byPrefix("upms", "group:")),
                                feature("policies", "策略治理", "策略文档、绑定与仿真",
                                        ACTIONS.get("policy")))),
                new PolicyProductView("platform-governance", "平台治理", "审计、开放平台与平台运维",
                        List.of(feature("authentication", "认证安全", "登录、令牌与多因素认证",
                                        ACTIONS.get("auth")),
                                feature("audit", "审计合规", "操作审计与告警",
                                        ACTIONS.get("audit")),
                                feature("open", "开放平台", "应用、范围与回调",
                                        ACTIONS.get("open")),
                                feature("workorder", "工单协同", "工单流转与处理",
                                        ACTIONS.get("workorder")),
                                feature("tenant", "租户运营", "租户和套餐生命周期",
                                        ACTIONS.get("tenant")),
                                feature("infra", "基础设施", "配置、文件与通知",
                                        ACTIONS.get("infra")),
                                feature("job", "任务调度", "定时任务与执行记录",
                                        ACTIONS.get("job")),
                                feature("monitor", "实时监控", "直播源与健康探测",
                                        ACTIONS.get("live")))));

        RESOURCES = List.of(
                serviceNode("upms", "用户权限中心", List.of(
                        resourceNode("user", "用户", "modality:upms:user/*"),
                        resourceNode("role", "角色", "modality:upms:role/*"),
                        resourceNode("group", "用户组", "modality:upms:group/*"),
                        resourceNode("dept", "部门", "modality:upms:dept/*"),
                        resourceNode("menu", "菜单", "modality:upms:menu/*"),
                        resourceNode("permission", "权限", "modality:upms:permission/*"),
                        resourceNode("access", "系统访问", "modality:upms:access/*"),
                        resourceNode("settings", "用户设置", "modality:upms:settings/*"),
                        resourceNode("system", "系统注册", "modality:upms:system/*"))),
                serviceNode("policy", "策略中心", List.of(
                        resourceNode("policy", "策略", "modality:policy:policy/*"),
                        resourceNode("binding", "策略绑定", "modality:policy:binding/*"),
                        resourceNode("boundary", "策略边界", "modality:policy:boundary/*"),
                        resourceNode("template", "策略模板", "modality:policy:template/*"),
                        resourceNode("test-case", "策略测试用例", "modality:policy:test-case/*"))),
                serviceNode("auth", "认证中心", List.of(
                        resourceNode("account", "账号", "modality:auth:account/*"),
                        resourceNode("token", "令牌", "modality:auth:token/*"),
                        resourceNode("mfa", "多因素认证", "modality:auth:mfa/*"),
                        resourceNode("third", "第三方认证", "modality:auth:third/*"),
                        resourceNode("session", "会话策略", "modality:auth:session/*"))),
                serviceNode("audit", "审计中心", List.of(
                        resourceNode("log", "审计日志", "modality:audit:log/*"),
                        resourceNode("api-log", "接口日志", "modality:audit:api-log/*"),
                        resourceNode("policy-log", "策略日志", "modality:audit:policy-log/*"),
                        resourceNode("online", "在线会话", "modality:audit:online/*"))),
                serviceNode("open", "开放平台", List.of(
                        resourceNode("app", "应用", "modality:open:app/*"),
                        resourceNode("scope", "授权范围", "modality:open:scope/*"),
                        resourceNode("consent", "用户授权", "modality:open:consent/*"),
                        resourceNode("webhook", "Webhook", "modality:open:webhook/*"))),
                serviceNode("workorder", "工单中心", List.of(
                        resourceNode("order", "工单", "modality:workorder:order/*"),
                        resourceNode("comment", "工单评论", "modality:workorder:comment/*"),
                        resourceNode("attachment", "工单附件", "modality:workorder:attachment/*"),
                        resourceNode("notification", "工单通知", "modality:workorder:notification/*"))),
                serviceNode("tenant", "租户中心", List.of(
                        resourceNode("tenant", "租户", "modality:tenant:tenant/*"),
                        resourceNode("plan", "租户套餐", "modality:tenant:plan/*"))),
                serviceNode("infra", "基础设施", List.of(
                        resourceNode("file", "文件", "modality:infra:file/*"),
                        resourceNode("config", "配置", "modality:infra:config/*"),
                        resourceNode("dict", "字典", "modality:infra:dict/*"),
                        resourceNode("notice", "公告", "modality:infra:notice/*"),
                        resourceNode("notify", "通知", "modality:infra:notify/*"),
                        resourceNode("apikey", "API Key", "modality:infra:apikey/*"),
                        resourceNode("search", "搜索", "modality:infra:search/*"),
                        resourceNode("recycle", "回收站", "modality:infra:recycle/*"))),
                serviceNode("job", "定时任务", List.of(
                        resourceNode("task", "任务", "modality:job:task/*"),
                        resourceNode("log", "任务日志", "modality:job:log/*"),
                        resourceNode("node", "任务节点", "modality:job:node/*"))),
                serviceNode("live", "实时监控", List.of(
                        resourceNode("stream", "直播源", "modality:live:stream/*"))));

        ACTION_CODES = ACTIONS.values().stream().flatMap(List::stream)
                .map(PolicyActionView::code).collect(Collectors.toUnmodifiableSet());
    }

    public List<PolicyProductView> products() {
        return PRODUCTS;
    }

    public List<PolicyServiceView> services() {
        return List.copyOf(SERVICES.values());
    }

    public List<PolicyActionView> actions(String serviceCode) {
        if (serviceCode == null || serviceCode.isBlank() || !ACTIONS.containsKey(serviceCode.trim())) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "策略服务 " + serviceCode + " 不存在");
        }
        return ACTIONS.get(serviceCode.trim());
    }

    public boolean supportsService(String serviceCode) {
        return serviceCode != null && ACTIONS.containsKey(serviceCode.trim());
    }

    public boolean supportsAction(String actionCode) {
        if (actionCode == null) {
            return false;
        }
        return ACTION_CODES.contains(actionCode);
    }

    public List<PolicyResourceNode> resources() {
        return RESOURCES;
    }

    private static void service(String code, String name, String description) {
        SERVICES.put(code, new PolicyServiceView(code, name, description));
    }

    private static void actions(String serviceCode, List<PolicyActionView> values) {
        ACTIONS.put(serviceCode, List.copyOf(values));
    }

    private static void extendActions(String serviceCode, String... specs) {
        Map<String, PolicyActionView> indexed = new LinkedHashMap<>();
        ACTIONS.getOrDefault(serviceCode, List.of()).forEach(action -> indexed.put(action.code(), action));
        for (String spec : specs) {
            String[] parts = spec.split("\\|", 2);
            String suffix = parts[0];
            String level = parts.length > 1 ? parts[1] : "WRITE";
            String code = "modality:" + serviceCode + ":" + suffix;
            indexed.putIfAbsent(code, new PolicyActionView(serviceCode, code,
                    friendlyName(suffix), level, "执行 " + friendlyName(suffix)));
        }
        ACTIONS.put(serviceCode, List.copyOf(indexed.values()));
    }

    private static String friendlyName(String code) {
        StringBuilder name = new StringBuilder();
        for (String segment : code.split(":")) {
            for (String token : segment.split("-")) {
                if (!name.isEmpty()) {
                    name.append(' ');
                }
                name.append(TOKEN_LABELS.getOrDefault(token, token));
            }
        }
        return name.toString();
    }

    private static PolicyActionView action(String serviceCode, String actionCode, String name,
                                           String level, String description) {
        return new PolicyActionView(serviceCode, "modality:" + serviceCode + ":" + actionCode,
                name, level, description);
    }

    private static PolicyFeatureView feature(String code, String name, String description,
                                             List<PolicyActionView> actions) {
        return new PolicyFeatureView(code, name, description, List.copyOf(actions));
    }

    private static List<PolicyActionView> byPrefix(String serviceCode, String prefix) {
        return ACTIONS.get(serviceCode).stream().filter(action -> action.code().contains(prefix)).toList();
    }

    private static PolicyResourceNode serviceNode(String code, String name, List<PolicyResourceNode> children) {
        return new PolicyResourceNode(code, name, "SERVICE", "modality:" + code + "/*", children);
    }

    private static PolicyResourceNode resourceNode(String code, String name, String resource) {
        return new PolicyResourceNode(code, name, "RESOURCE", resource, List.of());
    }
}
