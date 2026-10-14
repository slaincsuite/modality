package cn.slainc.changeaudit.support;

import cn.slainc.changeaudit.autoconfigure.ChangeAuditConfigReader;
import cn.slainc.changeaudit.autoconfigure.ChangeAuditProperties;
import cn.slainc.changeaudit.support.FieldChange;
import cn.slainc.changeaudit.support.TestObjectProviders;
import cn.slainc.mybatis.annotation.Sensitive;
import cn.slainc.mybatis.annotation.SensitiveType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldDifferTest {

    private final ChangeAuditProperties properties = new ChangeAuditProperties();

    private final ChangeAuditConfigReader reader = new ChangeAuditConfigReader(properties,
            TestObjectProviders.empty());

    private final FieldDiffer differ = new FieldDiffer(JsonMapper.builder().build(), reader);

    @Test
    void reportsOnlyTheChangedFields() {
        List<FieldChange> changes = differ.diff(demo(1L, "old", "13800001111", "aaaaaa", "keep"),
                demo(1L, "new", "13800001111", "aaaaaa", "keep"));

        assertThat(changes).extracting(FieldChange::field).containsExactly("name");
        assertThat(changes.get(0).before()).isEqualTo("old");
        assertThat(changes.get(0).after()).isEqualTo("new");
    }

    @Test
    void detectsAddedAndRemovedFields() {
        List<FieldChange> changes = differ.diff(demo(1L, null, "13800001111", "aaaaaa", "gone"),
                demo(1L, "created", "13800001111", "aaaaaa", null));

        assertThat(changes).extracting(FieldChange::field).containsExactly("name", "remark");
        assertThat(changes.get(0).before()).isNull();
        assertThat(changes.get(0).after()).isEqualTo("created");
        assertThat(changes.get(1).before()).isEqualTo("gone");
        assertThat(changes.get(1).after()).isNull();
    }

    @Test
    void ignoresTheConfiguredFields() {
        Demo before = demo(1L, "same", "13800001111", "aaaaaa", "keep");
        Demo after = demo(1L, "same", "13800001111", "aaaaaa", "keep");
        before.setCreatedBy("someone");
        after.setCreatedBy("another");

        assertThat(differ.diff(before, after)).isEmpty();
    }

    @Test
    void detectsAndMasksSensitiveChanges() {
        List<FieldChange> changes = differ.diff(demo(1L, "same", "13800001111", "aaaaaa", "keep"),
                demo(1L, "same", "13900002222", "bbbbbb", "keep"));

        assertThat(changes).extracting(FieldChange::field).containsExactlyInAnyOrder("phone", "secret");
        FieldChange phone = changes.stream().filter(change -> "phone".equals(change.field())).findFirst()
                .orElseThrow();
        assertThat(phone.before()).isEqualTo("138****1111");
        assertThat(phone.after()).isEqualTo("139****2222");
        FieldChange secret = changes.stream().filter(change -> "secret".equals(change.field())).findFirst()
                .orElseThrow();
        assertThat(secret.before()).isEqualTo("a****a");
        assertThat(secret.after()).isEqualTo("b****b");
    }

    @Test
    void truncatesLongValues() {
        properties.setMaxValueLength(8);

        List<FieldChange> changes = differ.diff(demo(1L, "0123456789", "13800001111", "aaaaaa", "keep"),
                demo(1L, "same", "13800001111", "aaaaaa", "keep"));

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).before()).isEqualTo("01234567");
    }

    @Test
    void neverEmitsUnchangedFields() {
        properties.setRecordNoChange(true);
        Demo demo = demo(1L, "same", "13800001111", "aaaaaa", "keep");

        assertThat(differ.diff(demo, demo)).isEmpty();
    }

    @Test
    void returnsNoChangeWhenNothingDiffers() {
        Demo demo = demo(1L, "same", "13800001111", "aaaaaa", "keep");

        assertThat(differ.diff(demo, demo)).isEmpty();
        assertThat(differ.diff(null, null)).isEmpty();
    }

    @Test
    void treatsAMissingSnapshotAsAnInsertOrDelete() {
        Demo demo = demo(1L, "same", "13800001111", "aaaaaa", "keep");

        List<FieldChange> inserted = differ.diff(null, demo);
        assertThat(inserted).extracting(FieldChange::field).contains("name");
        assertThat(inserted).allSatisfy(change -> assertThat(change.before()).isNull());

        List<FieldChange> deleted = differ.diff(demo, null);
        assertThat(deleted).extracting(FieldChange::field).contains("name");
        assertThat(deleted).allSatisfy(change -> assertThat(change.after()).isNull());
    }

    @Test
    void serializesTheChangeList() {
        List<FieldChange> changes = differ.diff(demo(1L, "old", "13800001111", "aaaaaa", "keep"),
                demo(1L, "new", "13800001111", "aaaaaa", "keep"));

        String json = differ.serialize(changes);

        assertThat(json).startsWith("[").contains("\"field\":\"name\"").contains("\"before\":\"old\"");
        assertThat(differ.serialize(List.of())).isEqualTo("[]");
    }

    private static Demo demo(Long id, String name, String phone, String secret, String remark) {
        Demo demo = new Demo();
        demo.setId(id);
        demo.setName(name);
        demo.setPhone(phone);
        demo.setSecret(secret);
        demo.setRemark(remark);
        return demo;
    }

    static class Demo {

        private Long id;

        private String name;

        @Sensitive(type = SensitiveType.PHONE)
        private String phone;

        @Sensitive(prefixKeep = 1, suffixKeep = 1)
        private String secret;

        private String remark;

        private String createdBy;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }
    }
}
