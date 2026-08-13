package cn.slainc.utils.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoAndFileUtilsTest {

    @Test
    void streamReading() {
        byte[] bytes = "hello 中文".getBytes(StandardCharsets.UTF_8);
        assertEquals("hello 中文", IoUtils.readToString(new ByteArrayInputStream(bytes)));
        assertEquals(bytes.length, IoUtils.readBytes(new ByteArrayInputStream(bytes)).length);
        assertTrue(IoUtils.toBuffered(new ByteArrayInputStream(bytes)) != null);
    }

    @Test
    void charsetDetection() {
        assertEquals(StandardCharsets.UTF_8, IoUtils.detectCharset("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals(StandardCharsets.UTF_8,
                IoUtils.detectCharset(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'}));
        assertEquals(StandardCharsets.UTF_16LE, IoUtils.detectCharset(new byte[]{(byte) 0xFF, (byte) 0xFE}));
        assertEquals(StandardCharsets.UTF_16BE, IoUtils.detectCharset(new byte[]{(byte) 0xFE, (byte) 0xFF}));
        assertEquals(StandardCharsets.UTF_8, IoUtils.detectCharset(new byte[0]));
        assertEquals(StandardCharsets.UTF_8, IoUtils.detectCharset("中文".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void extensionsAndMime() {
        assertEquals("txt", FileUtils.extension("a/b/c.txt"));
        assertEquals("", FileUtils.extension("noextension"));
        assertEquals("archive", FileUtils.baseName("/tmp/archive.tar"));
        assertTrue(FileUtils.hasExtension("a.PNG", "png"));
        assertFalse(FileUtils.hasExtension("a.png", "jpg"));
        assertEquals("application/json", FileUtils.mimeTypeByExtension("a.json"));
        assertEquals("image/png", FileUtils.mimeTypeByMagicNumber(
                new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertEquals("application/pdf", FileUtils.mimeTypeByMagicNumber(
                new byte[]{'%', 'P', 'D', 'F'}));
        assertEquals("image/png", FileUtils.mimeType(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, "a.bin"));
        assertEquals("1 KB", FileUtils.humanReadableSize(1024));
    }

    @Test
    void fileRoundTrip(@TempDir Path tempDir) {
        Path file = tempDir.resolve("sub/dir/sample.txt");
        FileUtils.writeText(file, "content 中文");
        assertEquals("content 中文", FileUtils.readText(file));
        assertEquals(FileUtils.readText(file).getBytes(StandardCharsets.UTF_8).length, FileUtils.size(file));
        assertTrue(FileUtils.md5(file).length() == 32);
        assertTrue(FileUtils.sha256(file).length() == 64);

        Path copy = tempDir.resolve("copy.txt");
        FileUtils.copy(file, copy);
        assertTrue(java.nio.file.Files.exists(copy));

        assertTrue(FileUtils.delete(tempDir.resolve("sub")));
        assertFalse(java.nio.file.Files.exists(tempDir.resolve("sub")));
        assertFalse(FileUtils.deleteQuietly(tempDir.resolve("missing")));
    }
}
