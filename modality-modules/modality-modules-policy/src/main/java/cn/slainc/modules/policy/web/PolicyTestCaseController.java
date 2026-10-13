package cn.slainc.modules.policy.web;

import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.dto.PolicyTestCaseRequest;
import cn.slainc.modules.policy.service.PolicyTestCaseService;
import cn.slainc.policy.simulate.PolicyTestReport;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "策略测试用例", description = "策略测试用例管理")
@RestController
@RequestMapping("/policy/test-cases")
public class PolicyTestCaseController {

    private final PolicyTestCaseService testCaseService;

    public PolicyTestCaseController(PolicyTestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @PostMapping
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_TEST_CASE_SAVE)
    @AuditLog(value = "保存策略测试用例", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Object create(@RequestBody PolicyTestCaseRequest request) {
        return testCaseService.create(request);
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_TEST_CASE_SAVE)
    @AuditLog(value = "修改策略测试用例", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public Object update(@PathVariable String id, @RequestBody PolicyTestCaseRequest request) {
        return testCaseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_TEST_CASE_DELETE)
    @AuditLog(value = "删除策略测试用例", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "policy")
    public void delete(@PathVariable String id) {
        testCaseService.delete(id);
    }

    @GetMapping("/page")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_TEST_CASE_QUERY)
    public Object page(@RequestParam(required = false) String policyId,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return testCaseService.page(policyId, current, size);
    }

    @GetMapping("/{id}")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_TEST_CASE_QUERY)
    public Object get(@PathVariable String id) {
        return testCaseService.get(id);
    }

    @PostMapping("/run")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_TEST_CASE_RUN)
    public Object run(@RequestParam(required = false) String policyId) {
        return report(testCaseService.run(policyId));
    }

    @PostMapping("/{id}/run")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_TEST_CASE_RUN)
    public Object runOne(@PathVariable String id) {
        return report(testCaseService.runOne(id));
    }

    private Map<String, Object> report(PolicyTestReport report) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", report.total());
        result.put("passed", report.passed());
        result.put("results", report.results());
        result.put("summary", report.summary());
        return result;
    }
}
