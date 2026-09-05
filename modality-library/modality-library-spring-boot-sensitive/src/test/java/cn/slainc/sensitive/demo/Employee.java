package cn.slainc.sensitive.demo;

import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Employee {

    private String name;

    @Sensitive(type = SensitiveType.PHONE)
    private String mobile;

    @Sensitive(type = SensitiveType.EMAIL)
    private String email;

    @Sensitive(type = SensitiveType.PHONE)
    private List<String> backupPhones = List.of("13611112222");

    @Sensitive(type = SensitiveType.PHONE)
    private Map<String, String> contacts = Map.of("home", "13533334444");

    @Sensitive(type = SensitiveType.ID_CARD)
    private String[] idCards = {"110101199003077777"};

    private Set<String> tags = Set.of("核心成员");

    public Employee(String name, String mobile, String email) {
        this.name = name;
        this.mobile = mobile;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getBackupPhones() {
        return backupPhones;
    }

    public Map<String, String> getContacts() {
        return contacts;
    }

    public String[] getIdCards() {
        return idCards;
    }

    public Set<String> getTags() {
        return tags;
    }
}
