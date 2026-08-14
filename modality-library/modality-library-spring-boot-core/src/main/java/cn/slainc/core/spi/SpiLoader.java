package cn.slainc.core.spi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public final class SpiLoader {

    private SpiLoader() {
    }

    public static <T> List<T> loadAll(Class<T> type) {
        return loadAll(type, Thread.currentThread().getContextClassLoader());
    }

    public static <T> List<T> loadAll(Class<T> type, ClassLoader classLoader) {
        if (type == null) {
            return new ArrayList<>();
        }
        ClassLoader loader = classLoader == null ? type.getClassLoader() : classLoader;
        List<T> extensions = new ArrayList<>();
        for (T extension : ServiceLoader.load(type, loader)) {
            if (!(extension instanceof ExtensionPoint point) || point.enabled()) {
                extensions.add(extension);
            }
        }
        extensions.sort(Comparator.comparingInt(SpiLoader::orderOf));
        return extensions;
    }

    public static <T> Optional<T> loadFirst(Class<T> type) {
        List<T> extensions = loadAll(type);
        return extensions.isEmpty() ? Optional.empty() : Optional.of(extensions.get(0));
    }

    public static <T> Optional<T> loadFirst(Class<T> type, ClassLoader classLoader) {
        List<T> extensions = loadAll(type, classLoader);
        return extensions.isEmpty() ? Optional.empty() : Optional.of(extensions.get(0));
    }

    private static int orderOf(Object extension) {
        return extension instanceof ExtensionPoint point ? point.order() : 0;
    }
}
