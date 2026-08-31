package cn.slainc.storage.support;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.antivirus.AntivirusService;
import cn.slainc.storage.antivirus.ScanningFileStorage;

public class AntivirusStorageWrapper implements StorageWrapper {

    private final AntivirusService antivirusService;

    public AntivirusStorageWrapper(AntivirusService antivirusService) {
        this.antivirusService = antivirusService;
    }

    @Override
    public ExtendedFileStorage wrap(ExtendedFileStorage delegate) {
        return new ScanningFileStorage(delegate, antivirusService);
    }
}
