package cn.slainc.modules.infra.service.console;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.logger.archive.LogArchiveService;
import cn.slainc.logger.level.LogLevelManager;
import cn.slainc.logger.query.LogEntry;
import cn.slainc.logger.query.LogFileQueryService;
import cn.slainc.logger.query.LogQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogConsoleServiceTest {

    @TempDir
    Path directory;

    @Test
    void pagingReusesParsedEntriesWithinCacheTtl() {
        CountingQueryService queryService = new CountingQueryService();
        LogConsoleService service = new LogConsoleService(queryService, emptyProvider(), emptyProvider());

        PageResult<LogEntry> first = service.page(1, 10, "关键字", null, null, null, null, null, false);
        PageResult<LogEntry> second = service.page(2, 10, "关键字", null, null, null, null, null, false);

        assertThat(first.records()).hasSize(10);
        assertThat(second.records()).hasSize(10);
        assertThat(first.total()).isEqualTo(25);
        assertThat(queryService.invocations()).isEqualTo(1);
    }

    @Test
    void oversizedCurrentKeepsScanBounded() {
        CountingQueryService queryService = new CountingQueryService();
        LogConsoleService service = new LogConsoleService(queryService, emptyProvider(), emptyProvider());

        PageResult<LogEntry> result = service.page(Long.MAX_VALUE, 500, null, null, null, null, null, null, false);

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isEqualTo(25);
        assertThat(queryService.invocations()).isEqualTo(1);
    }

    @Test
    void oversizedLogFileIsRejectedBeforeStreaming() throws IOException {
        LogConsoleService service = new LogConsoleService(new CountingQueryService(), emptyProvider(), emptyProvider(),
                Duration.ofSeconds(30), 4, 1024L);
        Path oversized = directory.resolve("oversized.log");
        Files.writeString(oversized, "x".repeat(4096), StandardCharsets.UTF_8);

        ByteArrayOutputStream target = new ByteArrayOutputStream();
        assertThatThrownBy(() -> service.downloadTo(oversized, target))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过在线下载上限");
        assertThat(target.size()).isZero();

        Path small = directory.resolve("small.log");
        Files.writeString(small, "日志内容", StandardCharsets.UTF_8);
        service.downloadTo(small, target);
        assertThat(target.toString(StandardCharsets.UTF_8)).isEqualTo("日志内容");
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("测试环境无可用实例");
            }
        };
    }

    private static final class CountingQueryService extends LogFileQueryService {

        private final AtomicInteger invocations = new AtomicInteger();

        private CountingQueryService() {
            super(null, null, null);
        }

        @Override
        public List<Path> logFiles(boolean includeArchived) {
            return List.of();
        }

        @Override
        public List<LogEntry> query(LogQuery query) {
            invocations.incrementAndGet();
            List<LogEntry> entries = new ArrayList<>();
            for (int index = 0; index < 25; index++) {
                entries.add(new LogEntry(Instant.parse("2026-01-01T00:00:00Z"), "2026-01-01 00:00:00.000", "INFO",
                        "cn.slainc.test", "main", "第 " + index + " 条", null, null, null, null, null, null,
                        "app.log", Map.of()));
            }
            return entries;
        }

        private int invocations() {
            return invocations.get();
        }
    }
}
