package cn.slainc.modules.policy.service;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.dto.BindingBatchRequest;
import cn.slainc.modules.policy.dto.PolicyView;
import cn.slainc.policy.support.PolicyBindingService;
import cn.slainc.policy.domain.PolicyBinding;
import cn.slainc.policy.domain.PolicySubjectType;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 绑定写操作委托 policy 库的 PolicyBindingService（派生策略 + 绑定镜像），本模块补批量绑定与主体视角查询
public class PolicyBindingPortalService {

    private final ObjectProvider<PolicyBindingService> bindingService;

    private final PolicyManageService manageService;

    public PolicyBindingPortalService(ObjectProvider<PolicyBindingService> bindingService,
                                      PolicyManageService manageService) {
        this.bindingService = bindingService;
        this.manageService = manageService;
    }

    public PolicyBinding bindToUser(String policyId, Long userId, String loginType, String operator) {
        if (loginType == null || loginType.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "绑定用户策略必须指定账号体系");
        }
        return delegate().bindToUser(policyId, userId, LoginType.of(loginType.trim()), operator);
    }

    public PolicyBinding bindToRole(String policyId, String roleCode, String operator) {
        return delegate().bindToRole(policyId, roleCode, operator);
    }

    public PolicyBinding bindToClient(String policyId, Long clientId, String operator) {
        return delegate().bindToClient(policyId, clientId, operator);
    }

    public Map<String, Object> batchBind(BindingBatchRequest request, String operator) {
        if (request.subjectIds() == null || request.subjectIds().isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, "批量绑定必须提供主体标识列表");
        }
        if (normalizeSubjectType(request.subjectType()) == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "批量绑定必须指定主体类型");
        }
        List<PolicyBinding> succeeded = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String subjectId : request.subjectIds()) {
            try {
                PolicyBinding record = switch (normalizeSubjectType(request.subjectType())) {
                    case "user" -> bindToUser(request.policyId(), Long.valueOf(subjectId.trim()),
                            request.loginType(), operator);
                    case "role" -> bindToRole(request.policyId(), subjectId.trim(), operator);
                    case "client" -> bindToClient(request.policyId(), Long.valueOf(subjectId.trim()), operator);
                    default -> throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            "不支持的绑定主体类型: " + request.subjectType());
                };
                succeeded.add(record);
            } catch (NumberFormatException e) {
                failures.add(subjectId + ": 主体标识必须是数字");
            } catch (BizException e) {
                failures.add(subjectId + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("succeeded", succeeded);
        result.put("failed", failures);
        return result;
    }

    public void unbind(Long bindingId) {
        delegate().unbind(bindingId);
    }

    public List<PolicyBinding> query(String subjectType, String subjectId, String policyId) {
        return delegate().query(querySubjectType(subjectType), subjectId, policyId);
    }

    public Map<String, Object> subjectView(String subjectType, String subjectId) {
        String type = normalizeSubjectType(subjectType);
        if (subjectId == null || subjectId.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "主体查询必须提供主体标识");
        }
        List<PolicyView> direct = directPolicies(type, subjectId.trim());
        List<PolicyBinding> bound = delegate().query(querySubjectType(type), subjectId.trim(), null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("directPolicies", direct);
        result.put("boundPolicies", bound);
        return result;
    }

    private List<PolicyView> directPolicies(String type, String subjectId) {
        return manageService.page(null, subjectTypeOf(type).code(), subjectId, null, null, null,
                1, Integer.MAX_VALUE).records().stream()
                .filter(view -> !"client".equals(type) || "APP".equalsIgnoreCase(view.loginType()))
                .toList();
    }

    private PolicySubjectType subjectTypeOf(String type) {
        return "role".equals(type) ? PolicySubjectType.ROLE : PolicySubjectType.USER;
    }

    private String querySubjectType(String type) {
        String normalized = normalizeSubjectType(type);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase();
    }

    private String normalizeSubjectType(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            return null;
        }
        String lowered = subjectType.trim().toLowerCase();
        if (!List.of("user", "role", "client").contains(lowered)) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "绑定主体类型只允许 user、role、client，当前为 " + subjectType);
        }
        return lowered;
    }

    private PolicyBindingService delegate() {
        PolicyBindingService service = bindingService.getIfAvailable();
        if (service == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "策略绑定能力未装配");
        }
        return service;
    }
}
