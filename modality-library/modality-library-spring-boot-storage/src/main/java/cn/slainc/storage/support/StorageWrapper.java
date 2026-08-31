package cn.slainc.storage.support;

import cn.slainc.storage.ExtendedFileStorage;

@FunctionalInterface
public interface StorageWrapper {

    ExtendedFileStorage wrap(ExtendedFileStorage delegate);
}
