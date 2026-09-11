package top.littlewin.codespark.screenshot;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriverException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 截图任务队列：有界队列 + 固定 worker 组，每个 worker 独占一个 WebDriver。
 * 投递方只入队不阻塞；worker 依次消费（每 worker 一个浏览器），避免共享内核竞争。
 * 队列满直接丢弃（封面为空可接受，下次部署会再截），不阻塞部署主流程。
 */
@Slf4j
@Component
public class ScreenshotTaskQueue implements ScreenshotTaskPublisher {

    private static final int SCREENSHOT_WIDTH = 1600;
    private static final int SCREENSHOT_HEIGHT = 1000;

    private final ScreenshotProperties properties;
    private final ScreenshotProcessor screenshotProcessor;
    private final BlockingQueue<ScreenshotTask> queue;

    /**
     * 去重：同一 app 在途只保留一个任务（用户连续点部署不会堆任务）
     */
    private final Set<Long> inFlightApps = ConcurrentHashMap.newKeySet();
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running = true;

    public ScreenshotTaskQueue(ScreenshotProperties properties, ScreenshotProcessor screenshotProcessor) {
        this.properties = properties;
        this.screenshotProcessor = screenshotProcessor;
        this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
    }

    @PostConstruct
    public void start() {
        for (int i = 0; i < properties.getWorkers(); i++) {
            Thread thread = new Thread(this::runWorker, "screenshot-worker-" + i);
            thread.start();
            workers.add(thread);
        }
        log.info("截图 worker 启动完成: workers={}, queueCapacity={}",
                properties.getWorkers(), properties.getQueueCapacity());
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        for (Thread thread : workers) {
            thread.interrupt();
        }
        log.info("截图 worker 停止信号已发出，队列剩余任务: {}", queue.size());
    }

    @Override
    public void publish(Long appId, String appUrl) {
        if (appId == null || StrUtil.isBlank(appUrl)) {
            return;
        }
        if (!inFlightApps.add(appId)) {
            log.info("截图任务已在途，跳过重复投递: appId={}", appId);
            return;
        }
        if (!queue.offer(new ScreenshotTask(appId, appUrl))) {
            inFlightApps.remove(appId);
            log.warn("截图队列已满，丢弃任务: appId={}, capacity={}", appId, properties.getQueueCapacity());
        }
    }

    private void runWorker() {
        ScreenshotDriver driver = null;
        while (running) {
            ScreenshotTask task = null;
            try {
                task = queue.poll(1, TimeUnit.SECONDS);
                if (task == null) {
                    continue;
                }
                driver = processTask(driver, task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("截图 worker 异常", e);
            } finally {
                if (task != null) {
                    inFlightApps.remove(task.appId());
                }
            }
        }
        closeQuietly(driver);
        log.info("截图 worker 退出: {}", Thread.currentThread().getName());
    }

    /**
     * 处理单个任务（含重试与 driver 重建），返回可继续使用的 driver
     */
    private ScreenshotDriver processTask(ScreenshotDriver driver, ScreenshotTask task) {
        int maxAttempts = Math.max(1, properties.getRetryTimes() + 1);
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (driver == null) {
                    driver = newDriver();
                }
                screenshotProcessor.process(task, driver);
                return driver;
            } catch (WebDriverException e) {
                // 浏览器会话异常：关闭并重建 driver 后重试
                lastError = e;
                log.warn("浏览器会话异常，重建 driver 后重试 ({}/{}): appId={}",
                        attempt, maxAttempts, task.appId(), e);
                closeQuietly(driver);
                driver = null;
            } catch (Exception e) {
                lastError = e;
                log.warn("截图任务失败 ({}/{}): appId={}, url={}",
                        attempt, maxAttempts, task.appId(), task.appUrl(), e);
            }
        }
        log.error("截图任务最终失败: appId={}, url={}", task.appId(), task.appUrl(), lastError);
        return driver;
    }

    private ScreenshotDriver newDriver() {
        return new ScreenshotDriver(SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT,
                properties.getPageLoadTimeoutSeconds());
    }

    private void closeQuietly(ScreenshotDriver driver) {
        if (driver != null) {
            try {
                driver.close();
            } catch (Exception e) {
                log.warn("关闭浏览器失败", e);
            }
        }
    }
}
