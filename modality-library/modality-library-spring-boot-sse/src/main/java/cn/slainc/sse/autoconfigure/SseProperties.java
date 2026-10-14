package cn.slainc.sse.autoconfigure;

import cn.slainc.utils.random.UuidUtils;
import cn.slainc.utils.string.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "modality.sse")
public class SseProperties {

    public static final String PREFIX = "modality.sse";

    public static final String CLUSTER_PREFIX = "modality.sse.cluster";

    private boolean enabled = true;

    private String endpoint = "/sse";

    private Duration timeout = Duration.ofHours(1);

    private Duration heartbeatInterval = Duration.ofSeconds(20);

    private Duration reconnectTime = Duration.ofSeconds(3);

    private int maxConnectionsPerUser = 5;

    private int replayBufferSize = 100;

    private String heartbeatEvent = "heartbeat";

    private final Cluster cluster = new Cluster();

    private final Auth auth = new Auth();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getReconnectTime() {
        return reconnectTime;
    }

    public void setReconnectTime(Duration reconnectTime) {
        this.reconnectTime = reconnectTime;
    }

    public int getMaxConnectionsPerUser() {
        return maxConnectionsPerUser;
    }

    public void setMaxConnectionsPerUser(int maxConnectionsPerUser) {
        this.maxConnectionsPerUser = maxConnectionsPerUser;
    }

    public int getReplayBufferSize() {
        return replayBufferSize;
    }

    public void setReplayBufferSize(int replayBufferSize) {
        this.replayBufferSize = replayBufferSize;
    }

    public String getHeartbeatEvent() {
        return heartbeatEvent;
    }

    public void setHeartbeatEvent(String heartbeatEvent) {
        this.heartbeatEvent = heartbeatEvent;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public Auth getAuth() {
        return auth;
    }

    public static class Cluster {

        private boolean enabled = true;

        private String topic = "modality:sse:cluster";

        private String nodeId;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public synchronized String nodeId() {
            if (StringUtils.isBlank(nodeId)) {
                nodeId = UuidUtils.uuidCompact();
            }
            return nodeId;
        }
    }

    public static class Auth {

        private String tokenParameter = "token";

        private String tokenHeader = "Authorization";

        private String tokenPrefix = "Bearer ";

        public String getTokenParameter() {
            return tokenParameter;
        }

        public void setTokenParameter(String tokenParameter) {
            this.tokenParameter = tokenParameter;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
        }

        public String getTokenPrefix() {
            return tokenPrefix;
        }

        public void setTokenPrefix(String tokenPrefix) {
            this.tokenPrefix = tokenPrefix;
        }
    }
}
