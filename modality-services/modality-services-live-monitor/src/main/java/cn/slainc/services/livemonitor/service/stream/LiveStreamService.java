package cn.slainc.services.livemonitor.service.stream;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.services.livemonitor.LiveMonitorKeys;
import cn.slainc.services.livemonitor.autoconfigure.LiveMonitorProperties;
import cn.slainc.services.livemonitor.dto.LiveStreamRequest;
import cn.slainc.services.livemonitor.dto.LiveStreamStatsView;
import cn.slainc.services.livemonitor.dto.LiveStreamView;
import cn.slainc.services.livemonitor.entity.LiveStreamRecord;
import cn.slainc.services.livemonitor.mapper.LiveStreamMapper;
import cn.slainc.services.livemonitor.support.StreamUrlGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class LiveStreamService {

    private static final int MAX_NAME_LENGTH = 128;

    private static final int MAX_URL_LENGTH = 1000;

    private static final int MAX_ERROR_LENGTH = 1000;

    private final LiveStreamMapper liveStreamMapper;

    private final LiveStreamProbe liveStreamProbe;

    private final StreamUrlGuard streamUrlGuard;

    private final LiveMonitorProperties properties;

    public LiveStreamService(LiveStreamMapper liveStreamMapper, LiveStreamProbe liveStreamProbe,
                             StreamUrlGuard streamUrlGuard, LiveMonitorProperties properties) {
        this.liveStreamMapper = liveStreamMapper;
        this.liveStreamProbe = liveStreamProbe;
        this.streamUrlGuard = streamUrlGuard;
        this.properties = properties;
    }

    public PageResult<LiveStreamView> page(long current, long size, String keyword, String status,
                                           Boolean enabled) {
        LambdaQueryWrapper<LiveStreamRecord> wrapper = new LambdaQueryWrapper<>();
        String actualKeyword = trimToNull(keyword);
        if (actualKeyword != null) {
            wrapper.and(query -> query.like(LiveStreamRecord::getName, actualKeyword)
                    .or()
                    .like(LiveStreamRecord::getUrl, actualKeyword));
        }
        String actualStatus = normalizeStatusFilter(status);
        if (actualStatus != null) {
            wrapper.eq(LiveStreamRecord::getStatus, actualStatus);
        }
        if (enabled != null) {
            wrapper.eq(LiveStreamRecord::getEnabled, enabled);
        }
        wrapper.orderByDesc(LiveStreamRecord::getId);
        Page<LiveStreamRecord> result = liveStreamMapper.selectPage(
                Page.of(normalizePage(current), normalizeSize(size)), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize())
                .map(LiveStreamView::from);
    }

    @Transactional(rollbackFor = Exception.class)
    public LiveStreamView create(LiveStreamRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "直播源信息不能为空");
        }
        String name = validateName(request.name());
        String url = validateUrl(request.url());
        streamUrlGuard.validate(url);
        long now = System.currentTimeMillis();
        LiveStreamRecord record = new LiveStreamRecord();
        record.setTenantId(TenantContext.getOrDefault());
        record.setName(name);
        record.setUrl(url);
        record.setEnabled(request.enabled() == null || request.enabled());
        record.setStatus(LiveMonitorKeys.STATUS_UNKNOWN);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(0);
        liveStreamMapper.insert(record);
        return LiveStreamView.from(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public LiveStreamView update(Long id, LiveStreamRequest request) {
        LiveStreamRecord record = require(id);
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "直播源信息不能为空");
        }
        String name = validateName(request.name());
        String url = validateUrl(request.url());
        streamUrlGuard.validate(url);
        boolean urlChanged = !Objects.equals(record.getUrl(), url);
        record.setName(name);
        record.setUrl(url);
        if (request.enabled() != null) {
            record.setEnabled(request.enabled());
        }
        if (urlChanged) {
            record.setStatus(LiveMonitorKeys.STATUS_UNKNOWN);
            record.setLastCheckAt(null);
            record.setLastLatencyMs(null);
            record.setLastError(null);
        }
        record.setUpdateTime(System.currentTimeMillis());
        if (liveStreamMapper.updateById(record) <= 0) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "直播源不存在或已被删除");
        }
        return LiveStreamView.from(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        if (liveStreamMapper.deleteById(id) <= 0) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "直播源不存在或已被删除");
        }
    }

    public LiveStreamView check(Long id) {
        LiveStreamRecord record = require(id);
        saveProbeResult(record, liveStreamProbe.probe(record.getUrl()));
        return LiveStreamView.from(record);
    }

    public LiveStreamStatsView checkAll() {
        List<LiveStreamRecord> records = liveStreamMapper.selectList(
                new LambdaQueryWrapper<LiveStreamRecord>().eq(LiveStreamRecord::getEnabled, true));
        for (LiveStreamRecord record : records) {
            StreamProbeResult result;
            try {
                result = liveStreamProbe.probe(record.getUrl());
            } catch (BizException exception) {
                result = new StreamProbeResult(LiveMonitorKeys.STATUS_DOWN, null, exception.getMessage());
            }
            saveProbeResult(record, result);
        }
        return stats();
    }

    public LiveStreamStatsView stats() {
        List<LiveStreamRecord> records = liveStreamMapper.selectList(new LambdaQueryWrapper<>());
        long online = 0L;
        long offline = 0L;
        long unknown = 0L;
        long latencyTotal = 0L;
        long latencyCount = 0L;
        for (LiveStreamRecord record : records) {
            if (LiveMonitorKeys.STATUS_UP.equals(record.getStatus())) {
                online++;
            } else if (LiveMonitorKeys.STATUS_DOWN.equals(record.getStatus())) {
                offline++;
            } else {
                unknown++;
            }
            if (record.getLastLatencyMs() != null) {
                latencyTotal += record.getLastLatencyMs();
                latencyCount++;
            }
        }
        long averageLatency = latencyCount == 0L ? 0L : Math.round((double) latencyTotal / latencyCount);
        return new LiveStreamStatsView(records.size(), online, offline, unknown, averageLatency);
    }

    private LiveStreamRecord require(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "直播源ID不能为空");
        }
        LiveStreamRecord record = liveStreamMapper.selectById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "直播源不存在");
        }
        return record;
    }

    private void saveProbeResult(LiveStreamRecord record, StreamProbeResult result) {
        long now = System.currentTimeMillis();
        record.setStatus(result.status());
        record.setLastCheckAt(now);
        record.setLastLatencyMs(result.latencyMs());
        record.setLastError(truncate(result.error()));
        record.setUpdateTime(now);
        if (liveStreamMapper.updateById(record) <= 0) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "直播源不存在或已被删除");
        }
    }

    private String validateName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "直播源名称不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "直播源名称长度不能超过128");
        }
        return name;
    }

    private String validateUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "直播源地址不能为空");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "直播源地址长度不能超过1000");
        }
        return url;
    }

    private String normalizeStatusFilter(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!LiveMonitorKeys.STATUS_UP.equals(normalized)
                && !LiveMonitorKeys.STATUS_DOWN.equals(normalized)
                && !LiveMonitorKeys.STATUS_UNKNOWN.equals(normalized)) {
            throw new BizException(ResultCode.BAD_REQUEST, "直播源状态筛选仅支持 UP、DOWN、UNKNOWN");
        }
        return normalized;
    }

    private long normalizePage(long current) {
        return Math.max(current, 1L);
    }

    private long normalizeSize(long size) {
        long maxPageSize = Math.max(properties.getMaxPageSize(), 1L);
        return Math.min(Math.max(size, 1L), maxPageSize);
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
