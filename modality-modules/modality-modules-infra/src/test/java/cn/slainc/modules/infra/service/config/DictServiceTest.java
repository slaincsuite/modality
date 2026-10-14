package cn.slainc.modules.infra.service.config;

import cn.slainc.redis.cache.CacheInvalidationListener;
import cn.slainc.redis.cache.CacheInvalidationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DictServiceTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:dict-test;DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", ""));
        jdbc.execute("DROP TABLE IF EXISTS t_infra_dict_item");
        jdbc.execute("DROP TABLE IF EXISTS t_infra_dict_type");
        jdbc.execute("CREATE TABLE t_infra_dict_type (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(64),"
                + " name VARCHAR(128), remark VARCHAR(255), enabled INT, create_time BIGINT, update_time BIGINT)");
        jdbc.execute("CREATE TABLE t_infra_dict_item (id BIGINT AUTO_INCREMENT PRIMARY KEY, type_code VARCHAR(64),"
                + " label VARCHAR(128), item_value VARCHAR(128), sort INT, enabled INT, remark VARCHAR(255),"
                + " create_time BIGINT, update_time BIGINT)");
    }

    @Test
    void cacheExpiresAfterTtl() throws Exception {
        DictService service = new DictService(jdbc, Duration.ofMillis(50), null, null);
        service.createType("gender", "性别", null);
        service.createItem("gender", "男", "M", 1, null);
        assertThat(service.itemsByType("gender")).hasSize(1);

        jdbc.update("UPDATE t_infra_dict_item SET label = '男性' WHERE type_code = 'gender'");
        assertThat(service.itemsByType("gender").get(0).label()).isEqualTo("男");

        Thread.sleep(150);
        assertThat(service.itemsByType("gender").get(0).label()).isEqualTo("男性");
    }

    @Test
    void remoteInvalidationClearsLocalCache() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        CacheInvalidationListener listener = new CacheInvalidationListener();
        DictService service = new DictService(jdbc, Duration.ofMinutes(5), publisher, listener);
        service.createType("gender", "性别", null);
        service.createItem("gender", "男", "M", 1, null);
        assertThat(service.itemsByType("gender")).hasSize(1);
        verify(publisher).publishInvalidate(DictService.CACHE_NAME, "gender");

        jdbc.update("UPDATE t_infra_dict_item SET label = '男性' WHERE type_code = 'gender'");
        assertThat(service.itemsByType("gender").get(0).label()).isEqualTo("男");

        listener.onMessage(message(DictService.CACHE_NAME, "gender"), null);
        assertThat(service.itemsByType("gender").get(0).label()).isEqualTo("男性");
    }

    @Test
    void clearCachePublishesClearMessage() {
        CacheInvalidationPublisher publisher = mock(CacheInvalidationPublisher.class);
        DictService service = new DictService(jdbc, Duration.ofMinutes(5), publisher, null);
        service.createType("gender", "性别", null);
        service.createItem("gender", "男", "M", 1, null);
        assertThat(service.itemsByType("gender")).hasSize(1);

        jdbc.update("UPDATE t_infra_dict_item SET label = '男性' WHERE type_code = 'gender'");
        service.clearCache();

        verify(publisher).publishClear(DictService.CACHE_NAME);
        assertThat(service.itemsByType("gender").get(0).label()).isEqualTo("男性");
    }

    private static Message message(String cacheName, String key) {
        String payload = "I|" + encode(cacheName) + "|" + encode(key);
        return new DefaultMessage("modality:cache:invalidate".getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
