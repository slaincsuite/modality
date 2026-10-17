package cn.slainc.modules.open.service.app;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.dto.ScopeGroupRequest;
import cn.slainc.modules.open.dto.ScopeGroupView;
import cn.slainc.modules.open.entity.ScopeGroupRecord;
import cn.slainc.modules.open.store.JdbcScopeGroupStore;
import cn.slainc.openapi.domain.OpenScope;
import cn.slainc.openapi.spi.ScopeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScopeGroupService {

    private static final int NAME_MAX_LENGTH = 64;

    private static final int DESCRIPTION_MAX_LENGTH = 512;

    private final JdbcScopeGroupStore groupStore;

    private final ScopeService scopeService;

    public ScopeGroupService(JdbcScopeGroupStore groupStore, ScopeService scopeService) {
        this.groupStore = groupStore;
        this.scopeService = scopeService;
    }

    public List<ScopeGroupView> list() {
        Map<String, ScopeGroupView> groups = new LinkedHashMap<>();
        groups.put(OpenKeys.DEFAULT_SCOPE_GROUP, new ScopeGroupView(OpenKeys.DEFAULT_SCOPE_GROUP, null, 0));
        JdbcScopeGroupStore store = groupStore;
        if (store != null) {
            for (ScopeGroupRecord group : store.list()) {
                groups.put(group.getName(), toView(group));
            }
        }
        for (OpenScope scope : scopeService.list()) {
            String name = trimToNull(scope.getGroup());
            if (name != null) {
                groups.putIfAbsent(name, new ScopeGroupView(name, null, Integer.MAX_VALUE));
            }
        }
        return groups.values().stream()
                .sorted(Comparator.comparingInt(ScopeGroupView::sort).thenComparing(ScopeGroupView::name))
                .toList();
    }

    public ScopeGroupView create(ScopeGroupRequest request) {
        JdbcScopeGroupStore store = requireStore();
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "分组不能为空");
        }
        String name = requireName(request.name());
        validateDescription(request.description());
        if (store.find(name).isPresent()) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "Scope 分组「" + name + "」已存在");
        }
        long now = System.currentTimeMillis();
        ScopeGroupRecord group = new ScopeGroupRecord(name, request.description(),
                request.sort() == null ? 0 : request.sort(), now, now);
        insert(store, group);
        return toView(group);
    }

    @Transactional
    public ScopeGroupView update(String currentName, ScopeGroupRequest request) {
        JdbcScopeGroupStore store = requireStore();
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "分组不能为空");
        }
        String sourceName = requireName(currentName);
        String targetName = trimToNull(request.name()) == null ? sourceName : requireName(request.name());
        validateDescription(request.description());
        ScopeGroupRecord existing = store.find(sourceName).orElseGet(() -> defaultGroup(sourceName));
        if (!sourceName.equals(targetName)) {
            if (OpenKeys.DEFAULT_SCOPE_GROUP.equals(sourceName)) {
                throw new BizException(ResultCode.DATA_STATE_INVALID, "默认分组不允许重命名");
            }
            if (store.find(targetName).isPresent()) {
                throw new BizException(ResultCode.DATA_DUPLICATE, "Scope 分组「" + targetName + "」已存在");
            }
        }
        int sort = request.sort() == null ? existing.getSort() : request.sort();
        long now = System.currentTimeMillis();
        if (!sourceName.equals(targetName)) {
            insert(store, new ScopeGroupRecord(targetName, request.description(), sort, now, now));
            renameScopes(sourceName, targetName);
            store.delete(sourceName);
            return new ScopeGroupView(targetName, request.description(), sort);
        }
        if (store.find(sourceName).isPresent()) {
            store.update(sourceName, request.description(), sort, now);
        } else {
            insert(store, new ScopeGroupRecord(sourceName, request.description(), sort, now, now));
        }
        return new ScopeGroupView(sourceName, request.description(), sort);
    }

    @Transactional
    public void delete(String name) {
        JdbcScopeGroupStore store = requireStore();
        String groupName = requireName(name);
        if (OpenKeys.DEFAULT_SCOPE_GROUP.equals(groupName)) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "默认分组不允许删除");
        }
        if (store.find(groupName).isEmpty()) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "Scope 分组「" + groupName + "」不存在");
        }
        long scopeCount = scopeService.list().stream()
                .filter(scope -> groupName.equals(scope.getGroup()))
                .count();
        if (scopeCount > 0) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "Scope 分组「" + groupName + "」仍包含 " + scopeCount + " 个 Scope，请先调整归属");
        }
        store.delete(groupName);
    }

    private void renameScopes(String sourceName, String targetName) {
        for (OpenScope scope : scopeService.list()) {
            if (sourceName.equals(scope.getGroup())) {
                scope.setGroup(targetName);
                scopeService.update(scope);
            }
        }
    }

    private static void insert(JdbcScopeGroupStore store, ScopeGroupRecord group) {
        try {
            store.insert(group);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "Scope 分组「" + group.getName() + "」已存在");
        }
    }

    private static ScopeGroupRecord defaultGroup(String name) {
        if (!OpenKeys.DEFAULT_SCOPE_GROUP.equals(name)) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "Scope 分组「" + name + "」不存在");
        }
        return new ScopeGroupRecord(name, null, 0, 0L, 0L);
    }

    private JdbcScopeGroupStore requireStore() {
        if (groupStore == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "Scope 分组存储不可用（缺少 JdbcTemplate）");
        }
        return groupStore;
    }

    private static String requireName(String name) {
        String value = trimToNull(name);
        if (value == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "分组名称不能为空");
        }
        if (value.length() > NAME_MAX_LENGTH) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "分组名称不能超过 64 个字符");
        }
        return value;
    }

    private static void validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "分组描述不能超过 512 个字符");
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ScopeGroupView toView(ScopeGroupRecord group) {
        return new ScopeGroupView(group.getName(), group.getDescription(), group.getSort());
    }
}
