package cn.slainc.utils.collection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class CollectionUtils {

    private CollectionUtils() {
    }

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    public static boolean isAnyEmpty(Collection<?>... collections) {
        if (collections == null || collections.length == 0) {
            return true;
        }
        for (Collection<?> collection : collections) {
            if (isEmpty(collection)) {
                return true;
            }
        }
        return false;
    }

    public static <T> List<T> emptyIfNull(Collection<T> collection) {
        return collection == null ? new ArrayList<>() : new ArrayList<>(collection);
    }

    public static <T, K> Map<K, List<T>> groupBy(Collection<T> collection, Function<T, K> classifier) {
        Map<K, List<T>> result = new LinkedHashMap<>();
        if (isEmpty(collection)) {
            return result;
        }
        for (T item : collection) {
            result.computeIfAbsent(classifier.apply(item), key -> new ArrayList<>()).add(item);
        }
        return result;
    }

    public static <T, K> List<T> distinctBy(Collection<T> collection, Function<T, K> keyExtractor) {
        List<T> result = new ArrayList<>();
        if (isEmpty(collection)) {
            return result;
        }
        Set<K> seen = new LinkedHashSet<>();
        for (T item : collection) {
            if (seen.add(keyExtractor.apply(item))) {
                result.add(item);
            }
        }
        return result;
    }

    public static <T> List<T> distinct(Collection<T> collection) {
        return isEmpty(collection) ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(collection));
    }

    public static <T> List<T> union(Collection<T> left, Collection<T> right) {
        Set<T> result = new LinkedHashSet<>();
        if (left != null) {
            result.addAll(left);
        }
        if (right != null) {
            result.addAll(right);
        }
        return new ArrayList<>(result);
    }

    public static <T> List<T> intersection(Collection<T> left, Collection<T> right) {
        List<T> result = new ArrayList<>();
        if (isEmpty(left) || isEmpty(right)) {
            return result;
        }
        Set<T> lookup = new LinkedHashSet<>(right);
        for (T item : left) {
            if (lookup.contains(item) && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    public static <T> List<T> difference(Collection<T> left, Collection<T> right) {
        List<T> result = new ArrayList<>();
        if (isEmpty(left)) {
            return result;
        }
        Set<T> lookup = right == null ? Collections.emptySet() : new LinkedHashSet<>(right);
        for (T item : left) {
            if (!lookup.contains(item) && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    public static <T> List<List<T>> partition(Collection<T> collection, int size) {
        List<List<T>> result = new ArrayList<>();
        if (isEmpty(collection) || size <= 0) {
            return result;
        }
        List<T> current = new ArrayList<>(size);
        for (T item : collection) {
            current.add(item);
            if (current.size() == size) {
                result.add(current);
                current = new ArrayList<>(size);
            }
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    public static <T> List<T> flatten(Collection<? extends Collection<T>> nested) {
        List<T> result = new ArrayList<>();
        if (isEmpty(nested)) {
            return result;
        }
        for (Collection<T> inner : nested) {
            if (inner != null) {
                result.addAll(inner);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T deepCopy(T source) {
        if (source == null) {
            return null;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(source);
            out.flush();
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                return (T) in.readObject();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("深拷贝失败，集合元素必须实现 Serializable", ex);
        }
    }

    public static <T> T first(Collection<T> collection) {
        return isEmpty(collection) ? null : collection.iterator().next();
    }

    public static <T> T last(List<T> list) {
        return isEmpty(list) ? null : list.get(list.size() - 1);
    }

    public static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    public static <T> List<T> concat(Collection<T> left, Collection<T> right) {
        List<T> result = new ArrayList<>();
        if (left != null) {
            result.addAll(left);
        }
        if (right != null) {
            result.addAll(right);
        }
        return result;
    }
}
