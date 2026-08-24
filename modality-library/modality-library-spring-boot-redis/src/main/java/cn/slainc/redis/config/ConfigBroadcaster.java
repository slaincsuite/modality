package cn.slainc.redis.config;

public interface ConfigBroadcaster {

    void publish(ConfigChangeMessage message);

    String channel();
}
