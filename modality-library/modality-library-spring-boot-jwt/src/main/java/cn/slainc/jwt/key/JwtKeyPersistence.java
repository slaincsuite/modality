package cn.slainc.jwt.key;

import java.util.List;

public interface JwtKeyPersistence {

    void saveAll(List<JwtKeyRecord> records);

    // 顺序敏感：必须按 createdAt 升序返回，并列时后写入者排后，JwtKeyManager 取最后一条为当前签发密钥
    List<JwtKeyRecord> loadAll();
}
