package cn.slainc.jwt.key;

import java.util.List;

public interface JwtKeyPersistence {

    void saveAll(List<JwtKeyRecord> records);

    List<JwtKeyRecord> loadAll();
}
