package cn.slainc.modules.infra.store;

import java.util.LinkedHashMap;
import java.util.Map;

public class MemoryChunkSessionStore implements ChunkSessionStore {

    private final Map<String, ChunkSession> sessions;

    public MemoryChunkSessionStore(int maxSessions) {
        int limit = Math.max(maxSessions, 10);
        this.sessions = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ChunkSession> eldest) {
                return size() > limit;
            }
        };
    }

    @Override
    public void save(ChunkSession session) {
        synchronized (sessions) {
            sessions.put(session.uploadId(), session);
        }
    }

    @Override
    public ChunkSession find(String uploadId) {
        if (uploadId == null) {
            return null;
        }
        synchronized (sessions) {
            return sessions.get(uploadId);
        }
    }

    @Override
    public void remove(String uploadId) {
        if (uploadId == null) {
            return;
        }
        synchronized (sessions) {
            sessions.remove(uploadId);
        }
    }

    public int size() {
        synchronized (sessions) {
            return sessions.size();
        }
    }
}
