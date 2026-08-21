package top.littlewin.codespark.store.strategy;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.model.enums.FileBizEnum;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 本地磁盘存储策略。
 * 存储标识：local:biz/yyyy/MM/dd/fileName
 * 物理路径：AppConstant/STORAGE_ROOT_DIR/biz/yyyy/MM/dd/fileName
 * 职责边界：只负责本地磁盘的保存/解析/读取/删除，不负责降级与流程控制；
 */
@Service
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class LocalStorageStrategy implements StorageStrategy {

    /** 本地存储标识前缀 */
    public static final String LOCAL_KEY_PREFIX = "local:";

    /** 本地静态访问 URL 前缀（resolveUrl 后由 FileController 提供服务） */
    private static final String LOCAL_URL_PREFIX = "/api/file/";

    /** 文件名白名单：只允许字母数字点横线下划线，防止路径穿越 */
    private static final String FILE_NAME_PATTERN = "^[a-zA-Z0-9.\\-_]+$";

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public String getKeyPrefix() {
        return LOCAL_KEY_PREFIX;
    }

    @Override
    public boolean isAvailable() {
        // 本地存储恒可用（失败场景由执行器按异常/空结果处理）
        return true;
    }

    @Override
    public boolean supports(String storageKey) {
        return StrUtil.isNotBlank(storageKey) && storageKey.startsWith(LOCAL_KEY_PREFIX);
    }

    @Override
    public String saveFile(FileBizEnum biz, Long ownerId, MultipartFile file) {

        // 文件名：雪花ID + 上传文件的后缀名
        String fileName = IdUtil.getSnowflakeNextIdStr() + "." + extOf(file.getOriginalFilename());

        // 构造文件路径：yyyy/MM/dd/文件名
        String relativeKey = buildRelativeKey(fileName);

        // 创建文件对象：工作目录 / 业务类型（avatar/cover）/yyyy/MM/dd/文件名
        File target = targetFile(biz, relativeKey);
        try {
            FileUtil.mkdir(target.getParentFile());
            file.transferTo(target);
        } catch (IOException e) {
            log.error("本地文件保存失败: biz={}, ownerId={}", biz.getValue(), ownerId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, ErrorMessage.FILE_SAVE_FAILED);
        }
        return buildStorageKey(biz, relativeKey);
    }

    @Override
    public String saveFile(FileBizEnum biz, Long ownerId, File file) {
        if (file == null || !file.exists()) {
            log.error("本地文件不存在: biz={}, ownerId={}, path={}", biz.getValue(), ownerId, file);
            return null;
        }

        // 将拓展名转为小写，如：.JPG -> .jpg
        String ext = FileUtil.extName(file.getName()).toLowerCase();
        if (StrUtil.isBlank(ext)) {
            ext = "jpg";
        }
        String relativeKey = buildRelativeKey(IdUtil.getSnowflakeNextIdStr() + "." + ext);
        File target = targetFile(biz, relativeKey);
        try {
            FileUtil.mkdir(target.getParentFile());
            FileUtil.copy(file, target, true);
        } catch (Exception e) {
            log.error("本地文件保存失败: biz={}, ownerId={}, path={}", biz.getValue(), ownerId, file, e);
            return null;
        }
        return buildStorageKey(biz, relativeKey);
    }

    @Override
    public String resolveUrl(String storageKey) {
        // local:cover/2026/08/19/xxx.jpg → /api/file/cover/2026/08/19/xxx.jpg
        String key = storageKey.startsWith(LOCAL_KEY_PREFIX)
                ? storageKey.substring(LOCAL_KEY_PREFIX.length())
                : storageKey;
        return LOCAL_URL_PREFIX + key;
    }

    @Override
    public Resource loadFile(FileBizEnum biz, String relativeKey) {
        if (StrUtil.isBlank(relativeKey) || !isSafeRelativeKey(relativeKey)) {
            return null;
        }
        try {
            Path base = Paths.get(AppConstant.STORAGE_ROOT_DIR, biz.getValue()).toAbsolutePath().normalize();
            Path target = base.resolve(relativeKey).normalize();
            if (!target.startsWith(base)) {
                return null;
            }
            File file = target.toFile();
            if (!file.exists() || !file.isFile()) {
                return null;
            }
            return new FileSystemResource(file);
        } catch (Exception e) {
            log.warn("加载本地文件失败: biz={}, relativeKey={}", biz.getValue(), relativeKey, e);
            return null;
        }
    }

    @Override
    public void delete(String storageKey) {
        if (StrUtil.isBlank(storageKey) || !storageKey.startsWith(LOCAL_KEY_PREFIX)) {
            return;
        }
        // 例：local:cover/2026/08/19/xxx.jpg → cover + 2026/08/19/xxx.jpg
        String body = storageKey.substring(LOCAL_KEY_PREFIX.length());
        int slash = body.indexOf('/');
        if (slash <= 0) {
            log.warn("本地存储标识格式非法，跳过删除: {}", storageKey);
            return;
        }

        // 获取biz：cover
        FileBizEnum biz = FileBizEnum.getEnumByValue(body.substring(0, slash));

        // 获取 2026/08/19/xxx.jpg
        String relativeKey = body.substring(slash + 1);

        // 校验，biz不为空、文件路径安全
        if (biz == null || !isSafeRelativeKey(relativeKey)) {
            log.warn("本地存储标识内容非法，跳过删除: {}", storageKey);
            return;
        }

        File target = targetFile(biz, relativeKey);
        if (target.exists()) {
            FileUtil.del(target);
            log.info("本地文件删除成功: {}", storageKey);
        }
        // 逐级清理空目录，回收磁盘
        cleanEmptyDirs(target.getParentFile());
    }

    /** 拼接本地存储标识 */
    private String buildStorageKey(FileBizEnum biz, String relativeKey) {
        return LOCAL_KEY_PREFIX + biz.getValue() + "/" + relativeKey;
    }

    /** 生成相对 key：yyyy/MM/dd/fileName */
    private String buildRelativeKey(String fileName) {
        return LocalDate.now().format(DATE_PATH) + "/" + fileName;
    }

    /** 定位磁盘文件 */
    private File targetFile(FileBizEnum biz, String relativeKey) {
        return new File(AppConstant.STORAGE_ROOT_DIR + File.separator + biz.getValue() + File.separator + relativeKey);
    }

    /** 相对 key 安全校验：只允许目录分隔符 + 字母数字点横线下划线 */
    private boolean isSafeRelativeKey(String relativeKey) {
        String[] parts = relativeKey.split("/");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (StrUtil.isBlank(part) || !part.matches(FILE_NAME_PATTERN) || "..".equals(part)) {
                return false;
            }
        }
        return true;
    }

    /** 从文件名取扩展名（小写，空则默认 jpg） */
    private String extOf(String originalName) {
        String ext = StrUtil.isBlank(originalName) ? "" : FileUtil.extName(originalName).toLowerCase();
        return StrUtil.isBlank(ext) ? "jpg" : ext;
    }

    /** 逐级清理空目录，直到存储根目录为止 */
    private void cleanEmptyDirs(File dir) {
        File stop = new File(AppConstant.STORAGE_ROOT_DIR);
        File current = dir;
        while (current != null && current.exists() && current.isDirectory()
                && !current.getAbsolutePath().equals(stop.getAbsolutePath())) {
            String[] children = current.list();
            if (children != null && children.length == 0) {
                FileUtil.del(current);
                current = current.getParentFile();
            } else {
                break;
            }
        }
    }
}