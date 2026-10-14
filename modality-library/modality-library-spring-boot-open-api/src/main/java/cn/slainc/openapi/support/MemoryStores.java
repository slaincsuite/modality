package cn.slainc.openapi.support;

import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.domain.ConsentRecord;
import cn.slainc.openapi.domain.OpenScope;
import cn.slainc.openapi.spi.AppStore;
import cn.slainc.openapi.spi.ConsentStore;
import cn.slainc.openapi.spi.CredentialStore;
import cn.slainc.openapi.spi.ScopeStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryStores {

    private MemoryStores() {
    }

    public static class InMemoryAppStore implements AppStore {

        private final Map<String, App> apps = new ConcurrentHashMap<>();

        @Override
        public App save(App app) {
            apps.put(app.getAppId(), app);
            return app;
        }

        @Override
        public Optional<App> find(String appId) {
            return Optional.ofNullable(apps.get(appId));
        }

        @Override
        public List<App> list() {
            return apps.values().stream().sorted(Comparator.comparingLong(App::getCreatedAt)).toList();
        }

        @Override
        public void delete(String appId) {
            apps.remove(appId);
        }
    }

    public static class InMemoryScopeStore implements ScopeStore {

        private final Map<String, OpenScope> scopes = new ConcurrentHashMap<>();

        @Override
        public OpenScope save(OpenScope scope) {
            scopes.put(scope.getName(), scope);
            return scope;
        }

        @Override
        public Optional<OpenScope> find(String name) {
            return Optional.ofNullable(scopes.get(name));
        }

        @Override
        public List<OpenScope> list() {
            return scopes.values().stream().sorted(Comparator.comparing(OpenScope::getName)).toList();
        }

        @Override
        public void remove(String name) {
            scopes.remove(name);
        }
    }

    public static class InMemoryConsentStore implements ConsentStore {

        private final Map<String, ConsentRecord> consents = new ConcurrentHashMap<>();

        @Override
        public ConsentRecord save(ConsentRecord record) {
            consents.put(record.getId(), record);
            return record;
        }

        @Override
        public Optional<ConsentRecord> find(String userId, String appId) {
            return consents.values().stream()
                    .filter(record -> record.getUserId().equals(userId) && record.getAppId().equals(appId))
                    .max(Comparator.comparingLong(ConsentRecord::getGrantedAt));
        }

        @Override
        public List<ConsentRecord> listByUser(String userId) {
            return consents.values().stream().filter(record -> record.getUserId().equals(userId))
                    .sorted(Comparator.comparingLong(ConsentRecord::getGrantedAt).reversed()).toList();
        }

        @Override
        public List<ConsentRecord> listByApp(String appId) {
            return consents.values().stream().filter(record -> record.getAppId().equals(appId))
                    .sorted(Comparator.comparingLong(ConsentRecord::getGrantedAt).reversed()).toList();
        }

        @Override
        public void delete(String id) {
            consents.remove(id);
        }
    }

    public static class InMemoryCredentialStore implements CredentialStore {

        private final Map<String, AppCredential> credentials = new ConcurrentHashMap<>();

        @Override
        public AppCredential save(AppCredential credential) {
            credentials.put(credential.getAppId(), credential);
            return credential;
        }

        @Override
        public Optional<AppCredential> find(String appId) {
            return Optional.ofNullable(credentials.get(appId));
        }

        @Override
        public void delete(String appId) {
            credentials.remove(appId);
        }
    }
}
