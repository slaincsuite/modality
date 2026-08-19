package cn.slainc.web.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class WebSocketTestClient implements WebSocket.Listener {

    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    private final StringBuilder buffer = new StringBuilder();

    private WebSocket socket;

    static WebSocketTestClient connect(int port, String query, String authorization) {
        WebSocketTestClient client = new WebSocketTestClient();
        WebSocket.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .newWebSocketBuilder();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        client.socket = builder.buildAsync(URI.create("ws://localhost:" + port + "/ws" + query), client).join();
        return client;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            messages.add(buffer.toString());
            buffer.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    void send(String text) {
        socket.sendText(text, true).join();
    }

    void subscribe(String topic) throws InterruptedException {
        send("{\"type\":\"subscribe\",\"topic\":\"" + topic + "\"}");
        if (awaitMessage(Duration.ofSeconds(5)) == null) {
            throw new IllegalStateException("未收到订阅确认：" + topic);
        }
    }

    String awaitMessage(Duration timeout) throws InterruptedException {
        return messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    void close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
    }
}
