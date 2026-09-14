package cn.slainc.oauth2.client;

import java.util.List;
import java.util.Optional;

public interface ClientRecordRepository {

    Optional<Oauth2ClientRecord> find(String clientId);

    List<Oauth2ClientRecord> list();

    Oauth2ClientRecord insert(Oauth2ClientRecord record);

    Oauth2ClientRecord update(Oauth2ClientRecord record);

    void delete(String clientId);
}
