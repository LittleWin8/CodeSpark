package top.littlewin.codespark.manager;

import cn.hutool.core.util.StrUtil;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.littlewin.codespark.config.OssClientConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;

/**
 * OSS 对象存储管理器
 * 只负责上传与预签名等业务封装。
 */
@Component
@Slf4j
public class OssManager {

    @Resource
    private OssClientConfig ossClientConfig;

    @Autowired(required = false)
    private OSSClient ossClient;

    /**
     * 上传文件到 OSS 并返回访问 URL
     *
     * @param key OSS 对象唯一键
     * @param file 文件
     * @return 文件的访问 URL，失败返回 null
     */
    public String upLoadFile(String key, File file){
        if (ossClient == null) {
            log.warn("OSS 未启用或客户端未创建，回退本地存储: {}", file.getName());
            return null;
        }
        try {
            PutObjectResult result = putObject(key, file);
            if (result.statusCode() == 200) {
                String url = buildAccessUrl(key);
                log.info("文件上传到 OSS 成功：{} -> {}", file.getName(), url);
                return url;
            }
            log.error("文件上传到 OSS 失败：{}，statusCode={}", file.getName(), result.statusCode());
            return null;
        } catch (Exception e) {
            log.error("OSS 上传异常: key={}, file={}", key, file.getName(), e);
            return null;
        }
    }

    /**
     * 上传对象（内部方法，仅供 upLoadFile 使用）
     *
     * @param key  对象唯一键
     * @param file 待上传文件
     * @return 上传结果（含 statusCode / requestId / eTag）
     */
    private PutObjectResult putObject(String key, File file) {

        try (InputStream in = Files.newInputStream(file.toPath())) {

            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossClientConfig.getBucketName())
                    .key(key)
                    .body(BinaryData.fromStream(in))
                    .build();

            PutObjectResult result = ossClient.putObject(request);
            log.info("OSS 上传成功: key={}, statusCode={}, requestId={}, eTag={}",
                    key, result.statusCode(), result.requestId(), result.eTag());
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败: " + file, e);
        }
    }

    /**
     * 拼接对外访问地址：优先自定义域名（aliyun.oss.domain / OSS_DOMAIN），
     * 否则用 Bucket 默认公网域名（{bucket}.{region}.aliyuncs.com）
     *
     * @param key OSS 对象键（兼容带/不带前导斜杠）
     * @return 形如 https://domain/cover/yyyy/MM/dd/xxx.jpg 的完整 URL
     */
    private String buildAccessUrl(String key) {
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        String host = StrUtil.isNotBlank(ossClientConfig.getDomain())
                ? ossClientConfig.getDomain()
                : ossClientConfig.getBucketName() + "." + ossClientConfig.getRegion() + ".aliyuncs.com";
        return "https://" + host + "/" + cleanKey;
    }

    /**
     * 生成预签名访问 URL（私有桶读取场景，有效期取自 aliyun.oss.presign-expire-seconds，默认 3600 秒）
     *
     * @param key OSS 对象键（兼容带/不带前导斜杠）
     * @return 预签名 URL，客户端或客户端未初始化时返回 null
     */
    public String buildPresignedUrl(String key) {

        if (ossClient == null) {
            log.warn("OSS 未启用或客户端未创建，无法生成预签名 URL: {}", key);
            return null;
        }

        long expireSeconds = ossClientConfig.getPresignExpireSeconds() == null
                ? 3600L
                : ossClientConfig.getPresignExpireSeconds();
        try {
            GetObjectRequest request = GetObjectRequest.newBuilder()
                    .bucket(ossClientConfig.getBucketName())
                    .key(stripLeadSlash(key))
                    .build();
            PresignOptions options = PresignOptions.newBuilder()
                    .expiration(Duration.ofSeconds(expireSeconds))
                    .build();
            PresignResult result = ossClient.presign(request, options);
            log.info("生成预签名 URL 成功: key={}, 有效期={}s", key, expireSeconds);
            return result.url();
        } catch (Exception e) {
            log.error("生成预签名 URL 异常: key={}", key, e);
            return null;
        }
    }

    /**
     * 去除对象 key 的前导斜杠，保证 key 与 URL 拼接干净
     */
    private String stripLeadSlash(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}