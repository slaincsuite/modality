package cn.slainc.storage.metadata;

import cn.slainc.storage.spi.StorageMetadata;

import java.util.Optional;

public interface MetadataStore {

    void save(String key, StorageMetadata metadata);

    Optional<StorageMetadata> load(String key);

    void delete(String key);

    default boolean internal(String key) {
        return false;
    }

    MetadataStore NOOP = new MetadataStore() {

        @Override
        public void save(String key, StorageMetadata metadata) {
        }

        @Override
        public Optional<StorageMetadata> load(String key) {
            return Optional.empty();
        }

        @Override
        public void delete(String key) {
        }
    };
}
