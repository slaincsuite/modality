package cn.slainc.modules.upms.service.self;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.dto.FavoriteRequest;
import cn.slainc.modules.upms.entity.FavoriteRecord;
import cn.slainc.modules.upms.mapper.FavoriteMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    public FavoriteService(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    public List<FavoriteRecord> list() {
        Long userId = SelfServiceSupport.currentUserId();
        return favoriteMapper.selectList(new LambdaQueryWrapper<FavoriteRecord>()
                .eq(FavoriteRecord::getUserId, userId)
                .orderByAsc(FavoriteRecord::getSort)
                .orderByDesc(FavoriteRecord::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public FavoriteRecord add(FavoriteRequest request) {
        if (request == null) {
            throw new BizException("收藏项不能为空");
        }
        Long userId = SelfServiceSupport.currentUserId();
        String targetType = SelfServiceSupport.targetType(request.targetType());
        String targetKey = SelfServiceSupport.requireText(request.targetKey(), "目标标识不能为空");
        String name = SelfServiceSupport.requireText(request.name(), "名称不能为空");
        FavoriteRecord exists = existing(userId, targetType, targetKey);
        long now = System.currentTimeMillis();
        if (exists != null) {
            // 重复收藏同一目标只刷新前端展示快照，不产生第二条记录
            exists.setName(name);
            exists.setPath(request.path());
            exists.setIcon(request.icon());
            if (request.sort() != null) {
                exists.setSort(request.sort());
            }
            exists.setUpdateTime(now);
            favoriteMapper.updateById(exists);
            return exists;
        }
        FavoriteRecord record = new FavoriteRecord();
        record.setUserId(userId);
        record.setTargetType(targetType);
        record.setTargetKey(targetKey);
        record.setName(name);
        record.setPath(request.path());
        record.setIcon(request.icon());
        record.setSort(request.sort() == null ? 0 : request.sort());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        favoriteMapper.insert(record);
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public int remove(List<Long> ids) {
        Long userId = SelfServiceSupport.currentUserId();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 删除条件带上当前账号：越权收藏 ID 不命中，无需先查归属
        return favoriteMapper.delete(new LambdaQueryWrapper<FavoriteRecord>()
                .eq(FavoriteRecord::getUserId, userId)
                .in(FavoriteRecord::getId, ids));
    }

    private FavoriteRecord existing(Long userId, String targetType, String targetKey) {
        return favoriteMapper.selectOne(new LambdaQueryWrapper<FavoriteRecord>()
                .eq(FavoriteRecord::getUserId, userId)
                .eq(FavoriteRecord::getTargetType, targetType)
                .eq(FavoriteRecord::getTargetKey, targetKey)
                .last("limit 1"));
    }
}
