package cn.slainc.storage.antivirus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ClamAvClient {

    private static final Logger log = LoggerFactory.getLogger(ClamAvClient.class);

    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] PING = "zPING\0".getBytes(StandardCharsets.US_ASCII);

    private static final int CHUNK_SIZE = 8192;

    private final String host;

    private final int port;

    private final Duration timeout;

    public ClamAvClient(String host, int port, Duration timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    public boolean ping() {
        try (Socket socket = connect()) {
            OutputStream output = socket.getOutputStream();
            output.write(PING);
            output.flush();
            String response = readResponse(socket.getInputStream());
            return response.contains("PONG");
        } catch (IOException ex) {
            log.warn("ClamAV 服务不可达 {}:{}，原因：{}", host, port, ex.getMessage());
            return false;
        }
    }

    public VirusScanResult scan(byte[] data) {
        return scan(new java.io.ByteArrayInputStream(data));
    }

    public VirusScanResult scan(InputStream data) {
        try (Socket socket = connect()) {
            socket.setSoTimeout((int) timeout.toMillis());
            OutputStream output = socket.getOutputStream();
            output.write(INSTREAM);
            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            while ((read = data.read(buffer)) > 0) {
                writeChunkLength(output, read);
                output.write(buffer, 0, read);
            }
            writeChunkLength(output, 0);
            output.flush();
            return interpret(readResponse(socket.getInputStream()));
        } catch (IOException ex) {
            log.warn("ClamAV 扫描失败 {}:{}，原因：{}", host, port, ex.getMessage());
            return VirusScanResult.unavailable("ClamAV 服务不可达：" + ex.getMessage());
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
        socket.setSoTimeout((int) timeout.toMillis());
        return socket;
    }

    private static void writeChunkLength(OutputStream output, int length) throws IOException {
        output.write(new byte[]{
                (byte) (length >>> 24),
                (byte) (length >>> 16),
                (byte) (length >>> 8),
                (byte) length});
    }

    private static String readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) >= 0) {
            if (value == 0) {
                break;
            }
            buffer.write(value);
            if (buffer.size() > 4096) {
                break;
            }
        }
        if (buffer.size() == 0) {
            throw new IOException("ClamAV 未返回应答内容");
        }
        return buffer.toString(StandardCharsets.UTF_8).trim();
    }

    private static VirusScanResult interpret(String response) {
        if (response.endsWith("OK")) {
            return VirusScanResult.clean(response);
        }
        if (response.endsWith("FOUND")) {
            String signature = response;
            int start = response.indexOf(':');
            int end = response.lastIndexOf(" FOUND");
            if (start >= 0 && end > start) {
                signature = response.substring(start + 1, end).trim();
            }
            return VirusScanResult.infected(signature, response);
        }
        if (response.contains("UNKNOWN COMMAND") || response.contains("INSTREAM size limit exceeded")) {
            return VirusScanResult.unavailable("ClamAV 拒绝 INSTREAM 请求：" + response);
        }
        return VirusScanResult.unavailable("ClamAV 返回无法识别的应答：" + response);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }
}
