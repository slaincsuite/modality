package cn.slainc.modules.infra.web.search;

import cn.slainc.core.result.Result;
import cn.slainc.modules.infra.service.search.SearchHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 自助接口：只作用于当前登录账号，登录即可访问，不设权限码
@Tag(name = "搜索历史", description = "当前登录账号最近搜索过的关键词")
@RestController
@RequestMapping("/infra/search/history")
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    public SearchHistoryController(SearchHistoryService searchHistoryService) {
        this.searchHistoryService = searchHistoryService;
    }

    @GetMapping
    public Result<List<SearchHistoryService.HistoryView>> list(
            @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.ok(searchHistoryService.list(limit));
    }

    @DeleteMapping
    public Result<Integer> clear() {
        return Result.ok(searchHistoryService.clear());
    }
}
