package cn.slainc.storage.antivirus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public class FakeClamAvServer implements AutoCloseable {

    private final ServerSocket serverSocket;

    private final String response;

    private final AtomicLong scannedBytes = new AtomicLong();

    private final Thread thread;

    private volatile boolean running = true;

    public FakeClamAvServer(String response) throws IOException {
        this.response = response;
        this.serverSocket = new ServerSocket(0);
        this.thread = new Thread(this::serve, "fake-clamav");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public long scannedBytes() {
        return scannedBytes.get();
    }

    private void serve() {
        while (running) {
            try (Socket socket = serverSocket.accept()) {
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                String command = readCommand(input);
                if (command.startsWith("zINSTREAM")) {
                    while (true) {
                        int length = readInt(input);
                        if (length == 0) {
                            break;
                        }
                        byte[] buffer = new byte[length];
                        int read = 0;
                        while (read < length) {
                            int value = input.read(buffer, read, length - read);
                            if (value < 0) {
                                break;
                            }
                            read += value;
                        }
                        scannedBytes.addAndGet(read);
                    }
                }
                output.write(response.getBytes(StandardCharsets.US_ASCII));
                output.write(0);
                output.flush();
            } catch (IOException ex) {
                if (running) {
                    throw new IllegalStateException(ex);
                }
            }
        }
    }

    private static String readCommand(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        int value;
        while ((value = input.read()) > 0) {
            builder.append((char) value);
        }
        return builder.toString();
    }

    private static int readInt(InputStream input) throws IOException {
        int first = input.read();
        int second = input.read();
        int third = input.read();
        int fourth = input.read();
        if (fourth < 0) {
            return 0;
        }
        return (first << 24) | (second << 16) | (third << 8) | fourth;
    }

    @Override
    public void close() throws IOException {
        running = false;
        serverSocket.close();
    }
}
