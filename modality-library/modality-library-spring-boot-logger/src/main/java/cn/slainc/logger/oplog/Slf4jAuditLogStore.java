package cn.slainc.logger.oplog;

import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jAuditLogStore implements AuditLogStore {

    public static final String AUDIT_LOGGER_NAME = "AUDIT";

    private static final Logger log = LoggerFactory.getLogger(AUDIT_LOGGER_NAME);

    @Override
    public void save(AuditRecord record) {
        if (record == null) {
            return;
        }
        AuditLevel level = record.level() == null ? AuditLevel.INFO : record.level();
        String payload = JsonUtils.toJson(record.toMap());
        switch (level) {
            case CRITICAL -> log.error(payload);
            case ERROR -> log.error(payload);
            case WARN -> log.warn(payload);
            default -> log.info(payload);
        }
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }
}
