package cn.slainc.modules.auth.mfa;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

final class LocalRedis {

    static final String SKIP_REASON = "本地 127.0.0.1:6379 没有可用的 Redis，已跳过集成测试";

    private LocalRedis() {
    }

    static void assumeRedisAvailable() {
        Assumptions.assumeTrue(isRedisAvailable(), SKIP_REASON);
    }

    static boolean isRedisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
