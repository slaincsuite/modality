package cn.slainc.modules.infra.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.search.api.SearchEngine;
import cn.slainc.search.api.SearchHit;
import cn.slainc.search.api.SearchQuery;
import cn.slainc.search.api.SearchStats;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.source.SearchIndexRefresher;
import cn.slainc.search.spi.SearchDocumentPayload;
import cn.slainc.utils.string.StringUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchManagementService.class);

    private static final int DEFAULT_PAGE = 1;

    private final SearchEngine searchEngine;

    private final SearchDocumentService documentService;

    private final SearchIndexRefresher indexRefresher;

    private final SearchHistoryService historyService;

    private final SearchProperties searchProperties;

    private final CurrentUser currentUser;

    public SearchManagementService(SearchEngine searchEngine, SearchDocumentService documentService,
                                   SearchIndexRefresher indexRefresher, SearchHistoryService historyService,
                                   SearchProperties searchProperties, CurrentUser currentUser) {
        this.searchEngine = searchEngine;
        this.documentService = documentService;
        this.indexRefresher = indexRefresher;
        this.historyService = historyService;
        this.searchProperties = searchProperties == null ? new SearchProperties() : searchProperties;
        this.currentUser = currentUser;
    }

    public PageResult<SearchHit> search(String keyword, List<String> types, Integer page, Integer size) {
        Set<String> sourceTypes = normalizeTypes(types);
        int pageNo = page == null ? DEFAULT_PAGE : Math.max(1, page);
        int pageSize = capPageSize(size == null ? 0 : size);
        recordHistory(keyword);
        return searchEngine.search(new SearchQuery(keyword, sourceTypes, pageNo, pageSize, true));
    }

    public PageResult<SearchDocumentService.DocumentView> documents(String keyword, String sourceType,
                                                                    Boolean enabled, long current, long size) {
        return documentService.page(keyword, sourceType, enabled, current, size);
    }

    public SearchDocumentService.DocumentView index(SearchDocumentPayload payload) {
        if (payload == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "检索文档不能为空");
        }
        return documentService.index(payload);
    }

    public int remove(String sourceType, String sourceId) {
        return documentService.remove(sourceType, sourceId);
    }

    public SearchStats stats() {
        return searchEngine.stats();
    }

    public int reindex(List<String> types) {
        Set<String> sourceTypes = normalizeTypes(types);
        return indexRefresher == null ? 0 : indexRefresher.refresh(sourceTypes);
    }

    // 自动记录搜索历史：登录且有有效关键词时写入，失败只降级记日志，不影响检索主流程
    private void recordHistory(String keyword) {
        if (StringUtils.isBlank(keyword) || currentUser == null || currentUser.userId() == null) {
            return;
        }
        try {
            if (historyService != null) {
                historyService.record(keyword);
            }
        } catch (RuntimeException e) {
            LOGGER.debug("记录搜索历史失败，已忽略: {}", e.getMessage());
        }
    }

    // 分页上限：请求值 <= 0 时保持引擎默认，否则不超过配置上限
    private int capPageSize(int requested) {
        int limit = searchProperties.getMaxPageSize();
        return requested <= 0 || limit <= 0 ? requested : Math.min(requested, limit);
    }

    private static Set<String> normalizeTypes(List<String> types) {
        Set<String> sourceTypes = new LinkedHashSet<>();
        if (types == null) {
            return sourceTypes;
        }
        for (String type : types) {
            if (StringUtils.isNotBlank(type)) {
                sourceTypes.add(type.trim());
            }
        }
        return sourceTypes;
    }
}
