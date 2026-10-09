package cn.slainc.storage.dedup;

public interface DedupRegistry {

    record Entry(String id, String key, int references) {
    }

    Entry find(String id);

    String lookup(String logicalId);

    void bind(String logicalId, String id);

    String unbind(String logicalId);

    Entry register(String id, String key);

    Entry retain(String id);

    Entry release(String id);

    void remove(String id);
}
