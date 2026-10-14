package cn.slainc.modules.upms.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.dto.RecentVisitRequest;
import cn.slainc.modules.upms.entity.MenuRecord;
import cn.slainc.modules.upms.entity.RecentVisitRecord;
import cn.slainc.modules.upms.mapper.MenuMapper;
import cn.slainc.modules.upms.mapper.RecentVisitMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class RecentVisitService {

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    // 每账号保留的最近访问条数，超出即删最旧，避免无限增长
    private static final int KEEP_PER_USER = 50;

    private static final int HOT_WINDOW_DAYS = 30;

    private static final int HOT_DEFAULT_LIMIT = 8;

    private static final int HOT_MAX_LIMIT = 50;

    private static final String HOT_TARGET_TYPE = "MENU";

    private final RecentVisitMapper recentVisitMapper;

    private final MenuMapper menuMapper;

    public RecentVisitService(RecentVisitMapper recentVisitMapper, MenuMapper menuMapper) {
        this.recentVisitMapper = recentVisitMapper;
        this.menuMapper = menuMapper;
    }

    public List<RecentVisitRecord> list(Integer limit) {
        Long userId = SelfServiceSupport.currentUserId();
        return recentVisitMapper.selectList(new LambdaQueryWrapper<RecentVisitRecord>()
                .eq(RecentVisitRecord::getUserId, userId)
                .orderByDesc(RecentVisitRecord::getVisitedAt)
                .orderByDesc(RecentVisitRecord::getId)
                .last("limit " + normalizeLimit(limit)));
    }

    @Transactional(rollbackFor = Exception.class)
    public RecentVisitRecord record(RecentVisitRequest request) {
        if (request == null) {
            throw new BizException("访问记录不能为空");
        }
        Long userId = SelfServiceSupport.currentUserId();
        String targetType = SelfServiceSupport.targetType(request.targetType());
        String targetKey = SelfServiceSupport.requireText(request.targetKey(), "目标标识不能为空");
        String name = SelfServiceSupport.requireText(request.name(), "名称不能为空");
        long now = System.currentTimeMillis();
        RecentVisitRecord record = existing(userId, targetType, targetKey);
        if (record == null) {
            record = new RecentVisitRecord();
            record.setUserId(userId);
            record.setTargetType(targetType);
            record.setTargetKey(targetKey);
            record.setName(name);
            record.setPath(request.path());
            record.setVisitedAt(now);
            record.setCreateTime(now);
            record.setUpdateTime(now);
            recentVisitMapper.insert(record);
        } else {
            record.setName(name);
            record.setPath(request.path());
            record.setVisitedAt(now);
            record.setUpdateTime(now);
            recentVisitMapper.updateById(record);
        }
        prune(userId);
        return record;
    }

    // 热门菜单：复用最近访问数据按访问次数推荐当前租户菜单，菜单本身是平台级表
    public List<MenuRecord> hot(Integer limit) {
        long windowStart = System.currentTimeMillis() - HOT_WINDOW_DAYS * 24L * 60 * 60 * 1000;
        List<Map<String, Object>> rows = recentVisitMapper.selectMaps(new QueryWrapper<RecentVisitRecord>()
                .select("target_key", "count(*) AS visits")
                .eq("target_type", HOT_TARGET_TYPE)
                .ge("visited_at", windowStart)
                .groupBy("target_key")
                .orderByDesc("visits")
                .last("limit " + normalizeHotLimit(limit)));
        List<String> paths = rows.stream().map(row -> text(row, "target_key"))
                .filter(path -> path != null && !path.isBlank()).toList();
        if (paths.isEmpty()) {
            return List.of();
        }
        List<MenuRecord> menus = menuMapper.selectList(new LambdaQueryWrapper<MenuRecord>()
                .in(MenuRecord::getPath, paths)
                .eq(MenuRecord::getStatus, UpmsKeys.STATUS_ENABLED));
        return paths.stream()
                .map(path -> menus.stream().filter(menu -> path.equals(menu.getPath())).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private RecentVisitRecord existing(Long userId, String targetType, String targetKey) {
        return recentVisitMapper.selectOne(new LambdaQueryWrapper<RecentVisitRecord>()
                .eq(RecentVisitRecord::getUserId, userId)
                .eq(RecentVisitRecord::getTargetType, targetType)
                .eq(RecentVisitRecord::getTargetKey, targetKey)
                .last("limit 1"));
    }

    private void prune(Long userId) {
        List<RecentVisitRecord> visits = recentVisitMapper.selectList(new LambdaQueryWrapper<RecentVisitRecord>()
                .eq(RecentVisitRecord::getUserId, userId)
                .orderByDesc(RecentVisitRecord::getVisitedAt)
                .orderByDesc(RecentVisitRecord::getId)
                .last("limit " + (KEEP_PER_USER + 1)));
        if (visits.size() <= KEEP_PER_USER) {
            return;
        }
        List<Long> stale = visits.subList(KEEP_PER_USER, visits.size()).stream()
                .map(RecentVisitRecord::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!stale.isEmpty()) {
            recentVisitMapper.deleteByIds(stale);
        }
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static int normalizeHotLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return HOT_DEFAULT_LIMIT;
        }
        return Math.min(limit, HOT_MAX_LIMIT);
    }

    // H2 会把未加引号的列名回成大写、MySQL 保持原样，两种键都兼容
    private static String text(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            value = row.get(column.toUpperCase(Locale.ROOT));
        }
        return value == null ? null : value.toString();
    }
}
