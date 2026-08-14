package cn.slainc.core.spi;

public interface ExtensionPoint {

    default String name() {
        return getClass().getName();
    }

    default int order() {
        return 0;
    }

    default boolean enabled() {
        return true;
    }
}
