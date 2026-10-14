package cn.slainc.modules.infra.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.infra.entity.SearchDocumentRecord;
import cn.slainc.modules.infra.entity.SearchIndexTermRecord;
import cn.slainc.modules.infra.mapper.SearchDocumentMapper;
import cn.slainc.modules.infra.mapper.SearchIndexTermMapper;
import cn.slainc.mybatis.support.LikeUtils;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.index.SearchIndexer;
import cn.slainc.search.source.SearchDocumentRegistry;
import cn.slainc.search.source.SearchDocumentSource;
import cn.slainc.search.spi.SearchDocumentPayload;
import cn.slainc.search.spi.SearchDocumentPort;
import cn.slainc.search.spi.SearchDocumentSnapshot;
import cn.slainc.search.spi.SearchTermRow;
import cn.slainc.utils.string.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchDocumentService implements SearchDocumentPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchDocumentService.class);

    private final SearchDocumentMapper documentMapper;

    private final SearchIndexTermMapper termMapper;

    private final SearchIndexer searchIndexer;

    private final SearchDocumentRegistry registry;

    private final SearchProperties properties;

    public SearchDocumentService(SearchDocumentMapper documentMapper, SearchIndexTermMapper termMapper,
                                 SearchIndexer searchIndexer, SearchDocumentRegistry registry,
                                 SearchProperties properties) {
        this.documentMapper = documentMapper;
        this.termMapper = termMapper;
        this.searchIndexer = searchIndexer;
        this.registry = registry;
        this.properties = properties == null ? new SearchProperties() : properties;
    }

    public DocumentView index(SearchDocumentPayload payload) {
        return view(indexRecord(payload));
    }

    private SearchDocumentRecord indexRecord(SearchDocumentPayload payload) {
        SearchDocumentPayload safe = requirePayload(payload);
        SearchDocumentRecord record = new SearchDocumentRecord();
        record.setSourceType(safe.sourceType().trim());
        record.setSourceId(safe.sourceId().trim());
        record.setTitle(safe.title());
        record.setSummary(safe.summary());
        record.setContent(safe.content());
        record.setTags(safe.tags());
        record.setUrl(safe.url());
        record.setEnabled(safe.enabled() == null ? Boolean.TRUE : safe.enabled());
        record.setIndexedAt(System.currentTimeMillis());
        SearchDocumentRecord saved = upsert(record);
        if (searchIndexer != null) {
            searchIndexer.reindex(snapshot(saved));
        }
        return saved;
    }

    public SearchDocumentRecord findBySource(String sourceType, String sourceId) {
        if (documentMapper == null || StringUtils.isBlank(sourceType) || StringUtils.isBlank(sourceId)) {
            return null;
        }
        return documentMapper.selectOne(Wrappers.<SearchDocumentRecord>lambdaQuery()
                .eq(SearchDocumentRecord::getSourceType, sourceType.trim())
                .eq(SearchDocumentRecord::getSourceId, sourceId.trim())
                .last("limit 1"));
    }

    public PageResult<DocumentView> page(String keyword, String sourceType, Boolean enabled, long current, long size) {
        long safeCurrent = Math.max(1L, current);
        long safeSize = normalizeSize(size);
        if (documentMapper == null) {
            return PageResult.empty(safeCurrent, safeSize);
        }
        LambdaQueryWrapper<SearchDocumentRecord> wrapper = Wrappers.<SearchDocumentRecord>lambdaQuery()
                .eq(StringUtils.isNotBlank(sourceType), SearchDocumentRecord::getSourceType,
                        StringUtils.trim(sourceType))
                .eq(enabled != null, SearchDocumentRecord::getEnabled, enabled)
                .and(StringUtils.isNotBlank(keyword), nested -> nested
                        .like(SearchDocumentRecord::getTitle, LikeUtils.escapeLike(keyword))
                        .or()
                        .like(SearchDocumentRecord::getSummary, LikeUtils.escapeLike(keyword)))
                .orderByDesc(SearchDocumentRecord::getIndexedAt);
        Page<SearchDocumentRecord> paged = documentMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        List<DocumentView> views = new ArrayList<>();
        for (SearchDocumentRecord record : paged.getRecords()) {
            views.add(view(record));
        }
        return PageResult.of(views, paged.getTotal(), safeCurrent, safeSize);
    }

    @Override
    public SearchDocumentSnapshot upsert(SearchDocumentPayload payload) {
        return snapshot(indexRecord(payload));
    }

    @Override
    public int remove(String sourceType, String sourceId) {
        if (documentMapper == null || StringUtils.isBlank(sourceType) || StringUtils.isBlank(sourceId)) {
            return 0;
        }
        SearchDocumentRecord existing = findBySource(sourceType, sourceId);
        if (existing == null || existing.getId() == null) {
            return 0;
        }
        if (searchIndexer != null) {
            searchIndexer.removeByDocumentId(existing.getId());
        } else if (termMapper != null) {
            termMapper.delete(Wrappers.<SearchIndexTermRecord>lambdaQuery()
                    .eq(SearchIndexTermRecord::getDocumentId, existing.getId()));
        }
        documentMapper.deleteById(existing.getId());
        return 1;
    }

    @Override
    public int reindexAll(Collection<String> sourceTypes) {
        if (documentMapper == null || registry == null) {
            return 0;
        }
        List<SearchDocumentSource> sources = registry.find(sourceTypes);
        if (sources.isEmpty()) {
            LOGGER.debug("没有已注册的检索文档源，跳过重建");
            return 0;
        }
        int indexed = 0;
        int batchSize = Math.max(1, properties.getReindexBatchSize());
        for (SearchDocumentSource source : sources) {
            List<SearchDocumentPayload> payloads = source.loadAll();
            if (payloads == null || payloads.isEmpty()) {
                LOGGER.debug("检索文档源 {} 没有返回文档", source.sourceType());
                continue;
            }
            Set<String> presentSourceIds = new HashSet<>();
            for (int from = 0; from < payloads.size(); from += batchSize) {
                List<SearchDocumentPayload> batch =
                        payloads.subList(from, Math.min(payloads.size(), from + batchSize));
                for (SearchDocumentPayload payload : batch) {
                    if (payload == null || StringUtils.isBlank(payload.sourceId())) {
                        continue;
                    }
                    index(payload);
                    presentSourceIds.add(payload.sourceId().trim());
                    indexed++;
                }
            }
            removeStale(source.sourceType(), presentSourceIds);
        }
        LOGGER.info("重建了 {} 个检索文档，涉及 {} 个来源类型", indexed, sources.size());
        return indexed;
    }

    @Override
    public int removeTerms(Long documentId) {
        if (termMapper == null || documentId == null) {
            return 0;
        }
        return termMapper.delete(Wrappers.<SearchIndexTermRecord>lambdaQuery()
                .eq(SearchIndexTermRecord::getDocumentId, documentId));
    }

    @Override
    public int insertTerms(Collection<SearchTermRow> terms, int batchSize) {
        if (termMapper == null || terms == null || terms.isEmpty()) {
            return 0;
        }
        List<SearchIndexTermRecord> records = new ArrayList<>(terms.size());
        for (SearchTermRow row : terms) {
            if (row != null) {
                records.add(termRecord(row));
            }
        }
        termMapper.insert(records, Math.max(1, batchSize));
        return records.size();
    }

    @Override
    public List<SearchTermRow> findTerms(Collection<String> terms) {
        if (termMapper == null || terms == null || terms.isEmpty()) {
            return List.of();
        }
        List<SearchIndexTermRecord> rows = termMapper.selectList(Wrappers.<SearchIndexTermRecord>lambdaQuery()
                .in(SearchIndexTermRecord::getTerm, terms));
        if (rows == null) {
            return List.of();
        }
        List<SearchTermRow> result = new ArrayList<>(rows.size());
        for (SearchIndexTermRecord row : rows) {
            if (row != null) {
                result.add(termRow(row));
            }
        }
        return result;
    }

    @Override
    public List<SearchDocumentSnapshot> findEnabledDocuments(Collection<Long> documentIds,
                                                             Collection<String> sourceTypes) {
        if (documentMapper == null || documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        Set<String> types = sourceTypes == null ? Set.of() : new HashSet<>(sourceTypes);
        List<SearchDocumentRecord> found = documentMapper.selectList(Wrappers.<SearchDocumentRecord>lambdaQuery()
                .in(SearchDocumentRecord::getId, documentIds)
                .eq(SearchDocumentRecord::getEnabled, Boolean.TRUE)
                .in(!types.isEmpty(), SearchDocumentRecord::getSourceType, types));
        if (found == null) {
            return List.of();
        }
        List<SearchDocumentSnapshot> documents = new ArrayList<>(found.size());
        for (SearchDocumentRecord record : found) {
            if (record != null && record.getId() != null) {
                documents.add(snapshot(record));
            }
        }
        return documents;
    }

    @Override
    public long countEnabledDocuments() {
        if (documentMapper == null) {
            return 0L;
        }
        Long count = documentMapper.selectCount(Wrappers.<SearchDocumentRecord>lambdaQuery()
                .eq(SearchDocumentRecord::getEnabled, Boolean.TRUE));
        return count == null ? 0L : count;
    }

    @Override
    public long countTerms() {
        if (termMapper == null) {
            return 0L;
        }
        Long count = termMapper.selectCount(null);
        return count == null ? 0L : count;
    }

    @Override
    public LocalDateTime lastIndexedAt() {
        if (documentMapper == null) {
            return null;
        }
        List<SearchDocumentRecord> rows = documentMapper.selectList(Wrappers.<SearchDocumentRecord>lambdaQuery()
                .select(SearchDocumentRecord::getIndexedAt)
                .isNotNull(SearchDocumentRecord::getIndexedAt)
                .orderByDesc(SearchDocumentRecord::getIndexedAt)
                .last("limit 1"));
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        return toDateTime(rows.get(0).getIndexedAt());
    }

    private SearchDocumentRecord upsert(SearchDocumentRecord record) {
        requireDocumentMapper();
        SearchDocumentRecord existing = findBySource(record.getSourceType(), record.getSourceId());
        long now = System.currentTimeMillis();
        if (existing == null) {
            record.setId(null);
            record.setCreateTime(now);
            record.setUpdateTime(now);
            documentMapper.insert(record);
        } else {
            record.setId(existing.getId());
            record.setCreateTime(existing.getCreateTime());
            record.setUpdateTime(now);
            documentMapper.updateById(record);
        }
        SearchDocumentRecord reloaded = findBySource(record.getSourceType(), record.getSourceId());
        return reloaded == null ? record : reloaded;
    }

    private void removeStale(String sourceType, Set<String> presentSourceIds) {
        if (StringUtils.isBlank(sourceType)) {
            return;
        }
        List<SearchDocumentRecord> stored = documentMapper.selectList(Wrappers.<SearchDocumentRecord>lambdaQuery()
                .select(SearchDocumentRecord::getId, SearchDocumentRecord::getSourceId)
                .eq(SearchDocumentRecord::getSourceType, sourceType.trim()));
        if (stored == null || stored.isEmpty()) {
            return;
        }
        List<Long> stale = new ArrayList<>();
        for (SearchDocumentRecord record : stored) {
            if (record != null && record.getId() != null && !presentSourceIds.contains(record.getSourceId())) {
                stale.add(record.getId());
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        if (searchIndexer != null && searchIndexer.usesLuceneIndex()) {
            for (Long documentId : stale) {
                searchIndexer.removeByDocumentId(documentId);
            }
        }
        if (termMapper != null) {
            termMapper.delete(Wrappers.<SearchIndexTermRecord>lambdaQuery()
                    .in(SearchIndexTermRecord::getDocumentId, stale));
        }
        documentMapper.deleteByIds(stale);
        LOGGER.info("清理了来源类型 {} 的 {} 个过期检索文档", sourceType, stale.size());
    }

    private long normalizeSize(long size) {
        long requested = size > 0 ? size : properties.getDefaultPageSize();
        return Math.min(Math.max(1, requested), Math.max(1, properties.getMaxPageSize()));
    }

    private void requireDocumentMapper() {
        if (documentMapper == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "未启用检索存储");
        }
    }

    private static SearchDocumentPayload requirePayload(SearchDocumentPayload payload) {
        if (payload == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "检索文档不能为空");
        }
        if (StringUtils.isBlank(payload.sourceType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "来源类型不能为空");
        }
        if (StringUtils.isBlank(payload.sourceId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "来源主键不能为空");
        }
        return payload;
    }

    public static SearchDocumentSnapshot snapshot(SearchDocumentRecord record) {
        if (record == null) {
            return null;
        }
        return new SearchDocumentSnapshot(record.getId(), record.getSourceType(), record.getSourceId(),
                record.getTitle(), record.getSummary(), record.getContent(), record.getTags(), record.getUrl(),
                record.getEnabled(), toDateTime(record.getIndexedAt()), record.getTenantId());
    }

    private static SearchIndexTermRecord termRecord(SearchTermRow row) {
        SearchIndexTermRecord record = new SearchIndexTermRecord();
        record.setDocumentId(row.documentId());
        record.setTerm(row.term());
        record.setField(row.field());
        record.setWeight(row.weight());
        record.setTenantId(row.tenantId());
        return record;
    }

    private static SearchTermRow termRow(SearchIndexTermRecord record) {
        return new SearchTermRow(record.getDocumentId(), record.getTerm(), record.getField(), record.getWeight(),
                record.getTenantId());
    }

    private static DocumentView view(SearchDocumentRecord record) {
        return new DocumentView(record.getId(), record.getSourceType(), record.getSourceId(), record.getTitle(),
                record.getSummary(), record.getTags(), record.getUrl(), record.getEnabled(), record.getIndexedAt(),
                record.getTenantId());
    }

    private static LocalDateTime toDateTime(Long millis) {
        return millis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    public record DocumentView(Long id, String sourceType, String sourceId, String title, String summary,
                               String tags, String url, Boolean enabled, Long indexedAt, Long tenantId) {
    }
}
