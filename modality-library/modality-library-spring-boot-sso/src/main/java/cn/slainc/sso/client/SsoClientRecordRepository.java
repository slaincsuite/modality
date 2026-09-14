package cn.slainc.sso.client;

import java.util.List;
import java.util.Optional;

public interface SsoClientRecordRepository {

    Optional<SsoClientRecord> find(String client);

    List<SsoClientRecord> list();

    SsoClientRecord insert(SsoClientRecord record);

    SsoClientRecord update(SsoClientRecord record);

    void delete(String client);
}
