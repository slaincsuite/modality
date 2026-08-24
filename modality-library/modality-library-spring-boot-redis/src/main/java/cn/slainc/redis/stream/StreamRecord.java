package cn.slainc.redis.stream;

import java.util.LinkedHashMap;
import java.util.Map;

public class StreamRecord {

    private String stream;

    private String group;

    private String id;

    private final Map<String, String> body = new LinkedHashMap<>();

    private long deliveryCount;

    public StreamRecord() {
    }

    public StreamRecord(String stream, String group, String id) {
        this.stream = stream;
        this.group = group;
        this.id = id;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, String> getBody() {
        return body;
    }

    public String getPayload() {
        return body.get("payload");
    }

    public long getDeliveryCount() {
        return deliveryCount;
    }

    public void setDeliveryCount(long deliveryCount) {
        this.deliveryCount = deliveryCount;
    }

    @Override
    public String toString() {
        return "流消息[流=" + stream + ", 组=" + group + ", 编号=" + id + ", 投递次数=" + deliveryCount
                + ", 内容=" + body + "]";
    }
}
