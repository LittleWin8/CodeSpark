package top.littlewin.codespark.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.model.enums.FileBizEnum;
import top.littlewin.codespark.model.enums.FileOperationTypeEnum;
import top.littlewin.codespark.store.model.FileStorageRequest;
import top.littlewin.codespark.store.strategy.StorageExecutor;
import top.littlewin.codespark.store.validator.FileValidator;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存储门面测试：唯一统一入口 {@link FileStorageFacade#execute(FileStorageRequest)} 按操作类型分发。
 * 门面不做存储方式判断；SAVE 分支统一调用 FileValidator 校验后再委托 StorageExecutor。
 */
@ExtendWith(MockitoExtension.class)
class FileStorageFacadeTest {

    @Mock
    private StorageExecutor storageExecutor;

    @Mock
    private FileValidator fileValidator;

    @InjectMocks
    private FileStorageFacade facade;

    @Test
    void execute_saveMultipart_routesToExecutorSaveFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(storageExecutor.saveFile(FileBizEnum.AVATAR, 1L, file))
                .thenReturn("oss:avatar/2026/08/19/x.jpg");

        Object result = facade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.SAVE_MULTIPART)
                .biz(FileBizEnum.AVATAR)
                .ownerId(1L)
                .multipartFile(file)
                .build());

        assertEquals("oss:avatar/2026/08/19/x.jpg", result);
        verify(fileValidator).validate(file);
        verify(storageExecutor).saveFile(FileBizEnum.AVATAR, 1L, file);
    }

    @Test
    void execute_saveFile_routesToExecutorSaveFile() {
        File file = new File("tmp/cover.png");
        when(storageExecutor.saveFile(FileBizEnum.COVER, 1L, file)).thenReturn("local:cover/x.png");

        Object result = facade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.SAVE_FILE)
                .biz(FileBizEnum.COVER)
                .ownerId(1L)
                .file(file)
                .build());

        assertEquals("local:cover/x.png", result);
        verify(fileValidator).validate(file);
        verify(storageExecutor).saveFile(FileBizEnum.COVER, 1L, file);
    }

    @Test
    void execute_resolveUrl_routesToExecutor() {
        when(storageExecutor.resolveUrl("oss:x.jpg")).thenReturn("https://x.jpg");

        Object result = facade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.RESOLVE_URL)
                .storageKey("oss:x.jpg")
                .build());

        assertEquals("https://x.jpg", result);
        verify(storageExecutor).resolveUrl("oss:x.jpg");
    }

    @Test
    void execute_loadFile_routesToExecutor() {
        Resource resource = mock(Resource.class);
        when(storageExecutor.loadFile(FileBizEnum.COVER, "2026/08/19/x.jpg")).thenReturn(resource);

        Object result = facade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.LOAD_FILE)
                .biz(FileBizEnum.COVER)
                .relativeKey("2026/08/19/x.jpg")
                .build());

        assertSame(resource, result);
        verify(storageExecutor).loadFile(FileBizEnum.COVER, "2026/08/19/x.jpg");
    }

    @Test
    void execute_delete_routesToExecutor() {
        Object result = facade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.DELETE)
                .storageKey("local:x.jpg")
                .build());

        assertNull(result);
        verify(storageExecutor).delete("local:x.jpg");
    }

    @Test
    void execute_nullRequest_rejected() {
        assertThrows(BusinessException.class, () -> facade.execute(null));
    }

    @Test
    void execute_nullOperationType_rejected() {
        assertThrows(BusinessException.class, () -> facade.execute(FileStorageRequest.builder().build()));
    }

    @Test
    void execute_saveFile_validationFailure_rejected() {
        // 文件校验失败时门面拦截（不触达执行器）
        doThrow(new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_FILE_TYPE))
                .when(fileValidator).validate(any(File.class));
        assertThrows(BusinessException.class, () -> facade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.SAVE_FILE)
                .biz(FileBizEnum.COVER)
                .ownerId(1L)
                .file(new File("tmp/cover.exe"))
                .build()));
        verify(storageExecutor, never()).saveFile(eq(FileBizEnum.COVER), eq(1L), any(File.class));
    }
}