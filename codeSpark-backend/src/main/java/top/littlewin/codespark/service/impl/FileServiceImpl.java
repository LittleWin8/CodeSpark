package top.littlewin.codespark.service.impl;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.model.enums.FileBizEnum;
import top.littlewin.codespark.model.enums.FileOperationTypeEnum;
import top.littlewin.codespark.service.FileService;
import top.littlewin.codespark.store.FileStorageFacade;
import top.littlewin.codespark.store.model.FileStorageRequest;

import java.io.File;

/**
 * 文件存储业务服务实现
 */
@Service
public class FileServiceImpl implements FileService {

    @Resource
    private FileStorageFacade fileStorageFacade;

    @Override
    public String saveAvatar(Long userId, MultipartFile file) {
        return (String) fileStorageFacade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.SAVE_MULTIPART)
                .biz(FileBizEnum.AVATAR)
                .ownerId(userId)
                .multipartFile(file)
                .build());
    }

    @Override
    public String saveCover(Long appId, File file) {
        return (String) fileStorageFacade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.SAVE_FILE)
                .biz(FileBizEnum.COVER)
                .ownerId(appId)
                .file(file)
                .build());
    }

    @Override
    public String resolveUrl(String storageKey) {
        return (String) fileStorageFacade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.RESOLVE_URL)
                .storageKey(storageKey)
                .build());
    }

    @Override
    public org.springframework.core.io.Resource loadFile(FileBizEnum biz, String relativeKey) {
        return (org.springframework.core.io.Resource) fileStorageFacade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.LOAD_FILE)
                .biz(biz)
                .relativeKey(relativeKey)
                .build());
    }

    @Override
    public void delete(String storageKey) {
        fileStorageFacade.execute(FileStorageRequest.builder()
                .operationType(FileOperationTypeEnum.DELETE)
                .storageKey(storageKey)
                .build());
    }
}