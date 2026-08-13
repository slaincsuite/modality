package cn.slainc.utils.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class IoUtils {

    private static final int BUFFER_SIZE = 8192;

    private IoUtils() {
    }

    public static byte[] readBytes(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(inputStream, output);
        return output.toByteArray();
    }

    public static String readToString(InputStream inputStream) {
        return readToString(inputStream, StandardCharsets.UTF_8);
    }

    public static String readToString(InputStream inputStream, Charset charset) {
        byte[] bytes = readBytes(inputStream);
        return bytes == null ? null : new String(bytes, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    public static long copy(InputStream inputStream, OutputStream outputStream) {
        if (inputStream == null || outputStream == null) {
            return 0L;
        }
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0L;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                total += read;
            }
            outputStream.flush();
            return total;
        } catch (IOException ex) {
            throw new IllegalStateException("流拷贝失败", ex);
        }
    }

    public static BufferedInputStream toBuffered(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        return inputStream instanceof BufferedInputStream buffered ? buffered : new BufferedInputStream(inputStream);
    }

    public static BufferedOutputStream toBuffered(OutputStream outputStream) {
        if (outputStream == null) {
            return null;
        }
        return outputStream instanceof BufferedOutputStream buffered ? buffered : new BufferedOutputStream(outputStream);
    }

    public static void closeQuietly(Closeable... closeables) {
        if (closeables == null) {
            return;
        }
        for (Closeable closeable : closeables) {
            if (closeable == null) {
                continue;
            }
            try {
                closeable.close();
            } catch (IOException ignored) {
                continue;
            }
        }
    }

    public static Charset detectCharset(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return StandardCharsets.UTF_8;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (isValidUtf8(bytes)) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName("GBK");
    }

    private static boolean isValidUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            int extra;
            if (b < 0x80) {
                extra = 0;
            } else if (b >= 0xC2 && b <= 0xDF) {
                extra = 1;
            } else if (b >= 0xE0 && b <= 0xEF) {
                extra = 2;
            } else if (b >= 0xF0 && b <= 0xF4) {
                extra = 3;
            } else {
                return false;
            }
            if (i + extra >= bytes.length) {
                return false;
            }
            for (int j = 1; j <= extra; j++) {
                if ((bytes[i + j] & 0xC0) != 0x80) {
                    return false;
                }
            }
            i += extra + 1;
        }
        return true;
    }
}
