package top.littlewin.codespark.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 指标收集器
 *
 * <p>所有按用户/应用统计的指标都同时打上 user_account / app_id 标签：
 * user_account（注册账号，必填）用于 Grafana 排行直接展示用户标识，而非数字 ID。
 * 缓存键不包含 user_account：同一 userId 复用同一计数器，账号变化只影响展示。</p>
 */
@Component
@Slf4j
public class AiModelMetricsCollector {

    @Resource
    private MeterRegistry meterRegistry;

    // 缓存已创建的指标，避免重复创建（按指标类型分离缓存）
    private final ConcurrentMap<String, Counter> requestCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> tokenCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> responseTimersCache = new ConcurrentHashMap<>();

    /**
     * 记录请求次数
     */
    public void recordRequest(String userId, String userAccount, String appId, String modelName, String status) {
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, status);
        Counter counter = requestCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_requests_total")
                        .description("AI模型总请求次数")
                        .tag("user_id", userId)
                        .tag("user_account", safeTag(userAccount))
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("status", status)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * 记录错误
     */
    public void recordError(String userId, String userAccount, String appId, String modelName, Throwable error) {
        String errorType = error == null ? "unknown" : error.getClass().getSimpleName();
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, errorType);
        Counter counter = errorCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_errors_total")
                        .description("AI模型错误次数")
                        .tag("user_id", userId)
                        .tag("user_account", safeTag(userAccount))
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("error_type", errorType)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * 记录Token消耗
     */
    public void recordTokenUsage(String userId, String userAccount, String appId, String modelName,
                                 String tokenType, long tokenCount) {
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, tokenType);
        Counter counter = tokenCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_tokens_total")
                        .description("AI模型Token消耗总数")
                        .tag("user_id", userId)
                        .tag("user_account", safeTag(userAccount))
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .tag("token_type", tokenType)
                        .register(meterRegistry)
        );
        counter.increment(tokenCount);
    }

    /**
     * 记录响应时间
     */
    public void recordResponseTime(String userId, String userAccount, String appId, String modelName, Duration duration) {
        String key = String.format("%s_%s_%s", userId, appId, modelName);
        Timer timer = responseTimersCache.computeIfAbsent(key, k ->
                Timer.builder("ai_model_response_duration_seconds")
                        .description("AI模型响应时间")
                        .tag("user_id", userId)
                        .tag("user_account", safeTag(userAccount))
                        .tag("app_id", appId)
                        .tag("model_name", modelName)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );
        timer.record(duration);
    }

    /** 标签值不允许为 null/空白，兜底为 unknown，避免 Micrometer 抛异常 */
    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
