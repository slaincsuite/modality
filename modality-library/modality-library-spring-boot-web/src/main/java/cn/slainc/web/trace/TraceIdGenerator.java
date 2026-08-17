package cn.slainc.web.trace;

import cn.slainc.utils.id.SnowflakeIdGenerator;
import cn.slainc.utils.random.UuidUtils;

@FunctionalInterface
public interface TraceIdGenerator {

    TraceIdGenerator UUID_GENERATOR = () -> UuidUtils.uuidCompact().toLowerCase();

    TraceIdGenerator SNOWFLAKE_GENERATOR = new TraceIdGenerator() {

        private final SnowflakeIdGenerator delegate = new SnowflakeIdGenerator(1L, 0L);

        @Override
        public String generate() {
            return Long.toString(delegate.nextId());
        }
    };

    String generate();
}
