package cn.slainc.modules.policy.service.binding;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.dto.BindingBatchRequest;
import cn.slainc.modules.policy.dto.PolicyBatchGrantRequest;
import cn.slainc.modules.policy.dto.PolicyBindingView;
import cn.slainc.modules.policy.dto.PolicyView;
import cn.slainc.modules.policy.excel.PolicyBindingExportRow;
import cn.slainc.modules.policy.service.manage.PolicyManageService;
import cn.slainc.modules.policy.store.PolicyBoundaryStore;
import cn.slainc.policy.domain.PolicyBinding;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.support.PolicyBindingService;
import org.springframework.beans.factory.ObjectProvider;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 绑定写操作委托 policy 库的 PolicyBindingService（派生策略 + 绑定镜像），本模块补批量绑定与主体视角查询
public class PolicyBindingPortalService {

    private final ObjectProvider<PolicyBindingService> bindingService;

    private final PolicyManageService manageService;

    private final PolicyBoundaryStore boundaryStore;

    public PolicyBindingPortalService(ObjectProvider<PolicyBindingService> bindingService,
                                      PolicyManageService manageService, PolicyBoundaryStore boundaryStore) {
        this.bindingService = bindingService;
        this.manageService = manageService;
        this.boundaryStore = boundaryStore;
    }

    public PolicyBinding bindToUser(String policyId, Long userId, String loginType, String operator) {
        if (loginType == null || loginType.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "绑定用户策略必须指定账号体系");
        }
        return complete(delegate().bindToUser(policyId, userId, LoginType.of(loginType.trim()), operator));
    }

    public PolicyBinding bindToRole(String policyId, String roleCode, String operator) {
        return complete(delegate().bindToRole(policyId, roleCode, operator));
    }

    public PolicyBinding bindToClient(String policyId, Long clientId, String operator) {
        return complete(delegate().bindToClient(policyId, clientId, operator));
    }

    public PolicyBinding bindToGroup(String policyId, String groupId, String operator) {
        return complete(delegate().bindToGroup(policyId, groupId, operator));
    }

    public Map<String, Object> batchBind(BindingBatchRequest request, String operator) {
        List<String> subjectIds = normalized(request.subjectIds(), "批量绑定必须提供主体标识列表");
        String subjectType = normalizeSubjectType(request.subjectType());
        if (subjectType == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "批量绑定必须指定主体类型");
        }
        List<PolicyBinding> succeeded = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String subjectId : subjectIds) {
            try {
                PolicyBinding record = switch (subjectType) {
                    case "user" -> bindToUser(request.policyId(), Long.valueOf(subjectId),
                            request.loginType(), operator);
                    case "role" -> bindToRole(request.policyId(), subjectId, operator);
                    case "client" -> bindToClient(request.policyId(), Long.valueOf(subjectId), operator);
                    case "group" -> bindToGroup(request.policyId(), subjectId, operator);
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

    public Map<String, Object> batchGrant(PolicyBatchGrantRequest request, String operator) {
        List<String> policyIds = normalized(request.policyIds(), "批量授权必须提供策略标识列表");
        List<String> subjectIds = normalized(request.subjectIds(), "批量授权必须提供主体标识列表");
        String subjectType = normalizeSubjectType(request.subjectType());
        if (subjectType == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "批量授权必须指定主体类型");
        }
        List<PolicyBindingView> succeeded = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String policyId : policyIds) {
            for (String subjectId : subjectIds) {
                try {
                    PolicyBinding binding = switch (subjectType) {
                        case "user" -> bindToUser(policyId, Long.valueOf(subjectId),
                                request.loginType(), operator);
                        case "role" -> bindToRole(policyId, subjectId, operator);
                        case "client" -> bindToClient(policyId, Long.valueOf(subjectId), operator);
                        case "group" -> bindToGroup(policyId, subjectId, operator);
                        default -> throw new BizException(ResultCode.PARAM_BIND_ERROR,
                                "不支持的绑定主体类型: " + request.subjectType());
                    };
                    succeeded.add(PolicyBindingView.of(binding));
                } catch (NumberFormatException exception) {
                    failures.add(policyId + "/" + subjectId + ": 主体标识必须是数字");
                } catch (BizException exception) {
                    failures.add(policyId + "/" + subjectId + ": " + exception.getMessage());
                }
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
        List<PolicyBinding> direct = delegate().query(querySubjectType(subjectType), subjectId, policyId);
        if (policyId == null || policyId.isBlank()) {
            return direct;
        }
        List<PolicyBinding> merged = new ArrayList<>(direct);
        Set<Long> ids = new LinkedHashSet<>();
        direct.forEach(binding -> ids.add(binding.getId()));
        delegate().queryBySource(policyId).stream()
                .filter(binding -> subjectType == null || subjectType.isBlank()
                        || querySubjectType(subjectType).equals(binding.getSubjectType()))
                .filter(binding -> subjectId == null || subjectId.isBlank()
                        || subjectId.trim().equals(binding.getSubjectId()))
                .filter(binding -> ids.add(binding.getId()))
                .forEach(merged::add);
        return merged;
    }

    public List<PolicyBindingView> sourceBindings(String sourcePolicyId) {
        manageService.view(sourcePolicyId);
        return delegate().queryBySource(sourcePolicyId).stream().map(PolicyBindingView::of).toList();
    }

    public void exportBindings(String sourcePolicyId, OutputStream outputStream) {
        List<PolicyBindingExportRow> rows = sourceBindings(sourcePolicyId).stream()
                .map(row -> new PolicyBindingExportRow(sourcePolicyId, row))
                .toList();
        ExcelUtils.write(outputStream, PolicyBindingExportRow.class, rows, "策略关联");
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
        if ("role".equals(type)) {
            return PolicySubjectType.ROLE;
        }
        if ("group".equals(type)) {
            return PolicySubjectType.GROUP;
        }
        return PolicySubjectType.USER;
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
        if (!List.of("user", "role", "client", "group").contains(lowered)) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "绑定主体类型只允许 user、role、client、group，当前为 " + subjectType);
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

    private PolicyBinding complete(PolicyBinding binding) {
        boundaryStore.find(binding.getSourcePolicyId()).ifPresent(boundary ->
                boundaryStore.save(binding.getPolicyId(), boundary.document(), binding.getOperator()));
        return binding;
    }

    private List<String> normalized(List<String> values, String message) {
        if (values == null || values.isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, message);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        if (result.isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, message);
        }
        return List.copyOf(result);
    }
}
