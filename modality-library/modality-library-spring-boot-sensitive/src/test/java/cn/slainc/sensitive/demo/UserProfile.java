package cn.slainc.sensitive.demo;

import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveStrategy;
import cn.slainc.sensitive.annotation.SensitiveType;

import java.util.List;

public class UserProfile {

    public static final Long ID = 1800000000000000001L;

    public static final String PHONE = "13812348888";

    public static final String WORK_PHONE = "13912345678";

    public static final String ID_CARD = "110101199003078888";

    public static final String BANK_CARD = "6222021234567890123";

    public static final String EMAIL = "zhangsan@example.com";

    public static final String REAL_NAME = "张三";

    public static final String ADDRESS = "北京市朝阳区建国路88号";

    public static final String PASSWORD = "P@ssw0rd123";

    public static final String REPLACE_RAW = "需要替换的文本";

    public static final String HASH_RAW = "hash-me";

    public static final String ENCRYPT_RAW = "encrypt-me";

    private Long id = ID;

    private String username = "zhangsan";

    @Sensitive(type = SensitiveType.PHONE)
    private String phone = PHONE;

    @Sensitive(type = SensitiveType.ID_CARD)
    private String idCard = ID_CARD;

    @Sensitive(type = SensitiveType.BANK_CARD)
    private String bankCard = BANK_CARD;

    @Sensitive(type = SensitiveType.EMAIL)
    private String email = EMAIL;

    @Sensitive(type = SensitiveType.NAME)
    private String realName = REAL_NAME;

    @Sensitive(type = SensitiveType.ADDRESS)
    private String address = ADDRESS;

    @Sensitive(type = SensitiveType.PASSWORD)
    private String password = PASSWORD;

    @Sensitive(strategy = SensitiveStrategy.REPLACE, replacement = "[已隐藏]")
    private String replaceField = REPLACE_RAW;

    @Sensitive(strategy = SensitiveStrategy.REPLACE)
    private String replaceDefaultField = REPLACE_RAW;

    @Sensitive(strategy = SensitiveStrategy.HASH)
    private String hashField = HASH_RAW;

    @Sensitive(strategy = SensitiveStrategy.ENCRYPT)
    private String encryptField = ENCRYPT_RAW;

    @Sensitive(type = SensitiveType.PHONE, prefixKeep = 2, suffixKeep = 3, maskChar = '#')
    private String customPhone = PHONE;

    @Sensitive(type = SensitiveType.PHONE, groups = "contact")
    private String workPhone = WORK_PHONE;

    private String plainField = "无需脱敏";

    @Sensitive(type = SensitiveType.PHONE)
    private List<String> emptyPhones = List.of();

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public List<String> getEmptyPhones() {
        return emptyPhones;
    }

    public String getIdCard() {
        return idCard;
    }

    public String getBankCard() {
        return bankCard;
    }

    public String getEmail() {
        return email;
    }

    public String getRealName() {
        return realName;
    }

    public String getAddress() {
        return address;
    }

    public String getPassword() {
        return password;
    }

    public String getReplaceField() {
        return replaceField;
    }

    public String getReplaceDefaultField() {
        return replaceDefaultField;
    }

    public String getHashField() {
        return hashField;
    }

    public String getEncryptField() {
        return encryptField;
    }

    public String getCustomPhone() {
        return customPhone;
    }

    public String getWorkPhone() {
        return workPhone;
    }

    public String getPlainField() {
        return plainField;
    }
}
