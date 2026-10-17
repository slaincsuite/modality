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

public class PolicyCatalogService {

    private static final Map<String, PolicyServiceView> SERVICES = new LinkedHashMap<>();

    private static final Map<String, List<PolicyActionView>> ACTIONS = new LinkedHashMap<>();

    private static final List<PolicyProductView> PRODUCTS;

    private static final List<PolicyResourceNode> RESOURCES;

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
        service("live-monitor", "实时监控", "运行指标、链路与告警监控");

        actions("upms", List.of(
                action("upms", "user:query", "查询用户", "READ", "分页或详情读取用户信息"),
                action("upms", "user:create", "创建用户", "WRITE", "创建租户用户账号"),
                action("upms", "user:update", "修改用户", "WRITE", "修改用户资料与状态"),
                action("upms", "user:delete", "删除用户", "WRITE", "删除或停用用户账号"),
                action("upms", "role:query", "查询角色", "READ", "读取角色与角色成员"),
                action("upms", "role:assign", "分配角色", "WRITE", "调整用户角色关系"),
                action("upms", "group:query", "查询用户组", "READ", "读取用户组与成员")));
        actions("auth", List.of(
                action("auth", "login", "账号登录", "WRITE", "建立登录会话"),
                action("auth", "logout", "退出登录", "WRITE", "注销当前会话"),
                action("auth", "token:issue", "签发令牌", "WRITE", "签发访问令牌"),
                action("auth", "mfa:verify", "多因素认证", "WRITE", "校验第二认证因素")));
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
                action("audit", "log:export", "导出审计日志", "READ", "导出审计明细"),
                action("audit", "alert:update", "维护告警规则", "WRITE", "调整审计告警策略")));
        actions("open", List.of(
                action("open", "app:query", "查询应用", "READ", "读取开放平台应用"),
                action("open", "app:create", "创建应用", "WRITE", "创建开放平台应用"),
                action("open", "scope:assign", "分配范围", "WRITE", "调整应用授权范围"),
                action("open", "webhook:manage", "维护 Webhook", "WRITE", "配置开放平台回调")));
        actions("workorder", List.of(
                action("workorder", "order:query", "查询工单", "READ", "读取工单详情与列表"),
                action("workorder", "order:create", "创建工单", "WRITE", "发起工单"),
                action("workorder", "order:update", "处理工单", "WRITE", "更新工单状态与处理人"),
                action("workorder", "order:close", "关闭工单", "WRITE", "完成或关闭工单")));
        actions("tenant", List.of(
                action("tenant", "tenant:query", "查询租户", "READ", "读取租户与套餐"),
                action("tenant", "tenant:create", "创建租户", "WRITE", "开通新租户"),
                action("tenant", "tenant:update", "修改租户", "WRITE", "修改租户配置"),
                action("tenant", "tenant:disable", "停用租户", "WRITE", "停用租户访问")));
        actions("infra", List.of(
                action("infra", "config:query", "查询配置", "READ", "读取动态配置"),
                action("infra", "config:update", "修改配置", "WRITE", "更新动态配置"),
                action("infra", "file:upload", "上传文件", "WRITE", "写入文件存储"),
                action("infra", "file:download", "下载文件", "READ", "读取文件存储")));
        actions("job", List.of(
                action("job", "task:query", "查询任务", "READ", "读取任务与执行记录"),
                action("job", "task:create", "创建任务", "WRITE", "创建定时任务"),
                action("job", "task:run", "执行任务", "WRITE", "立即触发任务"),
                action("job", "task:delete", "删除任务", "WRITE", "删除定时任务")));
        actions("live-monitor", List.of(
                action("live-monitor", "metric:query", "查询指标", "READ", "读取运行指标"),
                action("live-monitor", "trace:query", "查询链路", "READ", "读取调用链路"),
                action("live-monitor", "alert:query", "查询告警", "READ", "读取监控告警")));

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
                                feature("monitor", "实时监控", "指标、链路与告警",
                                        ACTIONS.get("live-monitor")))));

        RESOURCES = List.of(
                serviceNode("upms", "用户权限中心", List.of(
                        resourceNode("user", "用户", "modality:upms:user/*"),
                        resourceNode("role", "角色", "modality:upms:role/*"),
                        resourceNode("group", "用户组", "modality:upms:group/*"))),
                serviceNode("policy", "策略中心", List.of(
                        resourceNode("policy", "策略", "modality:policy:policy/*"),
                        resourceNode("binding", "策略绑定", "modality:policy:binding/*"))),
                serviceNode("auth", "认证中心", List.of(
                        resourceNode("account", "账号", "modality:auth:account/*"),
                        resourceNode("token", "令牌", "modality:auth:token/*"))),
                serviceNode("audit", "审计中心", List.of(
                        resourceNode("log", "审计日志", "modality:audit:log/*"))),
                serviceNode("open", "开放平台", List.of(
                        resourceNode("app", "应用", "modality:open:app/*"))),
                serviceNode("workorder", "工单中心", List.of(
                        resourceNode("order", "工单", "modality:workorder:order/*"))),
                serviceNode("tenant", "租户中心", List.of(
                        resourceNode("tenant", "租户", "modality:tenant:tenant/*"))),
                serviceNode("infra", "基础设施", List.of(
                        resourceNode("file", "文件", "modality:infra:file/*"),
                        resourceNode("config", "配置", "modality:infra:config/*"))),
                serviceNode("job", "定时任务", List.of(
                        resourceNode("task", "任务", "modality:job:task/*"))),
                serviceNode("live-monitor", "实时监控", List.of(
                        resourceNode("metric", "指标", "modality:live-monitor:metric/*"))));
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

    public List<PolicyResourceNode> resources() {
        return RESOURCES;
    }

    private static void service(String code, String name, String description) {
        SERVICES.put(code, new PolicyServiceView(code, name, description));
    }

    private static void actions(String serviceCode, List<PolicyActionView> values) {
        ACTIONS.put(serviceCode, List.copyOf(values));
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
