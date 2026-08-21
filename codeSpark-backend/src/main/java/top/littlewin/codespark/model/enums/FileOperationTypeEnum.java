package top.littlewin.codespark.model.enums;

/**
 * 文件存储操作类型：文件存储门面（FileStorageFacade）统一入口按该类型分发到存储执行器的对应流程。
 * <p>
 * 新增存储操作（如分片上传、合并等）时在此扩展枚举，并在门面统一入口补充分支即可。
 */
public enum FileOperationTypeEnum {

    /** 保存上传文件（MultipartFile） */
    SAVE_MULTIPART,

    /** 保存已落盘 / 下载后的临时文件（File） */
    SAVE_FILE,

    /** 将存储标识解析为对外可访问 URL */
    RESOLVE_URL,

    /** 读取文件资源（本地静态访问） */
    LOAD_FILE,

    /** 删除存储对象 */
    DELETE
}