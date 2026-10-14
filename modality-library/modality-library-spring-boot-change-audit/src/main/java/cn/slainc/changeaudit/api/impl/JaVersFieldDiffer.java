package cn.slainc.changeaudit.api.impl;

import cn.slainc.changeaudit.api.FieldDiffEngine;
import cn.slainc.changeaudit.support.FieldChange;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Change;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.PropertyChange;
import org.javers.core.diff.changetype.ValueChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JaVersFieldDiffer implements FieldDiffEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(JaVersFieldDiffer.class);

    private final FieldDiffer fallback;

    private final Map<Class<?>, Javers> javersByType = new ConcurrentHashMap<>();

    private volatile boolean warned;

    private volatile boolean fellBack;

    public JaVersFieldDiffer(FieldDiffer fallback) {
        this.fallback = fallback == null ? new FieldDiffer(null, null) : fallback;
    }

    @Override
    public List<FieldChange> diff(Object before, Object after) {
        Class<?> type = before != null ? before.getClass() : (after == null ? null : after.getClass());
        if (type == null) {
            return List.of();
        }
        try {
            Diff diff = javers(type).compare(before, after);
            List<FieldChange> changes = new ArrayList<>();
            for (Change change : diff.getChanges()) {
                if (change instanceof ValueChange valueChange && valueChange.getPropertyName() != null) {
                    String name = valueChange.getPropertyName();
                    if (ignored(name)) {
                        continue;
                    }
                    changes.add(new FieldChange(name, text(valueChange.getLeft()), text(valueChange.getRight())));
                } else if (change instanceof PropertyChange propertyChange && propertyChange.getPropertyName() != null) {
                    String name = propertyChange.getPropertyName();
                    if (ignored(name)) {
                        continue;
                    }
                    changes.add(new FieldChange(name, null, propertyChange.getChangeType().name()));
                }
            }
            return changes;
        } catch (RuntimeException e) {
            fellBack = true;
            if (!warned) {
                warned = true;
                LOGGER.warn("JaVers 差异引擎不可用（{}），已回退到内置引擎；如需完全启用请为实体注册 JaVers 映射",
                        e.getMessage());
            }
            return fallback.diff(before, after);
        }
    }

    @Override
    public String serialize(List<FieldChange> changes) {
        return fallback.serialize(changes);
    }

    boolean fellBack() {
        return fellBack;
    }

    private Javers javers(Class<?> type) {
        return javersByType.computeIfAbsent(type,
                key -> JaversBuilder.javers().registerValueObject(key).build());
    }

    private boolean ignored(String name) {
        return fallback.ignoredField(name);
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        int max = fallback.maxValueLength();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
