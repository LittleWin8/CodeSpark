package top.littlewin.codespark.store;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.store.model.FileStorageRequest;
import top.littlewin.codespark.store.strategy.StorageExecutor;
import top.littlewin.codespark.store.validator.FileValidator;

/**
 * 文件存储门面：业务层的统一存储入口。
 */
@Component
@Slf4j
public class FileStorageFacade {

    @Resource
    private StorageExecutor storageExecutor;

    @Resource
    private FileValidator fileValidator;

    /**
     * 统一入口：根据请求中的文件操作类型分发到执行器对应流程。
     * SAVE 分支会强制进行文件校验（大小/类型/非空）。
     *
     *   SAVE_MULTIPART / SAVE_FILE → 返回存储标识（oss:... 或 local:...），全部策略失败返回 null；
     *   RESOLVE_URL → 返回可访问 URL，无法解析返回 null；
     *   LOAD_FILE → 返回文件资源，不可访问返回 null；
     *   DELETE → 返回 null。
     *
     * @param request 操作入参（operationType 与对应分支字段必填）
     * @return 操作结果（String / Resource / null）
     */
    public Object execute(FileStorageRequest request) {
        ThrowUtils.throwIf(request == null || request.getOperationType() == null,
                ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_STORAGE_REQUEST);
        return switch (request.getOperationType()) {
            case SAVE_MULTIPART -> {
                // 强制校验：非空、大小、扩展名白名单（Failure 抛业务异常）
                fileValidator.validate(request.getMultipartFile());
                yield storageExecutor.saveFile(request.getBiz(), request.getOwnerId(), request.getMultipartFile());
            }
            case SAVE_FILE -> {
                fileValidator.validate(request.getFile());
                yield storageExecutor.saveFile(request.getBiz(), request.getOwnerId(), request.getFile());
            }
            case RESOLVE_URL -> storageExecutor.resolveUrl(request.getStorageKey());
            case LOAD_FILE -> storageExecutor.loadFile(request.getBiz(), request.getRelativeKey());
            case DELETE -> {
                storageExecutor.delete(request.getStorageKey());
                yield null;
            }
        };
    }
}