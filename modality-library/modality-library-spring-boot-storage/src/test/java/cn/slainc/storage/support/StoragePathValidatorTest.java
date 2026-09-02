package cn.slainc.storage.support;

import cn.slainc.storage.StorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoragePathValidatorTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("上级目录引用被拒绝")
    void rejectsParentTraversal() {
        assertThatThrownBy(() -> StoragePathValidator.normalize("../../etc/passwd"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("上级目录引用");
        assertThatThrownBy(() -> StoragePathValidator.normalize("a/../../etc/passwd"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("上级目录引用");
        assertThatThrownBy(() -> StoragePathValidator.normalize("a/b/.."))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("上级目录引用");
    }

    @Test
    @DisplayName("绝对路径与 Windows 盘符被拒绝")
    void rejectsAbsolutePath() {
        assertThatThrownBy(() -> StoragePathValidator.normalize("/etc/passwd"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("绝对路径");
        assertThatThrownBy(() -> StoragePathValidator.normalize("C:/Windows/win.ini"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("盘符");
        assertThatThrownBy(() -> StoragePathValidator.normalize("\\\\server\\share\\a.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("反斜杠");
    }

    @Test
    @DisplayName("空字节、空路径段、当前目录引用被拒绝")
    void rejectsNulAndEmptySegments() {
        assertThatThrownBy(() -> StoragePathValidator.normalize("a\0b"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("空字节");
        assertThatThrownBy(() -> StoragePathValidator.normalize("a//b"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("空路径段");
        assertThatThrownBy(() -> StoragePathValidator.normalize("./a"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("当前目录引用");
        assertThatThrownBy(() -> StoragePathValidator.normalize("   "))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("百分号编码的穿越路径同样被拒绝")
    void rejectsEncodedTraversal() {
        assertThatThrownBy(() -> StoragePathValidator.normalize("%2e%2e/%2e%2e/etc/passwd"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("上级目录引用");
        assertThatThrownBy(() -> StoragePathValidator.normalize("a%2F..%2Fb"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("上级目录引用");
    }

    @Test
    @DisplayName("正常路径返回原值并解析到根目录之内")
    void acceptsNormalKey() {
        assertThat(StoragePathValidator.normalize("tenant/2026/02/13/a.png")).isEqualTo("tenant/2026/02/13/a.png");
        Path resolved = StoragePathValidator.resolve(root, "tenant/a.png");
        assertThat(resolved.startsWith(root.toAbsolutePath().normalize())).isTrue();
    }

    @Test
    @DisplayName("符号链接被拒绝访问")
    void rejectsSymbolicLink() throws IOException {
        Path outside = Files.createDirectories(root.getParent().resolve("outside-" + System.nanoTime()));
        Files.writeString(outside.resolve("secret.txt"), "机密");
        Path link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException ex) {
            return;
        }
        assertThat(StoragePathValidator.isUnsafeSymbolicLink(root, link.resolve("secret.txt"))).isTrue();
        assertThatThrownBy(() -> StoragePathValidator.assertNoSymbolicLink(root, link.resolve("secret.txt")))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("符号链接");
    }
}
