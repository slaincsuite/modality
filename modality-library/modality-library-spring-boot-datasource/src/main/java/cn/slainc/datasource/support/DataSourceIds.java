package cn.slainc.datasource.support;

import cn.slainc.utils.id.SnowflakeIdGenerator;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class DataSourceIds {

    private static final SnowflakeIdGenerator GENERATOR = new SnowflakeIdGenerator(workerId());

    private DataSourceIds() {
    }

    public static long nextId() {
        return GENERATOR.nextId();
    }

    private static long workerId() {
        try {
            return Math.floorMod(InetAddress.getLocalHost().getHostName().hashCode(), 32);
        } catch (UnknownHostException exception) {
            return Math.floorMod(System.getProperty("user.name", "modality").hashCode(), 32);
        }
    }
}
