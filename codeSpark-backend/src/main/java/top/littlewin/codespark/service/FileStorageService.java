package top.littlewin.codespark.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储
 */
public interface FileStorageService {

    /**
     * 保存上传文件到指定目录，返回可访问的 URL
     *
     * @param file     上传的文件
     * @param rootDir  根目录
     * @param subDirId 子目录 ID（头像为用户 ID，封面为应用 ID）
     * @param type     类型（avatar / cover），用于拼 URL 路径
     * @return 可访问的 URL
     */
    String saveFile(MultipartFile file, String rootDir, Long subDirId, String type);

    /**
     * 加载文件（带路径穿越防护），不可访问时返回 null
     *
     * @param rootDir  根目录
     * @param subDirId 子目录 ID（头像为用户 ID，封面为应用 ID）
     * @param fileName 文件名
     * @return 文件资源；文件不存在或路径非法时返回 null
     */
    Resource loadFile(String rootDir, Long subDirId, String fileName);
}
