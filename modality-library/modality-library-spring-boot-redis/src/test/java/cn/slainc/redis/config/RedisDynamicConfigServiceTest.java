package cn.slainc.redis.config;

import cn.slainc.core.config.DynamicConfig;
import cn.slainc.redis.RedisTestSupport;
import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisDynamicConfigServiceTest {

    private static final String SUFFIX = "unittest";

    private static LettuceConnectionFactory factory;

    private static StringRedisTemplate stringRedisTemplate;

    private static RedisDynamicConfigService service;

    private static RedisDynamicConfigService passThrough;

    private String scope;

    @BeforeAll
    static void beforeAll() {
        RedisTestSupport.assumeRedis();
        factory = RedisTestSupport.newConnectionFactory();
        stringRedisTemplate = RedisTestSupport.newStringRedisTemplate(factory);
        service = newService(true, true);
        passThrough = newService(false, false);
    }

    @AfterAll
    static void afterAll() {
        if (stringRedisTemplate != null) {
            stringRedisTemplate.delete(List.of("modality:config:store:" + SUFFIX, "modality:config:version:" + SUFFIX));
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    private static RedisDynamicConfigService newService(boolean publish, boolean subscribe) {
        RedisProperties properties = new RedisProperties();
        properties.getConfig().setStoreKey("store:" + SUFFIX);
        properties.getConfig().setBroadcastChannel("broadcast:" + SUFFIX);
        properties.getConfig().setPublishEnabled(publish);
        properties.getConfig().setSubscribeEnabled(subscribe);
        ConfigBroadcaster broadcaster = publish
                ? new RedisConfigBroadcaster(stringRedisTemplate, RedisSerializers.plainMapper(),
                RedisDynamicConfigService.channelOf(properties))
                : null;
        return new RedisDynamicConfigService(stringRedisTemplate, new InMemoryDynamicConfigRepository(),
                RedisSerializers.plainMapper(), new TestCipher(), broadcaster, null, properties);
    }

    @BeforeEach
    void setUp() {
        scope = "unit." + UUID.randomUUID().toString().substring(0, 8) + ".";
    }

    @Test
    void 字符串与基础类型读写及默认值() {
        service.set(scope + "文本", "模态平台", "基础组");
        service.set(scope + "整数", "42", "基础组");
        service.set(scope + "长整数", "9007199254740993", "基础组");
        service.set(scope + "布尔", "true", "基础组");

        assertThat(service.findString(scope + "文本")).contains("模态平台");
        assertThat(service.getString(scope + "文本", "兜底")).isEqualTo("模态平台");
        assertThat(service.getString(scope + "缺失", "兜底")).isEqualTo("兜底");
        assertThat(service.findString(scope + "缺失")).isEmpty();

        assertThat(service.getInt(scope + "整数", 0)).isEqualTo(42);
        assertThat(service.getInt(scope + "缺失", 7)).isEqualTo(7);
        assertThat(service.getLong(scope + "长整数", 0L)).isEqualTo(9007199254740993L);
        assertThat(service.getLong(scope + "缺失", 9L)).isEqualTo(9L);
        assertThat(service.getBoolean(scope + "布尔", false)).isTrue();
        assertThat(service.getBoolean(scope + "缺失", true)).isTrue();

        assertThatThrownBy(() -> service.getInt(scope + "文本", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是合法整数");
        assertThatThrownBy(() -> service.getBoolean(scope + "文本", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是合法布尔值");
    }

    @Test
    void 对象与列表反序列化() {
        service.set(scope + "对象", "{\"名称\":\"模态\",\"数量\":5}", "基础组");
        Map<String, Object> map = service.getObject(scope + "对象", Map.class);
        assertThat(map).containsEntry("名称", "模态").containsEntry("数量", 5);
        assertThat(service.getObject(scope + "缺失", Map.class)).isNull();
        Map<String, Object> fallback = service.getObject(scope + "缺失", Map.class, Map.of("默认", 1));
        assertThat(fallback).containsEntry("默认", 1);

        service.set(scope + "列表", "[\"甲\",\"乙\"]", "基础组");
        List<String> list = service.getList(scope + "列表", String.class);
        assertThat(list).containsExactly("甲", "乙");
        assertThat(service.getList(scope + "缺失", String.class)).isEmpty();
        assertThat(service.getList(scope + "对象", String.class)).isEmpty();
    }

    @Test
    void 加密配置写入后可解密读取() {
        service.setEncrypted(scope + "密钥", "明文口令", "安全组");
        assertThat(service.getEncrypted(scope + "密钥")).isEqualTo("明文口令");
        assertThat(service.getString(scope + "密钥", null)).isEqualTo("明文口令");

        Object stored = stringRedisTemplate.opsForHash().get("modality:config:store:" + SUFFIX, scope + "密钥");
        assertThat(String.valueOf(stored)).contains("加密:").contains("明文口令".getBytes(java.nio.charset.StandardCharsets.UTF_8).length == 0 ? "" : "");
        assertThat(String.valueOf(stored)).doesNotContain("明文口令");
        assertThat(service.getEncrypted(scope + "缺失")).isNull();
    }

    @Test
    void 分组查询按分组过滤并跳过节用项() {
        service.set(scope + "组内一", "1", "分组甲");
        service.set(scope + "组内二", "2", "分组甲");
        service.set(scope + "组外", "3", "分组乙");

        Map<String, String> group = service.getGroup("分组甲");
        assertThat(group).containsEntry(scope + "组内一", "1").containsEntry(scope + "组内二", "2")
                .doesNotContainKey(scope + "组外");

        List<DynamicConfig> list = service.listByGroup("分组甲");
        assertThat(list).extracting(DynamicConfig::getConfigKey)
                .containsExactly(scope + "组内一", scope + "组内二");

        DynamicConfig disabled = new DynamicConfig();
        disabled.setConfigKey(scope + "禁用项");
        disabled.setConfigValue("9");
        disabled.setConfigGroup("分组甲");
        disabled.setEnabled(Boolean.FALSE);
        service.getRepository().save(disabled);
        service.refresh(scope + "禁用项");
        assertThat(service.getGroup("分组甲")).doesNotContainKey(scope + "禁用项");
        assertThat(service.listByGroup("分组甲")).extracting(DynamicConfig::getConfigKey)
                .contains(scope + "禁用项");
    }

    @Test
    void 版本号随每次变更单调递增() {
        long before = service.currentVersion();
        service.set(scope + "版本", "1", "版本组");
        long afterFirst = service.currentVersion();
        service.set(scope + "版本", "2", "版本组");
        long afterSecond = service.currentVersion();

        assertThat(afterFirst).isGreaterThan(before);
        assertThat(afterSecond).isGreaterThan(afterFirst);
        assertThat(service.getString(scope + "版本", null)).isEqualTo("2");
    }

    @Test
    void 本地缓存命中与删除后失效() {
        service.set(scope + "缓存", "初值", "缓存组");
        assertThat(service.isLocallyCached(scope + "缓存")).isTrue();
        assertThat(service.localCachedValue(scope + "缓存")).isEqualTo("初值");

        service.remove(scope + "缓存");
        assertThat(service.isLocallyCached(scope + "缓存")).isFalse();
        assertThat(service.findString(scope + "缓存")).isEmpty();
    }

    @Test
    void 版本号乐观锁只接受更大版本的消息() {
        RedisDynamicConfigService writer = newService(true, false);
        RedisDynamicConfigService reader = newService(false, false);

        writer.set(scope + "锁", "旧值", "锁组");
        long firstVersion = writer.currentVersion();
        assertThat(reader.getString(scope + "锁", null)).isEqualTo("旧值");
        assertThat(reader.localCachedValue(scope + "锁")).isEqualTo("旧值");

        writer.set(scope + "锁", "新值", "锁组");
        long secondVersion = writer.currentVersion();
        assertThat(secondVersion).isGreaterThan(firstVersion);

        reader.receive(new ConfigChangeMessage(scope + "锁", ConfigChangeMessage.Operation.SET, firstVersion,
                "其他节点"));
        assertThat(reader.localCachedValue(scope + "锁")).isEqualTo("旧值");

        reader.receive(new ConfigChangeMessage(scope + "锁", ConfigChangeMessage.Operation.SET, secondVersion,
                "其他节点"));
        assertThat(reader.localCachedValue(scope + "锁")).isEqualTo("新值");

        reader.receive(new ConfigChangeMessage(scope + "锁", ConfigChangeMessage.Operation.REMOVE, secondVersion,
                "其他节点"));
        assertThat(reader.localCachedValue(scope + "锁")).isEqualTo("新值");

        reader.receive(new ConfigChangeMessage(scope + "锁", ConfigChangeMessage.Operation.REMOVE,
                secondVersion + 10L, "其他节点"));
        assertThat(reader.isLocallyCached(scope + "锁")).isFalse();
    }

    @Test
    void 刷新与全量刷新重建本地缓存() {
        service.set(scope + "刷新", "一", "刷新组");
        assertThat(service.isLocallyCached(scope + "刷新")).isTrue();

        service.refresh(scope + "刷新");
        assertThat(service.localCachedValue(scope + "刷新")).isEqualTo("一");

        service.refreshAll();
        assertThat(service.isLocallyCached(scope + "刷新")).isFalse();
        assertThat(service.getString(scope + "刷新", null)).isEqualTo("一");
    }

    @Test
    void 键为空时抛出中文异常() {
        assertThatThrownBy(() -> service.set("  ", "值", "组"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("动态配置的键不能为空");
    }

    @Test
    void 存储键与频道命名符合约定() {
        assertThat(service.storeKey()).isEqualTo("modality:config:store:" + SUFFIX);
        assertThat(service.channel()).isEqualTo("modality:config:broadcast:" + SUFFIX);
    }

    private static final class TestCipher implements ConfigValueCipher {

        @Override
        public String encrypt(String plainText) {
            return "加密:" + Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String decrypt(String cipherText) {
            return new String(Base64.getDecoder().decode(cipherText.substring("加密:".length())),
                    StandardCharsets.UTF_8);
        }
    }
}
