package cn.slainc.modules.infra.store;

public interface ChunkSessionStore {

    void save(ChunkSession session);

    ChunkSession find(String uploadId);

    void remove(String uploadId);
}
