package top.littlewin.codespark.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.ai.guardrail.PromptSafetyInputGuardrail;
import top.littlewin.codespark.ai.tools.*;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;
import top.littlewin.codespark.service.ChatHistoryService;

import java.time.Duration;

/**
 * AI 代码生成服务工厂
 *
 * 模型装配策略（分级）：
 * - 代码生成（VUE_PROJECT / HTML / MULTI_FILE）：统一使用主模型 deepseek-v4-flash
 *   （langchain4j 自动装配注入，langchain4j.open-ai.* 配置）；
 * - 路由判断、应用命名等简单任务：使用轻量模型 deepseek-chat（见 {@link top.littlewin.codespark.config.AiModelConfig}），
 *   由 AiCodeGenTypeRoutingServiceFactory / AppNamingServiceFactory 独立装配，与本工厂无关。
 */
@Slf4j
@Service
public class AICodeGeneratorServiceFactory {

    // ===== 主模型：deepseek-v4-flash（代码生成） =====
    @Resource
    private ChatModel openAiChatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    @Resource
    private PromptSafetyInputGuardrail promptSafetyInputGuardrail;

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
            .removalListener((key, value, cause) ->
                log.debug("AI 服务实例被移除，key:{}，原因: {}", key, cause)
            )
            .build();

    /**
     * 根据 appId 与生成类型获取（或创建）AI 服务实例
     *
     * @param appId       应用 ID
     * @param codeGenType 生成类型
     */
    public AICodeGeneratorService getAICodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
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
            // Vue 工程：deepseek-v4-flash + 文件操作工具 + 对话记忆
            case VUE_PROJECT -> {
                MessageWindowChatMemory chatMemory = buildChatMemory(appId);
                yield AiServices.builder(AICodeGeneratorService.class)
                        .chatModel(openAiChatModel)
                        .streamingChatModel(streamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        // 工具统一由 ToolManager 管理：新增工具只需继承 BaseTool 并标注为 Spring Bean
                        .tools((Object[]) toolManager.getAllTools())
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,
                                        "Error: there is no tool called " + toolExecutionRequest.name())
                        )
                        .maxToolCallingRoundTrips(50)
                        .inputGuardrails(promptSafetyInputGuardrail)
                        .build();
            }
            // HTML / 多文件：deepseek-v4-flash + 对话记忆，无工具
            case HTML, MULTI_FILE -> AiServices.builder(AICodeGeneratorService.class)
                    .chatModel(openAiChatModel)
                    .streamingChatModel(streamingChatModel)
                    .chatMemory(buildChatMemory(appId))
                    .inputGuardrails(promptSafetyInputGuardrail)
                    .build();
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
