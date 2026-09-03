package cn.slainc.monitor.health;

import org.springframework.boot.health.contributor.Health;

public interface BusinessHealthCheck {

    String name();

    Health health();
}
