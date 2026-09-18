package cn.slainc.openapi.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.openapi.model.OpenScope;
import cn.slainc.openapi.model.ScopeRiskLevel;
import cn.slainc.openapi.spi.ScopeService;
import cn.slainc.openapi.spi.ScopeStore;

import java.util.List;
import java.util.Optional;

public class DefaultScopeService implements ScopeService {

    private static final String NAME_PATTERN = "[a-z0-9_]{2,64}";

    private final ScopeStore store;

    public DefaultScopeService(ScopeStore store) {
        this.store = store;
    }

    @Override
    public OpenScope register(OpenScope scope) {
        validate(scope);
        if (store.find(scope.getName()).isPresent()) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "Scope " + scope.getName() + " 已存在");
        }
        if (scope.getRiskLevel() != ScopeRiskLevel.LOW) {
            scope.setRequiresAudit(true);
        }
        return store.save(scope);
    }

    @Override
    public OpenScope update(OpenScope scope) {
        validate(scope);
        OpenScope existing = require(scope.getName());
        existing.setTitle(scope.getTitle());
        existing.setDescription(scope.getDescription());
        existing.setGroup(scope.getGroup());
        existing.setRiskLevel(scope.getRiskLevel());
        if (existing.getRiskLevel() != ScopeRiskLevel.LOW) {
            existing.setRequiresAudit(true);
        } else {
            existing.setRequiresAudit(scope.isRequiresAudit());
        }
        return store.save(existing);
    }

    @Override
    public void remove(String name) {
        store.remove(name);
    }

    @Override
    public OpenScope get(String name) {
        return require(name);
    }

    @Override
    public List<OpenScope> list() {
        return store.list();
    }

    @Override
    public List<OpenScope> listByGroup(String group) {
        return store.list().stream().filter(scope -> group.equals(scope.getGroup())).toList();
    }

    @Override
    public boolean requiresAudit(String scopeName) {
        Optional<OpenScope> found = store.find(scopeName);
        return found.map(OpenScope::risky).orElse(false);
    }

    private OpenScope require(String name) {
        return store.find(name)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "Scope " + name + " 不存在"));
    }

    private void validate(OpenScope scope) {
        if (scope.getName() == null || !scope.getName().matches(NAME_PATTERN)) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "Scope 名称只允许 2-64 位小写字母、数字或下划线: " + scope.getName());
        }
    }
}
