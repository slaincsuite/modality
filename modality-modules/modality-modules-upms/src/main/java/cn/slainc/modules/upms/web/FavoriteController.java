package cn.slainc.modules.upms.web;

import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.upms.dto.FavoriteRequest;
import cn.slainc.modules.upms.entity.FavoriteRecord;
import cn.slainc.modules.upms.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 自助接口只作用于当前登录账号，不设权限码（登录即可）
@Tag(name = "我的收藏", description = "当前登录账号的收藏项增删查")
@RestController
@RequestMapping("/upms/favorite")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "查询我的收藏")
    @GetMapping
    public List<FavoriteRecord> list() {
        return favoriteService.list();
    }

    @Operation(summary = "新增收藏", description = "已收藏同一目标时按最新名称、路径、图标与排序更新，不产生重复记录")
    @PostMapping
    @AuditLog(value = "新增收藏", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "upms")
    public FavoriteRecord add(@RequestBody FavoriteRequest request) {
        return favoriteService.add(request);
    }

    @Operation(summary = "取消收藏", description = "按收藏项 ID 批量删除，仅能删除当前账号自己的收藏")
    @DeleteMapping("/{ids}")
    @AuditLog(value = "取消收藏", operation = AuditOperation.DELETE, category = AuditCategory.DATA,
            module = "upms")
    public int remove(@PathVariable List<Long> ids) {
        return favoriteService.remove(ids);
    }
}
