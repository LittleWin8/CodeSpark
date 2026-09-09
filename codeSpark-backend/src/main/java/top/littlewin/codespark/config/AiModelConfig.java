package top.littlewin.codespark.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.littlewin.codespark.monitor.AiModelMonitorListener;

import java.util.List;

/**
 * AI 模型装配配置
 *
 * 模型分级策略：
 * - 主模型（deepseek-v4-flash）：由 langchain4j starter 自动装配（langchain4j.open-ai.* 配置），
 *   仅用于复杂的代码生成任务（HTML / MULTI_FILE / VUE_PROJECT）；
 * - 轻量模型（deepseek-chat）：本类手动构建，用于路由判断、应用命名等简单任务，成本低、响应快。
 */
@Configuration
public class AiModelConfig {

    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

    /**
     * 轻量模型：仅用于简单任务（AI 类型路由、应用命名），无流式需求
     */
    @Bean
    public ChatModel lightChatModel(
            @Value("${codespark.ai.light-model.base-url:https://open.bigmodel.cn/api/paas/v4}") String baseUrl,
            @Value("${codespark.ai.light-model.api-key:}") String apiKey,
            @Value("${codespark.ai.light-model.model-name:glm-4.7-flash}") String modelName,
            @Value("${codespark.ai.light-model.max-tokens:512}") Integer maxTokens) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .logRequests(true)
                .listeners(List.of(aiModelMonitorListener))
                .build();
    }
}
