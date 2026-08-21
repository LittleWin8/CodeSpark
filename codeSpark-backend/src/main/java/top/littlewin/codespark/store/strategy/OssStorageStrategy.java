package top.littlewin.codespark.store.strategy;

import jakarta.annotation.Resource;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.config.OssClientConfig;
import top.littlewin.codespark.manager.OssManager;
import top.littlewin.codespark.model.enums.FileBizEnum;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 阿里云 OSS 存储策略（包装 OssManager）。
 * 存储标识：oss:{biz}/{yyyy}/{MM}/{dd}/{fileName}，读取时由 resolveUrl 动态签名。
 * 职责边界：只负责 OSS 后端的保存/解析/删除，不负责降级与流程控制；
 */
@Service
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OssStorageStrategy implements StorageStrategy {

    /** OSS 存储标识前缀 */
    public static final String OSS_KEY_PREFIX = "oss:";

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Resource
    private OssManager ossManager;

    @Resource
    private OssClientConfig ossClientConfig;

    @Override
    public String getKeyPrefix() {
        return OSS_KEY_PREFIX;
    }

    @Override
    public boolean isAvailable() {
        // OSS 开关 + AccessKey 配置齐备才参与保存；运行时故障由执行器捕获后降级
        return ossClientConfig.isAvailable();
    }

    @Override
    public boolean supports(String storageKey) {
        return StrUtil.isNotBlank(storageKey) && storageKey.startsWith(OSS_KEY_PREFIX);
    }

    @Override
    public String saveFile(FileBizEnum biz, Long ownerId, MultipartFile file) {
        try {
            // 直接流式上传（OssManager 流式重载），无需中转临时文件
            String key = generateKey(biz, IdUtil.getSnowflakeNextIdStr() + "." + extOf(file.getOriginalFilename()));
            String url = ossManager.upLoadFile(key, file.getInputStream());
            if (StrUtil.isBlank(url)) {
                return null;
            }
            String storageKey = OSS_KEY_PREFIX + key;
            log.info("OSS 上传成功: {}", storageKey);
            return storageKey;
        } catch (Exception e) {
            log.error("OSS 上传失败: biz={}, ownerId={}", biz.getValue(), ownerId, e);
            return null;
        }
    }

    @Override
    public String saveFile(FileBizEnum biz, Long ownerId, File file) {
        String ext = FileUtil.extName(file.getName()).toLowerCase();
        if (StrUtil.isBlank(ext)) {
            ext = "jpg";
        }
        String key = generateKey(biz, IdUtil.getSnowflakeNextIdStr() + "." + ext);
        String url = ossManager.upLoadFile(key, file);
        if (StrUtil.isBlank(url)) {
            return null;
        }
        String storageKey = OSS_KEY_PREFIX + key;
        log.info("OSS 上传成功: {}", storageKey);
        return storageKey;
    }

    @Override
    public String resolveUrl(String storageKey) {
        String key = storageKey.startsWith(OSS_KEY_PREFIX)
                ? storageKey.substring(OSS_KEY_PREFIX.length())
                : storageKey;
        return ossManager.buildPresignedUrl(key);
    }

    @Override
    public org.springframework.core.io.Resource loadFile(FileBizEnum biz, String relativeKey) {
        // OSS 对象通过签名 URL 直接访问，不支持本地读取
        return null;
    }

    @Override
    public void delete(String storageKey) {
        if (StrUtil.isBlank(storageKey) || !storageKey.startsWith(OSS_KEY_PREFIX)) {
            return;
        }
        ossManager.deleteObject(storageKey.substring(OSS_KEY_PREFIX.length()));
    }

    /** 生成 OSS 对象键：{biz}/{yyyy}/{MM}/{dd}/{fileName} */
    private String generateKey(FileBizEnum biz, String fileName) {
        return String.format("%s/%s/%s", biz.getValue(), LocalDate.now().format(DATE_PATH), fileName);
    }

    /** 从原始文件名取扩展名（小写，空则默认 jpg） */
    private String extOf(String originalName) {
        String ext = StrUtil.isBlank(originalName) ? "" : FileUtil.extName(originalName).toLowerCase();
        return StrUtil.isBlank(ext) ? "jpg" : ext;
    }
}