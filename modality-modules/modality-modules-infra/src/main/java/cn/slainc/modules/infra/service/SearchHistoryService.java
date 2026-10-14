package cn.slainc.modules.infra.service;

import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.entity.SearchHistoryRecord;
import cn.slainc.modules.infra.mapper.SearchHistoryMapper;
import cn.slainc.utils.string.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchHistoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchHistoryService.class);

    private static final int DEFAULT_LIMIT = 10;

    private static final int MAX_LIMIT = 50;

    // 每个账号在库里保留的最近搜索条数，超出即删最旧的
    private static final int KEEP_PER_USER = 50;

    private static final int MAX_KEYWORD_LENGTH = 128;

    private final SearchHistoryMapper historyMapper;

    private final CurrentUser currentUser;

    public SearchHistoryService(SearchHistoryMapper historyMapper, CurrentUser currentUser) {
        this.historyMapper = historyMapper;
        this.currentUser = currentUser;
    }

    public void record(String keyword) {
        if (historyMapper == null) {
            return;
        }
        Long userId = currentUser == null ? null : currentUser.userId();
        String text = normalizeKeyword(keyword);
        // 自助数据：登录态缺失或关键词为空都直接忽略，不写 userId 为空的孤儿数据
        if (userId == null || text == null) {
            return;
        }
        long now = System.currentTimeMillis();
        SearchHistoryRecord existing = historyMapper.selectOne(Wrappers.<SearchHistoryRecord>lambdaQuery()
                .eq(SearchHistoryRecord::getUserId, userId)
                .eq(SearchHistoryRecord::getKeyword, text)
                .last("limit 1"));
        if (existing == null) {
            SearchHistoryRecord record = new SearchHistoryRecord();
            record.setUserId(userId);
            record.setKeyword(text);
            record.setSearchedAt(now);
            historyMapper.insert(record);
        } else {
            // 唯一索引 (user_id, keyword) 保证只有一条：命中即只刷新搜索时间
            existing.setSearchedAt(now);
            historyMapper.updateById(existing);
        }
        prune(userId);
    }

    public List<HistoryView> list(Integer limit) {
        if (historyMapper == null) {
            return List.of();
        }
        Long userId = currentUser == null ? null : currentUser.userId();
        if (userId == null) {
            return List.of();
        }
        List<SearchHistoryRecord> records = historyMapper.selectList(Wrappers.<SearchHistoryRecord>lambdaQuery()
                .eq(SearchHistoryRecord::getUserId, userId)
                .orderByDesc(SearchHistoryRecord::getSearchedAt)
                .orderByDesc(SearchHistoryRecord::getId)
                .last("limit " + normalizeLimit(limit)));
        List<HistoryView> views = new ArrayList<>();
        for (SearchHistoryRecord record : records == null ? List.<SearchHistoryRecord>of() : records) {
            if (record != null) {
                views.add(new HistoryView(record.getId(), record.getKeyword(), record.getSearchedAt()));
            }
        }
        return views;
    }

    public int clear() {
        if (historyMapper == null) {
            return 0;
        }
        Long userId = currentUser == null ? null : currentUser.userId();
        if (userId == null) {
            return 0;
        }
        return historyMapper.delete(Wrappers.<SearchHistoryRecord>lambdaQuery()
                .eq(SearchHistoryRecord::getUserId, userId));
    }

    // 每个账号只保留最近 KEEP_PER_USER 条：多查一条用于判断是否超限，再删掉后面的旧记录
    private void prune(Long userId) {
        List<SearchHistoryRecord> histories = historyMapper.selectList(Wrappers.<SearchHistoryRecord>lambdaQuery()
                .eq(SearchHistoryRecord::getUserId, userId)
                .orderByDesc(SearchHistoryRecord::getSearchedAt)
                .orderByDesc(SearchHistoryRecord::getId)
                .last("limit " + (KEEP_PER_USER + 1)));
        if (histories == null || histories.size() <= KEEP_PER_USER) {
            return;
        }
        List<Long> stale = new ArrayList<>();
        for (SearchHistoryRecord record : histories.subList(KEEP_PER_USER, histories.size())) {
            if (record != null && record.getId() != null) {
                stale.add(record.getId());
            }
        }
        if (!stale.isEmpty()) {
            historyMapper.deleteByIds(stale);
        }
        LOGGER.debug("账号 {} 的搜索历史超出 {} 条，已清理 {} 条旧记录", userId, KEEP_PER_USER, stale.size());
    }

    // 关键词归一：trim 后为空视为无关键词；超长按列宽截断，避免自动记录因超长写入失败
    private static String normalizeKeyword(String keyword) {
        String text = StringUtils.trimToNull(keyword);
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_KEYWORD_LENGTH ? text : text.substring(0, MAX_KEYWORD_LENGTH);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public record HistoryView(Long id, String keyword, Long searchedAt) {
    }
}
