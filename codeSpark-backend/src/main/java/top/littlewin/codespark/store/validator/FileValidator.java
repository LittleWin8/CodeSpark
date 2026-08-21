package top.littlewin.codespark.store.validator;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;

import java.io.File;
import java.util.Set;

/**
 * 文件校验器：统一校验上传/落盘/下载后的文件（非空、大小上限、扩展名白名单）
 *
 * 职责边界：
 *   由调用方在进入文件存储门面前调用；
 *   只做业务约束校验并抛业务异常，不做任何存储/降级处理。
 */
@Component
public class FileValidator {

    /** 上传文件大小上限 */
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /** 允许的图片扩展名白名单 */
    public static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");

    /**
     * 校验上传文件（MultipartFile）：非空、大小上限、扩展名白名单
     *
     * @param file 上传文件
     */
    public void validate(MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, ErrorMessage.EMPTY_FILE);
        validateSize(file.getSize());
        validateExtension(file.getOriginalFilename());
    }

    /**
     * 校验已落盘 / 下载后的临时文件（File）：存在、非空、大小上限、扩展名白名单
     *
     * @param file 本地文件
     */
    public void validate(File file) {
        ThrowUtils.throwIf(file == null || !file.exists() || !file.isFile(), ErrorCode.PARAMS_ERROR, ErrorMessage.EMPTY_FILE);
        ThrowUtils.throwIf(file.length() <= 0, ErrorCode.PARAMS_ERROR, ErrorMessage.EMPTY_FILE);
        validateSize(file.length());
        validateExtension(file.getName());
    }

    /**
     * 校验文件大小（字节）：超出上限抛 FILE_TOO_LARGE
     *
     * @param size 文件大小
     */
    public void validateSize(long size) {
        ThrowUtils.throwIf(size > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, ErrorMessage.FILE_TOO_LARGE);
    }

    /**
     * 校验文件名扩展名：不在白名单内抛 INVALID_FILE_TYPE
     *
     * @param fileName 原始文件名
     */
    public void validateExtension(String fileName) {
        String ext = StrUtil.isBlank(fileName) ? "" : FileUtil.extName(fileName).toLowerCase();
        if (StrUtil.isBlank(ext) || !ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_FILE_TYPE);
        }
    }
}