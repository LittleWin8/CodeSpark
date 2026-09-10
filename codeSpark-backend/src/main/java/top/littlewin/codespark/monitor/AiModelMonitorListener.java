package top.littlewin.codespark.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.littlewin.codespark.config.QuotaProperties;
import top.littlewin.codespark.service.UserQuotaUsageService;
import top.littlewin.codespark.service.UserTokenUsageService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * AI 大模型监听器
 */
@Slf4j
@Component
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的键
    private static final String REQUEST_STATUS_TIME_KEY = "request_start_time";

    // 用于监控上下文传递，请求和响应不是一个线程，所以要将上下文传递
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";

    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Resource
    private UserQuotaUsageService userQuotaUsageService;

    @Resource
    private UserTokenUsageService userTokenUsageService;

    @Resource
    private QuotaProperties quotaProperties;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {

        // 1. 获取当前时间
        requestContext.attributes().put(REQUEST_STATUS_TIME_KEY, Instant.now());

        // 2. 从监控上下文获取信息
        MonitorContext monitorContext = MonitorContextHolder.getContext();
        if (monitorContext == null) {
            log.debug("无监控上下文，跳过 AI 请求指标: model={}", requestContext.chatRequest().modelName());
            return;  // 没设监控上下文就跳过，不记录也不报错
        }

        // 3. 传递监控上下文（供 onResponse / onError 在其它线程读取）
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, monitorContext);

        // 4. 记录请求数据
        aiModelMetricsCollector.recordRequest(
                monitorContext.getUserId(), monitorContext.getUserAccount(),
                monitorContext.getAppId(), requestContext.chatRequest().modelName(), "started");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {

        // 1. 从属性中获取监控信息（由 onRequest 方法储存）
        MonitorContext monitorContext = (MonitorContext) responseContext.attributes().get(MONITOR_CONTEXT_KEY);
        if (monitorContext == null) {
            log.debug("响应上下文中无监控信息，跳过 AI 响应指标");
            return;
        }
        String userId = monitorContext.getUserId();
        String userAccount = monitorContext.getUserAccount();
        String appId = monitorContext.getAppId();
        String modelName = responseContext.chatResponse().modelName();

        // 2. 记录响应数据
        aiModelMetricsCollector.recordRequest(userId, userAccount, appId, modelName, "success");
        recordResponseTime(responseContext.attributes(), userId, userAccount, appId, modelName);
        recordTokenUsage(responseContext, userId, userAccount, appId, modelName);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {

        // 1. 从监控上下文中获取信息
        MonitorContext monitorContext = (MonitorContext) errorContext.attributes().get(MONITOR_CONTEXT_KEY);
        if (monitorContext == null) {
            log.debug("错误上下文中无监控信息，跳过 AI 错误指标");
            return;
        }
        String userId = monitorContext.getUserId();
        String userAccount = monitorContext.getUserAccount();
        String appId = monitorContext.getAppId();
        String modelName = errorContext.chatRequest().modelName();

        // 2. 记录失败数据
        aiModelMetricsCollector.recordRequest(userId, userAccount, appId, modelName, "error");
        aiModelMetricsCollector.recordError(userId, userAccount, appId, modelName, errorContext.error());
        recordResponseTime(errorContext.attributes(), userId, userAccount, appId, modelName);
    }

    /**
     * 记录响应时间
     */
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String userAccount,
                                    String appId, String modelName) {
        Instant startTime = (Instant) attributes.get(REQUEST_STATUS_TIME_KEY);
        if (startTime == null) {
            log.debug("缺少请求开始时间，跳过响应时间指标");
            return;
        }
        Duration responseTime = Duration.between(startTime, Instant.now());
        aiModelMetricsCollector.recordResponseTime(userId, userAccount, appId, modelName, responseTime);
    }

    /**
     * 记录 Token 消耗量
     */
    private void recordTokenUsage(ChatModelResponseContext responseContext,
                                  String userId, String userAccount,
                                  String appId, String modelName) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage != null) {
            aiModelMetricsCollector.recordTokenUsage(userId, userAccount, appId, modelName, "input", tokenUsage.inputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, userAccount, appId, modelName, "output", tokenUsage.outputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, userAccount, appId, modelName, "total", tokenUsage.totalTokenCount());

            //  记录总消耗
            Long uid = parseLongSafely(userId);
            userTokenUsageService.recordUsage(uid, modelName, tokenUsage.inputTokenCount(), tokenUsage.outputTokenCount(), tokenUsage.totalTokenCount());

            // 扣额度
            String requestModel = responseContext.chatRequest().modelName();
            if (quotaProperties.isEnabled() && quotaProperties.getCountedModels().contains(requestModel)){
                userQuotaUsageService.recordQuota(uid, tokenUsage.totalTokenCount());
            }
        }
    }

    /**
     * 安全的将字符串转换从 Long 类型
     * @param str 从监控上下文传入的 userId
     * @return Long 类型的 userId
     */
    public static Long parseLongSafely(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return null; // 或记录日志后返回默认值
        }
    }
}
