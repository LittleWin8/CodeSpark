package top.littlewin.codespark.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.littlewin.codespark.service.ChatHistoryService;

import java.time.Duration;

/**
 * AI 服务创建工厂
 */
@Slf4j
@Configuration
public class AICodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * AI 服务示例缓存
     * 缓存策略：
     * 1. 最大缓存1000个示例
     * 2. 写入后 30 分钟过期
     * 3. 访问后 10分钟过期
     */
    private final Cache<Long, AICodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId:{}，原因: {}", key,cause);
            })
            .build();



    /**
     * 根据 appId 获取服务
     *
     * @param appId appID
     * @return
     */
    public AICodeGeneratorService getAICodeGeneratorService(long appId){
        // 有缓存直接获取，无则创建缓存
        return serviceCache.get(appId, this::createAiCodeGeneratorService);
    }

    /**
     * 创建新的 AI 服务实例
     * @param appId
     * @return
     */
    private AICodeGeneratorService createAiCodeGeneratorService(long appId){
        log.info("为 appId: {} 创建 AI 服务实例", appId);
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return AiServices.builder(AICodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemory(chatMemory)
                .build();
    }
}
