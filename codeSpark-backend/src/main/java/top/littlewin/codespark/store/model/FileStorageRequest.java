package top.littlewin.codespark.store.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.model.enums.FileBizEnum;
import top.littlewin.codespark.model.enums.FileOperationTypeEnum;

import java.io.File;

/**
 * 文件存储操作的统一入参载体
 */
@Getter
@Builder
public class FileStorageRequest {

    /** 操作类型（必填） */
    private FileOperationTypeEnum operationType;

    /** 业务类型（头像/封面，保存 / 读取时必填） */
    private FileBizEnum biz;

    /** 归属 ID（头像为用户 ID，封面为应用 ID，保存时必填） */
    private Long ownerId;

    /** 上传文件（SAVE_MULTIPART 时必填） */
    private MultipartFile multipartFile;

    /** 已落盘 / 临时文件（SAVE_FILE 时必填） */
    private File file;

    /** 存储标识（RESOLVE_URL / DELETE 时必填） */
    private String storageKey;

    /** 相对 key（yyyy/MM/dd/fileName，LOAD_FILE 时必填） */
    private String relativeKey;
}