package cn.slainc.logger.trace;

import cn.slainc.utils.id.SnowflakeIdGenerator;
import cn.slainc.utils.random.UuidUtils;

@FunctionalInterface
public interface SpanIdGenerator {

    SpanIdGenerator UUID_GENERATOR = () -> UuidUtils.uuidV7Compact();

    SpanIdGenerator SNOWFLAKE_GENERATOR = new SpanIdGenerator() {

        private final SnowflakeIdGenerator delegate = new SnowflakeIdGenerator(2L, 0L);

        @Override
        public String generate() {
            return Long.toString(delegate.nextId(), 36);
        }
    };

    String generate();

    static SpanIdGenerator of(String style) {
        return "snowflake".equalsIgnoreCase(style) ? SNOWFLAKE_GENERATOR : UUID_GENERATOR;
    }
}
