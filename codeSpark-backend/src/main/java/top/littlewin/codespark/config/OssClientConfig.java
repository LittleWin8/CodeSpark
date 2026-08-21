package top.littlewin.codespark.config;

import cn.hutool.core.util.StrUtil;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 阿里云 OSS（V2 SDK）配置与客户端管理。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssClientConfig {

    /** 总开关：是否启用阿里云 OSS */
    private Boolean enabled;

    private String region;

    private String accessKeyId;

    private String accessKeySecret;

    private String bucketName;

    private String domain;

    private Long presignExpireSeconds;

    /**
     * 构建 OSS 客户端：开关未开启或 AK 未配置时返回 null（原因由 logOssStatus 启动时统一输出）
     */
    @Bean
    public OSSClient buildOssClient() {

        if (!isAvailable()){
            return null;
        }

        log.info("初始化阿里云 OSS V2 客户端：region={}, bucket={}", region, bucketName);

        OSSClientBuilder builder = OSSClient.newBuilder()
                .credentialsProvider(new StaticCredentialsProvider(accessKeyId, accessKeySecret))
                .region(region)
                // 设置建立连接的超时时间, 默认值 5秒
                .connectTimeout(Duration.ofSeconds(30))
                // 设置应用读写数据的超时时间, 默认值 20秒
                .readWriteTimeout(Duration.ofSeconds(30));
        return builder.build();
    }

    /**
     * OSS 客户端是否可用
     * @return
     */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(enabled)
                && StrUtil.isNotBlank(accessKeyId)
                && StrUtil.isNotBlank(accessKeySecret);
    }

    /**
     * 启动时输出 OSS 是否启用的状态日志
     */
    @PostConstruct
    public void logOssStatus() {
        if (enabled != null && !enabled) {
            log.warn("阿里云 OSS 已关闭（aliyun.oss.enabled=false），文件存储使用本地实现");
        } else if (StrUtil.isBlank(accessKeyId) || StrUtil.isBlank(accessKeySecret)) {
            log.warn("阿里云 OSS 已开启但未配置 AccessKey（OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET），文件存储回退本地实现");
        } else {
            log.info("阿里云 OSS 已启用：region={}, bucket={}", region, bucketName);
        }
    }
}
