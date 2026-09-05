package cn.slainc.sensitive.jackson;

import cn.slainc.crypto.digest.Sm3Utils;
import cn.slainc.sensitive.core.SensitiveConfig;
import cn.slainc.sensitive.core.SensitiveContext;
import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.core.SensitiveSwitch;
import cn.slainc.sensitive.demo.CycleNode;
import cn.slainc.sensitive.demo.GetterProfile;
import cn.slainc.sensitive.demo.OrgTree;
import cn.slainc.sensitive.demo.RecordProfile;
import cn.slainc.sensitive.demo.SecureMessage;
import cn.slainc.sensitive.demo.UserProfile;
import cn.slainc.sensitive.metrics.SensitiveMetrics;
import cn.slainc.sensitive.support.SensitiveTestSupport;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveSerializationTest {

    private SensitiveMasker masker;

    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        masker = SensitiveTestSupport.masker(SensitiveTestSupport.keyProvider(), SensitiveMetrics.noop());
        mapper = SensitiveTestSupport.mapper(masker);
    }

    @AfterEach
    void tearDown() {
        SensitiveContext.clear();
    }

    @Test
    void masksPhoneAndKeepsSourceObjectUntouched() throws Exception {
        UserProfile profile = new UserProfile();
        String json = mapper.writeValueAsString(profile);
        assertEquals("138****8888", read(json, "phone"));
        assertFalse(json.contains(UserProfile.PHONE), json);
        assertEquals(UserProfile.PHONE, profile.getPhone());
        assertEquals(UserProfile.ID_CARD, profile.getIdCard());
    }

    @Test
    void masksEveryBuiltInType() throws Exception {
        String json = mapper.writeValueAsString(new UserProfile());
        assertEquals("138****8888", read(json, "phone"));
        assertEquals("110101********8888", read(json, "idCard"));
        assertEquals("6222***********0123", read(json, "bankCard"));
        assertEquals("z*******@example.com", read(json, "email"));
        assertEquals("张*", read(json, "realName"));
        assertEquals("北京市朝阳区******", read(json, "address"));
        assertEquals("*".repeat(UserProfile.PASSWORD.length()), read(json, "password"));
        assertEquals("无需脱敏", read(json, "plainField"));
    }

    @Test
    void supportsCustomKeepAndMaskChar() throws Exception {
        String json = mapper.writeValueAsString(new UserProfile());
        assertEquals("13######888", read(json, "customPhone"));
    }

    @Test
    void appliesEveryStrategy() throws Exception {
        String json = mapper.writeValueAsString(new UserProfile());
        assertEquals("[已隐藏]", read(json, "replaceField"));
        assertEquals("[已隐藏]", read(json, "replaceDefaultField"));
        assertEquals(Sm3Utils.digestHex(UserProfile.HASH_RAW), read(json, "hashField"));
        String encrypted = read(json, "encryptField");
        assertTrue(encrypted.startsWith("enc:v1:"), encrypted);
        assertEquals(UserProfile.ENCRYPT_RAW, masker.decrypt(encrypted));
        assertFalse(json.contains(UserProfile.ENCRYPT_RAW), json);
    }

    @Test
    void penetratesNestedObjectListSetArrayAndMap() throws Exception {
        String json = mapper.writeValueAsString(new OrgTree());
        assertEquals("137****5678", read(json, "departments[0].owner.mobile"));
        assertEquals("l***@example.com", read(json, "departments[0].owner.email"));
        assertEquals("137****5678", read(json, "departments[0].members[0].mobile"));
        assertEquals("134****1111", read(json, "departments[0].members[1].mobile"));
        assertEquals("136****6666", read(json, "departmentArray[0].owner.mobile"));
        assertEquals("135****7777", read(json, "members[0].mobile"));
        assertEquals("134****8888", read(json, "departmentMap.research.owner.mobile"));
        assertEquals("136****2222", read(json, "departments[0].owner.backupPhones[0]"));
        assertEquals("135****4444", read(json, "departments[0].owner.contacts.home"));
        assertEquals("110101********7777", read(json, "departments[0].owner.idCards[0]"));
        assertFalse(json.contains("13712345678"), json);
        assertFalse(json.contains("13477778888"), json);
    }

    @Test
    void doesNotLoopOnSelfCycle() throws Exception {
        String json = mapper.writeValueAsString(CycleNode.selfCycle());
        assertEquals("138****1111", read(json, "phone"));
        assertTrue(json.contains("\"next\":null"), json);
        assertTrue(json.contains("\"peer\":null"), json);
    }

    @Test
    void doesNotLoopOnMutualCycle() throws Exception {
        String json = mapper.writeValueAsString(CycleNode.mutualCycle());
        assertEquals("138****2222", read(json, "phone"));
        assertEquals("138****3333", read(json, "peer.phone"));
        assertTrue(json.contains("\"peer\":null"), json);
    }

    @Test
    void failsOnCycleWhenCycleGuardDisabled() {
        SensitiveConfig config = new SensitiveConfig('*', "[已隐藏]", "SM3", false);
        SensitiveMasker plain = new SensitiveMasker(config, SensitiveMetrics.noop(),
                new SensitiveSwitch(true, () -> null), () -> null);
        JsonMapper.Builder builder = JsonMapper.builder();
        new SensitiveJacksonCustomizer(plain, config).customize(builder);
        JsonMapper plainMapper = builder.build();
        assertThrows(Exception.class, () -> plainMapper.writeValueAsString(CycleNode.selfCycle()));
    }

    @Test
    void appliesClassLevelAnnotationToTextFieldsOnly() throws Exception {
        String json = mapper.writeValueAsString(new SecureMessage());
        assertEquals("138****8888", read(json, "phone"));
        assertEquals("139****5678", read(json, "backupPhone"));
        assertEquals("100", read(json, "amount"));
        assertEquals("13812348888", read(json, "tags[0]"));
    }

    @Test
    void supportsGetterLevelAnnotation() throws Exception {
        String json = mapper.writeValueAsString(new GetterProfile());
        assertEquals("138****8888", read(json, "mobile"));
        assertEquals("小三", read(json, "nickname"));
    }

    @Test
    void supportsRecordComponentAnnotation() throws Exception {
        String json = mapper.writeValueAsString(new RecordProfile("13812348888", "记录"));
        assertEquals("138****8888", read(json, "phone"));
        assertEquals("记录", read(json, "name"));
    }

    @Test
    void masksGroupedFieldOnlyWhenGroupIsActive() throws Exception {
        String inactive = mapper.writeValueAsString(new UserProfile());
        assertEquals(UserProfile.WORK_PHONE, read(inactive, "workPhone"));
        assertEquals("138****8888", read(inactive, "phone"));

        SensitiveContext.setGroups("contact");
        String active = mapper.writeValueAsString(new UserProfile());
        assertEquals("139****5678", read(active, "workPhone"));
        assertEquals("138****8888", read(active, "phone"));
    }

    @Test
    void skipsMaskingWhenContextDisabled() throws Exception {
        SensitiveContext.disable();
        String json = mapper.writeValueAsString(new UserProfile());
        assertEquals(UserProfile.PHONE, read(json, "phone"));
        assertEquals(UserProfile.EMAIL, read(json, "email"));
    }

    @Test
    void runsWithoutSensitiveAndRestoresState() throws Exception {
        String json = SensitiveContext.callWithoutSensitive(() -> write(new UserProfile()));
        assertEquals(UserProfile.PHONE, read(json, "phone"));
        assertTrue(SensitiveContext.isEnabled());
        assertEquals("138****8888", read(write(new UserProfile()), "phone"));
    }

    @Test
    void skipsNullAndEmptyValues() throws Exception {
        UserProfile profile = new UserProfile();
        profile.setPhone(null);
        String json = mapper.writeValueAsString(profile);
        JsonNode phone = JsonUtils.pathNode(json, "phone");
        assertTrue(phone == null || phone.isNull(), json);
        assertEquals(0, JsonUtils.pathNode(json, "emptyPhones").size());
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String read(String json, String path) {
        JsonNode node = JsonUtils.pathNode(json, path);
        assertNotNull(node, "JSON 中不存在路径 " + path + "，正文=" + json);
        return node.asString();
    }

}
