package top.littlewin.codespark.manager;

import jakarta.annotation.Resource;

import cn.hutool.core.util.StrUtil;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.ListObjectsRequest;
import com.aliyun.sdk.service.oss2.models.ListObjectsResult;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.paginator.ListObjectsIterable;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
            log.error("OSS 客户端未初始化（未启用或未配置 AccessKey），无法上传: {}", file.getName());
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
     * 上传文件流到 OSS 并返回访问 URL（流式上传，无需落盘临时文件）
     *
     * @param key OSS 对象唯一键
     * @param in  文件输入流（本方法读取后会关闭该流）
     * @return 文件的访问 URL，失败返回 null
     */
    public String upLoadFile(String key, InputStream in) {
        if (ossClient == null) {
            log.error("OSS 客户端未初始化（未启用或未配置 AccessKey），无法上传: key={}", key);
            return null;
        }
        try {
            PutObjectResult result = putObject(key, in);
            if (result.statusCode() == 200) {
                String url = buildAccessUrl(key);
                log.info("文件流上传到 OSS 成功：{} -> {}", key, url);
                return url;
            }
            log.error("文件流上传到 OSS 失败：{}，statusCode={}", key, result.statusCode());
            return null;
        } catch (Exception e) {
            log.error("OSS 上传异常: key={}", key, e);
            return null;
        }
    }

    /**
     * 上传对象（内部方法，仅供 upLoadFile(InputStream) 使用，读取后关闭传入流）
     *
     * @param key 对象唯一键
     * @param in  待上传输入流
     * @return 上传结果（含 statusCode / requestId / eTag）
     */
    private PutObjectResult putObject(String key, InputStream in) {

        try (InputStream stream = in) {

            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossClientConfig.getBucketName())
                    .key(key)
                    .body(BinaryData.fromStream(stream))
                    .build();

            PutObjectResult result = ossClient.putObject(request);
            log.info("OSS 上传成功: key={}, statusCode={}, requestId={}, eTag={}",
                    key, result.statusCode(), result.requestId(), result.eTag());
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传流失败: " + key, e);
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
     * 生成预签名访问 URL（私有桶读取场景，有效期取自 aliyun.oss.presign-expire-seconds，默认 24小时 ）
     *
     * @param key OSS 对象键（兼容带/不带前导斜杠）
     * @return 预签名 URL，客户端不可用或失败时返回 null
     */
    public String buildPresignedUrl(String key) {
        if (ossClient == null) {
            log.error("OSS 客户端未初始化（未启用或未配置 AccessKey），无法生成预签名 URL: {}", key);
            return null;
        }

        long expireSeconds = ossClientConfig.getPresignExpireSeconds() == null
                ? 86400L
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
     * 删除 OSS 对象（失败仅记日志，不抛异常；供定时对账清理孤儿对象使用）
     *
     * @param key OSS 对象键（兼容带/不带前导斜杠）
     */
    public void deleteObject(String key) {
        if (ossClient == null) {
            log.error("OSS 客户端未初始化（未启用或未配置 AccessKey），无法删除: {}", key);
            return;
        }
        try {
            DeleteObjectRequest request = DeleteObjectRequest.newBuilder()
                    .bucket(ossClientConfig.getBucketName())
                    .key(stripLeadSlash(key))
                    .build();
            ossClient.deleteObject(request);
            log.info("OSS 删除成功: key={}", key);
        } catch (Exception e) {
            log.error("OSS 删除异常: key={}", key, e);
        }
    }

    /**
     * 列出指定前缀下最后修改时间早于 cutoff 的对象 key（cutoff 为 null 时不按时间过滤），
     * 供孤儿清理使用：只清"足够旧"的对象，防止误删当日上传但尚未落库的对象
     *
     * @param prefix 对象 key 前缀
     * @param cutoff 截止时间（含）
     * @return 对象 key 列表
     */
    public List<String> listObjectKeysBefore(String prefix, Instant cutoff) {
        if (ossClient == null) {
            log.error("OSS 客户端未初始化（未启用或未配置 AccessKey），无法列举对象: prefix={}", prefix);
            return List.of();
        }
        try {
            List<String> keys = new ArrayList<>();
            ListObjectsIterable paginator = ossClient.listObjectsPaginator(
                    ListObjectsRequest.newBuilder()
                            .bucket(ossClientConfig.getBucketName())
                            .prefix(prefix)
                            .build());
            for (ListObjectsResult result : paginator) {
                if (result.contents() == null) {
                    continue;
                }
                for (ObjectSummary obj : result.contents()) {
                    if (cutoff != null && obj.lastModified() != null && obj.lastModified().isAfter(cutoff)) {
                        // 近期对象跳过，留给下一轮，防止误删未落库的当日上传
                        continue;
                    }
                    keys.add(obj.key());
                }
            }
            log.info("OSS 列举对象完成: prefix={}, count={}", prefix, keys.size());
            return keys;
        } catch (Exception e) {
            log.error("OSS 列举对象异常: prefix={}", prefix, e);
            return List.of();
        }
    }

    /**
     * 去除对象 key 的前导斜杠，保证 key 与 URL 拼接干净
     */
    private String stripLeadSlash(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}