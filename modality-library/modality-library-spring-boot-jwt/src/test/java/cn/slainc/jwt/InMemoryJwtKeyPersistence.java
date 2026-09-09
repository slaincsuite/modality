package cn.slainc.jwt;

import cn.slainc.jwt.key.JwtKeyPersistence;
import cn.slainc.jwt.key.JwtKeyRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryJwtKeyPersistence implements JwtKeyPersistence {

    private final Map<String, JwtKeyRecord> records = new LinkedHashMap<>();

    public int saveCount;

    @Override
    public void saveAll(List<JwtKeyRecord> loaded) {
        saveCount++;
        records.clear();
        for (JwtKeyRecord record : loaded) {
            records.put(record.kid(), record);
        }
    }

    @Override
    public List<JwtKeyRecord> loadAll() {
        return new ArrayList<>(records.values());
    }

    public int size() {
        return records.size();
    }
}
