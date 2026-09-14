package cn.slainc.session;

import cn.slainc.core.event.DomainEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestEventCollector {

    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

    @EventListener
    public void onDomainEvent(DomainEvent event) {
        events.add(event);
    }

    public <T> List<T> of(Class<T> type) {
        List<T> matched = new ArrayList<>();
        for (DomainEvent event : events) {
            if (type.isInstance(event)) {
                matched.add(type.cast(event));
            }
        }
        return matched;
    }

    public List<DomainEvent> all() {
        return new ArrayList<>(events);
    }

    public void clear() {
        events.clear();
    }
}
