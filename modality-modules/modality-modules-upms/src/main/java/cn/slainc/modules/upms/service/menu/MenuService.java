package cn.slainc.modules.upms.service.menu;

import cn.slainc.modules.upms.service.common.SubtreeUtils;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.MenuRecord;
import cn.slainc.modules.upms.mapper.MenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MenuService {

    private final MenuMapper menuMapper;

    public MenuService(MenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    public MenuRecord create(String name, Long parentId, String type, String path, String component, String icon,
                             String permissionCode, Boolean visible, Integer sort) {
        if (name == null || name.isBlank()) {
            throw new BizException("菜单名称不能为空");
        }
        String menuType = type == null || type.isBlank() ? MenuRecord.TYPE_MENU : type;
        requireType(menuType);
        requireParentAlive(parentId);
        long now = System.currentTimeMillis();
        MenuRecord menu = new MenuRecord();
        menu.setName(name.trim());
        menu.setParentId(parentId == null ? 0L : parentId);
        menu.setType(menuType);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIcon(icon);
        menu.setPermissionCode(permissionCode);
        menu.setVisible(visible == null || visible);
        menu.setSort(sort == null ? 0 : sort);
        menu.setStatus(UpmsKeys.STATUS_ENABLED);
        menu.setCreateTime(now);
        menu.setUpdateTime(now);
        menuMapper.insert(menu);
        return menu;
    }

    public MenuRecord update(Long id, String name, Long parentId, String type, String path, String component,
                             String icon, String permissionCode, Boolean visible, Integer sort, String status) {
        MenuRecord menu = require(id);
        if (name != null && !name.isBlank()) {
            menu.setName(name.trim());
        }
        if (parentId != null) {
            SubtreeUtils.requireValidParent(collectSubtreeIds(id), id, parentId, "菜单");
            requireParentAlive(parentId);
            menu.setParentId(parentId);
        }
        if (type != null) {
            requireType(type);
            menu.setType(type);
        }
        if (path != null) {
            menu.setPath(path);
        }
        if (component != null) {
            menu.setComponent(component);
        }
        if (icon != null) {
            menu.setIcon(icon);
        }
        if (permissionCode != null) {
            menu.setPermissionCode(permissionCode);
        }
        if (visible != null) {
            menu.setVisible(visible);
        }
        if (sort != null) {
            menu.setSort(sort);
        }
        if (status != null) {
            if (!UpmsKeys.STATUS_ENABLED.equals(status) && !UpmsKeys.STATUS_DISABLED.equals(status)) {
                throw new BizException("菜单状态只允许 enabled、disabled");
            }
            menu.setStatus(status);
        }
        menu.setUpdateTime(System.currentTimeMillis());
        menuMapper.updateById(menu);
        return menu;
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        long children = menuMapper.selectCount(new LambdaQueryWrapper<MenuRecord>()
                .eq(MenuRecord::getParentId, id));
        if (children > 0) {
            throw new BizException("该菜单存在 " + children + " 个下级，先删除下级后再删除");
        }
        menuMapper.deleteById(id);
    }

    public List<MenuRecord> tree() {
        List<MenuRecord> all = menuMapper.selectList(new LambdaQueryWrapper<MenuRecord>()
                .orderByAsc(MenuRecord::getSort).orderByAsc(MenuRecord::getId));
        return childrenOf(all, 0L);
    }

    // 用户可见菜单 = 权限码命中的菜单 + 其祖先目录；目录必须依赖可见子项，无码的页面类菜单全员可见
    public List<MenuRecord> userMenuTree(Set<String> grantedPermissions) {
        List<MenuRecord> all = menuMapper.selectList(new LambdaQueryWrapper<MenuRecord>()
                .eq(MenuRecord::getStatus, UpmsKeys.STATUS_ENABLED)
                .orderByAsc(MenuRecord::getSort).orderByAsc(MenuRecord::getId));
        Set<Long> visibleIds = new LinkedHashSet<>();
        for (MenuRecord menu : all) {
            if (!Boolean.TRUE.equals(menu.getVisible())
                    || MenuRecord.TYPE_BUTTON.equals(menu.getType())
                    || MenuRecord.TYPE_DIRECTORY.equals(menu.getType())) {
                continue;
            }
            boolean permitted = menu.getPermissionCode() == null || menu.getPermissionCode().isBlank()
                    || grantedPermissions.contains(menu.getPermissionCode());
            if (permitted) {
                visibleIds.add(menu.getId());
                Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
                while (parentId != 0 && !visibleIds.contains(parentId)) {
                    visibleIds.add(parentId);
                    MenuRecord parent = byId(all, parentId);
                    parentId = parent == null || parent.getParentId() == null ? 0L : parent.getParentId();
                }
            }
        }
        List<MenuRecord> visible = all.stream()
                .filter(menu -> visibleIds.contains(menu.getId()))
                .toList();
        return childrenOf(visible, 0L);
    }

    public Set<Long> collectSubtreeIds(Long menuId) {
        return SubtreeUtils.collectSubtree(menuId, id -> menuMapper.selectList(
                        new LambdaQueryWrapper<MenuRecord>().eq(MenuRecord::getParentId, id))
                .stream().map(MenuRecord::getId).toList());
    }

    public MenuRecord require(Long id) {
        MenuRecord menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException("菜单不存在: " + id);
        }
        return menu;
    }

    private MenuRecord byId(List<MenuRecord> all, Long id) {
        for (MenuRecord menu : all) {
            if (id.equals(menu.getId())) {
                return menu;
            }
        }
        return null;
    }

    private List<MenuRecord> childrenOf(List<MenuRecord> all, Long parentId) {
        List<MenuRecord> children = new ArrayList<>();
        for (MenuRecord menu : all) {
            if (parentId.equals(menu.getParentId() == null ? 0L : menu.getParentId())) {
                children.add(menu);
            }
        }
        return children;
    }

    private void requireParentAlive(Long parentId) {
        if (parentId != null && parentId != 0 && menuMapper.selectById(parentId) == null) {
            throw new BizException("上级菜单不存在: " + parentId);
        }
    }

    private void requireType(String type) {
        if (!MenuRecord.TYPE_DIRECTORY.equals(type) && !MenuRecord.TYPE_MENU.equals(type)
                && !MenuRecord.TYPE_BUTTON.equals(type) && !MenuRecord.TYPE_LINK.equals(type)) {
            throw new BizException("非法的菜单类型: " + type + "，只允许 DIRECTORY、MENU、BUTTON、LINK");
        }
    }
}
