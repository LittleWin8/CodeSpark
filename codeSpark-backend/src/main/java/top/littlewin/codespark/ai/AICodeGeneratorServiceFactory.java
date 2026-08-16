package top.littlewin.codespark.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.littlewin.codespark.ai.tools.FileWriteTool;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;
import top.littlewin.codespark.service.ChatHistoryService;

import java.time.Duration;

/**
 * AI 服务创建工厂
 * 模型装配策略：
 * - deepseek-v4-flash（代码生成）：由 langchain4j 自动装配注入（langchain4j.open-ai.* 配置）
 * - deepseek-chat（应用命名）：由本工厂手动构建（轻量、无工具、无记忆）
 */
@Slf4j
@Configuration
public class AICodeGeneratorServiceFactory {

    // ===== 自动装配：deepseek-v4-flash 模型 =====
    @Resource
    private ChatModel openAiChatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    // ===== 手动装配：deepseek-chat 模型 =====
    @Resource
    private ChatModel namingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 手动装配：deepseek-chat 模型
     * 仅用于为应用命名，轻量、纯文本输出
     */
    @Bean
    public ChatModel namingChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key:}") String apiKey,
            @Value("${codespark.ai.app-name.model-name:deepseek-chat}") String modelName,
            @Value("${codespark.ai.app-name.max-tokens:128}") Integer maxTokens) {

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .build();
    }

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * 1. 最大缓存 1000 个实例
     * 2. 写入后 30 分钟过期
     * 3. 访问后 10 分钟过期
     */
    private final Cache<String, AICodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，key:{}，原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 与生成类型获取（或创建）AI 服务实例
     *
     * @param appId       应用 ID
     * @param codeGenType 生成类型
     */
    public AICodeGeneratorService getAICodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        if (codeGenType == CodeGenTypeEnum.NAMING){
            return this.createAiCodeGeneratorService(appId, codeGenType);
        }
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 创建新的 AI 服务实例
     * 不同生成类型使用不同的模型 / 工具 / 记忆策略
     *
     * @param appId       应用 ID
     * @param codeGenType 生成类型
     */
    private AICodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建 AI 服务实例, 类型: {}", appId, codeGenType.getValue());
        return switch (codeGenType) {
            // Vue 工程：deepseek-v4-flash + 文件写入工具 + 对话记忆
            case VUE_PROJECT -> {
                MessageWindowChatMemory chatMemory = buildChatMemory(appId);
                yield AiServices.builder(AICodeGeneratorService.class)
                        .chatModel(openAiChatModel)
                        .streamingChatModel(streamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(new FileWriteTool())
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,
                                        "Error: there is no tool called " + toolExecutionRequest.name()))
                        .build();
            }
            // HTML / 多文件：deepseek-v4-flash + 对话记忆，无工具
            case HTML, MULTI_FILE -> AiServices.builder(AICodeGeneratorService.class)
                    .chatModel(openAiChatModel)
                    .streamingChatModel(streamingChatModel)
                    .chatMemory(buildChatMemory(appId))
                    .build();
            // 命名：deepseek-chat，无工具、无记忆
            case NAMING -> AiServices.builder(AICodeGeneratorService.class)
                    .chatModel(namingChatModel)
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }

    /**
     * 构建带 Redis 持久化 + 历史回放的对话记忆
     */
    private MessageWindowChatMemory buildChatMemory(long appId) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return chatMemory;
    }

    /**
     * 构造缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }
}
