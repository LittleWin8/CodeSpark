package top.littlewin.codespark.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.service.FileStorageService;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 文件存储 服务层实现。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");

    @Override
    public String saveFile(MultipartFile file, String rootDir, Long subDirId, String type) {
        // 1. 校验文件
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "EMPTY_FILE");
        ThrowUtils.throwIf(file.getSize() > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, "FILE_TOO_LARGE");

        // 2. 校验文件类型（仅允许常见图片格式，防止上传恶意文件）
        String originalName = file.getOriginalFilename();
        String ext = StrUtil.isBlank(originalName) ? "" : FileUtil.extName(originalName).toLowerCase();
        if (StrUtil.isBlank(ext) || !ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "INVALID_FILE_TYPE");
        }

        // 3. 生成唯一文件名（保留原扩展名）
        String fileName = IdUtil.getSnowflakeNextIdStr() + "." + ext;

        // 4. 保存到 tmp/{type}/{subDirId}/{fileName}
        String dirPath = rootDir + File.separator + subDirId;
        try {
            FileUtil.mkdir(dirPath);
            file.transferTo(new File(dirPath + File.separator + fileName));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "FILE_SAVE_FAILED");
        }

        // 5. 返回访问 URL
        return "/api/file/" + type + "/" + subDirId + "/" + fileName;
    }

    @Override
    public Resource loadFile(String rootDir, Long subDirId, String fileName) {
        // 文件名白名单：只允许字母数字点横线，防止路径穿越
        if (fileName == null || !fileName.matches("^[a-zA-Z0-9.\\-_]+$")) {
            return null;
        }
        try {
            Path base = Paths.get(rootDir).toAbsolutePath().normalize();
            Path target = base.resolve(String.valueOf(subDirId)).resolve(fileName).normalize();
            if (!target.startsWith(base)) {
                return null;
            }
            File file = target.toFile();
            if (!file.exists() || !file.isFile()) {
                return null;
            }
            return new FileSystemResource(file);
        } catch (Exception e) {
            log.warn("加载文件失败: rootDir={}, subDirId={}, fileName={}", rootDir, subDirId, fileName, e);
            return null;
        }
    }
}
