package cn.slainc.excel.guard;

import cn.slainc.excel.UserRow;
import cn.slainc.excel.UserStatus;
import cn.slainc.excel.converter.DictRegistry;
import cn.slainc.excel.format.ExcelFormat;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbookSecurityTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        DictRegistry.clear();
        DictRegistry.registerEnum("user_status", UserStatus.class);
    }

    @Test
    void encryptsWorkbookAndRejectsWrongPassword() throws Exception {
        Path plain = plainWorkbook();
        Path encrypted = tempDir.resolve("encrypted.xlsx");

        WorkbookSecurity.encrypt(plain, encrypted, "Secr3t!");

        assertTrue(WorkbookSecurity.isEncrypted(encrypted));
        assertTrue(WorkbookSecurity.verifyPassword(encrypted, "Secr3t!"));
        assertFalse(WorkbookSecurity.verifyPassword(encrypted, "错误密码"));

        List<UserRow> rows = WorkbookSecurity.read(encrypted, "Secr3t!", UserRow.class);
        assertEquals(2, rows.size());
        assertEquals("张三", rows.get(0).getName());

        assertThrows(IllegalArgumentException.class, () -> WorkbookSecurity.open(encrypted, "错误密码"));
        assertThrows(Exception.class, () -> WorkbookFactory.create(encrypted.toFile(), "错误密码"));

        try (Workbook workbook = WorkbookSecurity.open(encrypted, "Secr3t!")) {
            assertEquals("张三", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void appliesEditPasswordAndReadOnlyProtection() throws Exception {
        Path file = plainWorkbook();

        WorkbookSecurity.apply(file, SecurityOptions.defaults().editPassword("编辑密码").readOnlyRecommended(true));

        assertFalse(WorkbookSecurity.isEncrypted(file));
        assertTrue(WorkbookSecurity.isReadOnlyRecommended(file));
        assertTrue(WorkbookSecurity.validateEditPassword(file, "编辑密码"));
        assertFalse(WorkbookSecurity.validateEditPassword(file, "其它密码"));
        try (Workbook workbook = WorkbookSecurity.open(file, null)) {
            assertTrue(((XSSFWorkbook) workbook).isStructureLocked(), "应锁定工作簿结构");
        }
    }

    @Test
    void combinesOpenPasswordAndEditPassword() throws Exception {
        Path file = plainWorkbook();

        WorkbookSecurity.apply(file, SecurityOptions.defaults().openPassword("打开密码").editPassword("编辑密码"));
        assertTrue(WorkbookSecurity.isEncrypted(file));
        assertTrue(WorkbookSecurity.verifyPassword(file, "打开密码"));

        byte[] decrypted = WorkbookSecurity.decrypt(file, "打开密码");
        assertEquals(ExcelFormat.XLSX, ExcelFormat.detect(decrypted));
        assertTrue(new String(decrypted, 0, 2, StandardCharsets.ISO_8859_1).startsWith("PK"));

        assertThrows(IllegalArgumentException.class, () -> WorkbookSecurity.decrypt(file, "错的密码"));
        WorkbookSecurity.apply(file, SecurityOptions.ofOpenPassword("打开密码").editPassword("新密码"));
        assertTrue(WorkbookSecurity.validateEditPassword(file, "打开密码", "新密码"));
        assertTrue(WorkbookSecurity.isReadOnlyRecommended(file, "打开密码") == false);
    }

    @Test
    void validatesSecurityInput() throws Exception {
        Path plain = plainWorkbook();
        assertThrows(IllegalArgumentException.class, () -> WorkbookSecurity.encrypt(plain, tempDir.resolve("a.xlsx"), " "));
        assertThrows(IllegalArgumentException.class, () -> WorkbookSecurity.apply(plain, SecurityOptions.defaults()));
        assertThrows(IllegalStateException.class,
                () -> WorkbookSecurity.verifyPassword(plain, "任意密码"));
        assertFalse(WorkbookSecurity.isEncrypted(tempDir.resolve("not-exists.xlsx")));
    }

    private Path plainWorkbook() {
        Path file = tempDir.resolve("plain-" + System.nanoTime() + ".xlsx");
        cn.slainc.excel.ExcelUtils.write(file, UserRow.class,
                List.of(new UserRow("张三", 20, UserStatus.ACTIVE), new UserRow("李四", 30, UserStatus.LOCKED)), "用户");
        return file;
    }
}
