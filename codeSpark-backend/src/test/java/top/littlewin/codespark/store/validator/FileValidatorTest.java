package top.littlewin.codespark.store.validator;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import top.littlewin.codespark.exception.BusinessException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FileValidator 校验逻辑测试：非空、大小上限、扩展名白名单
 * （校验独立于存储层，供调用方在进入门面前统一执行）。
 */
class FileValidatorTest {

    private final FileValidator validator = new FileValidator();

    // ---------- MultipartFile ----------

    @Test
    void validate_emptyMultipartFile_rejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThrows(BusinessException.class, () -> validator.validate(empty));
        assertThrows(BusinessException.class, () -> validator.validate((org.springframework.web.multipart.MultipartFile) null));
    }

    @Test
    void validate_multipartFile_oversize_rejected() {
        byte[] big = new byte[(int) FileValidator.MAX_FILE_SIZE + 1];
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", big);
        assertThrows(BusinessException.class, () -> validator.validate(file));
    }

    @Test
    void validate_multipartFile_invalidExt_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1});
        assertThrows(BusinessException.class, () -> validator.validate(file));
    }

    @Test
    void validate_multipartFile_valid_passed() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void validate_multipartFile_uppercaseExt_passed() {
        // 扩展名大小写不敏感
        MockMultipartFile file = new MockMultipartFile("file", "a.PNG", "image/png", new byte[]{1});
        assertDoesNotThrow(() -> validator.validate(file));
    }

    // ---------- File ----------

    @Test
    void validate_missingFile_rejected() {
        File missing = new File("tmp/not-exists.png");
        assertThrows(BusinessException.class, () -> validator.validate(missing));
        assertThrows(BusinessException.class, () -> validator.validate((File) null));
    }

    @Test
    void validate_file_oversize_rejected() throws IOException {
        Path path = Files.createTempFile("oversize", ".png");
        try {
            // 稀疏写入：快速生成超限大小文件
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path.toFile(), "rw")) {
                raf.setLength(FileValidator.MAX_FILE_SIZE + 1);
            }
            assertThrows(BusinessException.class, () -> validator.validate(path.toFile()));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void validate_file_invalidExt_rejected() throws IOException {
        Path path = Files.createTempFile("script", ".js");
        try {
            assertThrows(BusinessException.class, () -> validator.validate(path.toFile()));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void validate_file_valid_passed() throws IOException {
        Path path = Files.createTempFile("valid", ".jpg");
        try {
            Files.write(path, new byte[]{1, 2, 3});
            assertDoesNotThrow(() -> validator.validate(path.toFile()));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}