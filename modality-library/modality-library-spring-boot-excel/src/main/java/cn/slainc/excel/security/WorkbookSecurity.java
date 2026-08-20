package cn.slainc.excel.security;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.excel.format.ExcelFormat;
import cn.slainc.excel.support.WorkbookAccess;
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTFileSharing;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

public final class WorkbookSecurity {

    private WorkbookSecurity() {
    }

    public static boolean isEncrypted(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(file.toFile())) {
            return fileSystem.getRoot().hasEntry(EncryptionInfo.ENCRYPTION_INFO_ENTRY);
        } catch (IOException | RuntimeException ex) {
            // 非 OLE2 容器（未加密的 .xlsx 或非工作簿文件）直接判定为未加密
            return false;
        }
    }

    public static void encrypt(Path source, Path target, String password) {
        requirePassword(password);
        byte[] encrypted = encrypt(WorkbookAccess.readAll(source), password);
        try {
            Files.write(target, encrypted);
        } catch (IOException ex) {
            throw new IllegalStateException("写出加密工作簿失败: " + target, ex);
        }
    }

    public static byte[] encrypt(byte[] source, String password) {
        requirePassword(password);
        if (source == null || source.length == 0) {
            throw new IllegalArgumentException("工作簿内容不能为空");
        }
        if (ExcelFormat.detect(source) == ExcelFormat.XLS) {
            return encryptLegacy(source, password);
        }
        try (POIFSFileSystem fileSystem = new POIFSFileSystem()) {
            EncryptionInfo encryptionInfo = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = encryptionInfo.getEncryptor();
            encryptor.confirmPassword(password);
            try (OPCPackage opcPackage = OPCPackage.open(new ByteArrayInputStream(source))) {
                try (OutputStream dataStream = encryptor.getDataStream(fileSystem)) {
                    opcPackage.save(dataStream);
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            fileSystem.writeFilesystem(outputStream);
            return outputStream.toByteArray();
        } catch (IOException | GeneralSecurityException | InvalidFormatException ex) {
            throw new IllegalStateException("加密工作簿失败", ex);
        }
    }

    public static boolean verifyPassword(Path file, String password) {
        if (!isEncrypted(file)) {
            throw new IllegalStateException("工作簿未加密: " + file);
        }
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(file.toFile())) {
            Decryptor decryptor = Decryptor.getInstance(new EncryptionInfo(fileSystem));
            return decryptor.verifyPassword(password == null ? "" : password);
        } catch (IOException ex) {
            throw new IllegalStateException("校验打开密码失败: " + file, ex);
        } catch (GeneralSecurityException ex) {
            return false;
        }
    }

    public static byte[] decrypt(Path file, String password) {
        if (!isEncrypted(file)) {
            return WorkbookAccess.readAll(file);
        }
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(file.toFile())) {
            Decryptor decryptor = Decryptor.getInstance(new EncryptionInfo(fileSystem));
            if (!decryptor.verifyPassword(password == null ? "" : password)) {
                throw new IllegalArgumentException("打开密码错误，无法解密工作簿: " + file);
            }
            try (InputStream dataStream = decryptor.getDataStream(fileSystem)) {
                return dataStream.readAllBytes();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("解密工作簿失败: " + file, ex);
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("打开密码错误，无法解密工作簿: " + file, ex);
        }
    }

    public static Workbook open(Path file, String password) {
        if (!isEncrypted(file)) {
            return WorkbookAccess.open(file);
        }
        return WorkbookAccess.open(decrypt(file, password));
    }

    public static <T> List<T> read(Path file, String password, Class<T> head) {
        return ExcelUtils.read(new ByteArrayInputStream(decrypt(file, password)), head);
    }

    public static void apply(Path file, SecurityOptions options) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("安全选项不能为空");
        }
        boolean encrypted = isEncrypted(file);
        if (encrypted && blank(options.getOpenPassword())) {
            throw new IllegalArgumentException("工作簿已加密，必须提供打开密码: " + file);
        }
        byte[] plain = encrypted ? decrypt(file, options.getOpenPassword()) : WorkbookAccess.readAll(file);
        byte[] protectedBytes = withProtection(plain, options);
        byte[] output = blank(options.getOpenPassword())
                ? protectedBytes
                : encrypt(protectedBytes, options.getOpenPassword());
        try {
            Files.write(file, output);
        } catch (IOException ex) {
            throw new IllegalStateException("保存受保护的工作簿失败: " + file, ex);
        }
    }

    public static boolean validateEditPassword(Path file, String password) {
        return validateEditPassword(file, null, password);
    }

    public static boolean validateEditPassword(Path file, String openPassword, String password) {
        try (Workbook workbook = open(file, openPassword)) {
            if (workbook instanceof XSSFWorkbook xssfWorkbook) {
                return xssfWorkbook.validateWorkbookPassword(password);
            }
            return false;
        } catch (IOException ex) {
            throw new IllegalStateException("校验编辑密码失败: " + file, ex);
        }
    }

    public static boolean isReadOnlyRecommended(Path file) {
        return isReadOnlyRecommended(file, null);
    }

    public static boolean isReadOnlyRecommended(Path file, String openPassword) {
        try (Workbook workbook = open(file, openPassword)) {
            if (workbook instanceof XSSFWorkbook xssfWorkbook) {
                CTWorkbook ctWorkbook = xssfWorkbook.getCTWorkbook();
                return ctWorkbook.isSetFileSharing() && ctWorkbook.getFileSharing().getReadOnlyRecommended();
            }
            return false;
        } catch (IOException ex) {
            throw new IllegalStateException("读取只读保护失败: " + file, ex);
        }
    }

    private static byte[] withProtection(byte[] plain, SecurityOptions options) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(plain))) {
            if (workbook instanceof XSSFWorkbook xssfWorkbook) {
                if (!blank(options.getEditPassword())) {
                    if (xssfWorkbook.getCTWorkbook().isSetWorkbookProtection()) {
                        // POI 不会覆盖已存在的编辑密码，先清空保护再重设
                        xssfWorkbook.unLock();
                    }
                    xssfWorkbook.setWorkbookPassword(options.getEditPassword(), HashAlgorithm.sha512);
                    if (options.isLockStructure()) {
                        xssfWorkbook.lockStructure();
                    }
                }
                if (options.isReadOnlyRecommended()) {
                    CTWorkbook ctWorkbook = xssfWorkbook.getCTWorkbook();
                    CTFileSharing fileSharing = ctWorkbook.isSetFileSharing()
                            ? ctWorkbook.getFileSharing()
                            : ctWorkbook.addNewFileSharing();
                    fileSharing.setReadOnlyRecommended(true);
                }
            } else if (workbook instanceof HSSFWorkbook hssfWorkbook && !blank(options.getEditPassword())) {
                hssfWorkbook.writeProtectWorkbook(options.getEditPassword(), "modality");
            }
            return WorkbookAccess.bytes(workbook);
        } catch (IOException ex) {
            throw new IllegalStateException("设置工作簿保护失败", ex);
        }
    }

    private static byte[] encryptLegacy(byte[] source, String password) {
        try {
            Biff8EncryptionKey.setCurrentUserPassword(password);
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(source))) {
                return WorkbookAccess.bytes(workbook);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("加密 .xls 工作簿失败", ex);
        } finally {
            Biff8EncryptionKey.setCurrentUserPassword(null);
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

}
