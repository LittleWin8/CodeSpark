package top.littlewin.codespark.store.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.model.enums.FileBizEnum;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StorageExecutor 编排逻辑测试：
 * - 保存：OSS 优先，异常 / 空结果 / 不可用时自动降级本地，全部失败返回 null；
 * - 路由：URL 解析 / 删除按存储标识前缀路由到归属策略；
 * - 读取：依次尝试策略，返回第一个可访问资源。
 */
@ExtendWith(MockitoExtension.class)
class StorageExecutorTest {

    @Mock
    private StorageStrategy ossStrategy;

    @Mock
    private StorageStrategy localStrategy;

    private StorageExecutor executor;

    @BeforeEach
    void setUp() {
        // 手工构造有序策略列表：OSS 优先，本地兜底（构造函数按 @Order 排序，mock 无注解时保持注入顺序）
        executor = new StorageExecutor(List.of(ossStrategy, localStrategy));
    }

    // ---------- 保存降级 ----------

    @Test
    void saveFile_ossThrows_fallsBackToLocal() {
        when(ossStrategy.isAvailable()).thenReturn(true);
        when(ossStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class)))
                .thenThrow(new RuntimeException("OSS 服务异常"));
        when(localStrategy.isAvailable()).thenReturn(true);
        when(localStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class)))
                .thenReturn("local:cover/2026/08/19/x.jpg");

        String key = executor.saveFile(FileBizEnum.COVER, 1L, mockMultipartFile());

        assertEquals("local:cover/2026/08/19/x.jpg", key);
        verify(localStrategy).saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class));
    }

    @Test
    void saveFile_ossReturnsNull_fallsBackToLocal() {
        when(ossStrategy.isAvailable()).thenReturn(true);
        when(ossStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class)))
                .thenReturn(null);
        when(localStrategy.isAvailable()).thenReturn(true);
        when(localStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class)))
                .thenReturn("local:cover/2026/08/19/x.jpg");

        String key = executor.saveFile(FileBizEnum.COVER, 1L, mockMultipartFile());

        assertEquals("local:cover/2026/08/19/x.jpg", key);
        verify(localStrategy).saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class));
    }

    @Test
    void saveFile_ossUnavailable_skipsToLocal() {
        when(ossStrategy.isAvailable()).thenReturn(false);
        when(localStrategy.isAvailable()).thenReturn(true);
        when(localStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class)))
                .thenReturn("local:cover/2026/08/19/x.jpg");

        String key = executor.saveFile(FileBizEnum.COVER, 1L, mockMultipartFile());

        assertEquals("local:cover/2026/08/19/x.jpg", key);
        // OSS 不可用时不发起任何保存尝试
        verify(ossStrategy, never()).saveFile(any(FileBizEnum.class), any(), any(MultipartFile.class));
    }

    @Test
    void saveFile_allFail_returnsNull() {
        when(ossStrategy.isAvailable()).thenReturn(true);
        when(ossStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class)))
                .thenReturn(null);
        when(localStrategy.isAvailable()).thenReturn(true);
        when(localStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), any(MultipartFile.class)))
                .thenReturn(null);

        assertNull(executor.saveFile(FileBizEnum.COVER, 1L, mockMultipartFile()));
    }

    @Test
    void saveFile_diskFile_ossThrows_fallsBackToLocal() {
        File file = new File("tmp/cover.png");
        when(ossStrategy.isAvailable()).thenReturn(true);
        when(ossStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), eq(file)))
                .thenThrow(new RuntimeException("OSS 不可用"));
        when(localStrategy.isAvailable()).thenReturn(true);
        when(localStrategy.saveFile(eq(FileBizEnum.COVER), eq(1L), eq(file)))
                .thenReturn("local:cover/2026/08/19/x.png");

        String key = executor.saveFile(FileBizEnum.COVER, 1L, file);

        assertEquals("local:cover/2026/08/19/x.png", key);
        verify(localStrategy).saveFile(eq(FileBizEnum.COVER), eq(1L), eq(file));
    }

    // ---------- URL 解析路由 ----------

    @Test
    void resolveUrl_ossKey_routesToOssStrategy() {
        when(ossStrategy.supports("oss:cover/2026/08/19/x.jpg")).thenReturn(true);
        when(ossStrategy.resolveUrl("oss:cover/2026/08/19/x.jpg"))
                .thenReturn("https://bucket.aliyuncs.com/cover/2026/08/19/x.jpg?sign=xx");

        String url = executor.resolveUrl("oss:cover/2026/08/19/x.jpg");

        assertEquals("https://bucket.aliyuncs.com/cover/2026/08/19/x.jpg?sign=xx", url);
        verify(localStrategy, never()).resolveUrl(any());
    }

    @Test
    void resolveUrl_localKey_routesToLocalStrategy() {
        when(ossStrategy.supports("local:cover/2026/08/19/x.jpg")).thenReturn(false);
        when(localStrategy.supports("local:cover/2026/08/19/x.jpg")).thenReturn(true);
        when(localStrategy.resolveUrl("local:cover/2026/08/19/x.jpg"))
                .thenReturn("/api/file/cover/2026/08/19/x.jpg");

        String url = executor.resolveUrl("local:cover/2026/08/19/x.jpg");

        assertEquals("/api/file/cover/2026/08/19/x.jpg", url);
    }

    @Test
    void resolveUrl_unknownKey_returnsNull() {
        when(ossStrategy.supports("https://example.com/a.jpg")).thenReturn(false);
        when(localStrategy.supports("https://example.com/a.jpg")).thenReturn(false);

        assertNull(executor.resolveUrl("https://example.com/a.jpg"));
        verify(ossStrategy, never()).resolveUrl(any());
        verify(localStrategy, never()).resolveUrl(any());
    }

    @Test
    void resolveUrl_blank_returnsNull() {
        assertNull(executor.resolveUrl(null));
        assertNull(executor.resolveUrl(""));
        assertNull(executor.resolveUrl("   "));
        verify(ossStrategy, never()).supports(any());
        verify(localStrategy, never()).supports(any());
    }

    // ---------- 删除路由 ----------

    @Test
    void delete_routesByPrefix() {
        when(ossStrategy.supports("oss:cover/x.jpg")).thenReturn(true);
        executor.delete("oss:cover/x.jpg");
        verify(ossStrategy).delete("oss:cover/x.jpg");
    }

    @Test
    void delete_unknownKey_noop() {
        when(ossStrategy.supports("x:unknown")).thenReturn(false);
        when(localStrategy.supports("x:unknown")).thenReturn(false);

        executor.delete("x:unknown");

        verify(ossStrategy, never()).delete(any());
        verify(localStrategy, never()).delete(any());
    }

    // ---------- 读取 ----------

    @Test
    void loadFile_firstNonNullResourceWins() {
        Resource localResource = new FileSystemResource("tmp/storage/cover/x.jpg");
        when(ossStrategy.loadFile(FileBizEnum.COVER, "2026/08/19/x.jpg")).thenReturn(null);
        when(localStrategy.loadFile(FileBizEnum.COVER, "2026/08/19/x.jpg")).thenReturn(localResource);

        Resource resource = executor.loadFile(FileBizEnum.COVER, "2026/08/19/x.jpg");

        assertSame(localResource, resource);
    }

    @Test
    void loadFile_allNull_returnsNull() {
        when(ossStrategy.loadFile(FileBizEnum.COVER, "2026/08/19/x.jpg")).thenReturn(null);
        when(localStrategy.loadFile(FileBizEnum.COVER, "2026/08/19/x.jpg")).thenReturn(null);

        assertNull(executor.loadFile(FileBizEnum.COVER, "2026/08/19/x.jpg"));
    }

    // ---------- 首选可用性 ----------

    @Test
    void isPrimaryStrategyAvailable_returnsOssAvailability() {
        when(ossStrategy.isAvailable()).thenReturn(true);
        assertTrue(executor.isPrimaryStrategyAvailable());

        when(ossStrategy.isAvailable()).thenReturn(false);
        assertFalse(executor.isPrimaryStrategyAvailable());
    }

    private MultipartFile mockMultipartFile() {
        return org.mockito.Mockito.mock(MultipartFile.class);
    }
}