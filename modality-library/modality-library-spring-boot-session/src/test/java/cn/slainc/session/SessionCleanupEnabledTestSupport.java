package cn.slainc.session;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"modality.session.cleanup.enabled=true", "modality.session.cleanup.interval-minutes=30"})
public abstract class SessionCleanupEnabledTestSupport extends SessionTestSupport {
}
