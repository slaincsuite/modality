package cn.slainc.thirdauth.state;

public interface StateStore {

    String create(long ttlSeconds);

    void put(String state, long ttlSeconds);

    boolean consume(String state);
}
