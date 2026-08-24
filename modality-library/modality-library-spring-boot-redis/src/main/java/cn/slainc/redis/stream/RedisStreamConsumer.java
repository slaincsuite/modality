package cn.slainc.redis.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class RedisStreamConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConsumer.class);

    private final RedisStreamTemplate streamTemplate;

    private final String stream;

    private final String group;

    private final String consumerName;

    private final Consumer<StreamRecord> handler;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread worker;

    public RedisStreamConsumer(RedisStreamTemplate streamTemplate, String stream, String group, String consumerName,
                               Consumer<StreamRecord> handler) {
        if (streamTemplate == null) {
            throw new IllegalArgumentException("RedisStreamTemplate 不能为空");
        }
        if (handler == null) {
            throw new IllegalArgumentException("流消息处理器不能为空");
        }
        this.streamTemplate = streamTemplate;
        this.stream = stream;
        this.group = group;
        this.consumerName = consumerName;
        this.handler = handler;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        streamTemplate.ensureGroup(stream, group);
        worker = new Thread(this::loop, "modality-redis-stream-" + stream);
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        while (running.get()) {
            try {
                List<StreamRecord> records = streamTemplate.consume(stream, group, consumerName,
                        streamTemplate.getBatchSize(), Duration.ofSeconds(2));
                for (StreamRecord record : records) {
                    handle(record);
                }
            } catch (Exception exception) {
                if (running.get()) {
                    log.warn("流消息消费异常，流={}，原因={}", stream, exception.getMessage());
                    sleep();
                }
            }
        }
    }

    private void handle(StreamRecord record) {
        try {
            handler.accept(record);
            streamTemplate.ack(stream, group, record.getId());
        } catch (Exception exception) {
            log.warn("流消息处理失败，流={}，编号={}，原因={}", stream, record.getId(), exception.getMessage());
            streamTemplate.retry(stream, group, record);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(500L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }
}
