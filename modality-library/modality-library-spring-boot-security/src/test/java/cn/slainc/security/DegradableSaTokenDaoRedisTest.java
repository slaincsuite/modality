package cn.slainc.security;

import cn.dev33.satoken.session.SaSession;
import cn.slainc.security.dao.DegradableSaTokenDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DegradableSaTokenDaoRedisTest extends SecurityAcceptanceTestBase {

    @Autowired
    DegradableSaTokenDao degradableDao;

    @Test
    void realRedisDelegationWorksAndNotDegraded() {
        assertThat(degradableDao.isDegraded()).isFalse();
        degradableDao.set("modality:security:dao-test:string", "hello", 60);
        assertThat(degradableDao.get("modality:security:dao-test:string")).isEqualTo("hello");
    }

    @Test
    void realRedisSessionRoundTrip() {
        SaSession session = new SaSession("modality:security:dao-test:session");
        session.set("k", "v");
        degradableDao.setSession(session, 60);
        SaSession loaded = degradableDao.getSession("modality:security:dao-test:session");
        assertThat(loaded).isNotNull();
        assertThat(loaded.get("k")).isEqualTo("v");
    }

    @Test
    void realRedisTimeoutAndDelete() {
        degradableDao.set("modality:security:dao-test:ttl", "v", 120);
        assertThat(degradableDao.getTimeout("modality:security:dao-test:ttl")).isBetween(60L, 120L);
        degradableDao.updateTimeout("modality:security:dao-test:ttl", 300);
        assertThat(degradableDao.getTimeout("modality:security:dao-test:ttl")).isGreaterThan(200L);
        degradableDao.delete("modality:security:dao-test:ttl");
        assertThat(degradableDao.get("modality:security:dao-test:ttl")).isNull();
    }

    @Test
    void realRedisSearchData() {
        degradableDao.set("modality:security:dao-test:search:one", "1", 60);
        degradableDao.set("modality:security:dao-test:search:two", "2", 60);
        List<String> found = degradableDao.searchData("modality:security:dao-test:search", "", 0, 10, true);
        assertThat(found).hasSize(2);
    }

    @Test
    void primaryBeanWrapsRedisSaTokenDao() {
        assertThat(degradableDao).isInstanceOf(DegradableSaTokenDao.class);
        assertThat(degradableDao.currentTarget()).isSameAs(degradableDao.currentTarget());
    }
}
