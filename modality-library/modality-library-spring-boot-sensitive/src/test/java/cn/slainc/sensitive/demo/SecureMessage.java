package cn.slainc.sensitive.demo;

import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveType;

import java.util.List;

@Sensitive(type = SensitiveType.PHONE)
public class SecureMessage {

    private String phone = "13812348888";

    private String backupPhone = "13912345678";

    private Long amount = 100L;

    private List<String> tags = List.of("13812348888");

    public String getPhone() {
        return phone;
    }

    public String getBackupPhone() {
        return backupPhone;
    }

    public Long getAmount() {
        return amount;
    }

    public List<String> getTags() {
        return tags;
    }
}
