package top.littlewin.codespark.screenshot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 截图任务配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "codespark.screenshot")
public class ScreenshotProperties {
    /**
     * worker 数（并发 Chrome 上限，每个 worker 独占一个浏览器）
     */
    private int workers = 2;

    /**
     * 任务队列容量，满则丢弃任务并记日志
     */
    private int queueCapacity = 100;

    /**
     * 单个任务失败重试次数
     */
    private int retryTimes = 1;

    /**
     * 页面加载超时（秒）
     */
    private int pageLoadTimeoutSeconds = 30;
}
